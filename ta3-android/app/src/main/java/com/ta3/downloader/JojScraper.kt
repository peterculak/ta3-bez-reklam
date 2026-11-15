package com.ta3.downloader

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Scraper for the joj.sk archive (https://www.joj.sk/relacie) — browse shows/episodes (plain
 * HTML) and resolve a playable URL for either episode link shape.
 *
 * See [docs/joj-archive.md](../../../../../../../../docs/joj-archive.md) for how every endpoint
 * here was found and verified with curl before being written as Kotlin.
 */
object JojScraper {

    private const val TAG = "JojScraper"
    private const val BASE = "https://www.joj.sk"
    private val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val gson = Gson()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun get(url: String, referer: String? = null): String {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (referer != null) builder.header("Referer", referer)
        return client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} for $url")
            resp.body?.string() ?: ""
        }
    }

    private fun post(url: String, jsonBody: String, bearerToken: String? = null): String {
        val builder = Request.Builder().url(url).header("User-Agent", UA)
        if (bearerToken != null) builder.header("Authorization", "Bearer $bearerToken")
        val req = builder.post(jsonBody.toRequestBody(jsonMedia)).build()
        return client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} for $url: ${body.take(300)}")
            body
        }
    }

    // ─── Browsing ───────────────────────────────────────────────────────────────

    /**
     * All shows listed on https://www.joj.sk/relacie, as poster tiles — minus any show whose
     * entire episode list turned out to be subscription-only (see [fetchEpisodes]'s paywall
     * filter): showing a tile that then opens to "no episodes" would be confusing, since we have
     * no JOJ login to offer. Checked via each show's first episode page, with bounded concurrency
     * so ~150 shows don't all hit the network at once.
     */
    suspend fun fetchShows(): List<JojShow> = withContext(Dispatchers.IO) {
        val html = get("$BASE/relacie")
        val doc = Jsoup.parse(html)
        val showRegex = Regex("""^https://www\.joj\.sk/relacie/(\d+-[a-z0-9-]+)$""")

        val shows = doc.select("a[href]").mapNotNull { a ->
            val href = a.attr("abs:href")
            val match = showRegex.find(href) ?: return@mapNotNull null
            val img = a.selectFirst("img")
            val title = img?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val poster = img.attr("abs:src").takeIf { it.isNotEmpty() }
            JojShow(id = match.groupValues[1], title = title, url = href, posterUrl = poster)
        }.distinctBy { it.id }

        val semaphore = kotlinx.coroutines.sync.Semaphore(5)
        coroutineScope {
            shows.map { show ->
                async {
                    val hasFreeEpisodes = semaphore.withPermit { fetchEpisodes(show, page = 1).isNotEmpty() }
                    show to hasFreeEpisodes
                }
            }.awaitAll()
        }.filter { it.second }.map { it.first }
    }

    private val seasonRegex = Regex("""id:\s*'(\d+)',\s*label:\s*'([^']*séria[^']*)'""")

    /**
     * [show]'s seasons, newest first (matching joj.sk's own season selector order — e.g.
     * [(338, "2. séria"), (267, "1. séria")] for a 2-season show). Almost every show has at least
     * one entry, even with a single season. The plain /epizody endpoint (no `season=` param) only
     * ever returns the newest one, so a season id from here is needed to page through the rest.
     */
    suspend fun fetchSeasons(show: JojShow): List<JojSeason> = withContext(Dispatchers.IO) {
        val html = try {
            get("${show.url}/epizody")
        } catch (e: Exception) {
            Log.w(TAG, "fetchSeasons($show) failed: ${e.message}")
            return@withContext emptyList()
        }
        seasonRegex.findAll(html).map { JojSeason(id = it.groupValues[1], label = it.groupValues[2]) }.toList()
    }

    /**
     * One page (20 episodes) of [show]'s episode list within [seasonId] (or the newest season if
     * null — see [fetchSeasonIds]), newest first. [page] is 1-indexed. Returns an empty list once
     * there are no more pages in that season.
     *
     * Tivio episodes that need a JOJ play subscription are filtered out (we only support the
     * free anonymous flow, see [resolveTivioUrl]) — the app has no login UI for JOJ play.
     * Episodes on the joj.sk/relacia/.../epizoda/... embed-HLS path are never paywalled.
     */
    suspend fun fetchEpisodes(show: JojShow, page: Int = 1, seasonId: String? = null): List<Episode> = withContext(Dispatchers.IO) {
        val seasonParam = seasonId?.let { "?season=$it" } ?: ""
        val url = if (page <= 1) "${show.url}/epizody$seasonParam" else "${show.url}/epizody/strana-$page$seasonParam"
        val html = try {
            get(url)
        } catch (e: Exception) {
            Log.w(TAG, "fetchEpisodes($show, page=$page, season=$seasonId) failed: ${e.message}")
            return@withContext emptyList()
        }
        val doc = Jsoup.parse(html)

        // Episode tiles are <a> links to either a joj.sk/relacia/.../epizoda/... page (recent
        // episodes + bonus clips, playable via the public embed HLS) or straight to a
        // play.joj.sk/player/<id> page (older archive, playable via the Tivio getSourceUrl flow).
        // Both shapes are handled by resolvePlayableUrl() later; here we just collect the tile.
        val episodeLinkRegex = Regex("""^https://play\.joj\.sk/player/[^/?]+(\?.*)?$|^https://www\.joj\.sk/relacia/[^/]+/epizoda/[^/]+$""")

        val episodes = doc.select("a[href]").mapNotNull { a ->
            val href = a.attr("abs:href")
            if (!episodeLinkRegex.matches(href)) return@mapNotNull null
            val label = a.selectFirst("p.joj-h4")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val dateSpan = a.select("div.joj-description-14 span").lastOrNull()?.text()?.trim().orEmpty()
            val date = parseJojDate(dateSpan) ?: ""
            val thumbnail = a.selectFirst("img")?.attr("abs:src")?.takeIf { it.isNotEmpty() }
            Episode(title = label, date = date, time = "", url = href, showName = show.title, thumbnailUrl = thumbnail)
        }.distinctBy { it.url }

        // Check Tivio episodes' monetization in parallel (one small unauthenticated Firestore
        // GET each — Firestore's batchGet is blocked by the project's security rules, confirmed
        // with curl, so individual GETs it is) and drop the ones that need a subscription.
        val paidTivioIds = coroutineScope {
            episodes.mapNotNull { tivioIdOf(it.url) }.distinct()
                .map { id -> async { id to isTivioSubscriptionOnly(id) } }
                .awaitAll()
                .filter { it.second }
                .map { it.first }
                .toSet()
        }
        episodes.filterNot { tivioIdOf(it.url) in paidTivioIds }
    }

    private fun tivioIdOf(episodeUrl: String): String? =
        tivioIdRegex.find(episodeUrl)?.groupValues?.get(1)

    private val backdropRegex = Regex("""https://img\.joj\.sk/r1920x740n/[A-Za-z0-9-]+""")

    /**
     * Hero backdrop + short synopsis from [show]'s own page (not the grid poster) — fetched
     * lazily when a show is opened, not during [fetchShows], to avoid ~156 extra requests.
     */
    suspend fun fetchShowDetail(show: JojShow): JojShowDetail = withContext(Dispatchers.IO) {
        val html = try {
            get(show.url)
        } catch (e: Exception) {
            Log.w(TAG, "fetchShowDetail(${show.id}) failed: ${e.message}")
            return@withContext JojShowDetail()
        }
        val doc = Jsoup.parse(html)
        val backdrop = backdropRegex.find(html)?.value
        val description = doc.select("section[data-component-name=ShowTitleBox] p").firstOrNull()?.ownText()?.trim().orEmpty()
        JojShowDetail(backdropUrl = backdrop, description = description)
    }

    private val MONTHS = mapOf(
        "jan" to "01", "feb" to "02", "mar" to "03", "apr" to "04", "máj" to "05", "jun" to "06",
        "jún" to "06", "jul" to "07", "júl" to "07", "aug" to "08", "sep" to "09", "okt" to "10",
        "nov" to "11", "dec" to "12"
    )

    /** "20. aug 2014" -> "2014-08-20". Returns null if the text doesn't match. */
    private fun parseJojDate(text: String): String? {
        val m = Regex("""(\d{1,2})\.\s*([a-záéíóúýčďľňŕšťž]+)\.?\s*(\d{4})""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val day = m.groupValues[1].padStart(2, '0')
        val month = MONTHS[m.groupValues[2].lowercase()] ?: return null
        val year = m.groupValues[3]
        return "$year-$month-$day"
    }

    // ─── Playback resolution ──────────────────────────────────────────────────

    /**
     * Resolve [episodeUrl] (either episode-link shape — see [fetchEpisodes]) to a direct,
     * playable HLS/DASH URL.
     */
    suspend fun resolvePlayableUrl(episodeUrl: String): String = withContext(Dispatchers.IO) {
        val tivioId = tivioIdOf(episodeUrl)
        if (tivioId != null) {
            resolveTivioUrl(tivioId)
        } else {
            resolveEmbedHlsUrl(episodeUrl)
        }
    }

    private val tivioIdRegex = Regex("""play\.joj\.sk/player/([^/?]+)""")
    private val masterM3u8Regex = Regex("""https://[A-Za-z0-9.-]+/[^"'\\\s]*master\.m3u8[^"'\\\s]*""")
    private val embedIframeRegex = Regex("""https://media\.joj\.sk/embed/[A-Za-z0-9]+""")

    /** Path A: joj.sk/relacia/.../epizoda/... pages, which embed a public HLS master.m3u8. */
    private fun resolveEmbedHlsUrl(episodePageUrl: String): String {
        val pageHtml = get(episodePageUrl)
        masterM3u8Regex.find(pageHtml)?.let { return it.value }

        val embedUrl = embedIframeRegex.find(pageHtml)?.value
            ?: throw Exception("No media.joj.sk embed found on $episodePageUrl")
        val embedHtml = get(embedUrl, referer = "$BASE/")
        return masterM3u8Regex.find(embedHtml)?.value
            ?: throw Exception("No master.m3u8 found in embed page $embedUrl")
    }

    // ─── Tivio (play.joj.sk) ────────────────────────────────────────────────────

    private const val TIVIO_FIREBASE_API_KEY = "AIzaSyB02udgMkNLADkLJ_w5YNBMR2VR1WHfusI"
    private const val TIVIO_GET_SOURCE_URL = "https://europe-west3-tivio-production.cloudfunctions.net/getSourceUrl"
    private const val TIVIO_FIRESTORE_VIDEOS = "https://firestore.googleapis.com/v1/projects/tivio-production/databases/(default)/documents/videos"

    /**
     * True if [tivioId]'s Firestore doc lists a "subscription" monetization — i.e. it needs a
     * paid JOJ play account, which we have no login for (we only use anonymous access; see
     * docs/joj-archive.md). Fails open (false) on any error so a Firestore hiccup doesn't hide
     * an otherwise-free episode.
     */
    private fun isTivioSubscriptionOnly(tivioId: String): Boolean {
        return try {
            val body = get("$TIVIO_FIRESTORE_VIDEOS/$tivioId")
            val doc = gson.fromJson(body, JsonObject::class.java)
            val monetizations = doc.getAsJsonObject("fields")
                ?.getAsJsonObject("monetizations")?.getAsJsonObject("arrayValue")
                ?.getAsJsonArray("values") ?: return false
            monetizations.any { m ->
                m.asJsonObject.getAsJsonObject("mapValue")?.getAsJsonObject("fields")
                    ?.getAsJsonObject("type")?.get("stringValue")?.asString == "subscription"
            }
        } catch (e: Exception) {
            // A 403 means the doc itself isn't even readable (seen on some shows, e.g. "Ako som
            // prežil" — the getSourceUrl call then 404s with "No video with id ... found", i.e.
            // dead/inaccessible content on JOJ's own side) — treat that like a paywall and drop
            // it, rather than shipping an episode that fails to play. Any other error (network
            // blip, timeout) still fails open so it doesn't hide otherwise-fine episodes.
            val unavailable = e.message?.contains("HTTP 403") == true
            Log.w(TAG, "isTivioSubscriptionOnly($tivioId) failed, assuming ${if (unavailable) "unavailable" else "free"}: ${e.message}")
            unavailable
        }
    }

    @Volatile
    private var tivioIdToken: String? = null

    private fun fetchAnonymousTivioIdToken(): String {
        val body = post(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$TIVIO_FIREBASE_API_KEY",
            """{"returnSecureToken":true}"""
        )
        val json = gson.fromJson(body, JsonObject::class.java)
        return json.get("idToken")?.asString ?: throw Exception("No idToken in anonymous sign-up response")
    }

    /** Path B: play.joj.sk/player/<tivioId> pages (Tivio). See docs/joj-archive.md for how this works. */
    private fun resolveTivioUrl(tivioId: String, retryOnAuthFailure: Boolean = true): String {
        val token = tivioIdToken ?: fetchAnonymousTivioIdToken().also { tivioIdToken = it }

        val requestJson = """
            {"data":{"id":"$tivioId","documentType":"video","capabilities":[
                {"codec":"h264","protocol":"hls","encryption":"none"}
            ]}}
        """.trimIndent()

        val responseBody = try {
            post(TIVIO_GET_SOURCE_URL, requestJson, bearerToken = token)
        } catch (e: Exception) {
            if (retryOnAuthFailure && e.message?.contains("401") == true) {
                tivioIdToken = null
                return resolveTivioUrl(tivioId, retryOnAuthFailure = false)
            }
            // fetchEpisodes() already filters out subscription-only episodes, but a direct
            // resolvePlayableUrl() call (or stale list data) could still hit one — give a clear
            // message instead of the raw "User not found" the cloud function returns for this case.
            if (e.message?.contains("User not found") == true) {
                throw Exception("Toto video vyžaduje predplatné JOJ play — aplikácia podporuje len bezplatný obsah")
            }
            throw e
        }

        val json = gson.fromJson(responseBody, JsonObject::class.java)
        return json.getAsJsonObject("result")?.get("url")?.asString
            ?: throw Exception("No result.url in getSourceUrl response for $tivioId: ${responseBody.take(300)}")
    }
}

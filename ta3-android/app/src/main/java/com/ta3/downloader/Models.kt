package com.ta3.downloader

import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

// ─── Data Models ──────────────────────────────────────────────────────────────

data class Show(
    val name: String,
    val displayName: String,
    val url: String
)

data class Episode(
    val title: String,
    val date: String,
    val time: String = "",   // HH:MM extracted from article_date span, empty if not available
    val url: String,       // episode page URL
    val showName: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null
)

data class DownloadedFile(
    val episodeUrl: String,
    val title: String,
    val date: String,
    val showName: String,
    val localPath: String,
    val fileSizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
    /** "manual" (tapped in the app), "auto" (background downloader) or null when unknown (older entries, recovered files). */
    val source: String? = null
)

data class PendingDownload(
    val episodeUrl: String,
    val title: String,
    val date: String,
    val time: String = "",
    val showName: String,
    val directUrl: String? = null,
    val enqueuedAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0
)

enum class DownloadStatus {
    IDLE,
    RESOLVING,
    DOWNLOADING,
    DONE,
    FAILED
}

data class ActiveDownload(
    val episodeUrl: String,
    val title: String,
    val showName: String,
    val progress: Float,        // 0f..1f
    val status: DownloadStatus,
    val errorMessage: String? = null
)

// ─── JOJ Archive Models ────────────────────────────────────────────────────────

/**
 * A show from the joj.sk archive (https://www.joj.sk/relacie), discovered by scraping —
 * unlike [Show], there's no fixed list; see [JojScraper].
 */
data class JojShow(
    /** The "<numericId>-<slug>" path segment from /relacie/<id>-<slug>, e.g. "101-nevinni". Doubles as Episode.showName. */
    val id: String,
    val title: String,
    val url: String,
    val posterUrl: String? = null
)

/** Hero image + short synopsis from a show's own joj.sk page, fetched lazily when it's opened. */
data class JojShowDetail(
    val backdropUrl: String? = null,
    val description: String = ""
)

/** One season from a show's season selector, e.g. id="338" label="2. séria". See JojScraper.fetchSeasons. */
data class JojSeason(val id: String, val label: String)

/**
 * On-disk cache of [JojShow.fetchShows]'s ~156-show, per-show-paywall-checked result, so the JOJ
 * tab shows something instantly on cold start instead of re-scraping the whole archive every time.
 * Kept in app-private storage (no permission, not user content) — just a performance cache.
 */
object JojShowCache {
    private const val FILE_NAME = "joj_shows_cache.json"
    /** How long a cached list is used without a refresh — see [JojShowsRefreshWorker] for the periodic background refresh. */
    const val MAX_AGE_MS = 3 * 60 * 60 * 1000L
    private val gson = Gson()

    data class Cached(val shows: List<JojShow>, val savedAt: Long)

    private fun file(context: android.content.Context) = File(context.filesDir, FILE_NAME)

    fun load(context: android.content.Context): Cached? = try {
        val f = file(context)
        if (!f.exists()) {
            null
        } else {
            val cached = gson.fromJson(f.readText(), Cached::class.java)
            // Gson bypasses Kotlin's constructor null-checks on a malformed/older-shape JSON file
            // (e.g. a missing "shows" key leaves it null despite the non-null type) — validate
            // explicitly and self-heal by dropping the bad file, rather than crashing later when
            // this flows into UiState.copy().
            val shows: List<JojShow>? = cached?.shows
            if (shows == null) {
                f.delete()
                null
            } else {
                cached
            }
        }
    } catch (e: Exception) {
        try { file(context).delete() } catch (_: Exception) { }
        null
    }

    fun save(context: android.content.Context, shows: List<JojShow>) {
        try {
            file(context).writeText(gson.toJson(Cached(shows, System.currentTimeMillis())))
        } catch (e: Exception) {
            // best-effort; a failed write just means no instant load next time
        }
    }

    fun isStale(cached: Cached): Boolean = System.currentTimeMillis() - cached.savedAt > MAX_AGE_MS
}

// ─── Prehraj.to Models ────────────────────────────────────────────────────────

data class PrehrajMovie(
    val title: String,
    val year: String = "",
    val pageUrl: String,        // https://prehraj.to/<slug>/<id>
    val thumbnailUrl: String = ""
)

// ─── TA3 Show Definitions (same URLs as ta3-cli/config.json) ─────────────────

val TA3_SHOWS = listOf(
    Show(
        name = "hlavne-spravy",
        displayName = "Hlavné správy",
        url = "https://www.ta3.com/hlavne-spravy"
    ),
    Show(
        name = "tlacove-besedy",
        displayName = "Tlačové besedy",
        url = "https://www.ta3.com/tlacove-besedy"
    ),
    Show(
        name = "tema-dna",
        displayName = "Téma dňa",
        url = "https://www.ta3.com/tema-dna"
    ),
    Show(
        name = "tema",
        displayName = "Téma",
        url = "https://www.ta3.com/tema"
    ),
    Show(
        name = "kral-na-tahu",
        displayName = "Kráľ na ťahu",
        url = "https://www.ta3.com/kral-na-tahu"
    ),
    Show(
        name = "v-politike",
        displayName = "V politike",
        url = "https://www.ta3.com/v-politike"
    ),
    Show(
        name = "o-tom-potom",
        displayName = "O tom potom",
        url = "https://www.ta3.com/o-tom-potom"
    ),
    Show(
        name = "podcast-tema-z-europarlamentu",
        displayName = "Téma z europarlamentu",
        url = "https://www.ta3.com/podcast-tema-z-europarlamentu"
    ),
    Show(
        name = "udalosti-tyzdna",
        displayName = "Udalosti tyzdna",
        url = "https://www.ta3.com/udalosti-tyzdna"
    )
)

// ─── STVR Show Definitions ──────────────────────────────────────────────────

val STVR_SHOWS = listOf(
    Show(
        name = "odpovede-s-ankou-zitnou",
        displayName = "Odpovede s Ankou Žitnou",
        url = "https://www.stvr.sk/televizia/archiv/22386"
    ),
    Show(
        name = "komentare-dna",
        displayName = "Komentáre dňa",
        url = "https://www.stvr.sk/televizia/archiv/23659"
    ),
    Show(
        name = "o-5-minut-12",
        displayName = "O 5 minút 12",
        url = "https://www.stvr.sk/televizia/archiv/14036"
    ),
    Show(
        name = "sobotne-dialogy",
        displayName = "Sobotné dialógy",
        url = "https://www.stvr.sk/televizia/archiv/23649"
    )
)

// ─── Tyzden Show Definitions ──────────────────────────────────────────────────

val TYZDEN_SHOWS = listOf(
    Show(
        name = "bezpecnostny-radar",
        displayName = "Bezpečnostný radar",
        url = "https://www.tyzden.sk/podcasty/bezpecnostny-radar-generala-pavla-macka/"
    )
)

// ─── YouTube Channel Definitions ─────────────────────────────────────────────

data class YouTubeChannel(
    val name: String,           // internal key, e.g. "brano-zavodsky"
    val displayName: String,    // shown in UI, e.g. "Braňo Závodský"
    val channelId: String,      // YouTube channel ID starting with UC...
    val channelUrl: String,     // full YouTube channel URL
    val tab: String = "streams", // which page to scrape: "streams", "videos", or e.g. "search?query=Na+telo"
    /** If set, only videos whose title contains this text (case-insensitive) are listed/downloaded. */
    val titleFilter: String? = null,
    /** Minimum episode length (minutes) used until the user changes it in Settings. */
    val defaultMinDurationMinutes: Int = 0
)

val YOUTUBE_CHANNELS = listOf(
    YouTubeChannel(
        name = "brano-zavodsky",
        displayName = "Braňo Závodský",
        channelId = "UCW1pHS8ZHpI33xnOhBvSGyQ",
        channelUrl = "https://www.youtube.com/@Bra%C5%88oZ%C3%A1vodsk%C3%BDNa%C5%BEivo"
    ),
    YouTubeChannel(
        name = "bardy-a-kacer",
        displayName = "Bárdy & Káčer",
        channelId = "UC2kzV2zMZxLE1ywlO1e3GTg",
        channelUrl = "https://www.youtube.com/@BardyAndKacer",
        tab = "videos"
    ),
    YouTubeChannel(
        name = "portalmarker",
        displayName = "Marker.sk",
        channelId = "UC28hsAjds3mQzScBtYtkGsQ",
        channelUrl = "https://www.youtube.com/@PortalMarker",
        tab = "videos"
    ),
    // "Na telo PLUS" is one series on Televízia Markíza's busy channel (dozens of uploads a day), so the plain
    // "videos" tab would push it off the first page within days. The channel's own search lists the series
    // going back months; the title filter then drops anything else the search returns (notably the separate
    // Sunday "Na telo" duels, titled "A vs. B | Na telo"). Besides the full episodes (~36-46 min) the series
    // has 1-minute teaser clips, hence the minimum duration.
    YouTubeChannel(
        name = "na-telo-plus",
        displayName = "Na telo PLUS",
        channelId = "",
        channelUrl = "https://www.youtube.com/@TeleviziaMarkiza",
        tab = "search?query=Na+telo+PLUS",
        titleFilter = "Na telo PLUS",
        defaultMinDurationMinutes = 15
    ),
    // "Sme v kaviarni" is a ~90-minute podcast on TV JOJ's channel (titled "24 podcast - Sme v kaviarni: ...").
    // Same approach as above: the channel search reaches back months, the title filter keeps only this series.
    YouTubeChannel(
        name = "sme-v-kaviarni",
        displayName = "Sme v kaviarni",
        channelId = "",
        channelUrl = "https://www.youtube.com/@TVJOJofficial",
        tab = "search?query=Sme+v+kaviarni",
        titleFilter = "Sme v kaviarni"
    )
)

object CustomChannelManager {
    private val gson = Gson()
    private val customChannelsFile: File
        get() {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TA3")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, "custom_channels.json")
        }

    fun getCustomChannels(): List<YouTubeChannel> {
        return try {
            if (!customChannelsFile.exists()) emptyList()
            else {
                val type = object : TypeToken<List<YouTubeChannel>>() {}.type
                val list: List<YouTubeChannel>? = gson.fromJson(customChannelsFile.readText(), type)
                list?.filter { it.name != null } ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomChannels(channels: List<YouTubeChannel>) {
        try {
            customChannelsFile.writeText(gson.toJson(channels))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addChannel(channel: YouTubeChannel) {
        val current = getCustomChannels().toMutableList()
        if (current.none { it.name == channel.name }) {
            current.add(channel)
            saveCustomChannels(current)
        }
    }

    fun removeChannel(name: String) {
        val current = getCustomChannels().toMutableList()
        current.removeAll { it.name == name }
        saveCustomChannels(current)
    }

    fun updateChannel(oldName: String, updatedChannel: YouTubeChannel) {
        val current = getCustomChannels().toMutableList()
        val index = current.indexOfFirst { it.name == oldName }
        if (index != -1) {
            current[index] = updatedChannel
            saveCustomChannels(current)
        }
    }

    fun getAllYouTubeChannels(): List<YouTubeChannel> {
        return YOUTUBE_CHANNELS + getCustomChannels()
    }
}

// ─── TMDB (browse catalog for the Prehraj tab) ────────────────────────────────

data class TmdbItem(
    val id: Int,
    val mediaType: String,          // "movie" | "tv"
    val title: String,              // localized (cs-CZ) title
    val originalTitle: String,
    val year: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String = "",
    val rating: Double = 0.0
) {
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
    /** Large image for the detail page: backdrop if there is one, else the poster at high resolution. */
    val heroUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
        ?: posterPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    val isTv: Boolean get() = mediaType == "tv"
}

data class TmdbSearchPage(val items: List<TmdbItem>, val page: Int, val totalPages: Int)

data class TmdbDetails(
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int = 0,
    val tagline: String = "",
    val overview: String = ""
)

data class TmdbGenre(val id: Int, val name: String) {
    companion object {
        /** Pseudo-genres shown as chips; each is backed by a TMDB list endpoint rather than a genre filter. */
        val TRENDING = TmdbGenre(-1, "Trendy")
        val POPULAR = TmdbGenre(-2, "Populárne")
        val TOP_RATED = TmdbGenre(-3, "Najlepšie hodnotené")
        val SLOVAK = TmdbGenre(-4, "Slovenské")
        val CZECH = TmdbGenre(-5, "České")
        val LISTS = listOf(TRENDING, POPULAR, TOP_RATED, SLOVAK, CZECH)

        /** ISO origin country behind the country pseudo-genres. */
        fun country(g: TmdbGenre): String? = when (g) { SLOVAK -> "SK"; CZECH -> "CZ"; else -> null }

        fun category(g: TmdbGenre): TmdbApi.Category? = when (g) {
            TRENDING -> TmdbApi.Category.TRENDING
            POPULAR -> TmdbApi.Category.POPULAR
            TOP_RATED -> TmdbApi.Category.TOP_RATED
            else -> null
        }
    }
}
/** Ordering for genre / country grids (backed by TMDB discover's sort_by). */
enum class TmdbSort(val label: String) {
    RECOMMENDED("Odporúčané"), NAME("Názov"), YEAR("Rok"), RATING("Hodnotenie");

    fun param(type: String): String = when (this) {
        RECOMMENDED -> "popularity.desc"
        NAME -> if (type == "tv") "name.asc" else "title.asc"
        YEAR -> if (type == "tv") "first_air_date.desc" else "primary_release_date.desc"
        RATING -> "vote_average.desc"
    }
    /** Minimum votes so "best rated" isn't dominated by titles with a single 10/10 vote. */
    val minVotes: Int get() = if (this == RATING) 100 else 0
}
data class TmdbSeason(val number: Int, val episodeCount: Int)
data class TmdbEpisode(val number: Int, val name: String)

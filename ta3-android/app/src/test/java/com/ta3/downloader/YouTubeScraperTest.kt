package com.ta3.downloader

import org.junit.Test
import org.junit.Assert.*

class YouTubeScraperTest {

    @Test
    fun parseA11yDuration() {
        val label1 = "Do živého | Keď civilizácia kolabuje 1 hour, 5 minutes"
        val label2 = "7 dní v kocke: Hlavným problémom nie je Trump, ale Izrael 17 minutes"
        val label3 = "16 minutes, 58 seconds"
        val label4 = "1 hour, 21 minutes, 35 seconds"
        val label5 = "45 seconds"

        assertEquals(3900, parseA11yDurationToSeconds(label1))
        assertEquals(1020, parseA11yDurationToSeconds(label2))
        assertEquals(1018, parseA11yDurationToSeconds(label3))
        assertEquals(4895, parseA11yDurationToSeconds(label4))
        assertEquals(45, parseA11yDurationToSeconds(label5))
    }

    private fun ep(title: String) = Episode(title = title, date = "2026-11-15", url = "https://www.youtube.com/watch?v=x", showName = "na-telo-plus")

    @Test
    fun titleFilterKeepsOnlyMatchingEpisodes() {
        // real titles from Televízia Markíza's channel search for "Na telo PLUS"
        val all = listOf(
            ep("Michal Šipoš: Predčasné voľby nebudú, oligarchovia to nedovolia | Na telo PLUS"),
            ep("Kamil Šaško (Hlas-SD) vs. Tomáš Szalay (SaS) | Na telo"),
            ep("Divácke otázky - Milan Majerský (KDH) vs. Andrej Danko (SNS) | Na telo"),
            ep("Ani Ronaldo toľkokrát neprestúpil do iného klubu ako Malatinec | NA TELO PLUS"),
            ep("FARMA 18 X-TRA | #E48 | Úprimné priznanie plné bolesti"),
        )
        val kept = YouTubeScraper.filterByTitle(all, "Na telo PLUS")
        assertEquals(2, kept.size) // the Sunday "Na telo" duels and unrelated shows are dropped
        assertTrue(kept.all { it.title.contains("na telo plus", ignoreCase = true) })
    }

    @Test
    fun smeVKaviarniFilterKeepsOnlyThePodcast() {
        val all = listOf(
            ep("24 Podcast - Sme v kaviarni: Pokakaný z Putina"),
            ep("24 podcast: Sme v kaviarni - Level Tabák"),
            ep("Televízne noviny | 14. novembra"),
        )
        val c = YOUTUBE_CHANNELS.first { it.name == "sme-v-kaviarni" }
        assertEquals(2, YouTubeScraper.filterByTitle(all, c.titleFilter).size)
    }

    @Test
    fun noTitleFilterKeepsEverything() {
        val all = listOf(ep("a"), ep("b"))
        assertEquals(all, YouTubeScraper.filterByTitle(all, null))
        assertEquals(all, YouTubeScraper.filterByTitle(all, "  "))
    }

    @Test
    fun naTeloPlusChannelSkipsShortClipsByDefault() {
        val c = YOUTUBE_CHANNELS.first { it.name == "na-telo-plus" }
        assertEquals("Na telo PLUS", c.titleFilter)
        assertTrue("default must be one of the options offered in Settings", c.defaultMinDurationMinutes in AppSettings.MIN_DURATION_OPTIONS)
        // teaser clips are about a minute, full episodes are 36+ min
        assertTrue(c.defaultMinDurationMinutes * 60 > 2 * 60)
        assertTrue(c.defaultMinDurationMinutes * 60 < 36 * 60)
    }

    private fun parseA11yDurationToSeconds(text: String): Int {
        var total = 0
        val hMatch = Regex("""(\d+)\s+hour""").find(text)
        if (hMatch != null) total += hMatch.groupValues[1].toInt() * 3600
        
        val mMatch = Regex("""(\d+)\s+minute""").find(text)
        if (mMatch != null) total += mMatch.groupValues[1].toInt() * 60
        
        val sMatch = Regex("""(\d+)\s+second""").find(text)
        if (sMatch != null) total += sMatch.groupValues[1].toInt()
        
        return total
    }
}

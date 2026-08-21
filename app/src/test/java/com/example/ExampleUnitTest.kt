package com.example

import com.example.service.YoutubeExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testNormalizeYoutuBeWatchLink() {
        val input = "https://youtu.be/watch?v=KHYfygE9FYc&list=RDKHYfygE9FYc&start_radio=1"
        val expected = "https://www.youtube.com/watch?v=KHYfygE9FYc&list=RDKHYfygE9FYc&start_radio=1"
        assertEquals(expected, YoutubeExtractor.normalizeYouTubeUrl(input))
    }

    @Test
    fun testNormalizeYoutuBeShortLink() {
        val input = "https://youtu.be/KHYfygE9FYc"
        val expected = "https://www.youtube.com/watch?v=KHYfygE9FYc"
        assertEquals(expected, YoutubeExtractor.normalizeYouTubeUrl(input))
    }

    @Test
    fun testNormalizeYoutuBeShortLinkWithQueryParams() {
        val input = "https://youtu.be/KHYfygE9FYc?list=RDKHYfygE9FYc&start_radio=1"
        val expected = "https://www.youtube.com/watch?v=KHYfygE9FYc&list=RDKHYfygE9FYc&start_radio=1"
        assertEquals(expected, YoutubeExtractor.normalizeYouTubeUrl(input))
    }

    @Test
    fun testNormalizeYoutuBeWithoutProtocol() {
        val input = "youtu.be/watch?v=KHYfygE9FYc&list=RDKHYfygE9FYc&start_radio=1"
        val expected = "https://www.youtube.com/watch?v=KHYfygE9FYc&list=RDKHYfygE9FYc&start_radio=1"
        assertEquals(expected, YoutubeExtractor.normalizeYouTubeUrl(input))
    }

    @Test
    fun testStandardYouTubeUrlUnchanged() {
        val input = "https://www.youtube.com/watch?v=KHYfygE9FYc&list=RDKHYfygE9FYc"
        val expected = "https://www.youtube.com/watch?v=KHYfygE9FYc&list=RDKHYfygE9FYc"
        assertEquals(expected, YoutubeExtractor.normalizeYouTubeUrl(input))
    }
}

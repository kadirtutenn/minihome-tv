package com.minihome.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelParseTest {
    @Test
    fun skipsInvalidAndDuplicatesAndSorts() {
        val list = ChannelRepository.parse(
            """[
              {"id":"b","number":2,"name":"B","url":"https://x/b.m3u8","backup":["https://x/b.m3u8","https://y/b.m3u8","bad"]},
              {"id":"a","number":1,"name":"A","url":"https://x/a.m3u8"},
              {"id":"dupUrl","number":3,"url":"https://x/a.m3u8"},
              {"id":"dupNum","number":1,"url":"https://x/c.m3u8"},
              {"id":"noUrl","number":4},
              42
            ]"""
        )
        assertEquals(listOf("a", "b"), list.map { it.id })
        assertEquals(listOf("https://y/b.m3u8"), list[1].backupUrls)
    }

    @Test
    fun bundledListIsValid() {
        val raw = java.io.File("src/main/assets/channels.json").readText()
        val list = ChannelRepository.parse(raw)
        assertEquals(raw.split("\"url\"").size - 1, list.size) // nothing silently dropped
    }
}

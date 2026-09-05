package com.iykyk.videocollage.processing

import org.junit.Assert.*
import org.junit.Test

class CollageSaverTest {

    @Test
    fun `default filename starts with collage prefix`() {
        val filename = CollageSaver.defaultFilename()
        assertTrue("Filename should start with 'collage_'", filename.startsWith("collage_"))
    }

    @Test
    fun `default filename ends with png extension`() {
        val filename = CollageSaver.defaultFilename()
        assertTrue("Filename should end with '.png'", filename.endsWith(".png"))
    }

    @Test
    fun `default filename contains timestamp`() {
        val before = System.currentTimeMillis()
        val filename = CollageSaver.defaultFilename()
        val after = System.currentTimeMillis()

        val timestampStr = filename.removePrefix("collage_").removeSuffix(".png")
        val timestamp = timestampStr.toLongOrNull()
        assertNotNull("Filename should contain a numeric timestamp", timestamp)
        assertTrue("Timestamp should be >= before", timestamp!! >= before)
        assertTrue("Timestamp should be <= after", timestamp <= after)
    }

    @Test
    fun `default filename has correct format`() {
        val filename = CollageSaver.defaultFilename()
        val parts = filename.split("_", ".")
        assertEquals("Should have 3 parts", 3, parts.size)
        assertEquals("Prefix should be 'collage'", "collage", parts[0])
        assertNotNull("Timestamp should be numeric", parts[1].toLongOrNull())
        assertEquals("Extension should be 'png'", "png", parts[2])
    }
}

package com.iykyk.videocollage.processing

import org.junit.Assert.*
import org.junit.Test

class CollageGeneratorTest {

    private val config = CollageConfig(
        outputWidth = 1080,
        outputHeight = 1920,
        gapPx = 12,
        marginPx = 24
    )

    private val generator = CollageGenerator(config)

    @Test
    fun `layout for 1 person produces single tile`() {
        val layout = generator.computeLayout(1, 1080, 1920)
        assertEquals(1, layout.tiles.size)
        val tile = layout.tiles[0]
        assertTrue("Tile should fill most of the width", tile.width > 900)
        assertTrue("Tile should fill most of the height", tile.height > 1700)
    }

    @Test
    fun `layout for 2 persons produces two tiles stacked`() {
        val layout = generator.computeLayout(2, 1080, 1920)
        assertEquals(2, layout.tiles.size)
        val t1 = layout.tiles[0]
        val t2 = layout.tiles[1]
        assertEquals("Tiles should have same width", t1.width, t2.width, 1f)
        assertTrue("Second tile should be below first", t2.y > t1.y)
    }

    @Test
    fun `layout for 3 persons produces one large top two small bottom`() {
        val layout = generator.computeLayout(3, 1080, 1920)
        assertEquals(3, layout.tiles.size)
        val top = layout.tiles[0]
        val bottomLeft = layout.tiles[1]
        val bottomRight = layout.tiles[2]
        assertTrue("Top tile should be wider (full width)", top.width > bottomLeft.width)
        assertEquals("Bottom tiles should be side by side", bottomLeft.y, bottomRight.y, 1f)
        assertTrue("Bottom right should be to the right", bottomRight.x > bottomLeft.x)
    }

    @Test
    fun `layout for 4 persons produces 2x2 grid`() {
        val layout = generator.computeLayout(4, 1080, 1920)
        assertEquals(4, layout.tiles.size)
        val t0 = layout.tiles[0]
        val t1 = layout.tiles[1]
        val t2 = layout.tiles[2]
        val t3 = layout.tiles[3]
        assertEquals("Top row same y", t0.y, t1.y, 1f)
        assertEquals("Bottom row same y", t2.y, t3.y, 1f)
        assertTrue("Bottom row below top", t2.y > t0.y)
        assertEquals("Left column same x", t0.x, t2.x, 1f)
        assertEquals("Right column same x", t1.x, t3.x, 1f)
    }

    @Test
    fun `layout for 5 persons produces 2 top 3 bottom`() {
        val layout = generator.computeLayout(5, 1080, 1920)
        assertEquals(5, layout.tiles.size)
        assertEquals("Top row same y", layout.tiles[0].y, layout.tiles[1].y, 1f)
        assertEquals("Bottom row same y", layout.tiles[2].y, layout.tiles[3].y, 1f)
        assertEquals("Bottom row same y", layout.tiles[3].y, layout.tiles[4].y, 1f)
        assertTrue("Bottom row below top", layout.tiles[2].y > layout.tiles[0].y)
    }

    @Test
    fun `layout for 6 persons produces 3x2 grid`() {
        val layout = generator.computeLayout(6, 1080, 1920)
        assertEquals(6, layout.tiles.size)
        assertEquals("First row same y", layout.tiles[0].y, layout.tiles[1].y, 1f)
        assertEquals("First row same y", layout.tiles[1].y, layout.tiles[2].y, 1f)
        assertEquals("Second row same y", layout.tiles[3].y, layout.tiles[4].y, 1f)
        assertEquals("Second row same y", layout.tiles[4].y, layout.tiles[5].y, 1f)
        assertTrue("Second row below first", layout.tiles[3].y > layout.tiles[0].y)
    }

    @Test
    fun `layout for 9 persons produces 3x3 grid`() {
        val layout = generator.computeLayout(9, 1080, 1920)
        assertEquals(9, layout.tiles.size)
        val row0Y = layout.tiles[0].y
        val row3Y = layout.tiles[3].y
        val row6Y = layout.tiles[6].y
        assertEquals("Row 0 tiles same y", row0Y, layout.tiles[1].y, 1f)
        assertEquals("Row 1 tiles same y", row3Y, layout.tiles[4].y, 1f)
        assertEquals("Row 2 tiles same y", row6Y, layout.tiles[7].y, 1f)
        assertTrue("Row 1 below row 0", row3Y > row0Y)
        assertTrue("Row 2 below row 1", row6Y > row3Y)
    }

    @Test
    fun `all tiles fit within bounds`() {
        for (count in 1..9) {
            val layout = generator.computeLayout(count, 1080, 1920)
            for ((i, tile) in layout.tiles.withIndex()) {
                assertTrue("Tile $i x >= 0", tile.x >= 0f)
                assertTrue("Tile $i y >= 0", tile.y >= 0f)
                assertTrue("Tile $i right <= 1080", tile.x + tile.width <= 1080f)
                assertTrue("Tile $i bottom <= 1920", tile.y + tile.height <= 1920f)
                assertTrue("Tile $i has positive width", tile.width > 0f)
                assertTrue("Tile $i has positive height", tile.height > 0f)
            }
        }
    }

    @Test
    fun `tiles do not overlap`() {
        for (count in 1..6) {
            val layout = generator.computeLayout(count, 1080, 1920)
            for (i in layout.tiles.indices) {
                for (j in i + 1 until layout.tiles.size) {
                    val a = layout.tiles[i]
                    val b = layout.tiles[j]
                    val noOverlap = a.x + a.width <= b.x || b.x + b.width <= a.x ||
                            a.y + a.height <= b.y || b.y + b.height <= a.y
                    assertTrue("Tiles $i and $j should not overlap", noOverlap)
                }
            }
        }
    }

    @Test
    fun `empty count throws`() {
        try {
            generator.computeLayout(0, 1080, 1920)
            fail("Should throw for 0 persons")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}

package com.iykyk.videocollage.processing

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppearanceCounterTest {

    private lateinit var counter: AppearanceCounter

    @Before
    fun setup() {
        counter = AppearanceCounter(gapToleranceMs = 1000L)
    }

    private fun obs(personId: Int, timestampMs: Long, quality: Float = 1f) =
        AppearanceCounter.TimestampedObservation(personId, timestampMs, quality)

    @Test
    fun `empty input returns empty list`() {
        val result = counter.segmentTimestamps(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single observation creates single segment`() {
        val observations = listOf(obs(0, 1000L))
        val result = counter.segmentTimestamps(observations)

        assertEquals(1, result.size)
        assertEquals(1000L, result.first().startTimestampMs)
        assertEquals(1000L, result.first().endTimestampMs)
        assertEquals(1, result.first().observationCount)
    }

    @Test
    fun `continuous observations form single segment`() {
        val observations = listOf(
            obs(0, 0L),
            obs(0, 200L),
            obs(0, 400L),
            obs(0, 600L),
            obs(0, 800L)
        )
        val result = counter.segmentTimestamps(observations)

        assertEquals(1, result.size)
        assertEquals(5, result.first().observationCount)
        assertEquals(0L, result.first().startTimestampMs)
        assertEquals(800L, result.first().endTimestampMs)
    }

    @Test
    fun `large gap splits into separate segments`() {
        val observations = listOf(
            obs(0, 0L),
            obs(0, 200L),
            obs(0, 2000L),
            obs(0, 2200L)
        )
        val result = counter.segmentTimestamps(observations)

        assertEquals(2, result.size)
        assertEquals(2, result[0].observationCount)
        assertEquals(2, result[1].observationCount)
    }

    @Test
    fun `gap exactly at tolerance stays in same segment`() {
        val observations = listOf(
            obs(0, 0L),
            obs(0, 1000L)
        )
        val result = counter.segmentTimestamps(observations)

        assertEquals(1, result.size)
        assertEquals(2, result.first().observationCount)
    }

    @Test
    fun `gap just above tolerance splits segments`() {
        val observations = listOf(
            obs(0, 0L),
            obs(0, 1001L)
        )
        val result = counter.segmentTimestamps(observations)

        assertEquals(2, result.size)
        assertEquals(1, result[0].observationCount)
        assertEquals(1, result[1].observationCount)
    }

    @Test
    fun `multiple people tracked independently`() {
        val observations = listOf(
            obs(0, 0L),
            obs(1, 100L),
            obs(0, 200L),
            obs(1, 300L),
            obs(0, 400L)
        )
        val result = counter.segmentTimestamps(observations)

        val person0Segments = result.filter { it.personId == 0 }
        val person1Segments = result.filter { it.personId == 1 }

        assertEquals(1, person0Segments.size)
        assertEquals(3, person0Segments.first().observationCount)
        assertEquals(1, person1Segments.size)
        assertEquals(2, person1Segments.first().observationCount)
    }

    @Test
    fun `multiple people with gaps form independent segments`() {
        val observations = listOf(
            obs(0, 0L),
            obs(0, 200L),
            obs(0, 3000L),
            obs(1, 100L),
            obs(1, 400L)
        )
        val result = counter.segmentTimestamps(observations)

        val person0Segments = result.filter { it.personId == 0 }
        val person1Segments = result.filter { it.personId == 1 }

        assertEquals(2, person0Segments.size)
        assertEquals(1, person1Segments.size)
    }

    @Test
    fun `best candidate is highest quality observation`() {
        val observations = listOf(
            obs(0, 0L, quality = 0.3f),
            obs(0, 200L, quality = 0.9f),
            obs(0, 400L, quality = 0.5f)
        )
        val result = counter.segmentTimestamps(observations)

        assertEquals(1, result.size)
        assertEquals(1, result.first().bestCandidateIndex)
    }

    @Test
    fun `timestamps are sorted correctly`() {
        val observations = listOf(
            obs(0, 500L),
            obs(0, 100L),
            obs(0, 300L)
        )
        val result = counter.segmentTimestamps(observations)

        assertEquals(1, result.size)
        assertEquals(100L, result.first().startTimestampMs)
        assertEquals(500L, result.first().endTimestampMs)
    }

    @Test
    fun `three segments with two gaps`() {
        val observations = listOf(
            obs(0, 0L),
            obs(0, 200L),
            obs(0, 2000L),
            obs(0, 2200L),
            obs(0, 4000L),
            obs(0, 4200L)
        )
        val result = counter.segmentTimestamps(observations)

        assertEquals(3, result.size)
        assertEquals(2, result[0].observationCount)
        assertEquals(2, result[1].observationCount)
        assertEquals(2, result[2].observationCount)
    }

    @Test
    fun `configurable gap tolerance changes segmentation`() {
        val strictCounter = AppearanceCounter(gapToleranceMs = 500L)
        val looseCounter = AppearanceCounter(gapToleranceMs = 2000L)

        val observations = listOf(
            obs(0, 0L),
            obs(0, 800L),
            obs(0, 1600L)
        )

        val strictResult = strictCounter.segmentTimestamps(observations)
        val looseResult = looseCounter.segmentTimestamps(observations)

        assertTrue("Strict should create more segments", strictResult.size >= looseResult.size)
    }

    @Test
    fun `observation indices reference original list positions`() {
        val observations = listOf(
            obs(0, 500L),
            obs(0, 100L),
            obs(0, 300L)
        )
        val result = counter.segmentTimestamps(observations)

        assertEquals(1, result.size)
        val indices = result.first().observationIndices.sorted()
        assertEquals(listOf(0, 1, 2), indices)
    }

    @Test
    fun `person IDs are preserved in segments`() {
        val observations = listOf(
            obs(5, 0L),
            obs(3, 100L),
            obs(5, 200L)
        )
        val result = counter.segmentTimestamps(observations)

        val person5 = result.filter { it.personId == 5 }
        val person3 = result.filter { it.personId == 3 }

        assertEquals(1, person5.size)
        assertEquals(1, person3.size)
        assertEquals(2, person5.first().observationCount)
    }
}

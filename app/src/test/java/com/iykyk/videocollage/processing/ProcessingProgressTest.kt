package com.iykyk.videocollage.processing

import com.iykyk.videocollage.ui.ProcessingProgress
import com.iykyk.videocollage.ui.ProcessingStage
import org.junit.Assert.*
import org.junit.Test

class ProcessingProgressTest {

    @Test
    fun `stage order is sequential`() {
        val stages = ProcessingStage.entries
        for (i in stages.indices) {
            assertEquals("Stage $i should have order $i", i, stages[i].order)
        }
    }

    @Test
    fun `all stages have labels`() {
        for (stage in ProcessingStage.entries) {
            assertTrue("Stage ${stage.name} should have non-empty label", stage.label.isNotEmpty())
        }
    }

    @Test
    fun `overall fraction starts at 0`() {
        val progress = ProcessingProgress(
            stage = ProcessingStage.LOADING_VIDEO,
            fraction = 0f
        )
        assertEquals(0f, progress.overallFraction, 0.01f)
    }

    @Test
    fun `overall fraction ends at 1`() {
        val progress = ProcessingProgress(
            stage = ProcessingStage.COMPLETE,
            fraction = 1f
        )
        assertEquals(1f, progress.overallFraction, 0.01f)
    }

    @Test
    fun `overall fraction increases across stages`() {
        val stageCount = ProcessingStage.entries.size
        val prev = ProcessingProgress(
            stage = ProcessingStage.LOADING_VIDEO,
            fraction = 0.9f
        )
        val curr = ProcessingProgress(
            stage = ProcessingStage.EXTRACTING_FRAMES,
            fraction = 0.1f
        )
        assertTrue("Progress should increase", curr.overallFraction > prev.overallFraction)
    }

    @Test
    fun `overall fraction increases within a stage`() {
        val early = ProcessingProgress(
            stage = ProcessingStage.GENERATING_EMBEDDINGS,
            fraction = 0.2f
        )
        val late = ProcessingProgress(
            stage = ProcessingStage.GENERATING_EMBEDDINGS,
            fraction = 0.8f
        )
        assertTrue("Progress within stage should increase", late.overallFraction > early.overallFraction)
    }

    @Test
    fun `overall fraction is between 0 and 1`() {
        for (stage in ProcessingStage.entries) {
            val progress = ProcessingProgress(stage = stage, fraction = 0.5f)
            assertTrue("Fraction should be >= 0", progress.overallFraction >= 0f)
            assertTrue("Fraction should be <= 1", progress.overallFraction <= 1f)
        }
    }

    @Test
    fun `default progress is at loading stage`() {
        val progress = ProcessingProgress()
        assertEquals(ProcessingStage.LOADING_VIDEO, progress.stage)
        assertEquals(0f, progress.fraction, 0.001f)
        assertEquals("", progress.statusText)
    }
}

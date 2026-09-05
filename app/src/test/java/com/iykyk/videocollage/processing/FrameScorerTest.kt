package com.iykyk.videocollage.processing

import org.junit.Assert.*
import org.junit.Test

class FrameScorerTest {

    private val defaultWeights = FrameScorer.Weights()

    @Test
    fun `perfect face scores highest`() {
        val score = FrameScorer.scoreFromMetrics(
            frontality = 1.0f,
            sharpness = 1.0f,
            eyesOpen = 1.0f,
            expression = 1.0f,
            visibility = 1.0f
        )
        assertEquals(1.0f, score.totalScore, 0.001f)
        assertFalse(score.isRejected)
    }

    @Test
    fun `zero face scores lowest`() {
        val score = FrameScorer.scoreFromMetrics(
            frontality = 0f,
            sharpness = 0f,
            eyesOpen = 0f,
            expression = 0f,
            visibility = 0f
        )
        assertEquals(0f, score.totalScore, 0.001f)
    }

    @Test
    fun `weights are applied correctly`() {
        val score = FrameScorer.scoreFromMetrics(
            frontality = 1.0f,
            sharpness = 0f,
            eyesOpen = 0f,
            expression = 0f,
            visibility = 0f
        )
        assertEquals(0.30f, score.totalScore, 0.001f)
    }

    @Test
    fun `custom weights change scoring`() {
        val customWeights = FrameScorer.Weights(
            frontality = 0.50f,
            sharpness = 0.20f,
            eyesOpen = 0.10f,
            expression = 0.10f,
            visibility = 0.10f
        )
        val score = FrameScorer.scoreFromMetrics(
            frontality = 1.0f,
            sharpness = 0f,
            eyesOpen = 0f,
            expression = 0f,
            visibility = 0f,
            weights = customWeights
        )
        assertEquals(0.50f, score.totalScore, 0.001f)
    }

    @Test
    fun `individual scores are preserved`() {
        val score = FrameScorer.scoreFromMetrics(
            frontality = 0.8f,
            sharpness = 0.6f,
            eyesOpen = 0.9f,
            expression = 0.7f,
            visibility = 0.5f
        )
        assertEquals(0.8f, score.frontalityScore, 0.001f)
        assertEquals(0.6f, score.sharpnessScore, 0.001f)
        assertEquals(0.9f, score.eyesOpenScore, 0.001f)
        assertEquals(0.7f, score.expressionScore, 0.001f)
        assertEquals(0.5f, score.visibilityScore, 0.001f)
    }

    @Test
    fun `weighted sum matches total`() {
        val f = 0.7f; val s = 0.8f; val e = 0.9f; val ex = 0.6f; val v = 0.5f
        val score = FrameScorer.scoreFromMetrics(f, s, e, ex, v)
        val expected = f * 0.30f + s * 0.30f + e * 0.20f + ex * 0.10f + v * 0.10f
        assertEquals(expected, score.totalScore, 0.001f)
    }

    @Test
    fun `sharp image scores higher than blurry`() {
        val sharp = FrameScorer.scoreFromMetrics(
            frontality = 0.5f, sharpness = 0.9f,
            eyesOpen = 0.5f, expression = 0.5f, visibility = 0.5f
        )
        val blurry = FrameScorer.scoreFromMetrics(
            frontality = 0.5f, sharpness = 0.1f,
            eyesOpen = 0.5f, expression = 0.5f, visibility = 0.5f
        )
        assertTrue("Sharp should score higher than blurry", sharp.totalScore > blurry.totalScore)
    }

    @Test
    fun `open eyes score higher than closed`() {
        val open = FrameScorer.scoreFromMetrics(
            frontality = 0.5f, sharpness = 0.5f,
            eyesOpen = 1.0f, expression = 0.5f, visibility = 0.5f
        )
        val closed = FrameScorer.scoreFromMetrics(
            frontality = 0.5f, sharpness = 0.5f,
            eyesOpen = 0.0f, expression = 0.5f, visibility = 0.5f
        )
        assertTrue("Open eyes should score higher", open.totalScore > closed.totalScore)
    }

    @Test
    fun `frontal face scores higher than profile`() {
        val frontal = FrameScorer.scoreFromMetrics(
            frontality = 1.0f, sharpness = 0.5f,
            eyesOpen = 0.5f, expression = 0.5f, visibility = 0.5f
        )
        val profile = FrameScorer.scoreFromMetrics(
            frontality = 0.2f, sharpness = 0.5f,
            eyesOpen = 0.5f, expression = 0.5f, visibility = 0.5f
        )
        assertTrue("Frontal should score higher", frontal.totalScore > profile.totalScore)
    }

    @Test
    fun `smiling face scores higher than neutral`() {
        val smiling = FrameScorer.scoreFromMetrics(
            frontality = 0.5f, sharpness = 0.5f,
            eyesOpen = 0.5f, expression = 1.0f, visibility = 0.5f
        )
        val neutral = FrameScorer.scoreFromMetrics(
            frontality = 0.5f, sharpness = 0.5f,
            eyesOpen = 0.5f, expression = 0.0f, visibility = 0.5f
        )
        assertTrue("Smiling should score higher", smiling.totalScore > neutral.totalScore)
    }

    @Test
    fun `well-framed face scores higher than edge face`() {
        val centered = FrameScorer.scoreFromMetrics(
            frontality = 0.5f, sharpness = 0.5f,
            eyesOpen = 0.5f, expression = 0.5f, visibility = 1.0f
        )
        val edge = FrameScorer.scoreFromMetrics(
            frontality = 0.5f, sharpness = 0.5f,
            eyesOpen = 0.5f, expression = 0.5f, visibility = 0.2f
        )
        assertTrue("Centered should score higher", centered.totalScore > edge.totalScore)
    }

    @Test
    fun `weights must sum to 1`() {
        try {
            FrameScorer.Weights(frontality = 0.5f, sharpness = 0.5f, eyesOpen = 0.5f, expression = 0.5f, visibility = 0.5f)
            fail("Should throw exception for non-summing weights")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `all scores are between 0 and 1`() {
        val score = FrameScorer.scoreFromMetrics(
            frontality = 0.5f, sharpness = 0.7f,
            eyesOpen = 0.3f, expression = 0.8f, visibility = 0.6f
        )
        assertTrue(score.frontalityScore in 0f..1f)
        assertTrue(score.sharpnessScore in 0f..1f)
        assertTrue(score.eyesOpenScore in 0f..1f)
        assertTrue(score.expressionScore in 0f..1f)
        assertTrue(score.visibilityScore in 0f..1f)
        assertTrue(score.totalScore in 0f..1f)
    }
}

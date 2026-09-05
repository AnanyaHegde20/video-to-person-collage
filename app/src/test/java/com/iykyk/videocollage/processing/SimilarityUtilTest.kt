package com.iykyk.videocollage.processing

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class SimilarityUtilTest {

    @Test
    fun `identical embeddings have cosine similarity of 1_0`() {
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val similarity = SimilarityUtil.cosineSimilarity(embedding, embedding)
        assertEquals(1.0f, similarity, 1e-6f)
    }

    @Test
    fun `orthogonal embeddings have cosine similarity of 0`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val similarity = SimilarityUtil.cosineSimilarity(a, b)
        assertEquals(0.0f, similarity, 1e-6f)
    }

    @Test
    fun `opposite embeddings have cosine similarity of -1`() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(-1f, 0f, 0f)
        val similarity = SimilarityUtil.cosineSimilarity(a, b)
        assertEquals(-1.0f, similarity, 1e-6f)
    }

    @Test
    fun `similar embeddings have high cosine similarity`() {
        val a = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val b = floatArrayOf(0.11f, 0.21f, 0.29f, 0.41f)
        val similarity = SimilarityUtil.cosineSimilarity(a, b)
        assertTrue("Similarity should be > 0.99, was $similarity", similarity > 0.99f)
    }

    @Test
    fun `different embeddings have low cosine similarity`() {
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val b = floatArrayOf(0f, 0f, 0f, 1f)
        val similarity = SimilarityUtil.cosineSimilarity(a, b)
        assertEquals(0.0f, similarity, 1e-6f)
    }

    @Test
    fun `l2 distance of identical vectors is 0`() {
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f)
        val distance = SimilarityUtil.l2Distance(embedding, embedding)
        assertEquals(0.0f, distance, 1e-6f)
    }

    @Test
    fun `l2 distance is symmetric`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        assertEquals(SimilarityUtil.l2Distance(a, b), SimilarityUtil.l2Distance(b, a), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `different size arrays throw exception`() {
        val a = floatArrayOf(1f, 2f)
        val b = floatArrayOf(1f, 2f, 3f)
        SimilarityUtil.cosineSimilarity(a, b)
    }

    @Test
    fun `zero vectors return 0 similarity`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        val similarity = SimilarityUtil.cosineSimilarity(a, b)
        assertEquals(0.0f, similarity, 1e-6f)
    }
}

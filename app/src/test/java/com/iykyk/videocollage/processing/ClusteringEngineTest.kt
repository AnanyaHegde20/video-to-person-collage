package com.iykyk.videocollage.processing

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

class ClusteringEngineTest {

    private lateinit var engine: ClusteringEngine

    @Before
    fun setup() {
        engine = ClusteringEngine(similarityThreshold = 0.70f)
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (v in embedding) norm += v * v
        norm = sqrt(norm).coerceAtLeast(1e-12f)
        return FloatArray(embedding.size) { embedding[it] / norm }
    }

    @Test
    fun `empty input returns empty clusters`() {
        val result = engine.clusterEmbeddings(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single embedding creates single cluster`() {
        val embedding = l2Normalize(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f))
        val result = engine.clusterEmbeddings(listOf(embedding))
        assertEquals(1, result.size)
        assertEquals(1, result.first().memberIndices.size)
    }

    @Test
    fun `identical embeddings form single cluster`() {
        val embedding = l2Normalize(floatArrayOf(0.5f, 0.3f, 0.7f, 0.1f))
        val result = engine.clusterEmbeddings(listOf(embedding, embedding.copyOf(), embedding.copyOf()))

        assertEquals(1, result.size)
        assertEquals(3, result.first().memberIndices.size)
    }

    @Test
    fun `very different embeddings form separate clusters`() {
        val a = l2Normalize(floatArrayOf(1f, 0f, 0f, 0f))
        val b = l2Normalize(floatArrayOf(0f, 1f, 0f, 0f))

        val result = engine.clusterEmbeddings(listOf(a, b))

        assertEquals(2, result.size)
    }

    @Test
    fun `similar embeddings above threshold form single cluster`() {
        val base = l2Normalize(floatArrayOf(0.5f, 0.3f, 0.7f, 0.1f))
        val similar = l2Normalize(floatArrayOf(0.51f, 0.31f, 0.69f, 0.11f))

        val result = engine.clusterEmbeddings(listOf(base, similar))

        assertEquals(1, result.size)
    }

    @Test
    fun `embeddings below threshold form separate clusters`() {
        val a = l2Normalize(floatArrayOf(1f, 0f, 0f, 0f))
        val b = l2Normalize(floatArrayOf(0f, 1f, 0f, 0f))

        val result = engine.clusterEmbeddings(listOf(a, b))

        assertEquals(2, result.size)
    }

    @Test
    fun `cluster IDs are unique`() {
        val embeddings = (0..5).map { i ->
            l2Normalize(FloatArray(4) { if (it == i) 1f else 0f })
        }

        val result = engine.clusterEmbeddings(embeddings)

        val ids = result.map { it.id }.toSet()
        assertEquals(result.size, ids.size)
    }

    @Test
    fun `centroid is L2 normalized`() {
        val embedding = l2Normalize(floatArrayOf(0.5f, 0.3f, 0.7f, 0.1f))
        val result = engine.clusterEmbeddings(listOf(embedding))

        val centroid = result.first().centroid
        var norm = 0f
        for (v in centroid) norm += v * v
        assertEquals(1.0f, sqrt(norm), 1e-4f)
    }

    @Test
    fun `mixed similar and different faces cluster correctly`() {
        val personA1 = l2Normalize(floatArrayOf(0.8f, 0.2f, 0.1f, 0.1f))
        val personA2 = l2Normalize(floatArrayOf(0.79f, 0.21f, 0.1f, 0.1f))
        val personB1 = l2Normalize(floatArrayOf(0.1f, 0.1f, 0.2f, 0.8f))

        val result = engine.clusterEmbeddings(listOf(personA1, personA2, personB1))

        assertEquals(2, result.size)
        assertTrue(result.any { it.memberIndices.size == 2 })
        assertTrue(result.any { it.memberIndices.size == 1 })
    }

    @Test
    fun `threshold 0_99 is stricter than 0_50`() {
        val strictEngine = ClusteringEngine(similarityThreshold = 0.99f)
        val looseEngine = ClusteringEngine(similarityThreshold = 0.50f)

        val a = l2Normalize(floatArrayOf(0.5f, 0.3f, 0.7f, 0.1f))
        val b = l2Normalize(floatArrayOf(0.52f, 0.28f, 0.72f, 0.08f))

        val strictResult = strictEngine.clusterEmbeddings(listOf(a, b))
        val looseResult = looseEngine.clusterEmbeddings(listOf(a, b))

        assertTrue("Strict threshold should create >= clusters than loose",
            strictResult.size >= looseResult.size)
    }

    @Test
    fun `many identical embeddings form single cluster`() {
        val embedding = l2Normalize(floatArrayOf(0.4f, 0.6f, 0.2f, 0.8f))
        val embeddings = List(50) { embedding.copyOf() }

        val result = engine.clusterEmbeddings(embeddings)

        assertEquals(1, result.size)
        assertEquals(50, result.first().memberIndices.size)
    }

    @Test
    fun `member indices are correct`() {
        val a = l2Normalize(floatArrayOf(1f, 0f, 0f, 0f))
        val b = l2Normalize(floatArrayOf(0f, 1f, 0f, 0f))
        val c = l2Normalize(floatArrayOf(1f, 0f, 0f, 0f))

        val result = engine.clusterEmbeddings(listOf(a, b, c))

        assertEquals(2, result.size)
        val clusterWithA = result.find { it.memberIndices.contains(0) }!!
        assertTrue(clusterWithA.memberIndices.contains(2))
        assertEquals(2, clusterWithA.memberIndices.size)
    }
}

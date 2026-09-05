package com.iykyk.videocollage.processing

import kotlin.math.sqrt

object SimilarityUtil {

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding arrays must have the same size" }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 1e-12f) dotProduct / denominator else 0f
    }

    fun l2Distance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Embedding arrays must have the same size" }

        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }
}

package com.iykyk.videocollage.processing

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log

data class RepresentativeFrame(
    val personId: Int,
    val frame: Bitmap,
    val score: FrameScore,
    val timestampMs: Long
)

class RepresentativeFrameSelector(
    private val weights: FrameScorer.Weights = FrameScorer.Weights(),
    private val cropPadding: Float = CROP_PADDING
) {

    fun select(
        clusters: List<PersonCluster>
    ): List<RepresentativeFrame> {
        val results = mutableListOf<RepresentativeFrame>()

        for (cluster in clusters) {
            val best = selectBestFrame(cluster)
            if (best != null) {
                results.add(best)
            }
        }

        Log.d(TAG, "Selected ${results.size} representative frames from ${clusters.size} persons")
        return results
    }

    private fun selectBestFrame(cluster: PersonCluster): RepresentativeFrame? {
        if (cluster.members.isEmpty()) return null

        var bestFace: FaceWithEmbedding? = null
        var bestScore: FrameScore? = null
        var bestTotal = -1f

        for (fwe in cluster.members) {
            val score = FrameScorer.score(fwe.face, weights)
            if (!score.isRejected && score.totalScore > bestTotal) {
                bestTotal = score.totalScore
                bestFace = fwe
                bestScore = score
            }
        }

        if (bestFace == null || bestScore == null) {
            Log.w(TAG, "No valid frame found for person ${cluster.id}, using first available")
            val fallback = cluster.members.first()
            val fallbackScore = FrameScorer.score(fallback.face, weights)
            val crop = cropRepresentativeFrame(fallback.face)
            return RepresentativeFrame(
                personId = cluster.id,
                frame = crop,
                score = fallbackScore,
                timestampMs = fallback.face.timestampMs
            )
        }

        val crop = cropRepresentativeFrame(bestFace.face)
        return RepresentativeFrame(
            personId = cluster.id,
            frame = crop,
            score = bestScore,
            timestampMs = bestFace.face.timestampMs
        )
    }

    private fun cropRepresentativeFrame(face: DetectedFace): Bitmap {
        val source = face.sourceFrame
        val box = face.boundingBox

        val paddingX = box.width() * cropPadding
        val paddingY = box.height() * cropPadding

        val left = (box.left - paddingX).toInt().coerceIn(0, source.width - 1)
        val top = (box.top - paddingY).toInt().coerceIn(0, source.height - 1)
        val right = (box.right + paddingX).toInt().coerceIn(left + 1, source.width)
        val bottom = (box.bottom + paddingY).toInt().coerceIn(top + 1, source.height)

        val width = right - left
        val height = bottom - top

        return Bitmap.createBitmap(source, left, top, width, height)
    }

    companion object {
        private const val TAG = "RepFrameSelector"
        const val CROP_PADDING = 1.5f
    }
}

package com.iykyk.videocollage.processing

import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

class AppearanceCounter(
    private val gapToleranceMs: Long = DEFAULT_GAP_TOLERANCE_MS
) {

    data class TimestampedObservation(
        val personId: Int,
        val timestampMs: Long,
        val qualityScore: Float = 0f
    )

    data class SegmentResult(
        val personId: Int,
        val startTimestampMs: Long,
        val endTimestampMs: Long,
        val observationIndices: List<Int>,
        val bestCandidateIndex: Int = 0
    ) {
        val durationMs: Long get() = endTimestampMs - startTimestampMs
        val observationCount: Int get() = observationIndices.size
    }

    data class AppearanceResult(
        val segments: List<AppearanceSegment>,
        val personAppearanceCounts: Map<Int, Int>
    )

    fun segmentTimestamps(
        observations: List<TimestampedObservation>
    ): List<SegmentResult> {
        if (observations.isEmpty()) return emptyList()

        val allSegments = mutableListOf<SegmentResult>()

        val grouped = observations.withIndex().groupBy { it.value.personId }
        for ((personId, personObs) in grouped) {
            val sorted = personObs.sortedBy { it.value.timestampMs }
            val segments = mutableListOf<SegmentResult>()
            var currentStart = 0

            for (i in 1..sorted.lastIndex) {
                val prevTimestamp = sorted[i - 1].value.timestampMs
                val currTimestamp = sorted[i].value.timestampMs
                val gap = currTimestamp - prevTimestamp

                if (gap > gapToleranceMs) {
                    val segmentObs = sorted.subList(currentStart, i)
                    segments.add(buildSegmentResult(personId, segmentObs))
                    currentStart = i
                }
            }

            val finalSegment = sorted.subList(currentStart, sorted.size)
            segments.add(buildSegmentResult(personId, finalSegment))
            allSegments.addAll(segments)
        }

        return allSegments
    }

    private fun buildSegmentResult(
        personId: Int,
        sortedSubList: List<IndexedValue<TimestampedObservation>>
    ): SegmentResult {
        val indices = sortedSubList.map { it.index }
        val bestIdx = indices.indices.maxByOrNull { idx ->
            sortedSubList[idx].value.qualityScore
        } ?: 0

        return SegmentResult(
            personId = personId,
            startTimestampMs = sortedSubList.first().value.timestampMs,
            endTimestampMs = sortedSubList.last().value.timestampMs,
            observationIndices = indices,
            bestCandidateIndex = bestIdx
        )
    }

    fun countAppearances(
        clusters: List<PersonCluster>
    ): AppearanceResult {
        val allSegments = mutableListOf<AppearanceSegment>()

        for (cluster in clusters) {
            val sortedFaces = cluster.members.sortedBy { it.face.timestampMs }
            val filteredFaces = sortedFaces.filter { isObservationAcceptable(it.face) }
            val rejectedCount = sortedFaces.size - filteredFaces.size
            Log.d(TAG, "Person ${cluster.id}: ${sortedFaces.size} detections, ${filteredFaces.size} after quality filter ($rejectedCount rejected)")
            val personSegments = segmentByTimestamp(filteredFaces, cluster.id)
            allSegments.addAll(personSegments)
            Log.d(TAG, "Person ${cluster.id}: ${personSegments.size} appearance segments")
            for ((segIdx, seg) in personSegments.withIndex()) {
                Log.d(TAG, "  Segment ${segIdx + 1}: ${(seg.startTimestampMs / 1000.0)}s - ${(seg.endTimestampMs / 1000.0)}s (${seg.observations.size} observations)")
            }
        }

        val personCounts = allSegments
            .groupBy { it.personId }
            .mapValues { it.value.size }

        val totalAppearances = personCounts.values.sum()
        Log.d(TAG, "Total: ${allSegments.size} segments across ${clusters.size} persons (gapTolerance=${gapToleranceMs}ms)")
        Log.d(TAG, "Per-person counts: $personCounts")
        Log.d(TAG, "Total person appearances: $totalAppearances")

        return AppearanceResult(
            segments = allSegments,
            personAppearanceCounts = personCounts
        )
    }

    private fun isObservationAcceptable(face: DetectedFace): Boolean {
        val box = face.boundingBox
        val boxArea = box.width() * box.height()
        val frameArea = face.frameWidth * face.frameHeight
        if (frameArea <= 0) return false

        val faceRatio = boxArea / frameArea
        if (faceRatio < MIN_FACE_RATIO) return false

        if (abs(face.headEulerAngleY) > MAX_EULER_ANGLE) return false
        if (abs(face.headEulerAngleX) > MAX_EULER_ANGLE) return false

        val leftEyeOpen = face.leftEyeOpenProbability ?: 1f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 1f
        if (leftEyeOpen < CLOSED_EYE_THRESHOLD && rightEyeOpen < CLOSED_EYE_THRESHOLD) return false

        val rawSharpness = FrameScorer.computeSharpness(face.sourceFrame, face.boundingBox)
        if (rawSharpness < MIN_SHARPNESS_RAW) return false

        return true
    }

    private fun segmentByTimestamp(
        sortedFaces: List<FaceWithEmbedding>,
        personId: Int
    ): List<AppearanceSegment> {
        if (sortedFaces.isEmpty()) return emptyList()

        val segments = mutableListOf<AppearanceSegment>()
        var currentObservations = mutableListOf(sortedFaces.first())

        for (i in 1..sortedFaces.lastIndex) {
            val prev = sortedFaces[i - 1]
            val curr = sortedFaces[i]
            val gap = curr.face.timestampMs - prev.face.timestampMs

            if (gap <= gapToleranceMs) {
                currentObservations.add(curr)
            } else {
                segments.add(buildSegment(personId, currentObservations))
                currentObservations = mutableListOf(curr)
            }
        }

        segments.add(buildSegment(personId, currentObservations))

        return segments
    }

    private fun buildSegment(
        personId: Int,
        observations: List<FaceWithEmbedding>
    ): AppearanceSegment {
        val bestIndex = selectBestCandidate(observations)
        return AppearanceSegment(
            personId = personId,
            startTimestampMs = observations.first().face.timestampMs,
            endTimestampMs = observations.last().face.timestampMs,
            observations = observations,
            bestCandidateIndex = bestIndex
        )
    }

    private fun selectBestCandidate(observations: List<FaceWithEmbedding>): Int {
        if (observations.size == 1) return 0

        var bestIndex = 0
        var bestScore = -1f

        for ((index, fwe) in observations.withIndex()) {
            val face = fwe.face
            val score = computeFaceQualityScore(face)
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }

        return bestIndex
    }

    private fun computeFaceQualityScore(face: DetectedFace): Float {
        var score = 0f

        val frontalness = 1f - (
            abs(face.headEulerAngleY) / 90f +
            abs(face.headEulerAngleX) / 90f
        ).coerceIn(0f, 1f)
        score += frontalness * 3f

        face.leftEyeOpenProbability?.let { score += it * 1f }
        face.rightEyeOpenProbability?.let { score += it * 1f }

        face.smilingProbability?.let { score += it * 1f }

        val boxArea = face.boundingBox.width() * face.boundingBox.height()
        val frameArea = face.frameWidth * face.frameHeight
        if (frameArea > 0) {
            val faceRatio = boxArea / frameArea
            score += faceRatio.coerceIn(0f, 0.5f) * 2f
        }

        val rawSharpness = FrameScorer.computeSharpness(face.sourceFrame, face.boundingBox)
        score += (rawSharpness / 200f).coerceIn(0f, 1f) * 2f

        return score
    }

    companion object {
        private const val TAG = "AppearanceCounter"
        const val DEFAULT_GAP_TOLERANCE_MS = 1000L
        private const val MIN_FACE_RATIO = 0.005f
        private const val MAX_EULER_ANGLE = 45f
        private const val CLOSED_EYE_THRESHOLD = 0.3f
        private const val MIN_SHARPNESS_RAW = 5f
    }
}

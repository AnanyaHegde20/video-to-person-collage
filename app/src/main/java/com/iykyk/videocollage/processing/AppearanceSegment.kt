package com.iykyk.videocollage.processing

data class AppearanceSegment(
    val personId: Int,
    val startTimestampMs: Long,
    val endTimestampMs: Long,
    val observations: List<FaceWithEmbedding>,
    val bestCandidateIndex: Int = 0
) {
    val durationMs: Long get() = endTimestampMs - startTimestampMs
    val observationCount: Int get() = observations.size

    val bestCandidate: FaceWithEmbedding
        get() = observations[bestCandidateIndex.coerceIn(0, observations.lastIndex)]
}

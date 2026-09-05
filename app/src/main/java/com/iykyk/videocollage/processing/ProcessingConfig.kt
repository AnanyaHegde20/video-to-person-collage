package com.iykyk.videocollage.processing

enum class SpeedPreset(
    val samplingFps: Float,
    val detectionMode: DetectionMode,
    val deduplicationThreshold: Float,
    val targetWidth: Int,
    val maxFrames: Int
) {
    FAST(
        samplingFps = 2f,
        detectionMode = DetectionMode.FAST,
        deduplicationThreshold = 0.85f,
        targetWidth = 360,
        maxFrames = 120
    ),
    BALANCED(
        samplingFps = 3f,
        detectionMode = DetectionMode.FAST,
        deduplicationThreshold = 0.90f,
        targetWidth = 480,
        maxFrames = 180
    ),
    QUALITY(
        samplingFps = 5f,
        detectionMode = DetectionMode.ACCURATE,
        deduplicationThreshold = 0.95f,
        targetWidth = 540,
        maxFrames = 300
    )
}

enum class DetectionMode {
    FAST,
    ACCURATE
}

data class ProcessingConfig(
    val speedPreset: SpeedPreset = SpeedPreset.BALANCED
) {
    val samplingFps: Float get() = speedPreset.samplingFps
    val samplingIntervalMs: Long get() = (1000f / samplingFps).toLong()
    val detectionMode: DetectionMode get() = speedPreset.detectionMode
    val deduplicationThreshold: Float get() = speedPreset.deduplicationThreshold
    val targetWidth: Int get() = speedPreset.targetWidth
    val maxFrames: Int get() = speedPreset.maxFrames
}

package com.iykyk.videocollage.processing

data class CollageConfig(
    val outputWidth: Int = 1080,
    val outputHeight: Int = 1920,
    val backgroundColor: Int = 0xFF1A1A2E.toInt(),
    val gapPx: Int = 12,
    val marginPx: Int = 24,
    val tileCornerRadiusPx: Float = 24f,
    val tileBorderWidthPx: Float = 3f,
    val tileBorderColor: Int = 0xFF2A2A4A.toInt(),
    val labelHeightPx: Float = 96f,
    val labelBackgroundColor: Int = 0xCC000000.toInt(),
    val labelTextColor: Int = 0xFFFFFFFF.toInt(),
    val labelTextSizePx: Float = 36f,
    val labelPaddingPx: Float = 20f,
    val countTextColor: Int = 0xFFFFD700.toInt()
)

package com.iykyk.videocollage.processing

import android.graphics.Bitmap

data class ExtractedFrame(
    val bitmap: Bitmap,
    val timestampMs: Long
)

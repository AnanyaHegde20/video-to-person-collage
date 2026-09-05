package com.iykyk.videocollage.processing

import android.graphics.Bitmap
import android.graphics.RectF

data class DetectedFace(
    val timestampMs: Long,
    val boundingBox: RectF,
    val headEulerAngleX: Float,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,
    val frameWidth: Int,
    val frameHeight: Int,
    val sourceFrame: Bitmap
)

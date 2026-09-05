package com.iykyk.videocollage.processing

import android.graphics.Bitmap

data class CollageInput(
    val name: String,
    val appearanceCount: Int,
    val representativeFrame: Bitmap?
)

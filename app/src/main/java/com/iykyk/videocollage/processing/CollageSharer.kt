package com.iykyk.videocollage.processing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class CollageSharer(private val context: Context) {

    fun share(bitmap: Bitmap, filename: String = "collage_share.png"): Boolean {
        return try {
            val file = saveToCache(bitmap, filename)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Collage")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveToCache(bitmap: Bitmap, filename: String): File {
        val sharedDir = File(context.cacheDir, "shared")
        if (!sharedDir.exists()) {
            sharedDir.mkdirs()
        }

        val file = File(sharedDir, filename)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        return file
    }
}

package com.iykyk.videocollage.processing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

sealed class SaveResult {
    data class Success(val uri: android.net.Uri) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

class CollageSaver(private val context: Context) {

    fun save(bitmap: Bitmap, filename: String = defaultFilename()): SaveResult {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(bitmap, filename)
        } else {
            saveWithFile(bitmap, filename)
        }
    }

    private fun saveWithMediaStore(bitmap: Bitmap, filename: String): SaveResult {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IYKYK")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return SaveResult.Error("Failed to create MediaStore entry")

            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            } ?: return SaveResult.Error("Failed to open output stream")

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            SaveResult.Success(uri)
        } catch (e: SecurityException) {
            SaveResult.Error("Storage permission denied")
        } catch (e: Exception) {
            SaveResult.Error("Save failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    @Suppress("DEPRECATION")
    private fun saveWithFile(bitmap: Bitmap, filename: String): SaveResult {
        return try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val iykykDir = File(picturesDir, "IYKYK")
            if (!iykykDir.exists()) {
                iykykDir.mkdirs()
            }

            val file = File(iykykDir, filename)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            SaveResult.Success(android.net.Uri.fromFile(file))
        } catch (e: SecurityException) {
            SaveResult.Error("Storage permission denied")
        } catch (e: Exception) {
            SaveResult.Error("Save failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    companion object {
        fun defaultFilename(): String {
            val timestamp = System.currentTimeMillis()
            return "collage_$timestamp.png"
        }
    }
}

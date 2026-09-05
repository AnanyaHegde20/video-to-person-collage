package com.iykyk.videocollage.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val duration: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val dateAdded: Long,
    val mimeType: String
)

object VideoRepository {

    private const val TAG = "VideoRepository"

    fun queryAllVideos(context: Context): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATA
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "unknown"
                val duration = cursor.getLong(durationCol)
                val width = cursor.getInt(widthCol)
                val height = cursor.getInt(heightCol)
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol)
                val mimeType = cursor.getString(mimeCol) ?: "video/*"
                val path = cursor.getString(pathCol)

                if (duration <= 0 || size <= 0) continue
                if (path != null && !File(path).exists()) continue

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )

                videos.add(
                    VideoItem(
                        id = id,
                        uri = contentUri,
                        displayName = name,
                        duration = duration,
                        width = width,
                        height = height,
                        size = size,
                        dateAdded = dateAdded,
                        mimeType = mimeType
                    )
                )
            }
        }

        Log.d(TAG, "Raw MediaStore query: ${videos.size} videos found")
        return videos
    }

    fun deduplicate(videos: List<VideoItem>): List<VideoItem> {
        val groups = mutableMapOf<String, MutableList<VideoItem>>()

        for (video in videos) {
            val key = "${video.size}_${video.duration}_${video.mimeType}"
            groups.getOrPut(key) { mutableListOf() }.add(video)
        }

        val unique = mutableListOf<VideoItem>()
        for ((key, group) in groups) {
            if (group.size == 1) {
                unique.add(group.first())
            } else {
                val confirmed = confirmDuplicates(group)
                unique.addAll(confirmed)
            }
        }

        val result = unique.sortedByDescending { it.dateAdded }
        Log.d(TAG, "Deduplication: ${videos.size} -> ${result.size} unique videos (${videos.size - result.size} duplicates removed)")
        return result
    }

    private fun confirmDuplicates(candidates: List<VideoItem>): List<VideoItem> {
        val confirmed = mutableListOf<VideoItem>()
        val used = mutableSetOf<Int>()

        for (i in candidates.indices) {
            if (i in used) continue

            var group = listOf(candidates[i])
            val hashI = computeQuickHash(candidates[i])

            for (j in (i + 1)..candidates.lastIndex) {
                if (j in used) continue

                val hashJ = computeQuickHash(candidates[j])
                if (hashI == hashJ) {
                    group = group + candidates[j]
                    used.add(j)
                }
            }

            val best = group.maxByOrNull { it.dateAdded }!!
            confirmed.add(best)
        }

        return confirmed
    }

    private fun computeQuickHash(video: VideoItem): String {
        return "${video.size}_${video.duration}_${video.mimeType}"
    }

    fun computeContentHash(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }

                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute content hash for $uri", e)
            null
        }
    }
}

package com.iykyk.videocollage.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.sqrt

class VideoFrameExtractor(
    private val context: Context,
    private val config: ProcessingConfig = ProcessingConfig()
) {

    data class ExtractionProgress(
        val framesExtracted: Int,
        val totalFrames: Int,
        val totalDurationMs: Long,
        val currentPositionMs: Long
    ) {
        val fraction: Float
            get() = if (totalDurationMs > 0) {
                (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
            } else 0f

        val phase: String
            get() = "Extracting frames ($framesExtracted/$totalFrames sampled)"
    }

    suspend fun extract(
        videoUri: Uri,
        onProgress: (ExtractionProgress) -> Unit = {}
    ): List<ExtractedFrame> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)

            val durationStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )
            val totalDurationMs = durationStr?.toLongOrNull() ?: 0L

            val videoWidthStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )
            val videoHeightStr = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )
            val videoWidth = videoWidthStr?.toIntOrNull() ?: 1080
            val videoHeight = videoHeightStr?.toIntOrNull() ?: 1920

            val targetWidth = config.targetWidth
            val scale = targetWidth.toFloat() / max(videoWidth, videoHeight).coerceAtLeast(1)
            val targetHeight = (videoHeight * scale).toInt().coerceIn(1, 720)

            val intervalMs = config.samplingIntervalMs
            val estimatedFrames = if (totalDurationMs > 0) {
                (totalDurationMs / intervalMs).toInt() + 1
            } else {
                config.maxFrames
            }

            Log.d(TAG, "Extracting: duration=${totalDurationMs}ms, interval=${intervalMs}ms, " +
                    "target=${targetWidth}x${targetHeight}, estimated=$estimatedFrames frames")

            val frames = mutableListOf<ExtractedFrame>()
            var currentTimeMs = 0L
            var frameIndex = 0
            var lastAcceptedHash: Long = -1L
            var skippedDuplicate = 0

            while (currentTimeMs <= totalDurationMs && frames.size < config.maxFrames) {
                ensureActive()

                val bitmap = retriever.getFrameAtTime(
                    currentTimeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (bitmap != null) {
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        targetWidth,
                        targetHeight,
                        true
                    )
                    if (scaled !== bitmap) {
                        bitmap.recycle()
                    }

                    val hash = computeBitmapHash(scaled)
                    val isDuplicate = lastAcceptedHash != -1L &&
                            areSimilar(hash, lastAcceptedHash, config.deduplicationThreshold)

                    if (!isDuplicate) {
                        frames.add(
                            ExtractedFrame(
                                bitmap = scaled,
                                timestampMs = currentTimeMs
                            )
                        )
                        lastAcceptedHash = hash
                    } else {
                        scaled.recycle()
                        skippedDuplicate++
                    }
                }

                frameIndex++
                currentTimeMs += intervalMs

                onProgress(
                    ExtractionProgress(
                        framesExtracted = frames.size,
                        totalFrames = estimatedFrames,
                        totalDurationMs = totalDurationMs,
                        currentPositionMs = currentTimeMs.coerceAtMost(totalDurationMs)
                    )
                )
            }

            Log.d(TAG, "Extracted ${frames.size} frames (skipped $skippedDuplicate duplicates) " +
                    "from ${totalDurationMs}ms video (interval=${intervalMs}ms, video=${videoWidth}x${videoHeight})")
            frames
        } catch (e: Exception) {
            Log.e(TAG, "Frame extraction failed", e)
            throw e
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun computeBitmapHash(bitmap: Bitmap): Long {
        val sampleStep = 8
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return 0L

        var hash = 0L
        val totalPixels = w * h
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices step sampleStep * sampleStep) {
            val pixel = pixels[i]
            val r = Color.red(pixel) / 16
            val g = Color.green(pixel) / 16
            val b = Color.blue(pixel) / 16
            hash = hash * 31 + (r * 256 + g) * 256 + b
        }
        return hash
    }

    private fun areSimilar(hash1: Long, hash2: Long, threshold: Float): Boolean {
        if (hash1 == hash2) return true
        val h1 = hash1.toString(16)
        val h2 = hash2.toString(16)
        val maxLen = max(h1.length, h2.length)
        var matches = 0
        for (i in 0 until maxLen) {
            val c1 = if (i < h1.length) h1[i] else '0'
            val c2 = if (i < h2.length) h2[i] else '0'
            if (c1 == c2) matches++
        }
        return matches.toFloat() / maxLen >= threshold
    }

    companion object {
        private const val TAG = "VideoFrameExtractor"
    }
}

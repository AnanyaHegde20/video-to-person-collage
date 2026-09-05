package com.iykyk.videocollage.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class FaceDetector(
    context: Context,
    private val config: ProcessingConfig = ProcessingConfig()
) {

    private val detector: com.google.mlkit.vision.face.FaceDetector

    init {
        val builder = FaceDetectorOptions.Builder()
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(MIN_FACE_SIZE)

        when (config.detectionMode) {
            DetectionMode.FAST -> builder.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            DetectionMode.ACCURATE -> builder.setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        }

        detector = FaceDetection.getClient(builder.build())
        Log.d(TAG, "Face detector created: mode=${config.detectionMode}, minFaceSize=$MIN_FACE_SIZE")
    }

    data class DetectionProgress(
        val framesProcessed: Int,
        val totalFrames: Int,
        val facesFound: Int
    ) {
        val fraction: Float
            get() = if (totalFrames > 0) {
                (framesProcessed.toFloat() / totalFrames).coerceIn(0f, 1f)
            } else 0f

        val phase: String
            get() = "Detecting faces ($framesProcessed/$totalFrames frames, $facesFound faces)"
    }

    suspend fun detect(
        frames: List<ExtractedFrame>,
        onProgress: (DetectionProgress) -> Unit = {}
    ): List<DetectedFace> = withContext(Dispatchers.Default) {
        val allFaces = mutableListOf<DetectedFace>()
        var facesFound = 0

        val detectionBitmapSize = when (config.detectionMode) {
            DetectionMode.FAST -> 320
            DetectionMode.ACCURATE -> 480
        }

        for ((index, frame) in frames.withIndex()) {
            ensureActive()

            val scaledForDetection = if (frame.bitmap.width > detectionBitmapSize ||
                frame.bitmap.height > detectionBitmapSize) {
                val scale = detectionBitmapSize.toFloat() / maxOf(frame.bitmap.width, frame.bitmap.height)
                val w = (frame.bitmap.width * scale).toInt().coerceAtLeast(1)
                val h = (frame.bitmap.height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(frame.bitmap, w, h, true)
            } else {
                null
            }

            val detectBitmap = scaledForDetection ?: frame.bitmap

            try {
                val faces = detectInFrame(detectBitmap, frame.timestampMs)

                val scaleFactorX = frame.bitmap.width.toFloat() / detectBitmap.width
                val scaleFactorY = frame.bitmap.height.toFloat() / detectBitmap.height

                val scaledFaces = faces.map { face ->
                    face.copy(
                        boundingBox = RectF(
                            face.boundingBox.left * scaleFactorX,
                            face.boundingBox.top * scaleFactorY,
                            face.boundingBox.right * scaleFactorX,
                            face.boundingBox.bottom * scaleFactorY
                        ),
                        frameWidth = frame.bitmap.width,
                        frameHeight = frame.bitmap.height,
                        sourceFrame = frame.bitmap
                    )
                }

                allFaces.addAll(scaledFaces)
                facesFound += scaledFaces.size

                if (scaledFaces.isNotEmpty()) {
                    Log.d(TAG, "Frame ${index + 1}/${frames.size} @ ${(frame.timestampMs / 1000.0)}s: ${scaledFaces.size} faces detected")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Detection failed for frame at ${frame.timestampMs}ms", e)
            } finally {
                scaledForDetection?.recycle()
            }

            onProgress(
                DetectionProgress(
                    framesProcessed = index + 1,
                    totalFrames = frames.size,
                    facesFound = facesFound
                )
            )
        }

        Log.d(TAG, "Detected $facesFound faces across ${frames.size} frames")
        allFaces
    }

    private suspend fun detectInFrame(
        bitmap: Bitmap,
        timestampMs: Long
    ): List<DetectedFace> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)

        try {
            val faces = Tasks.await(detector.process(image))

            faces.mapNotNull { face ->
                val bounds = face.boundingBox
                if (bounds.width() <= 0 || bounds.height() <= 0) return@mapNotNull null

                DetectedFace(
                    timestampMs = timestampMs,
                    boundingBox = RectF(
                        bounds.left.toFloat(),
                        bounds.top.toFloat(),
                        bounds.right.toFloat(),
                        bounds.bottom.toFloat()
                    ),
                    headEulerAngleX = face.headEulerAngleX,
                    headEulerAngleY = face.headEulerAngleY,
                    headEulerAngleZ = face.headEulerAngleZ,
                    leftEyeOpenProbability = face.leftEyeOpenProbability,
                    rightEyeOpenProbability = face.rightEyeOpenProbability,
                    smilingProbability = face.smilingProbability,
                    frameWidth = bitmap.width,
                    frameHeight = bitmap.height,
                    sourceFrame = bitmap
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Face detection failed for frame at ${timestampMs}ms", e)
            emptyList()
        }
    }

    fun close() {
        detector.close()
    }

    companion object {
        private const val TAG = "FaceDetector"
        private const val MIN_FACE_SIZE = 0.05f
    }
}

package com.iykyk.videocollage.processing

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceEmbeddingEngine(context: Context) {

    private val interpreter: Interpreter
    private val reusableBuffer: ByteBuffer
    private val reusablePixels: IntArray

    init {
        val model = loadModelFile(context, MODEL_FILENAME)
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            try {
                val gpuDelegate = GpuDelegate()
                addDelegate(gpuDelegate)
                Log.d(TAG, "TFLite GPU delegate enabled")
            } catch (t: Throwable) {
                Log.w(TAG, "GPU delegate not available, using CPU: ${t.message}")
            }
        }
        interpreter = Interpreter(model, options)

        val singleImageFloats = INPUT_SIZE * INPUT_SIZE * 3
        reusableBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * singleImageFloats * 4)
        reusableBuffer.order(ByteOrder.nativeOrder())
        reusablePixels = IntArray(INPUT_SIZE * INPUT_SIZE)

        Log.d(TAG, "TFLite interpreter loaded for $MODEL_FILENAME (threads=4, GPU attempted)")
    }

    data class EmbeddingResult(
        val embedding: FloatArray,
        val sourceFrame: Bitmap
    )

    fun generateEmbedding(
        face: DetectedFace,
        faceRegionPadding: Float = FACE_REGION_PADDING
    ): EmbeddingResult? {
        return try {
            val croppedFace = cropFaceRegion(face, faceRegionPadding)
            val inputBuffer = preprocessImageReusable(croppedFace)
            croppedFace.recycle()

            val outputBuffer = Array(BATCH_SIZE) { FloatArray(EMBEDDING_SIZE) }
            interpreter.run(inputBuffer, outputBuffer)

            val rawEmbedding = outputBuffer[0]
            val normalized = l2Normalize(rawEmbedding)

            EmbeddingResult(
                embedding = normalized,
                sourceFrame = face.sourceFrame
            )
        } catch (e: Exception) {
            Log.e(TAG, "Embedding generation failed", e)
            null
        }
    }

    fun generateEmbeddings(
        faces: List<DetectedFace>,
        faceRegionPadding: Float = FACE_REGION_PADDING,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<Pair<DetectedFace, EmbeddingResult>> {
        val results = mutableListOf<Pair<DetectedFace, EmbeddingResult>>()

        for ((index, face) in faces.withIndex()) {
            val result = generateEmbedding(face, faceRegionPadding)
            if (result != null) {
                results.add(face to result)
            }
            onProgress(index + 1, faces.size)
        }

        Log.d(TAG, "Generated ${results.size} embeddings from ${faces.size} faces")
        return results
    }

    private fun cropFaceRegion(face: DetectedFace, padding: Float): Bitmap {
        val source = face.sourceFrame
        val box = face.boundingBox

        val paddedWidth = box.width() * (1f + 2 * padding)
        val paddedHeight = box.height() * (1f + 2 * padding)
        val centerX = box.centerX()
        val centerY = box.centerY()

        val left = (centerX - paddedWidth / 2).toInt().coerceIn(0, source.width - 1)
        val top = (centerY - paddedHeight / 2).toInt().coerceIn(0, source.height - 1)
        val right = (centerX + paddedWidth / 2).toInt().coerceIn(left + 1, source.width)
        val bottom = (centerY + paddedHeight / 2).toInt().coerceIn(top + 1, source.height)

        val width = right - left
        val height = bottom - top

        val cropped = Bitmap.createBitmap(source, left, top, width, height)
        val scaled = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
        if (scaled !== cropped) {
            cropped.recycle()
        }
        return scaled
    }

    private fun preprocessImageReusable(bitmap: Bitmap): ByteBuffer {
        reusableBuffer.clear()
        bitmap.getPixels(reusablePixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (slot in 0 until BATCH_SIZE) {
            for (pixel in reusablePixels) {
                val r = ((pixel shr 16 and 0xFF) / 127.5f) - 1f
                val g = ((pixel shr 8 and 0xFF) / 127.5f) - 1f
                val b = ((pixel and 0xFF) / 127.5f) - 1f
                reusableBuffer.putFloat(r)
                reusableBuffer.putFloat(g)
                reusableBuffer.putFloat(b)
            }
        }

        return reusableBuffer
    }

    private fun l2Normalize(embedding: FloatArray): FloatArray {
        var norm = 0f
        for (value in embedding) {
            norm += value * value
        }
        norm = sqrt(norm).coerceAtLeast(1e-12f)
        return FloatArray(embedding.size) { embedding[it] / norm }
    }

    fun close() {
        interpreter.close()
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val mapped = fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
        fileChannel.close()
        inputStream.close()
        fileDescriptor.close()
        return mapped
    }

    companion object {
        private const val TAG = "FaceEmbeddingEngine"
        const val MODEL_FILENAME = "MobileFaceNet.tflite"
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 192
        const val BATCH_SIZE = 2
        const val FACE_REGION_PADDING = 0.2f
    }
}

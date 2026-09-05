package com.iykyk.videocollage.processing

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

data class FrameScore(
    val frontalityScore: Float,
    val sharpnessScore: Float,
    val eyesOpenScore: Float,
    val expressionScore: Float,
    val visibilityScore: Float,
    val totalScore: Float,
    val isRejected: Boolean,
    val rejectionReason: String?
)

object FrameScorer {

    data class Weights(
        val frontality: Float = 0.30f,
        val sharpness: Float = 0.30f,
        val eyesOpen: Float = 0.20f,
        val expression: Float = 0.10f,
        val visibility: Float = 0.10f
    ) {
        init {
            val sum = frontality + sharpness + eyesOpen + expression + visibility
            require(abs(sum - 1.0f) < 0.001f) { "Weights must sum to 1.0, got $sum" }
        }
    }

    private val DEFAULT_WEIGHTS = Weights()

    private const val MIN_FACE_RATIO = 0.02f
    private const val MAX_EULER_ANGLE = 45f
    private const val BOUNDARY_MARGIN = 0.02f
    private const val CLOSED_EYE_THRESHOLD = 0.3f

    fun scoreFromMetrics(
        frontality: Float,
        sharpness: Float,
        eyesOpen: Float,
        expression: Float,
        visibility: Float,
        weights: Weights = DEFAULT_WEIGHTS
    ): FrameScore {
        val total = frontality * weights.frontality +
                sharpness * weights.sharpness +
                eyesOpen * weights.eyesOpen +
                expression * weights.expression +
                visibility * weights.visibility

        return FrameScore(
            frontalityScore = frontality,
            sharpnessScore = sharpness,
            eyesOpenScore = eyesOpen,
            expressionScore = expression,
            visibilityScore = visibility,
            totalScore = total,
            isRejected = false,
            rejectionReason = null
        )
    }

    fun score(
        face: DetectedFace,
        weights: Weights = DEFAULT_WEIGHTS
    ): FrameScore {
        val rejection = checkRejection(face)
        if (rejection != null) {
            return FrameScore(
                frontalityScore = 0f,
                sharpnessScore = 0f,
                eyesOpenScore = 0f,
                expressionScore = 0f,
                visibilityScore = 0f,
                totalScore = 0f,
                isRejected = true,
                rejectionReason = rejection
            )
        }

        val frontality = scoreFrontality(face)
        val sharpness = scoreSharpness(face)
        val eyesOpen = scoreEyesOpen(face)
        val expression = scoreExpression(face)
        val visibility = scoreVisibility(face)

        val total = frontality * weights.frontality +
                sharpness * weights.sharpness +
                eyesOpen * weights.eyesOpen +
                expression * weights.expression +
                visibility * weights.visibility

        return FrameScore(
            frontalityScore = frontality,
            sharpnessScore = sharpness,
            eyesOpenScore = eyesOpen,
            expressionScore = expression,
            visibilityScore = visibility,
            totalScore = total,
            isRejected = false,
            rejectionReason = null
        )
    }

    private fun checkRejection(face: DetectedFace): String? {
        val box = face.boundingBox
        val boxArea = box.width() * box.height()
        val frameArea = face.frameWidth * face.frameHeight
        if (frameArea <= 0) return "Invalid frame dimensions"

        val faceRatio = boxArea / frameArea
        if (faceRatio < MIN_FACE_RATIO) return "Face too small"

        val absYaw = abs(face.headEulerAngleY)
        if (absYaw > MAX_EULER_ANGLE) return "Face too rotated (yaw)"

        val absPitch = abs(face.headEulerAngleX)
        if (absPitch > MAX_EULER_ANGLE) return "Face too rotated (pitch)"

        val leftEyeOpen = face.leftEyeOpenProbability ?: 1f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 1f
        if (leftEyeOpen < CLOSED_EYE_THRESHOLD && rightEyeOpen < CLOSED_EYE_THRESHOLD) {
            return "Both eyes closed"
        }

        val marginX = face.frameWidth * BOUNDARY_MARGIN
        val marginY = face.frameHeight * BOUNDARY_MARGIN
        if (box.left < marginX || box.top < marginY ||
            box.right > face.frameWidth - marginX ||
            box.bottom > face.frameHeight - marginY
        ) {
            return "Face touching boundary"
        }

        return null
    }

    private fun scoreFrontality(face: DetectedFace): Float {
        val yawScore = 1f - (abs(face.headEulerAngleY) / 90f).coerceIn(0f, 1f)
        val pitchScore = 1f - (abs(face.headEulerAngleX) / 90f).coerceIn(0f, 1f)
        val rollScore = 1f - (abs(face.headEulerAngleZ) / 45f).coerceIn(0f, 1f)
        return (yawScore * 0.5f + pitchScore * 0.3f + rollScore * 0.2f)
    }

    fun computeSharpness(bitmap: Bitmap, region: android.graphics.RectF? = null): Float {
        val startX = (region?.left ?: 0f).toInt().coerceIn(0, bitmap.width - 1)
        val startY = (region?.top ?: 0f).toInt().coerceIn(0, bitmap.height - 1)
        val endX = (region?.right ?: bitmap.width.toFloat()).toInt().coerceIn(startX + 1, bitmap.width)
        val endY = (region?.bottom ?: bitmap.height.toFloat()).toInt().coerceIn(startY + 1, bitmap.height)

        val width = endX - startX
        val height = endY - startY
        if (width < 3 || height < 3) return 0f

        val grayscale = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(startX + x, startY + y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                grayscale[y * width + x] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            }
        }

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = grayscale[y * width + x].toDouble()
                val laplacian = -4 * center +
                        grayscale[(y - 1) * width + x].toDouble() +
                        grayscale[(y + 1) * width + x].toDouble() +
                        grayscale[y * width + (x - 1)].toDouble() +
                        grayscale[y * width + (x + 1)].toDouble()
                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0f

        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return sqrt(variance).toFloat().coerceIn(0f, 500f)
    }

    private fun scoreSharpness(face: DetectedFace): Float {
        val rawSharpness = computeSharpness(face.sourceFrame, face.boundingBox)
        return (rawSharpness / 200f).coerceIn(0f, 1f)
    }

    private fun scoreEyesOpen(face: DetectedFace): Float {
        val left = face.leftEyeOpenProbability ?: 0.5f
        val right = face.rightEyeOpenProbability ?: 0.5f
        return ((left + right) / 2f).coerceIn(0f, 1f)
    }

    private fun scoreExpression(face: DetectedFace): Float {
        return (face.smilingProbability ?: 0.5f).coerceIn(0f, 1f)
    }

    private fun scoreVisibility(face: DetectedFace): Float {
        val box = face.boundingBox
        val frameArea = (face.frameWidth * face.frameHeight).toFloat()
        if (frameArea <= 0) return 0f

        val faceArea = box.width() * box.height()
        val faceRatio = faceArea / frameArea

        val sizeScore = when {
            faceRatio < 0.03f -> 0.2f
            faceRatio < 0.05f -> 0.5f
            faceRatio < 0.15f -> 0.8f
            faceRatio < 0.40f -> 1.0f
            else -> 0.7f
        }

        val centerX = box.centerX() / face.frameWidth
        val centerY = box.centerY() / face.frameHeight
        val distFromCenter = sqrt(
            (centerX - 0.5f) * (centerX - 0.5f) +
            (centerY - 0.5f) * (centerY - 0.5f)
        )
        val positionScore = (1f - distFromCenter * 2f).coerceIn(0.3f, 1f)

        return sizeScore * 0.6f + positionScore * 0.4f
    }
}

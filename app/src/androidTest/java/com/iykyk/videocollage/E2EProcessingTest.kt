package com.iykyk.videocollage

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iykyk.videocollage.processing.AppearanceCounter
import com.iykyk.videocollage.processing.ClusteringEngine
import com.iykyk.videocollage.processing.CollageGenerator
import com.iykyk.videocollage.processing.CollageInput
import com.iykyk.videocollage.processing.CollageSaver
import com.iykyk.videocollage.processing.FaceDetector
import com.iykyk.videocollage.processing.FaceEmbeddingEngine
import com.iykyk.videocollage.processing.ProcessingConfig
import com.iykyk.videocollage.processing.RepresentativeFrameSelector
import com.iykyk.videocollage.processing.SpeedPreset
import com.iykyk.videocollage.processing.VideoFrameExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class E2EProcessingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun getTestVideoFile(fileName: String): File {
        val file = File(context.filesDir, fileName)
        assertTrue("Video file must exist at: ${file.absolutePath}", file.exists())
        assertTrue("Video file must have content", file.length() > 0)
        return file
    }

    @Test
    fun testFullPipeline_Balanced_Sample1() {
        val file = getTestVideoFile("Sample_1.mp4")
        runPipeline(file, ProcessingConfig(SpeedPreset.BALANCED))
    }

    @Test
    fun testFullPipeline_Fast_Sample1() {
        val file = getTestVideoFile("Sample_1.mp4")
        runPipeline(file, ProcessingConfig(SpeedPreset.FAST))
    }

    @Test
    fun testFullPipeline_Balanced_Sample2() {
        val file = getTestVideoFile("Sample_2.mp4")
        runPipeline(file, ProcessingConfig(SpeedPreset.BALANCED))
    }

    @Test
    fun testFullPipeline_Balanced_Sample3() {
        val file = getTestVideoFile("Sample_3.mp4")
        runPipeline(file, ProcessingConfig(SpeedPreset.BALANCED))
    }

    private fun runPipeline(videoFile: File, config: ProcessingConfig) {
        assertTrue("Video file must exist: ${videoFile.absolutePath}", videoFile.exists())
        assertTrue("Video file must have content", videoFile.length() > 0)

        val videoUri = Uri.fromFile(videoFile)
        Log.d(TAG, "========================================")
        Log.d(TAG, "Processing: ${videoFile.name}")
        Log.d(TAG, "Preset: ${config.speedPreset}")
        Log.d(TAG, "Sampling interval: ${config.samplingIntervalMs}ms (${config.samplingFps} fps)")
        Log.d(TAG, "Target width: ${config.targetWidth}px")
        Log.d(TAG, "Detection mode: ${config.detectionMode}")
        Log.d(TAG, "Max frames: ${config.maxFrames}")
        Log.d(TAG, "========================================")

        val timings = mutableMapOf<String, Long>()

        val frameExtractor = VideoFrameExtractor(context, config)
        val t1Start = System.currentTimeMillis()
        val frames = runBlocking { frameExtractor.extract(videoUri) }
        val t1End = System.currentTimeMillis()
        timings["Frame Extraction"] = t1End - t1Start
        Log.d(TAG, "Stage 1 - Frame Extraction: ${frames.size} frames in ${t1End - t1Start}ms")
        assertTrue("Should extract at least 1 frame", frames.isNotEmpty())
        assertTrue("Should extract <= maxFrames", frames.size <= config.maxFrames)

        val detector = FaceDetector(context, config)
        val t2Start = System.currentTimeMillis()
        val faces = runBlocking { detector.detect(frames) }
        val t2End = System.currentTimeMillis()
        timings["Face Detection"] = t2End - t2Start
        Log.d(TAG, "Stage 2 - Face Detection: ${faces.size} faces in ${t2End - t2Start}ms")
        assertTrue("Should detect at least 1 face", faces.isNotEmpty())

        val embeddingEngine = FaceEmbeddingEngine(context)
        val t3Start = System.currentTimeMillis()
        val facesWithEmbeddings = mutableListOf<com.iykyk.videocollage.processing.FaceWithEmbedding>()
        var skippedDedup = 0
        val recentPositions = mutableListOf<Triple<Float, Float, Float>>()

        for (face in faces) {
            val centerX = face.boundingBox.centerX()
            val centerY = face.boundingBox.centerY()
            val size = face.boundingBox.width() * face.boundingBox.height()

            val isDuplicate = recentPositions.any { (rcx, rcy, rSize) ->
                val distX = (centerX - rcx) / face.frameWidth
                val distY = (centerY - rcy) / face.frameHeight
                val sizeRatio = size / rSize
                val posSim = 1f - Math.sqrt((distX * distX + distY * distY).toDouble()).toFloat().coerceIn(0f, 1f)
                val sizeSim = if (sizeRatio > 1f) 1f / sizeRatio else sizeRatio
                posSim > 0.7f && sizeSim > 0.7f
            }

            if (!isDuplicate) {
                val embedResult = embeddingEngine.generateEmbedding(face)
                if (embedResult != null) {
                    facesWithEmbeddings.add(
                        com.iykyk.videocollage.processing.FaceWithEmbedding(face = face, embedding = embedResult.embedding)
                    )
                    recentPositions.add(Triple(centerX, centerY, size))
                    if (recentPositions.size > 30) recentPositions.removeAt(0)
                }
            } else {
                skippedDedup++
            }
        }
        val t3End = System.currentTimeMillis()
        timings["Embedding Generation"] = t3End - t3Start
        Log.d(TAG, "Stage 3 - Embeddings: ${facesWithEmbeddings.size} generated, $skippedDedup deduped in ${t3End - t3Start}ms")
        assertTrue("Should generate at least 1 embedding", facesWithEmbeddings.isNotEmpty())

        val clusteringEngine = ClusteringEngine()
        val t4Start = System.currentTimeMillis()
        val clusterResult = clusteringEngine.cluster(facesWithEmbeddings)
        val t4End = System.currentTimeMillis()
        timings["Clustering"] = t4End - t4Start
        Log.d(TAG, "Stage 4 - Clustering: ${clusterResult.uniquePersons} persons in ${t4End - t4Start}ms")
        assertTrue("Should find at least 1 person", clusterResult.uniquePersons >= 1)

        val appearanceCounter = AppearanceCounter()
        val t5Start = System.currentTimeMillis()
        val appearanceResult = appearanceCounter.countAppearances(clusterResult.clusters)
        val t5End = System.currentTimeMillis()
        timings["Appearance Counting"] = t5End - t5Start
        Log.d(TAG, "Stage 5 - Appearances: ${appearanceResult.segments.size} segments, counts: ${appearanceResult.personAppearanceCounts}")

        val repSelector = RepresentativeFrameSelector()
        val t6Start = System.currentTimeMillis()
        val repFrames = repSelector.select(clusterResult.clusters)
        val t6End = System.currentTimeMillis()
        timings["Rep Selection"] = t6End - t6Start
        Log.d(TAG, "Stage 6 - Rep Frames: ${repFrames.size} selected in ${t6End - t6Start}ms")

        val collageInputs = appearanceResult.personAppearanceCounts.map { (personId, count) ->
            val repFrame = repFrames.find { it.personId == personId }
            CollageInput(
                name = "Person ${personId + 1}",
                appearanceCount = count,
                representativeFrame = repFrame?.frame
            )
        }
        val collageGenerator = CollageGenerator()
        val t7Start = System.currentTimeMillis()
        val collageBitmap = collageGenerator.generate(collageInputs)
        val t7End = System.currentTimeMillis()
        timings["Collage Generation"] = t7End - t7Start
        Log.d(TAG, "Stage 7 - Collage: ${collageBitmap.width}x${collageBitmap.height} in ${t7End - t7Start}ms")
        assertNotNull("Collage bitmap should not be null", collageBitmap)

        val collageSaver = CollageSaver(context)
        val t8Start = System.currentTimeMillis()
        val saveResult = collageSaver.save(collageBitmap)
        val t8End = System.currentTimeMillis()
        timings["Save"] = t8End - t8Start
        Log.d(TAG, "Stage 8 - Save: ${t8End - t8Start}ms")

        for (frame in frames) {
            frame.bitmap.recycle()
        }
        collageBitmap.recycle()
        embeddingEngine.close()
        detector.close()

        val total = timings.values.sum()
        Log.d(TAG, "========================================")
        Log.d(TAG, "RESULTS for ${videoFile.name} (${config.speedPreset})")
        Log.d(TAG, "========================================")
        for ((stage, time) in timings) {
            Log.d(TAG, "  $stage: ${time}ms")
        }
        Log.d(TAG, "  -------------------------")
        Log.d(TAG, "  TOTAL: ${total}ms (${String.format("%.1f", total / 1000.0)}s)")
        Log.d(TAG, "  Frames: ${frames.size}, Faces: ${faces.size}, Embeddings: ${facesWithEmbeddings.size}")
        Log.d(TAG, "  Persons: ${clusterResult.uniquePersons}, Appearances: ${appearanceResult.segments.size}")
        Log.d(TAG, "========================================")
    }

    companion object {
        private const val TAG = "E2EProcessingTest"
    }
}

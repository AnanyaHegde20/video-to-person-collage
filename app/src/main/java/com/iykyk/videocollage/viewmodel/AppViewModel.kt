package com.iykyk.videocollage.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iykyk.videocollage.data.VideoRepository
import com.iykyk.videocollage.processing.AppearanceCounter
import com.iykyk.videocollage.processing.ClusteringEngine
import com.iykyk.videocollage.processing.CollageGenerator
import com.iykyk.videocollage.processing.CollageInput
import com.iykyk.videocollage.processing.CollageSaver
import com.iykyk.videocollage.processing.CollageSharer
import com.iykyk.videocollage.processing.FaceDetector
import com.iykyk.videocollage.processing.FaceEmbeddingEngine
import com.iykyk.videocollage.processing.FaceWithEmbedding
import com.iykyk.videocollage.processing.ProcessingConfig
import com.iykyk.videocollage.processing.RepresentativeFrameSelector
import com.iykyk.videocollage.processing.SaveResult
import com.iykyk.videocollage.processing.VideoFrameExtractor
import com.iykyk.videocollage.ui.AppUiState
import com.iykyk.videocollage.ui.CollagePerson
import com.iykyk.videocollage.ui.ProcessingProgress
import com.iykyk.videocollage.ui.ProcessingStage
import com.iykyk.videocollage.ui.VideoInfo
import com.iykyk.videocollage.ui.VideoResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    var uiState by mutableStateOf(AppUiState())
        private set

    private var processingJob: Job? = null
    private var videoCounter = 0

    private val processingConfig = ProcessingConfig()
    private val frameExtractor = VideoFrameExtractor(
        context = application,
        config = processingConfig
    )
    private val faceDetector = FaceDetector(
        context = application,
        config = processingConfig
    )
    private val embeddingEngine = FaceEmbeddingEngine(application)
    private val clusteringEngine = ClusteringEngine(
        similarityThreshold = ClusteringEngine.DEFAULT_SIMILARITY_THRESHOLD
    )
    private val appearanceCounter = AppearanceCounter(
        gapToleranceMs = AppearanceCounter.DEFAULT_GAP_TOLERANCE_MS
    )
    private val representativeSelector = RepresentativeFrameSelector()
    private val collageGenerator = CollageGenerator()
    private val collageSaver = CollageSaver(application)
    private val collageSharer = CollageSharer(application)

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { queryVideoInfo(uri) }
            uiState = uiState.copy(videoInfo = info, error = null)
        }
    }

    fun loadVideoGallery() {
        if (uiState.isVideoGalleryLoading) return
        uiState = uiState.copy(isVideoGalleryLoading = true)
        viewModelScope.launch {
            val videos = withContext(Dispatchers.IO) {
                val raw = VideoRepository.queryAllVideos(getApplication())
                VideoRepository.deduplicate(raw)
            }
            uiState = uiState.copy(
                videoGalleryVideos = videos,
                isVideoGalleryLoading = false
            )
            Log.d(TAG, "Video gallery loaded: ${videos.size} unique videos")
        }
    }

    fun onProcessVideo() {
        val info = uiState.videoInfo ?: return
        videoCounter++
        val sampleName = info.displayName.removeSuffix(".mp4").removeSuffix(".MP4").removeSuffix(".mov").removeSuffix(".MOV").ifEmpty { "Sample $videoCounter" }

        uiState = uiState.copy(
            isProcessing = true,
            currentSampleIndex = videoCounter,
            currentSampleName = sampleName,
            progress = ProcessingProgress(
                stage = ProcessingStage.LOADING_VIDEO,
                fraction = 0f,
                statusText = "Reading video metadata..."
            ),
            error = null,
            showResult = false,
            extractedFrames = emptyList(),
            detectedFaces = emptyList(),
            facesWithEmbeddings = emptyList(),
            personClusters = emptyList(),
            appearanceSegments = emptyList(),
            representativeFrames = emptyList(),
            collagePersons = emptyList(),
            collageBitmap = null
        )

        processingJob = viewModelScope.launch {
            try {
                setProgress(ProcessingStage.LOADING_VIDEO, 0.5f, "Reading video: ${info.displayName}")

                val frames = frameExtractor.extract(
                    videoUri = info.uri,
                    onProgress = { progress ->
                        setProgress(
                            ProcessingStage.EXTRACTING_FRAMES,
                            progress.fraction,
                            "Frame ${progress.framesExtracted}/${progress.totalFrames} sampled"
                        )
                    }
                )

                setProgress(ProcessingStage.EXTRACTING_FRAMES, 1f, "${frames.size} frames sampled")

                val faces = faceDetector.detect(
                    frames = frames,
                    onProgress = { progress ->
                        setProgress(
                            ProcessingStage.DETECTING_FACES,
                            progress.fraction,
                            progress.phase
                        )
                    }
                )

                setProgress(ProcessingStage.DETECTING_FACES, 1f, "${faces.size} faces detected")

                setProgress(ProcessingStage.GENERATING_EMBEDDINGS, 0f, "Preparing embedding engine...")

                val facesWithEmbeddings = withContext(Dispatchers.Default) {
                    val result = mutableListOf<FaceWithEmbedding>()

                    for ((index, face) in faces.withIndex()) {
                        val embedResult = embeddingEngine.generateEmbedding(face)
                        if (embedResult != null) {
                            result.add(
                                FaceWithEmbedding(face = face, embedding = embedResult.embedding)
                            )
                        }

                        if ((index + 1) % 10 == 0 || index == faces.lastIndex) {
                            Log.d(TAG, "Embedded ${index + 1}/${faces.size} faces")
                        }

                        setProgress(
                            ProcessingStage.GENERATING_EMBEDDINGS,
                            (index + 1).toFloat() / faces.size.coerceAtLeast(1),
                            "Face ${index + 1}/${faces.size} embedded"
                        )
                    }
                    Log.d(TAG, "Embeddings: ${result.size} generated from ${faces.size} faces")
                    result
                }

                setProgress(ProcessingStage.GENERATING_EMBEDDINGS, 1f, "${facesWithEmbeddings.size} embeddings generated")

                setProgress(ProcessingStage.CLUSTERING_PEOPLE, 0f, "Computing similarity matrix...")

                val clusteringResult = clusteringEngine.cluster(facesWithEmbeddings)

                Log.d(TAG, "=== CLUSTERING RESULT ===")
                Log.d(TAG, "Total embeddings: ${facesWithEmbeddings.size}")
                Log.d(TAG, "Unique persons: ${clusteringResult.uniquePersons}")
                for (cluster in clusteringResult.clusters) {
                    val timestamps = cluster.members.map { it.face.timestampMs / 1000.0 }
                    Log.d(TAG, "  Person ${cluster.id}: ${cluster.members.size} faces, timestamps(s)=${timestamps.map { "%.1f".format(it) }}")
                }

                setProgress(
                    ProcessingStage.CLUSTERING_PEOPLE,
                    1f,
                    "${clusteringResult.uniquePersons} unique persons found"
                )

                setProgress(ProcessingStage.COUNTING_APPEARANCES, 0f, "Analyzing time segments...")

                val appearanceResult = appearanceCounter.countAppearances(clusteringResult.clusters)

                Log.d(TAG, "=== APPEARANCE COUNTING RESULT ===")
                Log.d(TAG, "Total segments: ${appearanceResult.segments.size}")
                for ((personId, count) in appearanceResult.personAppearanceCounts) {
                    val segments = appearanceResult.segments.filter { it.personId == personId }
                    Log.d(TAG, "  Person $personId: $count appearances")
                    for ((segIdx, seg) in segments.withIndex()) {
                        Log.d(TAG, "    Segment ${segIdx + 1}: ${(seg.startTimestampMs / 1000.0)}s - ${(seg.endTimestampMs / 1000.0)}s (${seg.observations.size} observations)")
                    }
                }
                val totalPersonAppearances = appearanceResult.personAppearanceCounts.values.sum()
                Log.d(TAG, "Total person appearances: $totalPersonAppearances")

                setProgress(
                    ProcessingStage.COUNTING_APPEARANCES,
                    1f,
                    "${appearanceResult.segments.size} total appearances"
                )

                setProgress(ProcessingStage.SELECTING_FRAMES, 0f, "Scoring frames by quality...")

                val repFrames = representativeSelector.select(clusteringResult.clusters)

                setProgress(
                    ProcessingStage.SELECTING_FRAMES,
                    1f,
                    "${repFrames.size} representative frames selected"
                )

                val collagePersons = appearanceResult.personAppearanceCounts.map { (personId, count) ->
                    val repFrame = repFrames.find { it.personId == personId }
                    CollagePerson(
                        name = "Person ${personId + 1}",
                        appearanceCount = count,
                        representativeFrame = repFrame?.frame
                    )
                }

                val collageInputs = collagePersons.map {
                    CollageInput(
                        name = it.name,
                        appearanceCount = it.appearanceCount,
                        representativeFrame = it.representativeFrame
                    )
                }

                setProgress(ProcessingStage.GENERATING_COLLAGE, 0f, "Composing layout...")

                val collageBitmap = withContext(Dispatchers.Default) {
                    collageGenerator.generate(collageInputs)
                }

                setProgress(
                    ProcessingStage.COMPLETE,
                    1f,
                    "${collagePersons.size} persons, ${appearanceResult.segments.size} appearances"
                )

                val videoResult = VideoResult(
                    videoName = sampleName,
                    collageBitmap = collageBitmap,
                    collagePersons = collagePersons
                )

                uiState = uiState.copy(
                    representativeFrames = repFrames,
                    collagePersons = collagePersons,
                    collageBitmap = collageBitmap,
                    completedResults = uiState.completedResults + videoResult,
                    currentResult = videoResult,
                    isProcessing = false,
                    showResult = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                uiState = uiState.copy(
                    isProcessing = false,
                    error = "Processing failed: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun retryProcessing() {
        onProcessVideo()
    }

    private fun setProgress(stage: ProcessingStage, fraction: Float, statusText: String) {
        uiState = uiState.copy(
            progress = ProcessingProgress(
                stage = stage,
                fraction = fraction.coerceIn(0f, 1f),
                statusText = statusText
            )
        )
    }

    fun showError(message: String) {
        uiState = uiState.copy(
            isProcessing = false,
            error = message
        )
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        uiState = uiState.copy(
            isProcessing = false,
            error = "Processing cancelled"
        )
    }

    fun saveCollage() {
        val bitmap = uiState.collageBitmap ?: return
        uiState = uiState.copy(isSaving = true, feedback = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { collageSaver.save(bitmap) }
            uiState = when (result) {
                is SaveResult.Success -> uiState.copy(
                    isSaving = false,
                    feedback = "Collage saved to gallery"
                )
                is SaveResult.Error -> uiState.copy(
                    isSaving = false,
                    feedback = result.message
                )
            }
        }
    }

    fun shareCollage() {
        val bitmap = uiState.collageBitmap ?: return
        uiState = uiState.copy(feedback = null)
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { collageSharer.share(bitmap) }
            if (!success) {
                uiState = uiState.copy(feedback = "Share failed")
            }
        }
    }

    fun clearFeedback() {
        uiState = uiState.copy(feedback = null)
    }

    fun reset() {
        processingJob?.cancel()
        processingJob = null
        uiState = AppUiState()
        videoCounter = 0
    }

    override fun onCleared() {
        super.onCleared()
        faceDetector.close()
        embeddingEngine.close()
    }

    private fun queryVideoInfo(uri: Uri): VideoInfo {
        val context = getApplication<Application>()
        val contentResolver = context.contentResolver

        var displayName = "Unknown video"
        var duration = 0L
        var width = 0
        var height = 0

        contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                displayName = cursor.getString(nameIndex) ?: "Unknown video"
                duration = cursor.getLong(durationIndex)
                width = cursor.getInt(widthIndex)
                height = cursor.getInt(heightIndex)
            }
        }

        return VideoInfo(
            uri = uri,
            displayName = displayName,
            duration = duration,
            width = width,
            height = height
        )
    }

    companion object {
        private const val TAG = "AppViewModel"
    }
}

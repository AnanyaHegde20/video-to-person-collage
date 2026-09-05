package com.iykyk.videocollage.ui

import android.graphics.Bitmap
import android.net.Uri
import com.iykyk.videocollage.data.VideoItem
import com.iykyk.videocollage.processing.AppearanceSegment
import com.iykyk.videocollage.processing.DetectedFace
import com.iykyk.videocollage.processing.ExtractedFrame
import com.iykyk.videocollage.processing.FaceWithEmbedding
import com.iykyk.videocollage.processing.PersonCluster
import com.iykyk.videocollage.processing.RepresentativeFrame

data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val duration: Long,
    val width: Int,
    val height: Int
)

data class VideoResult(
    val videoName: String,
    val collageBitmap: Bitmap?,
    val collagePersons: List<CollagePerson>,
    val totalAppearances: Int = collagePersons.sumOf { it.appearanceCount }
)

enum class ProcessingStage(val label: String, val order: Int) {
    LOADING_VIDEO("Loading video", 0),
    EXTRACTING_FRAMES("Extracting frames", 1),
    DETECTING_FACES("Detecting faces", 2),
    GENERATING_EMBEDDINGS("Generating embeddings", 3),
    CLUSTERING_PEOPLE("Clustering people", 4),
    COUNTING_APPEARANCES("Counting appearances", 5),
    SELECTING_FRAMES("Selecting representative frames", 6),
    GENERATING_COLLAGE("Generating collage", 7),
    COMPLETE("Complete", 8)
}

data class ProcessingProgress(
    val stage: ProcessingStage = ProcessingStage.LOADING_VIDEO,
    val fraction: Float = 0f,
    val statusText: String = ""
) {
    val overallFraction: Float
        get() {
            val stageWeight = 1f / ProcessingStage.entries.size
            val base = stage.order * stageWeight
            return (base + fraction * stageWeight).coerceIn(0f, 1f)
        }
}

data class CollagePerson(
    val name: String,
    val appearanceCount: Int,
    val representativeFrame: Bitmap? = null
)

data class AppUiState(
    val videoInfo: VideoInfo? = null,
    val isProcessing: Boolean = false,
    val progress: ProcessingProgress = ProcessingProgress(),
    val extractedFrames: List<ExtractedFrame> = emptyList(),
    val detectedFaces: List<DetectedFace> = emptyList(),
    val facesWithEmbeddings: List<FaceWithEmbedding> = emptyList(),
    val personClusters: List<PersonCluster> = emptyList(),
    val appearanceSegments: List<AppearanceSegment> = emptyList(),
    val representativeFrames: List<RepresentativeFrame> = emptyList(),
    val collagePersons: List<CollagePerson> = emptyList(),
    val collageBitmap: Bitmap? = null,
    val error: String? = null,
    val showResult: Boolean = false,
    val feedback: String? = null,
    val isSaving: Boolean = false,
    val videoGalleryVideos: List<VideoItem> = emptyList(),
    val isVideoGalleryLoading: Boolean = false,
    val completedResults: List<VideoResult> = emptyList(),
    val currentResult: VideoResult? = null,
    val currentSampleIndex: Int = 0,
    val currentSampleName: String = ""
)

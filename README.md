# IYKYK Video Collage

An Android application that processes portrait videos to detect faces, identify unique individuals across frames, count their appearances, and generate Instagram Story-style collages -- entirely on-device using on-device ML.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Features](#2-features)
3. [Tech Stack](#3-tech-stack)
4. [Architecture](#4-architecture)
5. [Processing Pipeline](#5-processing-pipeline)
6. [Video Frame Sampling](#6-video-frame-sampling)
7. [ML Kit Face Detection](#7-ml-kit-face-detection)
8. [TFLite Embedding Model](#8-tflite-embedding-model)
9. [Model Input/Output Details](#9-model-inputoutput-details)
10. [Preprocessing](#10-preprocessing)
11. [Embedding Normalization](#11-embedding-normalization)
12. [Cosine Similarity](#12-cosine-similarity)
13. [Similarity Threshold](#13-similarity-threshold)
14. [Identity Clustering](#14-identity-clustering)
15. [Appearance Counting](#15-appearance-counting)
16. [Appearance Gap Tolerance](#16-appearance-gap-tolerance)
17. [Representative Frame Scoring](#17-representative-frame-scoring)
18. [Collage Generation](#18-collage-generation)
19. [Gallery Saving](#19-gallery-saving)
20. [Android Share Sheet](#20-android-share-sheet)
21. [Build Instructions](#21-build-instructions)
22. [Run Instructions](#22-run-instructions)
23. [Testing with Supplied Videos](#23-testing-with-supplied-videos)
24. [Known Limitations](#24-known-limitations)

---

## 1. Project Overview

**IYKYK Video Collage** takes a portrait video as input and produces an Instagram Story-sized collage (1080x1920) featuring the best face frame for each unique person detected, along with their appearance count. All processing runs entirely on-device -- no cloud services, no data leaves the phone.

The app extracts frames from the video, detects faces using ML Kit, generates 192-dimensional face embeddings using a MobileFaceNet TFLite model, clusters embeddings to identify unique individuals, counts how many separate times each person appeared, selects the highest-quality representative frame per person, and composes a styled collage.

---

## 2. Features

- **Video Selection** -- Pick any portrait video from the device gallery via the system video picker
- **Frame Extraction** -- Dense temporal sampling at 100ms intervals with `MediaMetadataRetriever.OPTION_CLOSEST`
- **Face Detection** -- ML Kit bundled model detecting faces as small as 5% of frame height, with classification (eye openness, smile probability) and head pose angles
- **Face Embedding** -- MobileFaceNet TFLite model producing L2-normalized 192-dimensional identity vectors
- **Identity Clustering** -- Centroid-based incremental clustering with cosine similarity threshold
- **Appearance Counting** -- Temporal gap-based segmentation counting separate appearances per person
- **Representative Frame Selection** -- Weighted quality scoring (frontality, sharpness, eye openness, expression, visibility)
- **Collage Generation** -- Adaptive Canvas-based layouts for 1-6+ persons, 1080x1920 output
- **Gallery Saving** -- MediaStore API (Android 10+) or direct file write (older versions)
- **Share** -- Android share sheet via FileProvider
- **9-Stage Progress UI** -- Real-time progress with stage breakdown and percentage
- **Retry/Cancel** -- Cancel in-progress processing or retry on failure

---

## 3. Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.0.21 |
| UI Framework | Jetpack Compose (BOM 2024.12.01) |
| Architecture | MVVM (AndroidViewModel + Compose state) |
| Navigation | Navigation Compose 2.8.5 |
| Face Detection | Google ML Kit Face Detection 16.1.6 (bundled model) |
| Face Embedding | TensorFlow Lite 2.16.1 + TFLite Support 0.4.4 |
| Concurrency | Kotlin Coroutines + Flow |
| Build System | Gradle 8.9, AGP 8.7.3 |
| Min SDK | 26 (Android 8.0) |
| Target/Compile SDK | 35 (Android 15) |

---

## 4. Architecture

The app follows MVVM architecture with a single-Activity, Compose-only UI:

```
MainActivity (single Activity, edge-to-edge)
  |
  +-- AppNavGraph (NavHost with 3 routes)
        |
        +-- HomeScreen      -- video selection, metadata display
        +-- ProcessingScreen -- 9-stage progress, cancel/retry
        +-- ResultScreen     -- collage preview, person list, save/share

AppViewModel (AndroidViewModel)
  |
  +-- UiState (Compose mutableStateOf, single source of truth)
  +-- Processing pipeline (orchestrated via viewModelScope coroutines)
  +-- All processing runs on Dispatchers.Default or Dispatchers.IO
```

**Source structure:**

```
com.iykyk.videocollage/
  MainActivity.kt              -- Edge-to-edge setup, Compose host
  IYKYKApp.kt                  -- Application class
  viewmodel/
    AppViewModel.kt            -- Pipeline orchestration, UI state
  ui/
    UiState.kt                 -- AppUiState, ProcessingStage, CollagePerson
    navigation/
      Screen.kt                -- Route definitions
      AppNavGraph.kt           -- Navigation graph
    screens/
      HomeScreen.kt            -- Video picker, info card
      ProcessingScreen.kt      -- Progress UI, stage list
      ResultScreen.kt          -- Collage image, person list, actions
  processing/
    VideoFrameExtractor.kt     -- Frame extraction from video
    FaceDetector.kt            -- ML Kit face detection wrapper
    FaceEmbeddingEngine.kt     -- TFLite MobileFaceNet inference
    ClusteringEngine.kt        -- Centroid-based identity clustering
    AppearanceCounter.kt       -- Temporal appearance segmentation
    FrameScorer.kt             -- Multi-factor frame quality scoring
    RepresentativeFrameSelector.kt -- Best frame per person
    CollageGenerator.kt        -- Canvas collage composition
    CollageConfig.kt           -- Collage styling parameters
    CollageSaver.kt            -- MediaStore / file save
    CollageSharer.kt           -- FileProvider share intent
    SimilarityUtil.kt          -- Cosine similarity, L2 distance
    DetectedFace.kt            -- Face detection result data class
    ExtractedFrame.kt          -- Frame extraction result data class
    FaceWithEmbedding.kt       -- Face + embedding pair
    PersonCluster.kt           -- Cluster identity data class
    AppearanceSegment.kt       -- Appearance segment data class
    CollageInput.kt            -- Collage input data class
```

---

## 5. Processing Pipeline

The pipeline runs 9 sequential stages, each reporting progress:

| Stage | Description |
|-------|-------------|
| 1. Loading Video | Read video metadata (name, duration, resolution) |
| 2. Extracting Frames | Sample frames every 100ms, scale to 540px width |
| 3. Detecting Faces | Run ML Kit on each frame, collect bounding boxes + classification |
| 4. Generating Embeddings | Run TFLite MobileFaceNet on each detected face crop |
| 5. Clustering People | Group embeddings by identity using cosine similarity threshold |
| 6. Counting Appearances | Segment each person's detections into separate appearances |
| 7. Selecting Frames | Score frames and pick the best representative per person |
| 8. Generating Collage | Compose 1080x1920 Canvas with adaptive tile layout |
| 9. Complete | Display results, enable save/share |

Heavy operations (embedding generation, collage generation, file I/O) run on `Dispatchers.Default` or `Dispatchers.IO` to keep the UI responsive.

---

## 6. Video Frame Sampling

**Implementation:** `VideoFrameExtractor.kt`

- **Interval:** 100ms between frames (`DEFAULT_INTERVAL_MS = 100L`)
- **Max frames:** 500 (`MAX_FRAMES = 500`)
- **Target width:** 540px (height scaled proportionally)
- **Extraction method:** `MediaMetadataRetriever.OPTION_CLOSEST` -- returns the frame closest to the requested timestamp, avoiding duplicate keyframes that `OPTION_CLOSEST_SYNC` would return for nearby timestamps
- **Scaling:** `Bitmap.createScaledBitmap` with bilinear filtering; original bitmap recycled after scaling

The 100ms interval provides dense temporal coverage for a typical 10-30 second video (100-300 frames), capturing multiple face detections per person across different poses and moments.

---

## 7. ML Kit Face Detection

**Implementation:** `FaceDetector.kt`

Uses the **bundled** ML Kit Face Detection model (`com.google.mlkit:face-detection:16.1.6`), which does not require Google Play Services.

**Configuration:**

| Parameter | Value |
|-----------|-------|
| Performance mode | `PERFORMANCE_MODE_ACCURATE` |
| Landmark mode | `LANDMARK_MODE_NONE` |
| Classification mode | `CLASSIFICATION_MODE_ALL` |
| Contour mode | `CONTOUR_MODE_NONE` |
| Min face size | `0.05f` (5% of frame height) |

**Per-face data captured:**

- Bounding box (`RectF`)
- Head Euler angles (X, Y, Z) -- pitch, yaw, roll
- Left/right eye open probability
- Smiling probability
- Source frame reference (for later cropping)

The `MIN_FACE_SIZE = 0.05f` setting detects faces as small as 5% of frame height, catching distant or partially visible faces that larger thresholds would miss.

---

## 8. TFLite Embedding Model

**Model file:** `app/src/main/assets/MobileFaceNet.tflite` (5.0 MB)

**Architecture:** MobileFaceNet -- a lightweight CNN designed for mobile face recognition. Produces 192-dimensional embeddings optimized for on-device inference.

**Runtime configuration:**

| Parameter | Value |
|-----------|-------|
| Interpreter threads | 4 |
| Input tensor | `[2, 112, 112, 3]` float32 |
| Output tensor | `[2, 192]` float32 |
| Batch size | 2 (both slots receive the same face for efficient inference) |

---

## 9. Model Input/Output Details

**Input tensor:** `[2, 112, 112, 3]` -- 301,056 float32 values

- Batch size = 2 (both slots contain the same face crop)
- Image size = 112 x 112 pixels
- Channels = 3 (RGB)
- Data type = float32

**Output tensor:** `[2, 192]` -- 384 float32 values

- Batch size = 2
- Embedding dimension = 192
- Only `outputBuffer[0]` (first slot) is used as the face embedding
- Data type = float32

---

## 10. Preprocessing

**Implementation:** `FaceEmbeddingEngine.preprocessImage()`

Each face crop is preprocessed into the model's expected input format:

1. **Crop face region** -- Extract face bounding box with 20% padding (`FACE_REGION_PADDING = 0.2f`)
2. **Resize** -- Scale cropped region to 112x112 pixels using bilinear interpolation
3. **Normalize pixels** -- For each channel (R, G, B):

```
normalized = (pixel / 127.5) - 1.0
```

This maps pixel values from `[0, 255]` to `[-1.0, 1.0]`.

4. **Pack into ByteBuffer** -- Float32 byte order, batch of 2 (same face in both slots)

---

## 11. Embedding Normalization

**Implementation:** `FaceEmbeddingEngine.l2Normalize()`

After TFLite inference, the raw 192-dimensional output vector is L2-normalized:

```
L2_norm = sqrt(sum(x_i^2))
normalized_i = x_i / max(L2_norm, 1e-12)
```

This maps the embedding to a unit hypersphere, making cosine similarity equivalent to dot product and ensuring consistent magnitude across all embeddings.

---

## 12. Cosine Similarity

**Implementation:** `SimilarityUtil.cosineSimilarity()`

```kotlin
cosine_sim(a, b) = dot(a, b) / (||a|| * ||b||)
```

For L2-normalized embeddings, this reduces to `dot(a, b)` since `||a|| = ||b|| = 1`. The implementation includes a safety check against near-zero denominators (`1e-12`).

Also available: `l2Distance()` for Euclidean distance calculations.

---

## 13. Similarity Threshold

**Value:** `0.50f` (`ClusteringEngine.DEFAULT_SIMILARITY_THRESHOLD`)

**Why 0.50:**

- **MobileFaceNet characteristic:** The model produces L2-normalized embeddings where same-person cosine similarities typically range from 0.5-0.9, and different-person similarities typically fall below 0.3
- **Empirically validated:** A threshold of 0.50 sits at the boundary of same-person similarity distributions, providing reliable identity grouping
- **Conservative choice:** Prioritizes avoiding identity merges (false positives) over catching all appearances of the same person (false negatives). Under-clustering (treating one person as two) is preferable to over-clustering (merging two people into one)
- **Tested with 0.99 threshold in unit tests** -- the test suite uses a stricter threshold to validate clustering logic; the production threshold of 0.50 handles real-world variation better

---

## 14. Identity Clustering

**Implementation:** `ClusteringEngine.cluster()`

Uses **centroid-based incremental clustering** (online/streaming approach):

1. Initialize empty cluster list
2. For each face embedding:
   a. Compute cosine similarity to all existing cluster centroids
   b. Find the most similar cluster
   c. If similarity >= threshold (0.50): add face to that cluster, recompute centroid as L2-normalized average of all member embeddings
   d. If no cluster matches: create a new cluster with this face as the initial member
3. Return all clusters with their member faces and updated centroids

**Key properties:**

- **Single-pass:** O(n * k) where n = number of faces, k = number of clusters (typically small)
- **Centroid update:** Each new member updates the cluster centroid via L2-normalized averaging, allowing the cluster to adapt to pose/lighting variation
- **Deterministic order:** Results depend on input order; faces are processed in temporal extraction order

---

## 15. Appearance Counting

**Implementation:** `AppearanceCounter.countAppearances()`

After clustering, each person's detected faces are sorted by timestamp. The algorithm segments consecutive detections into separate "appearances" based on temporal gaps:

1. For each person cluster, sort faces by `timestampMs`
2. Iterate through sorted faces:
   - If gap between consecutive detections <= gap tolerance: merge into same appearance segment
   - If gap > gap tolerance: start a new appearance segment
3. Count total segments per person = appearance count

Each segment records:
- `personId`, `startTimestampMs`, `endTimestampMs`
- All `observations` (FaceWithEmbedding list)
- `bestCandidateIndex` -- index of highest-quality face in the segment

---

## 16. Appearance Gap Tolerance

**Value:** `1000L` milliseconds (`AppearanceCounter.DEFAULT_GAP_TOLERANCE_MS`)

**Rationale:**

- At 100ms frame intervals, consecutive frames are 100ms apart
- A gap tolerance of 1000ms means: if the same person is detected in frames 100ms-900ms apart, it counts as one continuous appearance
- If the same person disappears for >1 second and reappears, it counts as a separate appearance
- This handles brief occlusions (person turns away momentarily) while correctly splitting genuine separate appearances (person walks away and comes back)

---

## 17. Representative Frame Scoring

**Implementation:** `FrameScorer.score()` + `RepresentativeFrameSelector.select()`

For each person cluster, the best representative frame is selected using weighted quality scoring:

| Factor | Weight | Description |
|--------|--------|-------------|
| Frontality | 0.30 | Head pose angles (Euler Y and X) -- lower angles = more frontal |
| Sharpness | 0.30 | Laplacian variance of the face region -- higher = sharper |
| Eye Openness | 0.20 | Average of left/right eye open probability from ML Kit |
| Expression | 0.10 | Smiling probability from ML Kit |
| Visibility | 0.10 | Face box area relative to frame area -- larger = more visible |

**Rejection criteria:**

- Face ratio < 2% of frame area
- Head Euler angle Y > 45 degrees
- Either eye open probability < 0.3 (eyes closed)

If all frames for a person are rejected, the first available frame is used as fallback.

The selected frame is cropped with 150% padding around the bounding box (`CROP_PADDING = 1.5f`) for the collage tile, providing generous framing that includes context around the face.

---

## 18. Collage Generation

**Implementation:** `CollageGenerator.generate()`

Generates a 1080x1920 pixel (Instagram Story) collage using Android Canvas:

**Output parameters:**

| Parameter | Value |
|-----------|-------|
| Width | 1080px |
| Height | 1920px |
| Background | `#1A1A2E` (dark navy) |
| Gap between tiles | 12px |
| Margin around edges | 24px |
| Tile corner radius | 24px |
| Tile border | 3px, `#2A2A4A` |

**Adaptive layout by person count:**

- **1 person:** Full-width tile
- **2 persons:** Two equal horizontal tiles
- **3 persons:** One large top tile (50% height) + two smaller bottom tiles
- **4 persons:** 2x2 grid
- **5 persons:** One large + two medium + two small
- **6+ persons:** Grid layout with equal sizing

Each tile includes:

- The person's representative face frame (cropped, scaled to tile)
- A label bar at the bottom with the person's name and appearance count
- Appearance count badge with gold text (`#FFD700`)

---

## 19. Gallery Saving

**Implementation:** `CollageSaver.save()`

Saves the collage bitmap to the device gallery:

- **Android 10+ (API 29+):** Uses `MediaStore.Images.Media` with `IS_PENDING` workflow for atomic insert. Saves to `Pictures/IYKYK/` directory as PNG.
- **Android 9 and below:** Direct file write to `Pictures/IYKYK/` using `Environment.getExternalStoragePublicDirectory`.

**Filename format:** `collage_<timestamp>.png`

Returns `SaveResult.Success(uri)` or `SaveResult.Error(message)` with appropriate error handling for storage permissions and I/O failures.

---

## 20. Android Share Sheet

**Implementation:** `CollageSharer.share()`

1. Save bitmap to app cache directory (`cache/shared/collage_share.png`)
2. Get content URI via `FileProvider` (authority: `com.iykyk.videocollage.fileprovider`)
3. Create `ACTION_SEND` intent with `image/*` MIME type and `FLAG_GRANT_READ_URI_PERMISSION`
4. Wrap in `Intent.createChooser` for the system share sheet
5. Launch with `FLAG_ACTIVITY_NEW_TASK`

FileProvider paths are configured in `res/xml/file_paths.xml` to expose the cache directory.

---

## 21. Build Instructions

### Prerequisites

- **JDK 17** (set `JAVA_HOME`)
- **Android SDK** (set `ANDROID_HOME` or `ANDROID_SDK_ROOT`)
- **Android SDK Platform 35**, **Build Tools 35.0.0**, **Platform Tools**

### Build

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "C:\Android\Sdk"
cd IYKYKVideoCollage
.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon
```

```bash
# Linux/macOS
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
cd IYKYKVideoCollage
./gradlew clean assembleDebug testDebugUnitTest --no-daemon
```

**Output:** `app/build/outputs/apk/debug/app-debug.apk`

### Run Tests Only

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

70 unit tests across 7 test files:
- `SimilarityUtilTest` (9 tests)
- `ClusteringEngineTest` (12 tests)
- `AppearanceCounterTest` (14 tests)
- `FrameScorerTest` (13 tests)
- `CollageGeneratorTest` (10 tests)
- `CollageSaverTest` (4 tests)
- `ProcessingProgressTest` (8 tests)

---

## 22. Run Instructions

### Install on Emulator

```powershell
# Start emulator (if not running)
& "C:\Android\Sdk\emulator\emulator.exe" -avd test_avd

# Install APK
& "C:\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk

# Launch app
& "C:\Android\Sdk\platform-tools\adb.exe" shell am start -n com.iykyk.videocollage/.MainActivity
```

### Usage

1. Open the app -- tap **Select Video**
2. Choose a portrait video from the gallery
3. Review video info (name, duration, resolution) on the home screen
4. Tap **Process Video** -- watch the 9-stage progress
5. View results: collage image, detected persons, appearance counts
6. Tap **Save** to save to gallery, or **Share** to share via other apps
7. Tap **New Video** to process another video

---

## 23. Testing with Supplied Videos

Test with three different portrait videos to validate:

### Video Requirements

- Portrait orientation (height > width)
- At least one visible face
- 5-30 seconds duration recommended

### Expected Behavior

| Video | What to Verify |
|-------|---------------|
| Sample_1 (multiple people) | Should detect multiple unique persons, assign correct appearance counts, generate multi-tile collage |
| Sample_2 (single person) | Should detect 1 person, show appearance count, generate single-tile layout |
| Sample_3 (varied poses) | Should handle pose variation within same person, cluster correctly across angles |

### Validation Checklist

- [ ] Frames extracted at 100ms intervals (check logcat: `VideoFrameExtractor`)
- [ ] Faces detected in most frames (check logcat: `FaceDetector`)
- [ ] Embeddings are non-zero (check logcat: `FaceEmbeddingEngine` -- raw values should not all be 0.0)
- [ ] Persons clustered correctly (logcat: `ClusteringEngine` -- shows similarity scores and cluster assignments)
- [ ] Appearance counts reasonable (e.g., person visible throughout = 1 appearance; person leaves and returns = 2+)
- [ ] Collage generated with correct tile count matching detected persons
- [ ] Save writes file to `Pictures/IYKYK/` directory
- [ ] Share opens system share sheet with collage image

---

## 24. Known Limitations

1. **No face tracking across frames** -- Each frame is processed independently. Temporal face tracking could improve clustering accuracy and reduce redundant embeddings.

2. **Appearance count depends on video content** -- A person who is continuously visible will count as 1 appearance. Brief occlusions (>1s gap) may split a single continuous appearance into multiple counts.

3. **Clustering is order-dependent** -- The incremental centroid-based algorithm processes faces in temporal order. Different frame extraction rates or video orderings could produce slightly different clustering results.

4. **Single face per person per frame** -- If multiple faces of the same person appear in one frame (e.g., in a mirror), they are treated as separate detections. The clustering should merge them, but this is not guaranteed.

5. **No face quality-based frame skipping** -- All detected faces are embedded and clustered, even very small or blurred ones. Quality filtering at detection time could improve performance.

6. **Model loading time** -- MobileFaceNet TFLite model loading takes approximately 3 seconds on first run. The model is not cached between app launches.

7. **No GPU delegation** -- The TFLite interpreter runs on CPU with 4 threads. GPU delegate support could improve inference speed.

8. **Collage layout limited to 6+ persons** -- For >6 persons, the layout switches to a simple grid. More sophisticated layouts could improve aesthetics for large groups.

9. **No undo for save** -- Once saved to the gallery, the collage cannot be unsaved from within the app.

10. **Emulator-only testing** -- The app has been tested on an Android 14 emulator without Google Play Store. The bundled ML Kit model works without Play Services, but performance on physical devices may differ.

---



# Vantage: Technical Implementation

## Overview

Vantage is an AI-powered camera app that runs **Google Gemma 4 (E2B)** entirely on-device via **Qualcomm's Snapdragon NPU** using **LiteRT-LM**. When a user taps the AI shutter, Gemma analyzes the scene in real time, recommends optimal camera settings (ISO, shutter speed, white balance, zoom, composition), selects a cinematic filter, and produces a professionally enhanced photo, all without any cloud dependency.

---

## Architecture

```
User Input (Voice / Shutter Tap)
        |
        v
  +--------------+       +------------------+
  | CameraX      |------>| Frame Capture    |
  | Preview       |       | (JPEG to disk)   |
  +--------------+       +------------------+
                                |
                                v
                    +------------------------+
                    | LiteRT-LM Engine       |
                    | (Gemma 4 E2B on NPU)   |
                    |                        |
                    | Round 1: Full analysis  |
                    | Round 2: Refinement     |
                    +------------------------+
                                |
                                v
                    +------------------------+
                    | SceneAnalysis Output   |
                    | filter, iso, shutter,  |
                    | wb, brightness,        |
                    | contrast, saturation,  |
                    | gamma, zoom,           |
                    | composition_tip        |
                    +------------------------+
                          |             |
                          v             v
              +---------------+  +------------------+
              | Camera2 API   |  | GPUImage Pipeline |
              | (Hardware)    |  | (Post-Processing) |
              | ISO, Shutter, |  | Filters, LUTs,   |
              | WB, Focus     |  | Tone Curves      |
              +---------------+  +------------------+
                                        |
                                        v
                                +--------------+
                                | Enhanced     |
                                | Photo Saved  |
                                | to Gallery   |
                                +--------------+
```

---

## Core SDKs & Dependencies

| Component | SDK / Library | Version | Purpose |
|-----------|--------------|---------|---------|
| On-Device AI | `com.google.ai.edge.litertlm:litertlm-android` | latest | Gemma 4 E2B inference on NPU/GPU/CPU |
| Camera | `androidx.camera:camera-camera2` | 1.6.0 | CameraX with Camera2 interop for manual controls |
| Image Processing | `jp.co.cyberagent.android:gpuimage` | 2.1.0 | GPU-accelerated filter pipeline |
| Image Loading | `io.coil-kt:coil-compose` | 2.7.0 | Async image loading for references and gallery |
| Networking | `com.squareup.okhttp3:okhttp` | 4.12.0 | Unsplash API and reference image downloads |
| UI | `androidx.compose` (BOM 2026.04.01) | Latest | Jetpack Compose for reactive UI |
| Navigation | `androidx.navigation:navigation-compose` | 2.9.8 | Screen navigation |
| Coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.2 | Async orchestration |
| Lifecycle | `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.10.0 | ViewModel integration |

**Build targets:** `minSdk 31`, `targetSdk 35`, `compileSdk 36`, ABI filter: `arm64-v8a` only (Snapdragon optimized).

---

## 1. LiteRT-LM and Gemma 4 on Snapdragon NPU

### Model Loading

The engine searches for `.litertlm` model files across multiple locations, prioritizing files with `sm8750` in the name (Snapdragon 8 Elite):

```
Search order:
  1. getExternalFilesDir(null)  - App-specific external storage
  2. Downloads folder           - User-downloaded models
  3. filesDir                   - Internal storage
  4. /data/local/tmp            - ADB-pushed models (development)
```

### NPU Backend Configuration

LiteRT-LM is initialized with a multi-backend strategy:

```kotlin
val config = EngineConfig(
    backends = listOf(
        Backend.NPU(nativeLibDir),   // Primary: Qualcomm Hexagon DSP
        Backend.GPU(),                // Fallback: Vision tasks
        Backend.CPU()                 // Fallback: Audio/text
    )
)
```

**QNN (Qualcomm Neural Network) Runtime Setup:**
- Native `.so` libraries are extracted via `useLegacyPackaging = true` in the build config, enabling `dlopen()` for QNN
- Environment variables `ADSP_LIBRARY_PATH` and `LD_LIBRARY_PATH` are set to the app's native library directory for DSP interop
- Manifest declares `libcdsprpc.so` as an optional native library

### Multi-Modal Inference

Gemma 4 E2B accepts both images and text through LiteRT-LM's `Contents` API:

```kotlin
// Single image analysis
val contents = Contents.of(
    Content.ImageFile(imagePath),
    Content.Text(prompt)
)

// Dual image analysis (reference + scene)
val contents = Contents.of(
    Content.ImageFile(referenceImagePath),   // Style reference
    Content.ImageFile(scenePath),            // Camera frame
    Content.Text(prompt)
)

val message = Message.user(contents)
```

Responses stream asynchronously via Kotlin Flow:

```kotlin
conversation.sendMessageAsync(message).collect { response ->
    val text = response.contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString("")
}
```

### Multi-Round Analysis

The AI analysis runs in an iterative loop for progressive refinement:

**Round 1 (Cold Start):** Gemma receives the camera frame and outputs a complete JSON recommendation covering filter selection, camera hardware settings, post-processing parameters, zoom, and composition guidance.

**Round 2+ (Refinement):** Gemma receives the same frame with its previous recommendations as context. It evaluates whether exposure, color, and framing are correct, fine-tunes values, and sets `ready: true` when satisfied.

**Filter Locking:** Once Round 1 selects a filter, subsequent rounds preserve it to prevent filter oscillation during refinement.

**Timeout:** 15-second maximum for the entire analysis loop with graceful fallback to defaults.

---

## 2. Prompt Engineering

### Round 1 Prompt

```
[Optional: USER WANTS: "{voice prompt}". Match this mood.]
[Optional: REFERENCE IMAGE instructions for dual-image style matching]

Analyze this photo. Output ONLY a short JSON, no explanation.
Pick a filter: NATURAL|WARM|COOL|VIVID|DRAMATIC|CINEMATIC|VINTAGE|NOIR|KODAK_GOLD|
               PORTRA|FUJI_VELVIA|GOLDEN_HOUR|BLUE_HOUR|MUTED|FADE
Set iso (100-3200), shutter (30-2000),
    wb (auto|daylight|cloudy|shade|incandescent|fluorescent|twilight),
    brightness (-0.1 to 0.1), contrast (1.1-1.6),
    saturation (0.6-1.4), gamma (1.0-1.2).
Zoom: 0.6 wide/group, 1.0 default, 2.0 portrait, 3.0 distant subject.
Composition: detect subject, give a short tip (e.g. "move left", "tilt down",
             "step back"). Set composition_ok:true if framing is good.
Do NOT use default values. Every photo needs visible enhancement.
{"filter":"...","iso":...,"shutter":...,"wb":"...","zoom":...,
 "brightness":...,"contrast":...,"saturation":...,"gamma":...,
 "composition_tip":"...","composition_ok":...,"reason":"..."}
```

### Round 2 Prompt

```
Previous: filter={prev}, iso={prev}, shutter=1/{prev}, wb={prev},
          brightness={prev}, contrast={prev}, saturation={prev}, gamma={prev}
Is exposure/color/framing correct now? Fine-tune if needed.
Set ready:true if good.
{"filter":"...","iso":...,"shutter":...,"wb":"...","zoom":...,
 "brightness":...,"contrast":...,"saturation":...,"gamma":...,
 "composition_tip":"...","composition_ok":...,"ready":true,"reason":"..."}
```

### Reference Image Prompt (Style Matching)

When a user provides a voice prompt (e.g., "vintage"), Gemma receives two images:

```
USER WANTS: "vintage"
REFERENCE IMAGE: The FIRST image is a reference photo showing the target style.
The SECOND image is the camera's current frame.
Analyze the reference image's color grading, contrast, saturation, warmth, mood,
and aesthetic. Choose filter, brightness, contrast, saturation, gamma to make
the camera frame match the reference style.
```

---

## 3. JSON Parsing & Robustness

Gemma's JSON output is parsed with extensive error handling:

- **JSON extraction:** Regex finds the first `{...}` block, handles unterminated objects
- **Fuzzy key matching:** Tolerates trailing underscores, partial key names, and case variations
- **Shutter notation fix:** Converts `"1/125"` string to integer `125`
- **Value clamping:** All numeric parameters are clamped to valid hardware ranges
- **Repetition stripping:** Detects and truncates text repetition artifacts (30-char window within 50-char distance)
- **Filter keyword dictionary:** 100+ terms map to filter enums (e.g., "golden", "warm sunset", "moody" all resolve correctly)

### SceneAnalysis Data Model

```kotlin
data class SceneAnalysis(
    val filter: FilterType,
    val iso: Int,               // 100-3200
    val shutter: Int,           // 8-4000 (denominator of 1/x)
    val wbMode: Int,            // CameraMetadata.CONTROL_AWB_MODE_*
    val focusDistance: Float,    // 0-20 (0 = infinity)
    val noiseReductionMode: Int,
    val sharpnessMode: Int,
    val zoom: Float,            // 0.6x - 3.0x
    val brightness: Float,      // -0.15 to 0.15
    val contrast: Float,        // 0.8 to 1.8
    val saturation: Float,      // 0.0 to 1.6
    val gamma: Float,           // 0.9 to 1.3
    val ready: Boolean,
    val reasoning: String,
    val compositionTip: String,
    val compositionOk: Boolean,
    val sceneDescription: String,
    val photographyTip: String
)
```

---

## 4. Camera Pipeline

### CameraX + Camera2 Interop

The app uses CameraX for lifecycle management and preview, with Camera2 interop (`ExperimentalCamera2Interop`) for manual hardware control:

```kotlin
Camera2CameraControl.from(camera.cameraControl)
    .setCaptureRequestOptions(
        CaptureRequestOptions.Builder().apply {
            setCaptureRequestOption(CONTROL_AE_MODE, CONTROL_AE_MODE_OFF)
            setCaptureRequestOption(SENSOR_SENSITIVITY, analysis.iso)
            setCaptureRequestOption(SENSOR_EXPOSURE_TIME,
                1_000_000_000L / analysis.shutter.toLong())
            setCaptureRequestOption(CONTROL_AWB_MODE, analysis.wbMode)
            setCaptureRequestOption(LENS_FOCUS_DISTANCE, analysis.focusDistance)
            setCaptureRequestOption(NOISE_REDUCTION_MODE, analysis.noiseReductionMode)
            setCaptureRequestOption(EDGE_MODE, analysis.sharpnessMode)
        }.build()
    )
```

| Parameter | Range | Notes |
|-----------|-------|-------|
| ISO | 100-3200 | Manual sensor sensitivity |
| Shutter Speed | 1/4000 - 1/8 s | Converted to nanoseconds |
| White Balance | 7 modes | auto, daylight, cloudy, shade, incandescent, fluorescent, twilight |
| Focus Distance | 0-20 diopters | 0 = infinity focus |
| Noise Reduction | 4 modes | OFF, FAST, MINIMAL, HIGH_QUALITY |
| Edge Enhancement | 3 modes | OFF, FAST, HIGH_QUALITY |

**Front Camera:** Retains auto-exposure, only applies WB, noise reduction, and edge mode.

**Reset:** After each capture session, all settings are restored to auto mode.

### Frame Capture Flow

1. User taps AI shutter
2. `ImageCapture.takePicture()` captures full-resolution frame
3. Original saved to MediaStore as `Vantage_{timestamp}`
4. Frame written to `cacheDir/ai_frame.jpg` for Gemma analysis
5. Gemma runs multi-round analysis (1-2 rounds, 15s max)
6. Camera2 hardware settings applied based on analysis
7. `ImageProcessor.process()` applies software filters to original bitmap
8. Enhanced version saved as `Vantage_{timestamp}_enhanced`
9. Both URIs stored as `PhotoPair` for gallery display

---

## 5. GPU-Accelerated Post-Processing Pipeline

### Filter Architecture

The post-processing pipeline uses **GPUImage** for hardware-accelerated image filtering:

```
Original Bitmap
       |
       v
+------------------+
| LUT Pass         |  (For film stock filters: Kodak Gold, Portra, Fuji Velvia, etc.)
| GPUImageLookup   |  512x512 procedurally generated lookup tables
+------------------+
       |
       v
+------------------+
| Filter Group     |  Sequential GPU filter chain:
|                  |
| 1. Style Filters |  Filter-specific tone curves, split-toning, vignette
| 2. Exposure      |  GPUImageExposureFilter
| 3. Contrast      |  GPUImageToneCurveFilter (S-curve for naturalism)
| 4. Saturation    |  GPUImageSaturationFilter
| 5. Gamma         |  GPUImageGammaFilter
| 6. Recovery      |  GPUImageHighlightShadowFilter
| 7. Sharpening    |  GPUImageSharpenFilter (0.3 strength)
+------------------+
       |
       v
  Enhanced Bitmap
```

### LUT Generation (LutGenerator.kt)

Film stock emulation uses procedurally generated 512x512 lookup tables:

- **64x64x64 RGB color space** encoded into a 2D texture
- Color grading recipes define per-filter transformations: warmth shifts, channel boosts, S-curves, black lifting, split-toning
- LUTs are cached in memory after first generation
- Applied as a separate GPUImage pass via `GPUImageLookupFilter`

**Example: Kodak Gold 200 recipe:**
```
warmth(+0.12) -> saturation(1.15x) -> S-curve(0.25) ->
lift blacks(+0.03) -> red channel(+0.04) ->
split tone(shadows: warm amber, highlights: golden cream)
```

### Style-Specific Processing

Each filter has unique processing beyond the LUT:

- **CINEMATIC:** Teal-orange split tone via RGB tone curves, reduced saturation, vignette, 5800K WB
- **VINTAGE:** Raised blacks (faded film), warm color shift, 0.7x saturation, vignette, 6200K WB
- **DRAMATIC:** Deep S-curve, cool-toned shadows, highlight/shadow recovery, heavy vignette
- **NOIR:** Monochrome conversion, heavy S-curve for B&W contrast, strong vignette
- **GOLDEN_HOUR:** Warm temperature shift, amber highlights, lifted shadows

### Real-Time Preview Shaders (FilterEngine.kt)

The live viewfinder runs OpenGL ES 2.0 shaders for real-time filter preview:

```glsl
// Common adjustment block applied by all shaders
vec3 applyAdjustments(vec3 rgb) {
    rgb += uBrightness;
    rgb = (rgb - 0.5) * uContrast + 0.5;
    float gray = dot(rgb, vec3(0.299, 0.587, 0.114));
    rgb = mix(vec3(gray), rgb, uSaturation);
    rgb = pow(max(rgb, 0.0), vec3(1.0 / uGamma));
    return clamp(rgb, 0.0, 1.0);
}
```

- External OES texture input from CameraX preview
- Per-filter fragment shaders for color grading
- Center-crop fill scaling with rotation support (0/90/180/270)
- Front camera mirroring

---

## 6. Voice Input & Unsplash Reference Image Flow

### Voice Recognition

The app uses Android's `SpeechRecognizer` for speech-to-text:

1. User holds mic button
2. STT converts speech to text
3. Intent classification routes the input:
   - **Style intent** (e.g., "vintage", "cinematic", "linkedin headshot"): triggers reference image search + style-matched analysis
   - **Inspiration intent** (e.g., "pose ideas", "inspire me"): triggers Unsplash photo carousel for composition reference

### Reference Image Pipeline

When a user provides a style-related voice prompt:

```
Voice Input: "vintage look"
       |
       v
+-------------------------+
| 1. Capture Preview      |  Snapshot current viewfinder frame
|    Frame (1.5s timeout)  |
+-------------------------+
       |
       v
+-------------------------+
| 2. Gemma Scene Tag      |  "Describe this photo in 1-4 words.
|    (On-device)          |   Subject and setting only."
|                         |   Output: "woman portrait outdoor"
+-------------------------+
       |
       v
+-------------------------+
| 3. Unsplash Search      |  Query: "vintage woman portrait
|    (OkHttp, 8s timeout) |   outdoor photography aesthetic"
|                         |  Returns: top result smallUrl (400px)
+-------------------------+
       |
       v
+-------------------------+
| 4. Download Reference   |  smallUrl -> ref_{timestamp}.jpg
|    (~20-40KB, fast)     |  Stored in cache directory
+-------------------------+
       |
       v
+-------------------------+
| 5. Dual-Image Analysis  |  Gemma receives:
|    via LiteRT-LM        |   Image 1: Unsplash reference (target style)
|                         |   Image 2: Camera frame (to enhance)
|                         |   Prompt: Match the reference's color
|                         |   grading, contrast, mood, aesthetic
+-------------------------+
```

This pipeline runs as a `Deferred` coroutine, pre-fetching the reference image while the user is still framing the shot. When the shutter is tapped, it awaits the reference with a 6-second timeout before falling back to analysis without a reference.

### Unsplash API Integration

- **Client:** OkHttpClient with 8-second timeout
- **Caching:** LRU cache (10 entries, 75% load factor) for repeated queries
- **Randomization:** Random page selection (1-5) for variety
- **Image size:** `smallUrl` (400px wide) for minimal download time
- **Scene enrichment:** Gemma-generated scene tags appended to query for contextual relevance

---

## 7. Enhancement Metadata Persistence

Every enhanced photo's AI parameters are persisted to disk:

```
File: {app.filesDir}/enhancement_metadata.json

Structure:
{
    "1714567890123": {
        "filter": "KODAK_GOLD",
        "iso": 400,
        "shutter": 125,
        "whiteBalance": "daylight",
        "brightness": 0.05,
        "contrast": 1.3,
        "saturation": 1.1,
        "gamma": 1.05,
        "zoom": 1.0,
        "sceneDescription": "Portrait in warm afternoon light",
        "aiReasoning": "Boosted warmth to complement golden hour...",
        "voicePrompt": "vintage look",
        "photographyTip": "Try stepping back for more context",
        "referenceImageUrl": "https://images.unsplash.com/..."
    }
}
```

This metadata powers the "i" info button on enhanced photos in the gallery, showing the user exactly what the AI did and why.

---

## 8. UI Architecture

Built entirely with **Jetpack Compose** and **Material 3**.

### Screens

| Screen | Purpose |
|--------|---------|
| **CameraScreen** | Main viewfinder with AI controls, grid overlay, zoom pills, voice input, composition tips, AI analysis card |
| **GalleryScreen** | Scrollable grid of original/enhanced photo pairs |
| **PhotoDetailScreen** | Full-screen photo viewer with original/enhanced toggle, enhancement metadata overlay, reference image display |
| **LoadingScreen** | Splash screen during model initialization |

### Navigation

- `HorizontalPager` (2 pages): Camera and Gallery
- `Crossfade` transitions for photo detail views
- `MainActivity` orchestrates pager state and screen routing

### Key UI Components

- **AI Analysis Card:** Animated card showing Gemma's reasoning, scene description, and composition tips during analysis
- **Zoom Pills:** Quick-select buttons (0.6x, 1x, 2x, 3x) + pinch-to-zoom gesture
- **Voice Prompt Pill:** Displays active voice prompt with dismiss button
- **InspoCard:** Unsplash photo carousel for pose/composition inspiration
- **Original/Enhanced Toggle:** Bottom pill selector in photo detail view
- **Enhancement Info Panel:** Scrollable overlay showing all AI parameters, filter details, reference image, and photography tips

---

## 9. Filter Library

### Film Stock Emulations (LUT-based)

| Filter | Inspiration |
|--------|------------|
| KODAK_GOLD | Kodak Gold 200: warm amber tones, lifted blacks |
| PORTRA | Kodak Portra 400: soft pastels, natural skin tones |
| FUJI_VELVIA | Fujifilm Velvia 50: high saturation, vivid greens/blues |
| GOLDEN_HOUR | Warm golden light, amber highlights |
| BLUE_HOUR | Cool twilight tones, blue shadows |

### Creative Filters (Shader + Post-Processing)

| Filter | Effect |
|--------|--------|
| NATURAL | Minimal processing, true-to-life |
| WARM | Red/amber shift (+1.2R, +1.1G, -0.1B) |
| COOL | Blue shift (-0.1R, +1.1G, +1.2B) |
| VIVID | Saturation boost + mild S-curve |
| DRAMATIC | Deep contrast, cool shadows, heavy vignette |
| CINEMATIC | Teal-orange split tone, desaturated |
| VINTAGE | Raised blacks, warm shift, faded film look |
| NOIR | High-contrast black and white |
| MUTED | Desaturated, soft, editorial |
| FADE | Lifted blacks, reduced contrast |
| MONO | Pure monochrome |
| SILVERTONE | Cool-toned B&W |

---

## 10. Performance & On-Device Optimization

### NPU Acceleration

- **Primary compute:** Gemma 4 E2B inference runs on Qualcomm Hexagon DSP via `Backend.NPU`
- **Model format:** `.litertlm` optimized for on-device execution
- **ABI restriction:** `arm64-v8a` only, eliminating x86 overhead
- **QNN runtime:** Native `.so` libraries extracted at install time for `dlopen()` compatibility

### GPU Acceleration

- **Real-time preview:** OpenGL ES 2.0 shaders render filter effects at frame rate
- **Post-processing:** GPUImage library runs the full filter pipeline on GPU
- **LUT application:** Texture-based lookup on GPU

### Latency Budget

| Stage | Target |
|-------|--------|
| Frame capture | < 500ms |
| Gemma Round 1 | 3-8s |
| Gemma Round 2 | 2-5s |
| Reference image fetch | < 6s (timeout) |
| Post-processing | < 1s |
| Total E2E | < 15s (hard timeout) |

### Memory Management

- LUT cache: In-memory HashMap, generated on-demand
- Unsplash cache: LRU (10 entries)
- Reference images: Temporary files in cache directory
- Original bitmaps: Held in memory only during processing, then released

---

## 11. Key Technical Differentiators

1. **Fully on-device AI:** Zero cloud dependency for inference. Gemma 4 E2B runs entirely on the Snapdragon NPU, ensuring privacy and offline capability.

2. **Multi-modal, multi-round reasoning:** Gemma analyzes the actual camera frame, reasons about optimal settings across multiple iterations, and self-corrects.

3. **Reference-guided style transfer:** Users describe a style in natural language, Gemma describes the scene for contextual Unsplash search, then performs dual-image analysis to match the reference aesthetic.

4. **Hardware + software co-optimization:** Camera2 API controls real sensor parameters (ISO, shutter, WB) while GPUImage handles creative post-processing, combining hardware-accurate exposure with artistic filters.

5. **Professional filter pipeline:** Film stock emulations use procedurally generated LUTs with physically-inspired color science, not simple color matrix overlays.

6. **Voice-driven UX:** Natural language commands ("linkedin headshot", "moody cinematic", "vintage") drive the entire enhancement pipeline without menu navigation.

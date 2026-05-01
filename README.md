# Vantage - AI Camera powered by Gemma 4 on Snapdragon NPU

Vantage is an on-device AI camera app that uses **Google Gemma 4 (E2B)** running on the **Qualcomm Snapdragon NPU** via **LiteRT-LM** to analyze scenes in real time, optimize camera hardware settings, and produce professionally enhanced photos, all without any cloud dependency.

Speak a style ("vintage", "cinematic", "linkedin headshot") and Vantage will find a matching reference photo, analyze both images on-device, and tune ISO, shutter speed, white balance, zoom, composition, and a cinematic filter to match that aesthetic.

## Download

Download the latest APK from the [GitHub Releases page](https://github.com/ParthPatel00/Vantage/releases/tag/v1.0).

## Team


| Name | Email |
|------|-------|
| Parth Patel | patelsparth00@gmail.com |
| Adam Christley | achristl@mtu.edu |
| Rohit Dadlani | rohitdadlani85@gmail.com |
| Devam Sheth | devamsheth0806@gmail.com |

## Features

- **On-Device AI Analysis:** Gemma 4 E2B runs entirely on the Snapdragon Hexagon NPU via LiteRT-LM. Zero cloud calls, full offline capability.
- **Multi-Round Reasoning:** Gemma analyzes the camera frame, recommends settings, then self-corrects in a refinement round for optimal results.
- **Voice-Driven Styles:** Say "vintage", "moody cinematic", or "golden hour" and Vantage matches that aesthetic using AI-selected filters and camera tuning.
- **Reference Image Matching:** Voice prompts trigger a scene-aware Unsplash search. Gemma receives both the reference photo and your camera frame, then color-matches the style.
- **Hardware Camera Control:** Camera2 API sets real sensor parameters (ISO 100-3200, shutter 1/4000-1/8s, white balance, focus distance, noise reduction, sharpening).
- **Professional Filter Library:** 15+ filters including film stock emulations (Kodak Gold 200, Portra 400, Fuji Velvia 50) built with procedurally generated LUTs and GPU-accelerated post-processing.
- **Composition Guidance:** Gemma detects subjects and provides framing tips ("move left", "step back", "tilt down").
- **Enhancement Metadata:** Every enhanced photo records what the AI did and why, viewable via the info button in the gallery.
- **Pose Inspiration:** Ask "pose ideas" and Vantage shows contextual reference photos from Unsplash.

## Tech Stack

| Component | Technology | Details |
|-----------|-----------|---------|
| On-Device LLM | Google Gemma 4 E2B | Multi-modal vision-language model |
| Inference Runtime | LiteRT-LM | `com.google.ai.edge.litertlm:litertlm-android` |
| NPU Acceleration | Qualcomm Hexagon DSP | `Backend.NPU` with QNN runtime |
| Camera | CameraX + Camera2 Interop | Manual ISO, shutter, WB, focus via `Camera2CameraControl` |
| Image Processing | GPUImage 2.1.0 | GPU-accelerated filters, tone curves, LUTs |
| UI | Jetpack Compose + Material 3 | Reactive UI with animations |
| Voice | Android SpeechRecognizer + TTS | On-device speech-to-text and text-to-speech |
| Reference Images | Unsplash API + OkHttp | Scene-aware image search for style matching |
| Image Loading | Coil Compose 2.7.0 | Async image loading |
| Language | Kotlin | Coroutines for async orchestration |

## Setup Instructions

### Prerequisites

- Samsung Galaxy S25 Ultra (or any Snapdragon 8 Elite device)
- USB cable for ADB
- A computer with ADB installed (Android SDK Platform Tools)

### Option 1: Install the APK directly

1. Download `vantage.apk` from the [Releases page](https://github.com/ParthPatel00/Vantage/releases/tag/v1.0)
2. Connect your S25 via USB and enable USB debugging in Developer Options
3. Install via ADB:
   ```bash
   adb install vantage.apk
   ```
4. Download the Gemma 4 E2B LiteRT-LM model file (`.litertlm` format) and push it to the device:
   ```bash
   adb push gemma4-e2b-sm8750.litertlm /storage/emulated/0/Android/data/com.vantage/files/
   ```
5. Launch "Vantage" from the app drawer
6. Grant camera, microphone, and storage permissions when prompted

### Option 2: Build from source

1. Clone the repository:
   ```bash
   git clone https://github.com/ParthPatel00/Vantage.git
   cd Vantage
   ```
2. Open the project in Android Studio (Ladybug or later recommended)
3. Ensure you have:
   - Android SDK 36 installed
   - Kotlin plugin up to date
   - NDK for arm64-v8a (if modifying native code)
4. Connect your S25 via USB
5. Build and install:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
6. Push the Gemma model file as described in Option 1, Step 4
7. Launch the app

### Model File

The app searches for the `.litertlm` model file in this order:
1. `Android/data/com.vantage/files/` (recommended)
2. `Downloads/` folder
3. App internal storage
4. `/data/local/tmp/` (development)

Files with `sm8750` in the name are prioritized for Snapdragon 8 Elite optimization.

## Usage

### Basic AI Photo

1. Open Vantage and point the camera at a scene
2. Tap the **AI shutter button** (center bottom)
3. Gemma analyzes the scene, adjusts camera settings, and applies an optimal filter
4. View both the original and enhanced photo in the gallery

### Voice-Driven Styles

1. Tap and hold the **mic button** (bottom left)
2. Say a style: "vintage", "cinematic", "golden hour", "linkedin headshot", "moody", etc.
3. Vantage searches for a matching reference photo and pre-fetches it
4. Tap the AI shutter to capture. Gemma matches your photo to the reference style
5. Tap the **i** button on the enhanced photo to see the reference image and AI reasoning

### Pose Inspiration

1. Hold the mic button and say "pose ideas" or "inspire me"
2. Vantage shows a carousel of reference photos matching your current scene
3. Select a pose for compositional guidance

### Gallery

- Swipe left from the camera to view the gallery
- Tap any photo pair to view full-screen
- Toggle between "Original" and "Enhanced" versions
- Tap the **i** button to see all AI parameters, filter details, and the reference image used

## Project Structure

```
app/src/main/java/com/vantage/
  ai/
    GemmaEngine.kt        # LiteRT-LM initialization, Gemma inference, prompt building
    ImageProcessor.kt     # GPUImage post-processing pipeline
    SceneAnalysis.kt      # Data model for Gemma's JSON output
    LutGenerator.kt       # Procedural LUT generation for film stock filters
    CameraToolSet.kt      # Voice intent routing, inspiration flow
  api/
    UnsplashApiService.kt # Unsplash search and image download
    UnsplashClientImpl.kt # Client with caching and query enrichment
  filters/
    FilterEngine.kt       # OpenGL ES 2.0 real-time preview renderer
    Shaders.kt            # GLSL fragment shaders for each filter
  models/
    CameraUiState.kt      # UI state for camera screen
    FilterType.kt         # Filter enum with keyword matching
    EnhancementInfo.kt    # Persistent metadata for enhanced photos
    PhotoPair.kt          # Original + Enhanced URI pair
  ui/
    screens/
      CameraScreen.kt     # Main camera viewfinder and controls
      GalleryScreen.kt    # Photo grid
      PhotoDetailScreen.kt # Full-screen viewer with metadata overlay
  viewmodel/
    CameraViewModel.kt    # Orchestrates AI analysis, voice, references, persistence
  voice/
    VoiceSystem.kt        # SpeechRecognizer and TextToSpeech wrapper
  MainActivity.kt         # Entry point, navigation, permissions
```

## How It Works (E2E Flow)

```
Voice Input ("vintage")
    |
    v
Scene Tag (Gemma: "woman portrait outdoor")  -->  Unsplash Search
    |                                                    |
    v                                                    v
AI Shutter Tap                                  Reference Image Downloaded
    |                                                    |
    v                                                    v
Frame Captured  ------>  Gemma 4 E2B on NPU  <-----  Dual Image Input
                         (Round 1: Full Analysis)
                         (Round 2: Refinement)
                                |
                                v
                        SceneAnalysis JSON
                  {filter, iso, shutter, wb, zoom,
                   brightness, contrast, saturation,
                   gamma, composition_tip, reason}
                        |               |
                        v               v
                Camera2 API       GPUImage Pipeline
                (Hardware)        (Software Filters)
                ISO, Shutter,     LUTs, Tone Curves,
                WB, Focus         Vignette, Sharpening
                                        |
                                        v
                                Enhanced Photo Saved
                                + Metadata Persisted
```

## License

See [LICENSE](LICENSE) file.

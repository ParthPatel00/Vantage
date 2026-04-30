# Vantage - Complete Hackathon Specification

## Qualcomm x LiteRT Developer Hackathon | April 30 - May 1, 2026

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Hackathon Constraints and Rules](#2-hackathon-constraints-and-rules)
3. [Architecture Overview](#3-architecture-overview)
4. [Technology Stack](#4-technology-stack)
5. [Feature Specification](#5-feature-specification)
6. [UI/UX Design](#6-uiux-design)
7. [LiteRT-LM Integration](#7-litert-lm-integration)
8. [Camera Engine](#8-camera-engine)
9. [Filter System](#9-filter-system)
10. [Unsplash Integration](#10-unsplash-integration)
11. [Conversational Voice System](#11-conversational-voice-system)
12. [Auto-Capture System](#12-auto-capture-system)
13. [Project Structure](#13-project-structure)
14. [Build and Deployment](#14-build-and-deployment)
15. [Demo Script](#15-demo-script)
16. [Submission Checklist](#16-submission-checklist)

---

## 1. Project Overview

### One-Liner

Vantage is a conversational AI camera co-pilot powered by Gemma 4 running on-device via LiteRT-LM that talks to you, controls your camera, applies filters, finds inspiration photos, and auto-captures the perfect shot.

### Elevator Pitch

Google's Camera Coach on Pixel 10 requires cloud processing, takes up to a minute, only gives text tips, cannot control camera settings, and never auto-captures. Vantage does all of this on-device, in real time, with zero internet required for core features. It runs Gemma 4 E2B via LiteRT-LM on the Samsung Galaxy S25 Ultra's Snapdragon 8 Elite. It talks to you like a photographer friend using voice narration, listens to your voice commands, controls every camera parameter (zoom, ISO, shutter speed, white balance, focus, exposure), applies live viewfinder filters, fetches inspiration photos from Unsplash based on the scene and your conversation, and automatically takes the picture when everything looks right.

### Track

Track 1: LiteRT-LM - LLM-Based Consumer Use Journeys

### Target Device

Samsung Galaxy S25 Ultra (Snapdragon 8 Elite, SM8750)

---

## 2. Hackathon Constraints and Rules

### Hard Requirements (Must Meet)

- Must use Google LiteRT-LM compiled model API
- Must run on-device on the Samsung Galaxy S25 Ultra (Qualcomm Snapdragon)
- Must be a working application that runs consistently
- Must be newly created after April 30, 2026 12:00 PM PT
- Public GitHub repository with open-source license
- README with: app description, team member names/emails, setup instructions, run/usage instructions
- Text description of features on Devpost
- One submission per team
- All materials in English
- Third-party integrations must comply with their license/terms (Unsplash API terms)

### Submission Deadline

May 1, 2026, 1:00 PM PT (approximately 25 hours of build time)

### Judging Criteria (5 equally weighted)

1. **LiteRT Usage** - Must use LiteRT-LM compiled model API (pass/fail gate + scored)
2. **Technical Implementation** - Resource utilization, optimization, latency, performance, energy efficiency
3. **Application Use-Case and Innovation** - Problem solving, creativity, uniqueness, UX
4. **Deployment and Accessibility** - Ease of installation and use
5. **Presentation and Documentation** - Clarity of explanation, code quality, documentation

### Prizes

- Grand Prize (Judge's Top Choice): Nintendo Switch 2 per team member + Qualcomm DevRel support + Play Store publication
- Runner-Up (Participant's Top Choice): Nintendo Switch 2 per team member + Qualcomm DevRel support

---

## 3. Architecture Overview

### System Architecture Diagram

```
+------------------------------------------------------------------+
|                        Vantage Android App                        |
|                                                                   |
|  +---------------------+     +------------------------------+    |
|  |   Camera Engine     |     |   AI Brain (LiteRT-LM)       |    |
|  |   (Camera2 API)     |     |   Gemma 4 E2B Model          |    |
|  |                     |     |                              |    |
|  |  - Viewfinder       |---->|  - Scene Analysis            |    |
|  |  - Zoom Control     |     |  - Coaching Tips             |    |
|  |  - ISO/Shutter      |<----|  - Camera Parameter JSON     |    |
|  |  - White Balance    |     |  - Filter Recommendation     |    |
|  |  - Focus Distance   |     |  - Ready-to-Capture Signal   |    |
|  |  - Exposure Comp    |     |  - Conversation Context      |    |
|  |  - Auto-Capture     |     |                              |    |
|  +---------------------+     +------------------------------+    |
|           |                            |          ^               |
|           v                            v          |               |
|  +---------------------+     +------------------+ |               |
|  |   Filter Engine     |     | Voice System     | |               |
|  |   (OpenGL ES)       |     |                  | |               |
|  |                     |     | - TTS Output     | |               |
|  |  - Warm/Amber       |     | - STT Input      | |               |
|  |  - Cool             |     | - Chat Bubbles   | |               |
|  |  - Noir/B&W         |     |                  | |               |
|  |  - Vivid            |     +------------------+ |               |
|  |  - Cinematic        |                           |               |
|  |  - Vintage          |     +------------------+  |               |
|  |  - Muted            |     | Unsplash API     |--+               |
|  |  - Dramatic         |     |                  |                 |
|  |  - Silvertone       |     | - Scene Search   |                 |
|  |  - Fade             |     | - Inspo Gallery  |                 |
|  +---------------------+     | - Style Matching |                 |
|                               +------------------+                 |
|  +-------------------------------------------------------------+ |
|  |                    Overlay Renderer                           | |
|  |  - Rule of thirds grid    - Horizon level indicator          | |
|  |  - Directional arrows     - Subject positioning guides       | |
|  |  - Chat bubbles           - Filter preview strip             | |
|  |  - Inspo thumbnail panel  - Auto-capture countdown           | |
|  +-------------------------------------------------------------+ |
+------------------------------------------------------------------+
```

### Data Flow

```
User opens app
    |
    v
Camera2 API initializes viewfinder
    |
    v
User taps AI Coach button (sparkle icon)
    |
    +--> Viewfinder frame captured as Bitmap
    |
    +--> Frame sent to Gemma 4 E2B via LiteRT-LM multimodal API
    |        Content.ImageFile(framePath) + Content.Text(prompt)
    |
    +--> Gemma returns structured JSON via function calling:
    |    {
    |      "scene_description": "Iced latte on wooden table, warm natural light",
    |      "camera_settings": { "zoom": 1.5, "iso": 100, "wb": "daylight", ... },
    |      "filter": "warm",
    |      "coaching_tips": ["Move closer to fill the frame", "Angle down 15 degrees"],
    |      "user_actions": ["Tilt phone down slightly"],
    |      "voice_message": "Nice, that's a great latte shot. I'm bumping up the warmth...",
    |      "ready_to_capture": false,
    |      "unsplash_query": "coffee latte overhead wooden table"
    |    }
    |
    +--> Camera Engine applies camera_settings automatically
    +--> Filter Engine applies recommended filter
    +--> Voice System speaks voice_message via TTS
    +--> Overlay Renderer shows coaching tips + directional guides
    +--> Unsplash API fetches inspiration photos using unsplash_query
    |
    v
User follows voice/visual directions + optionally selects inspo photo
    |
    +--> If inspo selected: re-analyze with inspo image context
    |    "Match the composition and style of this reference photo"
    |
    +--> User can also speak: "Make it moodier" / "Zoom in more"
    |    STT captures -> feeds to Gemma as conversation turn
    |    Gemma adjusts settings + responds conversationally
    |
    v
Continuous re-analysis loop (every few seconds or on significant change)
    |
    +--> When Gemma returns "ready_to_capture": true
    |
    v
Auto-capture triggers
    +--> Shutter sound + visual flash
    +--> Photo saved to gallery
    +--> Voice: "Got it! That turned out great."
```

---

## 4. Technology Stack

### Core Framework

| Component | Technology | Version/Details |
|-----------|-----------|----------------|
| Language | Kotlin | Latest stable |
| UI Framework | Jetpack Compose | Latest stable |
| Min SDK | API 31 (Android 12) | Required for NPU support |
| Target SDK | API 35 (Android 15) | Galaxy S25 Ultra ships with this |
| Compile SDK | API 36 (Android 16) | Latest stable SDK |
| Build System | Gradle 9.x (Kotlin DSL) | AGP 9.x bundles Kotlin |

### AI/ML

| Component | Technology | Details |
|-----------|-----------|---------|
| Runtime | LiteRT-LM | `com.google.ai.edge.litertlm:litertlm-android:latest.release` |
| Model | Gemma 4 E2B | `litert-community/gemma-4-E2B-it-litert-lm` from HuggingFace |
| Backend (primary) | GPU | `Backend.GPU()` - broad compatibility, proven stable |
| Backend (attempt) | NPU | `Backend.NPU(nativeLibraryDir = ...)` - try first, fallback to GPU |
| Multimodal | Vision | `visionBackend = Backend.GPU()` for image analysis |
| Tool Use | Function Calling | `@Tool` / `@ToolParam` annotations for camera control |

### Camera

| Component | Technology | Details |
|-----------|-----------|---------|
| Camera API | Camera2 API | Full manual control (ISO, shutter speed, focus, WB, zoom) |
| Preview | SurfaceView or TextureView | For OpenGL filter pipeline |
| Filter Rendering | OpenGL ES 2.0 | Fragment shaders for real-time color grading |

### Voice

| Component | Technology | Details |
|-----------|-----------|---------|
| Text-to-Speech (primary) | ElevenLabs API | `POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}` — natural, expressive, human-sounding voice. Requires internet + API key. |
| Text-to-Speech (fallback) | Android TTS (`android.speech.tts.TextToSpeech`) | Silent offline fallback only. Used when ElevenLabs is unavailable. Never used as primary. |
| Speech-to-Text (primary) | Google Cloud Speech-to-Text API | `POST https://speech.googleapis.com/v1/speech:recognize` — best accuracy, handles noisy environments, streaming support. Requires internet + API key. |
| Speech-to-Text (fallback) | Android SpeechRecognizer | Fallback when no connectivity. Acceptable for simple commands. |

**ElevenLabs setup:**
- Register at https://elevenlabs.io — free tier includes 10,000 characters/month, enough for a hackathon demo.
- Choose voice: **"Rachel"** (conversational, warm) or **"Elli"** (energetic, enthusiastic). Rachel recommended for Vantage's personality.
- Store API key in `local.properties` as `ELEVENLABS_API_KEY`.
- Voice ID for Rachel: `21m00Tcm4TlvDq8ikWAM`. Set in `BuildConfig`.
- Audio is returned as MP3 bytes. Play via Android's `MediaPlayer` or `ExoPlayer`.
- Stream audio chunks as they arrive for lower latency: use the `/v1/text-to-speech/{voice_id}/stream` endpoint.

**Google Cloud STT setup:**
- Enable the Speech-to-Text API at https://console.cloud.google.com.
- Create an API key restricted to the Speech

### External API

| Component | Technology | Details |
|-----------|-----------|---------|
| Inspiration Photos | Unsplash API | Free tier, 50 req/hr demo mode. Endpoint: `GET https://api.unsplash.com/search/photos?query={query}&client_id={key}` |
| Image Loading | Coil | Kotlin-first image loading library for Compose |

### AndroidManifest Permissions and Libraries

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<application>
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>
</application>
```

> **Note:** With minSdk 31, `WRITE_EXTERNAL_STORAGE` and `READ_EXTERNAL_STORAGE` are not needed.
> Use `MediaStore` to save photos (no permission required). Use `READ_MEDIA_IMAGES` (API 33+) only if reading the gallery.

---

## 5. Feature Specification

### Feature 1: AI Camera Control ("Do It For Me" Mode)

**Trigger:** User taps the AI sparkle button on the viewfinder.

**Behavior:**
1. App captures the current viewfinder frame as a JPEG/bitmap.
2. Sends the frame + system prompt to Gemma 4 E2B via LiteRT-LM multimodal API.
3. Gemma returns structured JSON with camera settings and coaching instructions.
4. App automatically applies all camera parameters it can control:
   - Zoom level (via Camera2 `SCALER_CROP_REGION` or `CONTROL_ZOOM_RATIO`)
   - ISO (`SENSOR_SENSITIVITY`)
   - Shutter speed (`SENSOR_EXPOSURE_TIME`)
   - White balance (`CONTROL_AWB_MODE` or manual `COLOR_CORRECTION_GAINS`)
   - Focus distance (`LENS_FOCUS_DISTANCE`)
   - Exposure compensation (`CONTROL_AE_EXPOSURE_COMPENSATION`)
5. For things requiring physical movement (move left, angle down, step back), the app:
   - Speaks the instruction via TTS
   - Shows directional arrows/text overlay on viewfinder
6. App re-analyzes every 2-3 seconds to check if user has followed through.
7. When Gemma determines the composition is ready, it returns `ready_to_capture: true`.
8. App auto-captures the photo with a shutter animation and sound.
9. Voice: "Got it! That turned out great."

### Feature 2: Guided Mode ("Coach Me" Mode)

**Trigger:** User toggles to "Coach Me" mode before tapping AI button.

**Behavior:**
1. Same scene analysis as above.
2. Instead of auto-applying camera settings, the app tells the user what to change via voice and on-screen instructions.
   - "Try switching to portrait mode"
   - "Bump your zoom to about 2x"
   - "Lower the exposure a touch"
3. The app highlights relevant UI controls (similar to Pixel's blue highlight, but as a pulsing glow on our custom UI).
4. App monitors whether the user has made the changes.
5. Once all steps are followed and composition looks good, voice: "Perfect, that looks ready. Taking the shot in 3... 2... 1..."
6. Auto-captures.

### Feature 3: Live Viewfinder Filters

**Available Filters (inspired by iOS camera filters):**

| Filter Name | Description | OpenGL Approach |
|------------|-------------|-----------------|
| Natural | No filter, standard processing | Pass-through shader |
| Warm / Amber | Golden warm tones | Color matrix: boost R, slight boost G, reduce B |
| Cool | Blue-shifted cool tones | Color matrix: reduce R, boost B |
| Noir | High-contrast black and white | Luminance conversion + contrast curve |
| Vivid | Saturated, punchy colors | Increase saturation in HSV space |
| Dramatic | Deep shadows, muted highlights | S-curve contrast + slight desaturation |
| Silvertone | Classic silver B&W | Luminance + slight warm tint |
| Cinematic | Teal shadows, orange highlights | Split-toning in shadows/highlights |
| Vintage | Faded, lifted blacks, warm cast | Lift black point + warm color shift |
| Muted | Desaturated, soft pastel look | Reduce saturation + lift midtones |
| Fade | Washed out, low contrast | Lift shadows + reduce contrast |
| Mono | Pure black and white | Simple luminance conversion |

**Interaction:**
- User can swipe horizontally through filters on a strip at the bottom of the viewfinder.
- Filters apply in real time to the preview.
- In AI mode, Gemma can recommend and auto-apply a filter based on scene analysis.
- When a user selects an Unsplash inspo photo, Gemma analyzes the color tone and suggests the closest matching filter.

**Implementation:**
- Each filter is a GLSL fragment shader applied to the camera preview texture.
- Filters are rendered via OpenGL ES 2.0 on a `GLSurfaceView` or through a custom `TextureView` pipeline.
- The captured photo also has the filter baked in (render to offscreen framebuffer, then save).

### Feature 4: Unsplash Inspiration Integration

**Flow:**
1. When AI coaching is triggered, Gemma generates a scene description and an `unsplash_query` string.
2. App calls `GET https://api.unsplash.com/search/photos?query={unsplash_query}&per_page=6&client_id={UNSPLASH_ACCESS_KEY}`
3. Returns 6 thumbnail inspiration photos displayed in a horizontal scrollable row above the viewfinder.
4. User can tap any photo and say "I want my picture to match that."
5. The selected inspo photo is then fed back to Gemma (either as an image via multimodal input or as Gemma's text analysis of it) along with the current viewfinder frame.
6. Gemma adjusts its coaching, camera settings, and filter recommendation to match the reference photo's style.
7. As the conversation continues (user gives more voice commands), the inspo query refines. For example:
   - Initial: "coffee latte wooden table"
   - After user says "make it more moody": refined to "moody coffee dark background low light"
   - New Unsplash results fetched and displayed.

**Unsplash API Requirements (must comply):**
- Hotlink image URLs directly from `photo.urls.small` or `photo.urls.thumb` (do not download and re-serve).
- Attribute the photographer: show `photo.user.name` and link to their Unsplash profile.
- Include `utm_source=vantage&utm_medium=referral` on all Unsplash links.
- Keep Access Key confidential (store in `local.properties` or `BuildConfig`, not in source code).

**Registration:**
- Go to https://unsplash.com/developers
- Create a new application
- Note: demo mode allows 50 requests/hour, which is plenty for a hackathon demo.

### Feature 5: Conversational Voice System

**Voice Output (TTS):**
- Primary: ElevenLabs API for natural, human-sounding voice (requires internet + API key). Fallback: Android's built-in `TextToSpeech` engine when offline.
- Personality: Friendly, enthusiastic photographer friend. Not robotic. Casual tone.
- Gemma's system prompt instructs it to write `voice_message` fields in this tone.
- Examples:
  - "Ooh, food shot! Let me crank up the warmth on this one."
  - "Try stepping back just a bit... yeah, right there. Hold still."
  - "I found some cool inspo for you. Take a look at these."
  - "That lighting is chef's kiss. Taking the shot... got it!"

**Voice Input (STT):**
- Uses Android's `SpeechRecognizer` API.
- Activated by holding a microphone button on the viewfinder, or by a "Hey Vantage" wake word (stretch goal).
- User's speech is transcribed and sent to Gemma as the next conversation turn.
- Examples of voice commands:
  - "Make it moodier"
  - "Zoom in more"
  - "I want it to look like a magazine cover"
  - "Switch to black and white"
  - "Show me some inspiration for this scene"
  - "Take the picture now"

**Chat Bubbles:**
- Gemma's text responses also appear as floating chat-style bubbles on the viewfinder.
- Bubbles slide up from the bottom, stack briefly, then fade out after 4 seconds.
- User's voice input appears as a right-aligned bubble (like iMessage).
- Keeps the interaction feeling like a conversation, not a utility.

### Feature 6: Auto-Capture

**Logic:**
1. After coaching begins, Gemma continuously re-evaluates the frame (every 2-3 seconds).
2. Each re-evaluation checks:
   - Are camera settings optimized? (applied automatically)
   - Is the composition aligned with the coaching target?
   - Has the user followed physical movement instructions?
   - Is the scene stable (not blurry/moving)?
3. When all conditions are met, Gemma returns `"ready_to_capture": true`.
4. App shows a brief countdown overlay: "3... 2... 1..." with voice narration.
5. Photo is captured using `CameraCaptureSession.capture()` with the current settings.
6. Photo is saved to the device gallery via `MediaStore`.
7. A brief preview of the captured photo flashes on screen.
8. Voice: "Got it! That turned out great."

**Manual Override:**
- User can always tap the shutter button to capture immediately, bypassing auto-capture.
- User can say "Take the picture now" to trigger immediate capture.

---

## 6. UI/UX Design

### Screen Layout

```
+------------------------------------------+
|  [Status Bar]                            |
+------------------------------------------+
|                                          |
|  [Unsplash Inspo Row - hidden by default]|
|  | img1 | img2 | img3 | img4 | img5 |   |
|                                          |
+------------------------------------------+
|                                          |
|                                          |
|           VIEWFINDER                     |
|        (Full-screen camera preview       |
|         with filter applied)             |
|                                          |
|    +----------------------------------+  |
|    |  [Chat Bubble from AI]           |  |
|    |  "Try angling down a bit..."     |  |
|    +----------------------------------+  |
|                                          |
|         [Directional Arrow Overlay]      |
|              (when needed)               |
|                                          |
|    [Rule of Thirds Grid - subtle]        |
|                                          |
+------------------------------------------+
|                                          |
|  [Filter Strip - horizontal scroll]      |
|  | Natural | Warm | Cool | Noir | ... |  |
|                                          |
+------------------------------------------+
|                                          |
|  [Bottom Controls Bar]                   |
|                                          |
|   [Gallery]  [Shutter]  [AI Sparkle]     |
|    thumb      (O)        [*]             |
|                                          |
|   [Mode Toggle: "Do It" | "Coach Me"]   |
|                                          |
|   [Mic Button]                           |
|                                          |
+------------------------------------------+
```

### Design Principles

- **Dark theme only** - standard for camera apps, reduces distraction.
- **Minimal chrome** - the viewfinder dominates. UI elements are semi-transparent overlays.
- **Smooth animations** - all transitions use Compose animations (fade, slide, scale).
- **No clutter** - inspo row, chat bubbles, and overlays only appear when AI coaching is active.
- **Haptic feedback** - subtle vibration on shutter, mode toggle, filter change.

### Color Palette

- Background: Pure black (`#000000`)
- Primary accent: Electric blue (`#2979FF`) for AI-related elements
- Secondary accent: Warm amber (`#FFB300`) for coaching highlights
- Text: White (`#FFFFFF`) with 80% opacity for secondary text
- Chat bubbles: Semi-transparent dark (`#1A1A1A` at 85% opacity) with white text
- Shutter button: White ring, fills blue when AI coaching is active

### Typography

- Sans-serif system font (Roboto on Samsung)
- Chat bubbles: 14sp
- Coaching overlays: 16sp semi-bold
- Filter labels: 12sp

### Key UI States

**State 1: Standard Camera (No AI)**
- Clean viewfinder with filter strip at bottom
- Shutter button, gallery thumbnail, AI sparkle button
- Mode toggle visible but subtle

**State 2: AI Coaching Active**
- Viewfinder has subtle rule-of-thirds grid overlay
- Chat bubbles appear from AI
- Directional arrows/guides when physical movement needed
- Inspo row slides down from top (if Unsplash results available)
- Shutter button pulses blue
- Mic button glows to indicate voice input is available

**State 3: Inspo Photo Selected**
- Selected inspo photo appears as a small picture-in-picture overlay (top-right corner, ~80x80dp)
- AI coaching adjusts to match the reference
- Chat bubble: "Matching that style. Let me adjust the settings..."

**State 4: Ready to Capture**
- Viewfinder border briefly glows green
- Countdown overlay: "3... 2... 1..."
- Voice narration of countdown
- Shutter fires automatically

**State 5: Photo Captured**
- Brief full-screen flash animation
- Captured photo slides to gallery thumbnail
- Chat bubble: "Got it! That turned out great."
- Returns to State 1

---

## 7. LiteRT-LM Integration

### Engine Initialization

```kotlin
// BackendFactory with NPU -> GPU fallback
object VantageEngine {
    private var engine: Engine? = null

    suspend fun initialize(context: Context, modelPath: String) {
        val backend = try {
            Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
        } catch (e: Exception) {
            Log.w("Vantage", "NPU unavailable, falling back to GPU", e)
            Backend.GPU()
        }

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            visionBackend = Backend.GPU(),
            cacheDir = context.cacheDir.absolutePath
        )

        engine = Engine(engineConfig)
        engine!!.initialize() // ~10 seconds, must be on background thread
    }

    fun getEngine(): Engine = engine ?: throw IllegalStateException("Engine not initialized")
}
```

### System Prompt

```kotlin
val SYSTEM_PROMPT = """
You are Vantage, a friendly and enthusiastic AI photography coach built into a camera app.
You analyze scenes through the camera viewfinder and help users take amazing photos.

Your personality:
- You talk like an excited photographer friend, not a robot
- You're encouraging but also give honest, specific feedback
- You use casual language: "Ooh!", "Nice!", "Let me tweak that..."
- Keep voice messages under 2 sentences so TTS sounds natural

When analyzing a scene, you MUST call the analyze_scene tool with your analysis.
When the user asks you to adjust something, call the adjust_camera tool.
When you determine the shot is ready, call the capture_ready tool.

IMPORTANT: Always respond conversationally even while calling tools.
Your voice_message should sound natural when spoken aloud.
"""
```

### Tool Definitions (Function Calling)

```kotlin
class CameraToolSet : ToolSet {

    @Tool(description = "Analyze the current camera scene and provide coaching. Call this whenever a new frame is analyzed or the user asks for help with their shot.")
    fun analyzeScene(
        @ToolParam(description = "Short natural description of the scene, e.g. 'iced latte on wooden table with natural side light'") sceneDescription: String,
        @ToolParam(description = "Zoom level to set, 1.0 = no zoom, range 0.5 to 10.0") zoom: Double = 1.0,
        @ToolParam(description = "ISO sensitivity, range 50-3200. Lower = less noise. Use 0 for auto.") iso: Int = 0,
        @ToolParam(description = "Shutter speed in fraction denominator, e.g. 250 means 1/250s. Use 0 for auto.") shutterSpeed: Int = 0,
        @ToolParam(description = "White balance mode: auto, daylight, cloudy, tungsten, fluorescent, shade") whiteBalance: String = "auto",
        @ToolParam(description = "Focus distance in diopters, 0.0 = infinity, higher = closer. Use -1 for autofocus.") focusDistance: Double = -1.0,
        @ToolParam(description = "Exposure compensation in stops, range -3.0 to +3.0") exposureComp: Double = 0.0,
        @ToolParam(description = "Recommended filter: natural, warm, cool, noir, vivid, dramatic, silvertone, cinematic, vintage, muted, fade, mono") filter: String = "natural",
        @ToolParam(description = "List of instructions for things requiring physical user action, e.g. ['Move 2 steps to the left', 'Angle the phone down about 15 degrees']") userActions: List<String> = emptyList(),
        @ToolParam(description = "Conversational voice message to speak to the user via TTS. Keep under 2 sentences. Sound like an enthusiastic friend.") voiceMessage: String,
        @ToolParam(description = "Search query for Unsplash inspiration photos related to this scene") unsplashQuery: String = "",
        @ToolParam(description = "True if the composition is ready and the app should auto-capture") readyToCapture: Boolean = false
    ): Map<String, Any> {
        return mapOf(
            "status" to "applied",
            "scene" to sceneDescription,
            "settings_applied" to true
        )
    }

    @Tool(description = "Adjust specific camera settings based on user voice command, e.g. 'make it moodier' or 'zoom in more'")
    fun adjustCamera(
        @ToolParam(description = "Zoom level, 1.0 = no zoom") zoom: Double = -1.0,
        @ToolParam(description = "ISO, 0 = no change") iso: Int = 0,
        @ToolParam(description = "Shutter speed denominator, 0 = no change") shutterSpeed: Int = 0,
        @ToolParam(description = "White balance mode, empty = no change") whiteBalance: String = "",
        @ToolParam(description = "Exposure compensation, 99 = no change") exposureComp: Double = 99.0,
        @ToolParam(description = "Filter name, empty = no change") filter: String = "",
        @ToolParam(description = "Conversational voice response") voiceMessage: String,
        @ToolParam(description = "Updated Unsplash query based on the adjustment, empty = keep current") unsplashQuery: String = ""
    ): Map<String, Any> {
        return mapOf("status" to "adjusted")
    }

    @Tool(description = "Signal that the shot composition is ready for auto-capture")
    fun captureReady(
        @ToolParam(description = "Final voice message before capture, e.g. 'Perfect, hold still...'") voiceMessage: String
    ): Map<String, Any> {
        return mapOf("status" to "capturing")
    }
}
```

### Conversation Management

```kotlin
class CoachingSession(private val engine: Engine) {
    private var conversation: Conversation? = null

    fun start() {
        val config = ConversationConfig(
            systemInstruction = Contents.of(SYSTEM_PROMPT),
            samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.7),
            tools = listOf(tool(CameraToolSet()))
        )
        conversation = engine.createConversation(config)
    }

    suspend fun analyzeFrame(framePath: String): Flow<Message> {
        return conversation!!.sendMessageAsync(
            Contents.of(
                Content.ImageFile(framePath),
                Content.Text("Analyze this scene and help me take an amazing photo.")
            )
        )
    }

    suspend fun handleVoiceCommand(transcription: String): Flow<Message> {
        return conversation!!.sendMessageAsync(transcription)
    }

    suspend fun matchInspoPhoto(inspoDescription: String, framePath: String): Flow<Message> {
        return conversation!!.sendMessageAsync(
            Contents.of(
                Content.ImageFile(framePath),
                Content.Text("I want my photo to match this style: $inspoDescription. Adjust everything to get as close as possible to that look.")
            )
        )
    }

    fun close() {
        conversation?.close()
    }
}
```

### Model Download Strategy

The Gemma 4 E2B `.litertlm` model file is approximately 2.5 GB. The HuggingFace repo contains multiple variants. **Use the Snapdragon 8 Elite (SM8750) optimized variant** for the S25 Ultra:

| File | Use case |
|------|----------|
| `gemma-4-E2B-it_qualcomm_sm8750.litertlm` | **S25 Ultra (NPU-optimized, use this)** |
| `gemma-4-E2B-it.litertlm` | Generic (CPU/GPU fallback) |

For the hackathon:

1. **Pre-download the SM8750 model** to the Galaxy S25 Ultra before the demo.
2. Store it in the device's internal storage: `/data/local/tmp/gemma-4-E2B-it_qualcomm_sm8750.litertlm` or in the app's files directory.
3. The app's first-launch screen should have a "Select Model" button that lets the user pick the model file path, or auto-detect if placed in a known directory.
4. For production, this would use Google Play AI Pack delivery, but for the hackathon, manual placement is fine.

---

## 8. Camera Engine

### Camera2 API Setup

```kotlin
class CameraEngine(private val context: Context) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null

    // Camera characteristics for the S25 Ultra main camera
    private lateinit var characteristics: CameraCharacteristics

    fun openCamera(surfaceTexture: SurfaceTexture) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        // Select the main rear camera (typically "0")
        val cameraId = cameraManager.cameraIdList.first { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        characteristics = cameraManager.getCameraCharacteristics(cameraId)
        // ... open camera, create capture session, start preview
    }

    fun applySettings(settings: CameraSettings) {
        previewRequestBuilder?.apply {
            // Zoom
            if (settings.zoom != null) {
                set(CaptureRequest.CONTROL_ZOOM_RATIO, settings.zoom)
            }

            // ISO (requires manual mode)
            if (settings.iso != null && settings.iso > 0) {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.SENSOR_SENSITIVITY, settings.iso)
            }

            // Shutter speed (requires manual mode)
            if (settings.shutterSpeed != null && settings.shutterSpeed > 0) {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                val exposureTimeNs = 1_000_000_000L / settings.shutterSpeed
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTimeNs)
            }

            // White balance
            when (settings.whiteBalance) {
                "daylight" -> set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT)
                "cloudy" -> set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
                "tungsten" -> set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
                "fluorescent" -> set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT)
                "shade" -> set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_SHADE)
                "auto" -> set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            }

            // Focus distance
            if (settings.focusDistance != null && settings.focusDistance >= 0) {
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                set(CaptureRequest.LENS_FOCUS_DISTANCE, settings.focusDistance)
            } else {
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }

            // Exposure compensation
            if (settings.exposureComp != null) {
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                val step = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)!!
                val compValue = (settings.exposureComp / step.toDouble()).toInt()
                set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, compValue)
            }
        }

        // Apply to the live preview
        captureSession?.setRepeatingRequest(previewRequestBuilder!!.build(), null, null)
    }

    fun capturePhoto(callback: (Bitmap) -> Unit) {
        // Use ImageReader to capture a full-resolution still image
        // Apply the current filter via OpenGL rendering before saving
        // Save to MediaStore for gallery access
    }

    fun capturePreviewFrame(): String {
        // Capture current preview frame as a temporary JPEG file
        // Return the file path for LiteRT-LM multimodal input
        // This is used for AI analysis, not the final photo
    }
}

data class CameraSettings(
    val zoom: Float? = null,
    val iso: Int? = null,
    val shutterSpeed: Int? = null,  // denominator: 250 = 1/250s
    val whiteBalance: String? = null,
    val focusDistance: Float? = null,
    val exposureComp: Float? = null
)
```

### Frame Capture for AI Analysis

- Capture a preview frame at reduced resolution (e.g., 640x480) to minimize the payload sent to Gemma.
- Save as temporary JPEG to a cache directory.
- Pass the file path to `Content.ImageFile(path)` in the LiteRT-LM API.
- Delete the temporary file after analysis completes.

---

## 9. Filter System

### OpenGL ES Filter Pipeline

```kotlin
class FilterRenderer : GLSurfaceView.Renderer {
    private var currentFilter: FilterType = FilterType.NATURAL
    private var cameraTexture: Int = 0
    private var shaderProgram: Int = 0

    enum class FilterType {
        NATURAL, WARM, COOL, NOIR, VIVID, DRAMATIC,
        SILVERTONE, CINEMATIC, VINTAGE, MUTED, FADE, MONO
    }

    // Each filter is a GLSL fragment shader
    // The vertex shader is shared across all filters

    companion object {
        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        const val WARM_FILTER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                vec4 color = texture2D(uTexture, vTexCoord);
                color.r = min(color.r * 1.15, 1.0);
                color.g = color.g * 1.05;
                color.b = color.b * 0.85;
                gl_FragColor = color;
            }
        """

        const val NOIR_FILTER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                vec4 color = texture2D(uTexture, vTexCoord);
                float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                // Increase contrast with S-curve
                lum = lum * lum * (3.0 - 2.0 * lum);
                gl_FragColor = vec4(vec3(lum), color.a);
            }
        """

        const val CINEMATIC_FILTER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                vec4 color = texture2D(uTexture, vTexCoord);
                float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                // Teal in shadows, orange in highlights
                vec3 shadows = vec3(0.0, 0.1, 0.15);
                vec3 highlights = vec3(0.15, 0.08, 0.0);
                vec3 toned = mix(color.rgb + shadows, color.rgb + highlights, lum);
                gl_FragColor = vec4(toned, color.a);
            }
        """

        // ... similar shaders for COOL, VIVID, DRAMATIC, SILVERTONE, VINTAGE, MUTED, FADE, MONO
    }

    fun setFilter(filter: FilterType) {
        currentFilter = filter
        // Recompile shader program with the new fragment shader
    }
}
```

### Filter Strip UI (Compose)

```kotlin
@Composable
fun FilterStrip(
    currentFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(FilterType.values()) { filter ->
            FilterChip(
                label = filter.displayName,
                isSelected = filter == currentFilter,
                previewColor = filter.previewColor,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}
```

---

## 10. Unsplash Integration

### API Client

```kotlin
object UnsplashClient {
    private const val BASE_URL = "https://api.unsplash.com"
    private val client = OkHttpClient()

    suspend fun searchPhotos(
        query: String,
        perPage: Int = 6,
        accessKey: String = BuildConfig.UNSPLASH_ACCESS_KEY
    ): List<UnsplashPhoto> {
        val url = "$BASE_URL/search/photos?query=${query.urlEncode()}&per_page=$perPage&client_id=$accessKey"

        val request = Request.Builder().url(url).build()
        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        val json = JSONObject(response.body?.string() ?: return emptyList())
        val results = json.getJSONArray("results")

        return (0 until results.length()).map { i ->
            val photo = results.getJSONObject(i)
            UnsplashPhoto(
                id = photo.getString("id"),
                thumbUrl = photo.getJSONObject("urls").getString("thumb"),
                smallUrl = photo.getJSONObject("urls").getString("small"),
                regularUrl = photo.getJSONObject("urls").getString("regular"),
                photographerName = photo.getJSONObject("user").getString("name"),
                photographerUrl = photo.getJSONObject("user").getJSONObject("links").getString("html"),
                altDescription = photo.optString("alt_description", ""),
                downloadLocation = photo.getJSONObject("links").getString("download_location")
            )
        }
    }
}

data class UnsplashPhoto(
    val id: String,
    val thumbUrl: String,
    val smallUrl: String,
    val regularUrl: String,
    val photographerName: String,
    val photographerUrl: String,
    val altDescription: String,
    val downloadLocation: String
)
```

### Unsplash API Setup Steps

1. Go to https://unsplash.com/join and create an account.
2. Go to https://unsplash.com/oauth/applications and click "New Application."
3. Accept the API Use and Guidelines.
4. Name the app "Vantage" and provide a description.
5. Copy the "Access Key" (not the Secret Key for public/search-only use).
6. Add it to your project's `local.properties`:
   ```
   UNSPLASH_ACCESS_KEY=your_access_key_here
   ```
7. In `build.gradle.kts`:
   ```kotlin
   android {
       defaultConfig {
           buildConfigField("String", "UNSPLASH_ACCESS_KEY",
               "\"${project.findProperty("UNSPLASH_ACCESS_KEY")}\"")
       }
   }
   ```

### Attribution UI

Per Unsplash guidelines, every displayed photo must show:
```
Photo by [Photographer Name] on Unsplash
```
Where "Photographer Name" links to `{photographerUrl}?utm_source=vantage&utm_medium=referral`
and "Unsplash" links to `https://unsplash.com/?utm_source=vantage&utm_medium=referral`

This is shown as small text below each inspo thumbnail.

---

## 11. Conversational Voice System

### TTS Manager

```kotlin
class VoiceCoach(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setSpeechRate(1.05f) // Slightly faster than default for natural feel
            tts.setPitch(1.0f)
            isReady = true
        }
    }

    fun speak(message: String) {
        if (isReady) {
            tts.speak(message, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
        }
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
```

### STT Manager

```kotlin
class VoiceInput(
    private val context: Context,
    private val onResult: (String) -> Unit
) {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { onResult(it) }
            }
            // ... implement other required methods (onError, onReadyForSpeech, etc.)
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.e("VoiceInput", "Speech recognition error: $error")
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
    }

    fun destroy() {
        speechRecognizer.destroy()
    }
}
```

### Chat Bubble UI

```kotlin
@Composable
fun ChatBubble(
    message: String,
    isFromUser: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isFromUser)
                Color(0xFF2979FF).copy(alpha = 0.85f)
            else
                Color(0xFF1A1A1A).copy(alpha = 0.85f),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
```

---

## 12. Auto-Capture System

### Readiness Evaluator

```kotlin
class AutoCaptureManager(
    private val coachingSession: CoachingSession,
    private val cameraEngine: CameraEngine,
    private val voiceCoach: VoiceCoach
) {
    private var isCoachingActive = false
    private var analysisJob: Job? = null

    fun startCoaching(scope: CoroutineScope) {
        isCoachingActive = true
        analysisJob = scope.launch {
            while (isCoachingActive) {
                delay(3000) // Re-analyze every 3 seconds

                val framePath = cameraEngine.capturePreviewFrame()
                coachingSession.analyzeFrame(framePath).collect { message ->
                    // Parse tool calls from the message
                    message.toolCalls.forEach { toolCall ->
                        when (toolCall.name) {
                            "analyzeScene" -> handleSceneAnalysis(toolCall.arguments)
                            "adjustCamera" -> handleCameraAdjustment(toolCall.arguments)
                            "captureReady" -> handleCaptureReady(toolCall.arguments)
                        }
                    }
                }
            }
        }
    }

    private fun handleCaptureReady(arguments: String) {
        val args = JSONObject(arguments)
        val voiceMessage = args.getString("voiceMessage")

        voiceCoach.speak(voiceMessage)

        // Start countdown
        CoroutineScope(Dispatchers.Main).launch {
            delay(1500) // Let TTS finish
            voiceCoach.speak("3... 2... 1...")
            delay(2500)
            cameraEngine.capturePhoto { bitmap ->
                // Save to gallery
                voiceCoach.speak("Got it! That turned out great.")
            }
        }
    }

    fun stopCoaching() {
        isCoachingActive = false
        analysisJob?.cancel()
    }
}
```

---

## 13. Project Structure

```
Vantage/
  app/
    src/
      main/
        java/com/vantage/
          VantageApplication.kt          # Application class, engine init
          MainActivity.kt                 # Single activity, Compose host
          
          contracts/                      # Frozen interfaces (set in Phase 0)
            ICameraEngine.kt
            IFilterEngine.kt
            IAICoach.kt
            IVoiceSystem.kt
            IUnsplashClient.kt

          models/                         # Frozen data classes (set in Phase 0)
            CameraSettings.kt
            CoachingResult.kt
            FilterType.kt
            AppMode.kt
            ChatMessage.kt
            UnsplashPhoto.kt
            CameraUiState.kt

          ui/
            theme/
              Theme.kt                    # Dark theme, colors, typography
              Colors.kt                   # Color palette
            screens/
              CameraScreen.kt             # Main camera viewfinder screen
              LoadingScreen.kt            # Loading screen during model init
            components/
              Viewfinder.kt               # Camera preview composable
              FilterStrip.kt              # Horizontal filter selector
              ChatBubbleList.kt           # Floating chat bubbles overlay
              InspoRow.kt                 # Unsplash inspiration photo row
              OverlayGuides.kt            # Rule of thirds, arrows, guides
              ShutterButton.kt            # Animated shutter button
              ModeToggle.kt               # "Do It" / "Coach Me" toggle
              MicButton.kt                # Voice input button
              CountdownOverlay.kt         # 3-2-1 capture countdown
          
          camera/
            CameraEngine.kt              # Camera2 API wrapper
            PhotoSaver.kt                # Save to MediaStore
            FrameCapture.kt              # Preview frame grabber for AI
          
          ai/
            VantageEngine.kt             # LiteRT-LM engine singleton
            CoachingSession.kt           # Conversation management
            CameraToolSet.kt             # @Tool function calling definitions
            ToolCallParser.kt            # Message.toolCalls -> CoachingResult
            SystemPrompts.kt             # System prompt constants
            ModelPathHelper.kt           # Finds .litertlm on device
          
          filters/
            FilterEngine.kt              # Implements IFilterEngine
            FilterRenderer.kt            # OpenGL ES filter pipeline
            Shaders.kt                   # GLSL shader source strings
            FilterGLSurfaceView.kt       # Custom GLSurfaceView
          
          voice/
            VoiceSystem.kt               # TTS + STT, implements IVoiceSystem
          
          api/
            UnsplashClientImpl.kt        # Implements IUnsplashClient
            UnsplashApiService.kt        # OkHttp + JSON parsing
          
          viewmodel/
            CameraViewModel.kt           # Main ViewModel, orchestrates everything
        
        res/
          drawable/
            ic_sparkle.xml               # AI coach button icon
            ic_mic.xml                   # Microphone icon
            ic_gallery.xml               # Gallery thumbnail icon
          values/
            strings.xml
            colors.xml
            themes.xml
          raw/
            shutter_sound.mp3            # Capture sound effect
        
        AndroidManifest.xml
    
    build.gradle.kts                      # App-level build config
  
  build.gradle.kts                        # Project-level build config
  gradle/
    libs.versions.toml                    # All dependency versions
  settings.gradle.kts
  local.properties                        # API keys (not committed)
  gradle.properties
  documentation/
    Spec.md                               # Full spec
    Phases.md                             # Development phases
  
  README.md                               # Required by hackathon
  LICENSE                                  # Open source license (MIT or Apache 2.0)
```

---

## 14. Build and Deployment

### Gradle Configuration

The project uses a version catalog (`gradle/libs.versions.toml`) for all dependency versions. AGP 9.x bundles Kotlin, so no separate Kotlin plugin is needed. Compose compiler is a Gradle plugin (`org.jetbrains.kotlin.plugin.compose`).

**Key build config:**
- `namespace = "com.vantage"`, `applicationId = "com.vantage"`
- `compileSdk = 36`, `minSdk = 31`, `targetSdk = 35`
- BuildConfig fields: `UNSPLASH_ACCESS_KEY`, `ELEVENLABS_API_KEY` (read from `local.properties`)
- All dependencies are declared via the version catalog. See `gradle/libs.versions.toml` for exact versions.

**Dependencies included:**

| Category | Libraries |
|----------|-----------|
| Compose | BOM 2026.04.01, UI, Material3, Tooling Preview |
| AndroidX | Activity Compose 1.13.0, Lifecycle 2.10.0, Navigation Compose 2.9.8 |
| Camera | Camera2 1.6.0, Camera Lifecycle 1.6.0 |
| Networking | OkHttp 4.12.0 |
| Images | Coil Compose 2.7.0 |
| Async | Coroutines Android 1.10.2 |

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Install on connected S25 Ultra
./gradlew installDebug

# Release build (for demo)
./gradlew assembleRelease
```

### Model Deployment to Device

```bash
# Download model from HuggingFace (do this before the hackathon)
# https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

# Push model to device
adb push gemma-4-E2B-it_qualcomm_sm8750.litertlm /sdcard/Download/

# The app will prompt the user to select the model file on first launch
# or auto-detect it in /sdcard/Download/
```

### APK Installation

The app should be installable as a single APK with no external setup required beyond placing the model file on the device. For the hackathon demo, the model will be pre-loaded on the S25 Ultra.

---

## 15. Demo Script

**Total demo time: ~4-5 minutes**

### Setup (before demo starts)
- Model pre-loaded on S25 Ultra
- App installed
- Unsplash API key configured
- Have a few objects ready: a coffee cup, a plant, a group of people

### Act 1: Introduction (30 seconds)
"This is Vantage, a conversational AI camera co-pilot. Unlike Google's Camera Coach, which requires the cloud and takes a minute to give you text tips, Vantage runs entirely on-device using Gemma 4 via LiteRT-LM on this Galaxy S25 Ultra. It controls your camera, talks to you, and takes the picture for you."

### Act 2: "Do It For Me" Mode (90 seconds)
1. Open the app. Show the clean, dark camera UI.
2. Point at a coffee cup on a table.
3. Tap the AI sparkle button.
4. Vantage speaks: "Ooh, coffee shot! Let me set this up for you."
5. Show the camera settings changing automatically (zoom adjusts, warmth filter applies).
6. Show the chat bubble: "Try angling down just a bit for a nice overhead look."
7. Follow the directional arrow overlay.
8. Vantage speaks: "Perfect, hold still... 3, 2, 1..."
9. Auto-capture fires. Show the result.

### Act 3: Voice Conversation (60 seconds)
1. Hold the mic button: "Make it more dramatic."
2. Vantage adjusts filter to "dramatic," tweaks exposure.
3. Vantage speaks: "Going for that moody vibe. I darkened the shadows and switched to the dramatic filter."
4. Hold mic: "Show me some inspiration."
5. Inspo row slides in with Unsplash photos.
6. Tap one. Vantage speaks: "Love that choice. Let me match the angle and tone."

### Act 4: Filters (30 seconds)
1. Swipe through the filter strip: Warm, Cool, Noir, Cinematic, Vintage.
2. Show filters applying in real time on the viewfinder.
3. "You can manually browse filters, or let the AI pick the best one for the scene."

### Act 5: Coach Me Mode (60 seconds)
1. Toggle to "Coach Me" mode.
2. Point at a different scene (a plant, a landscape out a window).
3. Tap AI button.
4. Vantage speaks step-by-step instructions instead of auto-applying.
5. "Now instead of doing everything automatically, it walks you through each step so you learn."
6. Follow instructions, show auto-capture at the end.

### Act 6: Technical Close (30 seconds)
"All AI inference runs on-device via LiteRT-LM with Gemma 4 E2B. No cloud. No internet required for core features. The Unsplash integration is the only part that needs connectivity, and it degrades gracefully without it. The function calling API lets Gemma directly control camera parameters, and the multimodal input lets it actually see what you're shooting."

---

## 16. Submission Checklist

### GitHub Repository
- [ ] Public repository created
- [ ] Open source license file (MIT or Apache 2.0) at root, visible in About section
- [ ] README.md with:
  - [ ] Application description
  - [ ] Team member names and emails
  - [ ] Setup instructions from scratch (including model download, Unsplash key)
  - [ ] All dependencies listed
  - [ ] Run and usage instructions
  - [ ] Screenshots/GIFs of the app in action
- [ ] Well-commented code
- [ ] Clean commit history

### Devpost Submission
- [ ] Text description of features and functionality
- [ ] Link to GitHub repository
- [ ] Track selection: Track 1 (LiteRT-LM)
- [ ] Team members listed

### Recommended Extras
- [ ] Architecture diagram in README
- [ ] Latency benchmarks (model load time, inference time per frame)
- [ ] Demo video or GIF
- [ ] Notes section with references
- [ ] Known limitations documented

### Technical Verification
- [ ] App installs cleanly on Galaxy S25 Ultra
- [ ] Model loads successfully (test both GPU and NPU backends)
- [ ] Camera preview renders without lag
- [ ] Filters apply in real time
- [ ] LiteRT-LM function calling works (Gemma returns structured tool calls)
- [ ] TTS speaks coaching tips
- [ ] STT captures voice commands
- [ ] Unsplash search returns results and displays thumbnails
- [ ] Auto-capture triggers correctly
- [ ] Photo saves to device gallery
- [ ] App handles model loading gracefully (loading screen, progress)
- [ ] App works with camera permissions flow
- [ ] App does not crash on rotation or lifecycle changes

---

## Appendix A: Key Documentation Links

| Resource | URL |
|----------|-----|
| LiteRT-LM Android Guide | https://ai.google.dev/edge/litert-lm/android |
| LiteRT-LM GitHub | https://github.com/google-ai-edge/LiteRT-LM |
| Gemma 4 E2B Model | https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm |
| Gemma 4 E4B Model | https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm |
| Google AI Edge Gallery App | https://github.com/google-ai-edge/gallery |
| Camera2 API Reference | https://developer.android.com/reference/android/hardware/camera2/package-summary |
| Unsplash API Docs | https://unsplash.com/documentation |
| Unsplash API Guidelines | https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines |
| Qualcomm AI Hub | https://aihub.qualcomm.com |
| Hackathon Devpost | https://google-x-qualcomm-hackathon.devpost.com/ |
| Qualcomm Discord Support | https://discord.com/invite/yxqdjx6x6g |

## Appendix B: Gemma 4 E2B vs E4B Decision

| Factor | E2B (~2B params) | E4B (~4B params) |
|--------|------------------|-------------------|
| Model size | ~2.5 GB | ~4.5 GB |
| Inference speed | Faster | Slower |
| Quality | Good for structured output | Better reasoning |
| RAM usage | Lower | Higher |
| Recommendation | **Use E2B** | Backup option |

**Decision: Use E2B.** For a camera coaching app, speed matters more than deep reasoning. The structured JSON output (camera settings, filter names, short coaching tips) does not require the extra capacity of E4B. Faster inference means the re-analysis loop runs more frequently, making the experience feel more responsive.

## Appendix C: Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Model fails to load on NPU | Fallback to GPU backend (already coded) |
| Gemma returns malformed JSON | Parse tool calls, not raw text. LiteRT-LM handles this via `@Tool` annotations |
| Unsplash API rate limit (50/hr demo) | Cache results aggressively. Limit searches to new coaching sessions only |
| Camera2 API compatibility issues on S25 Ultra | Test early. Use CameraX as fallback for preview if needed |
| TTS sounds robotic | Keep messages short (under 2 sentences). Set speech rate to 1.05x |
| 10-second model load time | Show a polished loading screen with progress animation |
| Voice recognition poor in noisy hackathon venue | Always show text chat bubbles alongside voice. Voice is additive, not required |
| OpenGL filter rendering issues | Have a simpler ColorMatrix-based fallback using Android's built-in ColorFilter |
| App crashes during demo | Pre-test every demo scenario. Have a screen recording backup |

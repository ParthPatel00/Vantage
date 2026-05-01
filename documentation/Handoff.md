# Vantage — Handoff Context (single-file)

This file is meant to be the **only** thing handed to ChatGPT (or another
assistant) for full project context. It folds in the relevant parts of
`Spec.md`, `Phases.md`, and `Developer_guide.md`, plus the current repo /
local-environment state as of **2026-04-30**.

If you are an assistant reading this: assume nothing else from the repo is
visible to you. Everything you need to reason about Vantage is in this file.
Where a code excerpt is shown, it is the team's intended pattern, not
necessarily what is in the tree today — see §6 "Status snapshot" for what is
actually built versus stubbed.

---

## 1. What Vantage is

**Vantage** is an Android app being built for the **Google × Qualcomm
Hackathon (April 30 – May 1, 2026)** under **Track 1 — LiteRT-LM, LLM-based
consumer use journeys**. Submission deadline **May 1, 2026, 1:00 PM PT**
(~25 hours of build time tot/effoal).

**One-liner:** a conversational AI camera co-pilot powered by **Gemma 4 E2B**
running on-device via **LiteRT-LM** that talks to you, controls your camera,
applies filters, finds inspiration photos, and auto-captures the perfect shot.

**Pitch:** Google's Camera Coach on Pixel 10 needs the cloud, takes up to a
minute, gives only text tips, can't change camera settings, and never
auto-captures. Vantage does all of this **on-device, in real time, with no
internet required for core features**. It runs on the **Samsung Galaxy S25
Ultra (Snapdragon 8 Elite, SM8750, Hexagon v79)**, talks to you like a
photographer friend (TTS), listens to voice commands (STT), controls every
camera parameter (zoom, ISO, shutter, white balance, focus, exposure
compensation), applies live viewfinder filters, fetches Unsplash inspiration
based on the scene, and automatically takes the picture when everything looks
right.

**Two app modes:**
- `DO_IT_FOR_ME` — Gemma applies camera settings automatically.
- `COACH_ME` — Gemma only suggests; user adjusts via on-screen instructions
  + voice.

**Judging criteria (5 equally weighted):** LiteRT usage (pass/fail gate +
scored); technical implementation; use-case + innovation; deployment +
accessibility; presentation + documentation.

---

## 2. Architecture (system diagram + data flow)

```
+------------------------------------------------------------------+
|                        Vantage Android App                       |
|                                                                  |
|  +---------------------+     +------------------------------+    |
|  |   Camera Engine     |     |   AI Brain (LiteRT-LM)       |    |
|  |   (Camera2 API)     |     |   Gemma 4 E2B                |    |
|  |                     |---->|                              |    |
|  |  - Viewfinder       |     |  - Scene Analysis            |    |
|  |  - Zoom / ISO /     |<----|  - Coaching Tips             |    |
|  |    Shutter / WB /   |     |  - Camera Parameter JSON     |    |
|  |    Focus / EV       |     |  - Filter Recommendation     |    |
|  |  - Auto-Capture     |     |  - ready_to_capture          |    |
|  +---------------------+     |  - Conversation Context      |    |
|           |                  +------------------------------+    |
|           v                            |          ^              |
|  +---------------------+     +------------------+ |              |
|  |   Filter Engine     |     | Voice System     | |              |
|  |   (OpenGL ES 2.0)   |     | - TTS (out)      | |              |
|  |   12 GLSL filters   |     | - STT (in)       | |              |
|  +---------------------+     | - Chat Bubbles   | |              |
|                              +------------------+ |              |
|                              +------------------+ |              |
|                              | Unsplash API     |-+              |
|                              | - Scene Search   |                |
|                              | - Inspo Gallery  |                |
|                              | - Style Matching |                |
|                              +------------------+                |
|                                                                  |
|  +-------------------------------------------------------------+ |
|  |                    Overlay Renderer                         | |
|  |  - Rule-of-thirds grid  - Horizon level                     | |
|  |  - Directional arrows   - Subject positioning guides        | |
|  |  - Chat bubbles         - Filter preview strip              | |
|  |  - Inspo thumbnails     - Auto-capture countdown            | |
|  +-------------------------------------------------------------+ |
+------------------------------------------------------------------+
```

**Data flow (happy path):**

1. App launches → camera preview starts.
2. User taps the AI sparkle button (or is auto-coached on entry).
3. App captures a 640×480 JPEG of the current preview frame to
   `cacheDir/preview_frame.jpg`.
4. Frame + system prompt sent to Gemma via LiteRT-LM
   (`Content.ImageFile(path)` + `Content.Text(prompt)`).
5. Gemma streams back tool calls (function calling) carrying a structured
   `CoachingResult`:
   ```json
   {
     "scene_description": "Iced latte on wooden table, warm side light",
     "camera_settings": { "zoom": 1.5, "iso": 100, "wb": "daylight", ... },
     "filter": "warm",
     "user_actions": ["Tilt phone down 15°"],
     "voice_message": "Ooh, latte shot. Bumping up the warmth.",
     "unsplash_query": "coffee latte overhead wooden table",
     "ready_to_capture": false
   }
   ```
6. App applies camera settings (DO_IT_FOR_ME only) + filter, speaks
   `voice_message` via TTS, shows directional arrows for `user_actions`,
   fetches Unsplash photos for `unsplash_query`.
7. Loop re-analyzes every ~3s. When Gemma returns `ready_to_capture: true`,
   app shows a 3-2-1 countdown and auto-captures via `MediaStore`.
8. User can interrupt anytime: tap shutter, hold mic to speak ("make it
   moodier"), or tap an Unsplash thumbnail to ask Gemma to match that style.

---

## 3. Tech stack

| Layer | Choice / details |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (single-activity, NavHost: `loading` → `camera`) |
| Build | Gradle 9.4.1 KTS, AGP 9.2.0, Kotlin 2.3.0, Compose BOM 2026.04.01 |
| `compileSdk` / `minSdk` / `targetSdk` | 36 / 31 / 35 |
| Namespace / appId | `com.vantage` / `com.vantage` |
| ABI filter | `arm64-v8a` only |
| Camera | Camera2 API + CameraX preview classes (`camera-camera2`, `camera-lifecycle`, `camera-view`) |
| Filter rendering | OpenGL ES 2.0 fragment shaders on a custom `GLSurfaceView` |
| AI runtime | **LiteRT-LM** (`com.google.ai.edge.litertlm`) — `Engine`, `Conversation`, `Backend.NPU/GPU/CPU` |
| Model | `gemma-4-E2B-it_qualcomm_sm8750.litertlm` (~2.5–2.8 GB, NPU-compiled). Downloaded from `litert-community/gemma-4-E2B-it-litert-lm` on HuggingFace. **Do not** use the generic `gemma-4-E2B-it.litertlm` — it's CPU/GPU-only and significantly slower. |
| TTS (primary) | **ElevenLabs** API (voice "Rachel" `21m00Tcm4TlvDq8ikWAM`, MP3 streamed via the `/v1/text-to-speech/{voice_id}/stream` endpoint). API key in `local.properties → ELEVENLABS_API_KEY`. Spec wants ElevenLabs as primary; **current code uses Android `TextToSpeech` only** — ElevenLabs not yet integrated. |
| TTS (fallback) | Android `android.speech.tts.TextToSpeech`, `Locale.US`, `speechRate = 1.05f`, `QUEUE_FLUSH` |
| STT (primary, planned) | Google Cloud Speech-to-Text API |
| STT (current) | Android `SpeechRecognizer`, `LANGUAGE_MODEL_FREE_FORM` |
| Networking | OkHttp + `org.json` (Unsplash, ElevenLabs) |
| Image loading | Coil Compose (Unsplash thumbnails) |
| Inspiration photos | **Unsplash API** — `GET https://api.unsplash.com/search/photos?query=...&per_page=6&client_id={key}`. Free demo tier = 50 req/hr. Key in `local.properties → UNSPLASH_ACCESS_KEY`. Required UTM: append `?utm_source=vantage&utm_medium=referral` to all profile / Unsplash links. |

`local.properties` (git-ignored) holds: `sdk.dir`, `UNSPLASH_ACCESS_KEY`,
`ELEVENLABS_API_KEY`. Both keys are exposed via `BuildConfig` because
`buildFeatures { buildConfig = true }` is set.

**LiteRT vs LiteRT-LM:** LiteRT runs `.tflite` classical models. **LiteRT-LM**
is the higher-level wrapper that runs `.litertlm` LLM/VLM models — handles
tokenization, KV cache, multimodal input. Vantage uses **LiteRT-LM**. Backend
hierarchy is CPU (universal fallback) → GPU (parallel acceleration) → NPU
(Hexagon HTP via QNN, fastest on SM8750).

---

## 4. AndroidManifest, native libs, and the NPU

`AndroidManifest.xml` declares:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<application android:name=".VantageApplication" ...>
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so"     android:required="false"/>
    <!-- Required for CPU↔Hexagon DSP communication on Qualcomm NPU -->
    <uses-native-library android:name="libcdsprpc.so"    android:required="false"/>
</application>
```

Activity is `MainActivity`, `screenOrientation="portrait"`. With `minSdk=31`,
`WRITE_EXTERNAL_STORAGE`/`READ_EXTERNAL_STORAGE` are not needed —
`MediaStore` is used for saved photos.

**`app/src/main/jniLibs/arm64-v8a/` must contain seven `.so` files**, all from
the `litert-samples` repo (NOT from the Qualcomm QAIRT SDK — those are QNN
1.6.0 and incompatible; LiteRT-LM's dispatch lib needs ≥ 1.8.0):

```
libLiteRtDispatch_Qualcomm.so      ← NPU dispatch bridge
libGemmaModelConstraintProvider.so ← Gemma constraint loader
libQnnHtp.so                       ← QNN HTP runtime
libQnnHtpV79CalculatorStub.so
libQnnHtpV79Skel.so                ← Hexagon DSP skel
libQnnHtpV79Stub.so
libQnnSystem.so
```

`app/build.gradle.kts` sets:

```kotlin
packaging {
    resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // QNN must dlopen .so files from disk, not from a compressed APK entry.
    jniLibs { useLegacyPackaging = true }
}
defaultConfig { ndk { abiFilters += "arm64-v8a" } }
```

Source for the libs:

```bash
git clone --depth=1 --filter=blob:none --sparse \
  https://github.com/google-ai-edge/litert-samples.git /tmp/litert-samples
cd /tmp/litert-samples
git sparse-checkout set compiled_model_api/qualcomm/llm_chatbot_npu/app/src/main/jniLibs/arm64-v8a
cp compiled_model_api/qualcomm/llm_chatbot_npu/app/src/main/jniLibs/arm64-v8a/*.so \
   <Vantage>/app/src/main/jniLibs/arm64-v8a/
```

**Pushing the model to the device:**

```bash
adb shell mkdir -p /sdcard/Android/data/com.vantage/files/
adb push gemma-4-E2B-it_qualcomm_sm8750.litertlm \
    /sdcard/Android/data/com.vantage/files/
```

The path `/sdcard/Android/data/com.vantage/files/` is the app-specific
external dir — readable by the app without extra permissions on Android 13+
scoped storage.

---

## 5. Module / file map (what's actually in the repo)

```
app/src/main/java/com/vantage/
  MainActivity.kt              single activity; requests CAMERA + RECORD_AUDIO at runtime;
                               on permission grant, hosts CameraScreen via Compose.
  VantageApplication.kt        Application class — currently EMPTY STUB. Phase 1B/2
                               will trigger GemmaEngine.initialize() here.

  ai/
    GemmaEngine.kt             LiteRT-LM wrapper. Implemented and confirmed working
                               on the S25 Ultra NPU. Methods: initialize(context),
                               describeImage(imagePath) -> String, close().
                               (See §7 for full code.)

  voice/
    VoiceSystem.kt             implements IVoiceSystem. Android TTS (Locale.US,
                               rate 1.05, QUEUE_FLUSH) + SpeechRecognizer
                               (LANGUAGE_MODEL_FREE_FORM). ElevenLabs not yet wired.

  api/
    UnsplashApiService.kt      OkHttp call + JSON parsing.
    UnsplashClientImpl.kt      implements IUnsplashClient. LinkedHashMap query cache.

  contracts/                   FROZEN interfaces — do NOT change without team agreement.
    ICameraEngine.kt           openCamera, applySettings, capturePreviewFrame, capturePhoto, close
    IFilterEngine.kt           setFilter, getCurrentFilter
    IAICoach.kt                initialize, isReady, analyzeFrame, sendVoiceCommand,
                               matchInspoStyle, close
    IVoiceSystem.kt            speak, startListening, stopListening, shutdown
    IUnsplashClient.kt         searchPhotos(query, perPage) -> Result<List<UnsplashPhoto>>

  models/                      FROZEN data classes.
    AppMode.kt                 enum: DO_IT_FOR_ME, COACH_ME
    CameraSettings.kt          zoom: Float?, iso: Int?, shutterSpeed: Int? (denominator),
                               whiteBalance: String? (auto/daylight/cloudy/tungsten/
                               fluorescent/shade), focusDistance: Float? (-1=AF),
                               exposureComp: Float? (-3..+3 stops). All nullable = no change.
    CameraUiState.kt           Full UI state (see §8).
    ChatMessage.kt             text: String, isFromUser: Boolean
    CoachingResult.kt          sceneDescription, cameraSettings, filter, userActions: List<String>,
                               voiceMessage, unsplashQuery, readyToCapture
    FilterType.kt              enum of 12 (Natural/Warm/Cool/Noir/Vivid/Dramatic/Silvertone/
                               Cinematic/Vintage/Muted/Fade/Mono) + displayName
    FlashMode.kt               OFF / ON / AUTO
    UnsplashPhoto.kt           id, thumbUrl, smallUrl, regularUrl, photographerName,
                               photographerUrl, altDescription, downloadLocation

  viewmodel/
    CameraViewModel.kt         AndroidViewModel. Owns _uiState (MutableStateFlow<CameraUiState>),
                               GemmaEngine, VoiceSystem. Exposes onCaptureButtonTapped,
                               onAIButtonTapped (stub), onModeToggled, onFilterSelected (stub),
                               onInspoPhotoSelected (stub), onFlashToggled, onMicButtonToggled,
                               onManualShutter (stub), onCountdownComplete (stub).
                               IMPORTANT: it currently runs `startFakeCoachLoop()` that cycles
                               through 6 canned coaching strings every 4s — placeholder content
                               for the bubble until Phase 2 wires real Gemma output.

  ui/
    theme/
      Colors.kt, Theme.kt      Dark theme constants (see §8).
    screens/
      LoadingScreen.kt         "Vantage / AI Camera Co-Pilot" + progress bar
                               (driven by uiState.modelLoadProgress)
      CameraScreen.kt          MAIN screen. Box-stacked layers (back→front):
                                 1. AndroidView(PreviewView) — CameraX preview (BACK camera,
                                    FILL_CENTER scaleType, ImageCapture bound).
                                 2. Flash IconButton, top-left, .statusBarsPadding().
                                 3. CoachingOverlay — coaching bubble pinned to BottomCenter
                                    with `padding(bottom = BottomControlsHeight)`.
                                 4. ChatBubbleOverlay — last 3 AI chat messages, BottomStart,
                                    `padding(bottom = BottomControlsHeight + 160.dp)`.
                                 5. Bottom Column (.navigationBarsPadding(), Arrangement.Bottom):
                                      ModeToggle → 24dp spacer → CaptureButton → 32dp spacer.
                                 6. MicButton, BottomEnd, padding(24+80dp).
                               IMPORTANT: the ModeToggle and CaptureButton composables actually
                               live INSIDE CameraScreen.kt as private fns (not in their own files).
                               Constant `BottomControlsHeight = 172.dp` controls the
                               coaching bubble vertical position — recently tightened from 210.dp.
    components/
      CoachingOverlay.kt       Picks a Direction from suggestion text (tilt up/down, rotate
                               left/right, zoom in/out, move left/right) and renders an amber
                               64sp glyph at the matching screen edge (TopCenter, CenterStart,
                               CenterEnd). DOWN intentionally has no edge arrow — the bubble
                               at BottomCenter is enough. Internal `windowInsetsPadding(systemBars)`
                               COMPOUNDS with the parent's `padding(bottom = BottomControlsHeight)`.
      ChatBubbleList.kt
      MicButton.kt             hold-to-listen mic button
      ModeToggle.kt            EMPTY FILE (0 bytes). The real composable is private to
                               CameraScreen.kt. The empty file is harmless.
      BottomControls.kt, FilterStrip.kt, InspoRow.kt, OverlayGuides.kt, Viewfinder.kt
                               (scaffolding for components not yet wired)
```

**Theme constants (`ui/theme/Colors.kt`, used everywhere instead of
hardcoded hex):**

| Name | Hex | Purpose |
|---|---|---|
| `Black` | `#000000` | App background |
| `AIAccentBlue` | `#2979FF` | AI-related elements, active filter chip |
| `CoachingAmber` | `#FFB300` | Coaching arrows / highlights |
| `ReadyGreen` | `#4CAF50` | "Ready to capture" border glow, capture flash |
| `ChatBubbleBg` | `#1A1A1A` @ 88% alpha | Chat bubbles |
| `White` | `#FFFFFF` | Text (80% alpha for secondary) |

Typography: Roboto. Chat 14sp, coaching overlays 16sp semi-bold, filter
labels 12sp.

---

## 6. Status snapshot (what's done vs stubbed)

| Phase | Status | Reality |
|---|---|---|
| 0 — Scaffolding | ✅ Done | All contracts, models, theme, gradle, manifest, Application/Activity in place. |
| 1A — Camera + Filters | ⏳ Mostly stubbed | CameraScreen uses CameraX `PreviewView` + `ImageCapture` for live preview and stills. The full Camera2 manual control + 12 GLSL filter pipeline (`camera/`, `filters/`) is **not yet written** — those directories don't exist in the tree. Filter strip UI, manual zoom/ISO/WB/focus/EV control, and filter-baked-in capture are TODO. |
| 1B — AI Engine | ✅ Done on device | `GemmaEngine` runs on the NPU. Logcat shows `MainExecutorSettings: backend: NPU` and `Gemma engine ready`. `describeImage()` works end-to-end. Higher-level coaching session (`CoachingSession`, `CameraToolSet`, `ToolCallParser`, `SystemPrompts`) is NOT yet written. |
| 1C — UI + Voice + Unsplash | 🟡 Partial | `CameraScreen`, `CoachingOverlay`, `MicButton`, `VoiceSystem` (Android TTS+STT only, no ElevenLabs yet), `UnsplashClientImpl` exist. `InspoRow`/`FilterStrip`/`OverlayGuides` are scaffolds; not yet placed in `CameraScreen`. ViewModel is **not** wired to Gemma — coaching bubble is fed by `startFakeCoachLoop()`. |
| 2 — Integration | ⏳ Not started | This is where the periodic frame-analysis coroutine, `applyCoachingResult()` dispatcher, voice-command routing, inspo selection, and 3-2-1 countdown live. See §10. |
| 3 — Polish + submit | ⏳ Not started | README, Devpost write-up, demo recording. |

**Things that look done but aren't:**

- `VantageApplication.onCreate()` is empty — model load is not yet started
  on app launch.
- `LoadingScreen` exists but is not currently in `MainActivity`'s flow —
  `MainActivity` jumps straight to `CameraScreen` after permissions.
- `CameraViewModel.onAIButtonTapped()`, `onFilterSelected()`,
  `onInspoPhotoSelected()`, `onManualShutter()`, `onCountdownComplete()`
  are **all empty `{}` stubs**.
- `ElevenLabs` is documented as primary TTS but no client code exists yet.
- `pendingUserActions` in state is the source for the coaching bubble, but
  it's only ever populated by the fake coach loop today.

---

## 7. LiteRT-LM integration (what the code looks like today)

**`GemmaEngine.kt` (current implementation, abridged):**

```kotlin
class GemmaEngine {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        val modelPath = findModelFile(context) ?: run {
            Log.e("Vantage", "No .litertlm model file found on device"); return@withContext
        }
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        // CRITICAL: must be set BEFORE Engine() is called or QNN aborts with SIGABRT.
        Os.setenv("ADSP_LIBRARY_PATH", nativeLibDir, true)
        Os.setenv("LD_LIBRARY_PATH",   nativeLibDir, true)

        val config = EngineConfig(
            modelPath     = modelPath,
            backend       = Backend.NPU(nativeLibDir),  // text → NPU
            visionBackend = Backend.GPU(),              // images → GPU
            audioBackend  = Backend.CPU()               // audio → CPU (required field even if unused)
        )
        engine = Engine(config).also { it.initialize() }
        conversation = engine!!.createConversation()      // currently no-arg, no system prompt yet
    }

    suspend fun describeImage(imagePath: String): String = withContext(Dispatchers.IO) {
        val conv = conversation ?: return@withContext "Engine not ready"
        val msg = Message.user(Contents.of(
            Content.ImageFile(imagePath),
            Content.Text("Describe what you see in this image in 2-3 sentences.")
        ))
        val sb = StringBuilder()
        conv.sendMessageAsync(msg).collect { response ->
            val chunk = response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
            sb.append(chunk)
        }
        sb.toString().ifBlank { "No response" }
    }

    fun close() { conversation?.close(); engine?.close(); conversation = null; engine = null }

    // Search order: getExternalFilesDir(null) → /sdcard/Download → filesDir → /data/local/tmp
    // Prefers any file whose name contains "sm8750"
    private fun findModelFile(context: Context): String? { /* ... */ }
}
```

**Phase 2 will replace `describeImage` with a structured tool-calling
session** based on this pattern (from Spec.md, not yet written):

```kotlin
class CoachingSession(private val engine: Engine) {
    private var conversation: Conversation? = null

    fun start() {
        val config = ConversationConfig(
            systemInstruction = Contents.of(SYSTEM_PROMPT),
            samplerConfig     = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.7),
            tools             = listOf(tool(CameraToolSet()))
        )
        conversation = engine.createConversation(config)
    }

    suspend fun analyzeFrame(framePath: String): Flow<Message> =
        conversation!!.sendMessageAsync(Contents.of(
            Content.ImageFile(framePath),
            Content.Text("Analyze this scene and help me take an amazing photo.")
        ))

    suspend fun handleVoiceCommand(text: String) = conversation!!.sendMessageAsync(text)

    suspend fun matchInspoPhoto(inspoDesc: String, framePath: String) =
        conversation!!.sendMessageAsync(Contents.of(
            Content.ImageFile(framePath),
            Content.Text("I want my photo to match this style: $inspoDesc. " +
                         "Adjust everything to get as close as possible to that look.")
        ))
}
```

**Planned system prompt (Spec §7):**

```
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
```

**Planned tool surface (Spec §7) — three tools annotated `@Tool` /
`@ToolParam` on a `CameraToolSet : ToolSet`:**

- `analyzeScene(sceneDescription, zoom, iso, shutterSpeed, whiteBalance,
  focusDistance, exposureComp, filter, userActions, voiceMessage,
  unsplashQuery, readyToCapture)` — full scene analysis + camera plan +
  Unsplash query + voice line.
- `adjustCamera(zoom, iso, shutterSpeed, whiteBalance, exposureComp,
  filter, voiceMessage, unsplashQuery)` — incremental update from a voice
  command.
- `captureReady(voiceMessage)` — fires the auto-capture countdown.

`ToolCallParser` (to be written) maps these into `CoachingResult` and feeds
the ViewModel.

---

## 8. UI: state model, screen layout, design constants

**`CameraUiState`** (frozen):

```kotlin
data class CameraUiState(
    val isCoachingActive: Boolean = false,
    val appMode: AppMode = AppMode.DO_IT_FOR_ME,
    val currentFilter: FilterType = FilterType.NATURAL,
    val chatMessages: List<ChatMessage> = emptyList(),
    val inspoPhotos: List<UnsplashPhoto> = emptyList(),
    val selectedInspoPhoto: UnsplashPhoto? = null,
    val isListening: Boolean = false,
    val pendingUserActions: List<String> = emptyList(),
    val countdownValue: Int = 0,
    val readyToCapture: Boolean = false,
    val modelLoaded: Boolean = false,
    val modelLoadProgress: Float = 0f,
    val lastCapturedUri: Uri? = null,
    val errorMessage: String? = null,
    val flashMode: FlashMode = FlashMode.OFF
)
```

**Intended screen layout (top-down, dark theme, full-bleed viewfinder):**

```
+------------------------------------------+
| [Status Bar]                             |
+------------------------------------------+
| [Unsplash Inspo Row — only when active]  |
|  | img1 | img2 | img3 | img4 | img5 |    |
+------------------------------------------+
|                                          |
|              VIEWFINDER                  |
|   (camera preview with filter applied)   |
|                                          |
|    [Chat Bubble: "Try angling down..."]  |
|         [Directional Arrow Overlay]      |
|         [Rule-of-Thirds Grid, subtle]    |
|                                          |
+------------------------------------------+
| [Filter Strip — horizontal, 12 chips]    |
+------------------------------------------+
| [Bottom Controls]                        |
|   [Gallery] [Shutter] [AI Sparkle]       |
|   [Mode Toggle: "Do It" | "Coach Me"]    |
|   [Mic Button]                           |
+------------------------------------------+
```

**Current screen layout (what's actually rendered today):** PreviewView →
top-left flash IconButton → CoachingOverlay (bubble above ModeToggle) →
ChatBubbleOverlay → bottom Column { ModeToggle, 24dp, CaptureButton, 32dp }
→ MicButton (BottomEnd). No FilterStrip, no InspoRow, no AI sparkle button,
no rule-of-thirds grid yet.

**UI states (Spec §6):**

| State | Visual |
|---|---|
| Standard camera | Clean viewfinder + filter strip + shutter + gallery + AI sparkle. Mode toggle subtle. |
| AI coaching active | Rule-of-thirds grid visible, chat bubbles appearing, directional arrows on physical-action steps, inspo row slides down from top, shutter button pulses blue, mic button glows. |
| Inspo photo selected | Selected photo PIP'd top-right (~80×80dp). Coaching adjusts to match the reference. Chat: "Matching that style. Let me adjust..." |
| Ready to capture | Viewfinder border glows green. "3… 2… 1…" countdown overlay + voice. Auto-capture fires. |
| Photo captured | Brief full-screen flash. Photo slides into gallery thumbnail. "Got it! That turned out great." Returns to standard. |

**Animations:** all transitions use Compose animations (fade, slide, scale).
Filter strip transitions, chat bubble slide-in, inspo row slide-down, capture
flash. Subtle haptics on shutter / mode toggle / filter change.

**Why `BottomControlsHeight = 172.dp`?** It's the bottom padding applied to
the `CoachingOverlay`'s `fillMaxSize` modifier. Counting the bottom Column
from the bottom up: 32dp spacer + 72dp CaptureButton + 24dp spacer + ~42dp
ModeToggle ≈ 170dp. With CoachingOverlay's internal
`windowInsetsPadding(systemBars)` adding the nav-bar inset and the bubble's
own 8dp bottom padding, the bubble lands ~5–10dp above the top of the
ModeToggle. Any change to ModeToggle/CaptureButton sizing or to that
internal `windowInsetsPadding` needs this re-tuned.

---

## 9. The 12 filters (Spec §9 — math, not yet implemented)

Each filter is a GLSL fragment shader sampling
`GL_TEXTURE_EXTERNAL_OES`. Filter switching = recompile + relink fragment
shader, applied on the GL thread via `queueEvent {}`. The filter is also
baked into captured photos via `ColorMatrixColorFilter` after
`capturePhoto()`.

| Filter | Approach |
|---|---|
| Natural | Pass-through |
| Warm / Amber | `r *= 1.15`, `g *= 1.05`, `b *= 0.85` |
| Cool | Reduce R, boost B |
| Noir | Luminance + S-curve contrast (`lum*lum*(3-2*lum)`) |
| Vivid | Increase saturation in HSV |
| Dramatic | S-curve contrast + slight desaturation |
| Silvertone | Luminance + slight warm tint |
| Cinematic | Teal in shadows `(0, 0.1, 0.15)`, orange in highlights `(0.15, 0.08, 0)`, mixed by luminance |
| Vintage | Lift black point + warm color shift |
| Muted | Reduce saturation + lift midtones |
| Fade | Lift shadows + reduce contrast |
| Mono | `dot(rgb, vec3(0.299, 0.587, 0.114))` |

Vertex shader is shared:

```glsl
attribute vec4 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;
void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }
```

---

## 10. Phase 2 wiring (the integration plan)

All wiring lives in `CameraViewModel`. Other modules stay essentially
unchanged.

**1. Model loading on app start (`VantageApplication.onCreate()`):** call
`ModelPathHelper.findModel()` → launch a coroutine to call
`GemmaEngine.initialize()`, animating `uiState.modelLoadProgress` from 0f
to 1f over ~10s, then setting `modelLoaded = true` so `MainActivity` /
`NavHost` transitions from `LoadingScreen` to `CameraScreen`.

**2. AI button — the periodic analysis loop:**

```kotlin
fun onAIButtonTapped() {
    if (uiState.value.isCoachingActive) {
        analysisJob?.cancel()
        _uiState.update { it.copy(isCoachingActive = false) }
        return
    }
    _uiState.update { it.copy(isCoachingActive = true) }
    analysisJob = viewModelScope.launch {
        while (isActive) {
            cameraEngine.capturePreviewFrame { framePath ->
                launch {
                    val result = aiCoach.analyzeFrame(framePath)
                    applyCoachingResult(result)
                }
            }
            delay(3000)
        }
    }
}
```

**3. The central dispatcher:**

```kotlin
private fun applyCoachingResult(result: CoachingResult) {
    _uiState.update { it.copy(
        chatMessages       = it.chatMessages + ChatMessage(result.voiceMessage, isFromUser = false),
        pendingUserActions = result.userActions,
        currentFilter      = result.filter,
        readyToCapture     = result.readyToCapture
    )}
    if (uiState.value.appMode == AppMode.DO_IT_FOR_ME) {
        cameraEngine.applySettings(result.cameraSettings)
    }
    filterEngine.setFilter(result.filter)
    if (result.voiceMessage.isNotBlank()) voiceSystem.speak(result.voiceMessage)
    if (result.unsplashQuery.isNotBlank()) fetchInspoPhotos(result.unsplashQuery)
    if (result.readyToCapture) startCountdown()
}
```

**4. Voice command (mic):**

```kotlin
fun onMicButtonHeld() {
    _uiState.update { it.copy(isListening = true) }
    voiceSystem.startListening(
        onResult = { text ->
            _uiState.update { it.copy(
                isListening = false,
                chatMessages = it.chatMessages + ChatMessage(text, isFromUser = true)
            )}
            viewModelScope.launch {
                applyCoachingResult(aiCoach.sendVoiceCommand(text))
            }
        },
        onError = {
            _uiState.update { it.copy(isListening = false) }
            voiceSystem.speak("Sorry, didn't catch that. Try again.")
        }
    )
}
```

**5. Inspo selection (`onInspoPhotoSelected`):** capture preview frame +
call `aiCoach.matchInspoStyle(photo.altDescription, framePath)` → feed
result into `applyCoachingResult`.

**6. Auto-capture (`startCountdown` + `onCountdownComplete`):**

- `startCountdown()` — coroutine counts 3 → 2 → 1, updating
  `uiState.countdownValue` each second.
- `onCountdownComplete()` — cancel analysis loop, call
  `cameraEngine.capturePhoto { uri → ... }`, play shutter sound, speak
  "Got it! That turned out great.", reset coaching state.

**Done when:** end-to-end works on device — tap AI → camera analyzes → voice
speaks → settings change → Unsplash loads; voice command "make it warmer"
changes filter and gets a verbal response; tapping an inspo photo redirects
coaching; auto-capture fires when Gemma returns `readyToCapture = true`;
manual shutter still works; "Coach Me" mode shows instructions instead of
auto-applying; analysis loop cancels on ViewModel cleared (no leaks).

---

## 11. Repo / branch state

GitHub: **`ParthPatel00/Vantage`**. Default branch `main`.

Branches as of this handoff:

| Branch | Status |
|---|---|
| `main` | Latest. Contains compile fixes + 172dp coaching bubble tweak. Pushed to origin. |
| `Adam-Inspo` | Adam's working branch. Currently identical to `main`. Tracks `origin/Adam-Inspo`. (Was `Adam2-UI`; renamed and old name deleted both locally and on origin.) |
| `phase/1c-ui-voice-unsplash`, `Bubble-Adam` | Older branches still on origin. |

**Recent compile-fix commits worth knowing about** — both were merge
artifacts that broke `main` cleanly off a fresh checkout. If `main` ever
won't compile, these are the first places to look:

1. `CameraScreen.kt` was missing
   `import androidx.compose.foundation.layout.statusBarsPadding`. The flash
   `IconButton` at the top-left calls `.statusBarsPadding()`.
2. `CameraViewModel.kt` had **two** `override fun onCleared()` declarations
   (one shutting down `voiceSystem`, one cancelling `fakeCoachJob` and
   closing `gemmaEngine`). They are now merged into one.

---

## 12. Local development environment (Adam's machine)

- **OS:** Windows 11.
- **Repo path:** `C:\Users\adam_\OneDrive\Documents\GitHub\Vantage`.
- **Shell:** bash (Unix syntax — `/dev/null`, forward slashes). PowerShell is
  available but not the default.
- **Android SDK:** `C:\Users\adam_\AppData\Local\Android\Sdk` (set via
  `local.properties`). Tools at
  `C:/Users/adam_/AppData/Local/Android/Sdk/platform-tools/` and `.../emulator/`.
- **AVD:** one AVD called **`Vantage_Test`**. Boot with:
  ```bash
  "C:/Users/adam_/AppData/Local/Android/Sdk/emulator/emulator.exe" -avd Vantage_Test
  ```
  Then `adb wait-for-device` + poll `getprop sys.boot_completed`.
- **No physical S25 Ultra is connected on this machine.** Implications:
  - The emulator is x86_64 / no Hexagon — `Backend.NPU(...)` will fail at
    init. Real end-to-end testing (NPU + the pre-pushed model) requires
    the team's S25 Ultra demo device.
  - The model file is **not** on the emulator. `findModelFile()` returns
    `null` and logs `No .litertlm model file found on device`. The rest of
    the app still runs — UI works, coaching bubble is fed by the fake loop.
- **Build / install / launch loop** (works on this machine):
  ```bash
  ./gradlew :app:assembleDebug
  "C:/Users/adam_/AppData/Local/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
  "C:/Users/adam_/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am force-stop com.vantage
  "C:/Users/adam_/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell monkey -p com.vantage -c android.intent.category.LAUNCHER 1
  ```
  First-time installs also need:
  ```bash
  adb shell pm grant com.vantage android.permission.CAMERA
  adb shell pm grant com.vantage android.permission.RECORD_AUDIO
  ```
- **Java:** 21 (Temurin) — required by the team toolchain.

---

## 13. Setup pointers for a new dev (condensed Developer_guide.md)

Full version is in `documentation/Developer_guide.md`. Condensed:

1. **Git, Java 21, Android Studio.** Check SDK Platforms 36 (compileSdk) and
   31 (minSdk for NPU). Install NDK side-by-side `27.0.12077973`. Add
   `platform-tools` to PATH so `adb` works.
2. **S25 Ultra:** enable Developer options + USB debugging. `adb devices`
   should show it as `device` (not `unauthorized`). Samsung USB driver may
   be needed on Windows.
3. **Clone + `local.properties`:** include `sdk.dir`,
   `UNSPLASH_ACCESS_KEY`, `ELEVENLABS_API_KEY`.
4. **Copy NPU `.so` files** from
   `litert-samples/compiled_model_api/qualcomm/llm_chatbot_npu/app/src/main/jniLibs/arm64-v8a/`
   into `app/src/main/jniLibs/arm64-v8a/` (see §4).
5. **Pull the SM8750 model** from
   `litert-community/gemma-4-E2B-it-litert-lm` on HuggingFace
   (`gemma-4-E2B-it_qualcomm_sm8750.litertlm`, ~2.8 GB) and `adb push` to
   `/sdcard/Android/data/com.vantage/files/`.
6. **Unsplash key:** create a "demo" application at
   <https://unsplash.com/oauth/applications>, copy the **Access Key** (not
   the Secret) into `local.properties`. 50 req/hr is plenty for the demo.
7. **First build:** `./gradlew assembleDebug` then `installDebug`. Should
   land on the loading screen on real hardware (model isn't auto-loaded
   yet — see §6).

**Verification checklist:**

```bash
java -version                    # 21.x.x
adb version                      # any recent version
adb devices                      # S25 Ultra shows as "device"
ls app/src/main/jniLibs/arm64-v8a/libLiteRtDispatch_Qualcomm.so      # exists
ls app/src/main/jniLibs/arm64-v8a/libGemmaModelConstraintProvider.so # exists
./gradlew assembleDebug                                              # BUILD SUCCESSFUL
adb shell ls /sdcard/Android/data/com.vantage/files/gemma-4-E2B-it_qualcomm_sm8750.litertlm
```

---

## 14. Demo script (Spec §15, abridged)

The pitch is a four-act demo on the S25 Ultra. Useful so an assistant knows
what "done enough" looks like.

1. **Act 1 — Standard photo.** Open app, point at a coffee cup, tap shutter.
   Photo saves. Show the filter strip; swipe through 2–3 filters live.
2. **Act 2 — AI coaches a food shot.** Tap AI sparkle. Inspo row slides
   down with 6 latte photos. Voice: "Ooh, latte shot. Bumping up the warmth
   and angling for that overhead look." Camera zooms slightly; warm filter
   applies. Amber arrow says "tilt down." User tilts. Border glows green.
   "Perfect, holding still… 3… 2… 1…" Auto-captures. "Got it! That turned
   out great."
3. **Act 3 — Voice command.** Hold mic. "Make it moodier." STT transcript
   shows in a right-side bubble. Filter swaps to Cinematic. Voice: "Going
   moody — teal shadows, orange highlights." New darker inspo set loads.
4. **Act 4 — Match an inspo photo.** Tap one of the moodier inspo
   thumbnails. Selected photo PIPs top-right. "Matching that style. Pulling
   the exposure down half a stop and tightening the crop." Settings shift,
   border glows green, auto-capture fires.

If voice or networking fails, fall back to chat bubbles + offline filters
+ manual shutter — the on-device LLM is still the headline.

---

## 15. Hard rules / things NOT to do

- **Don't push to `origin/main`** without explicit confirmation — it's
  shared with the team.
- **Don't force-push, hard-reset, or rewrite history** on any branch with
  an upstream.
- **Don't add new dependencies** — versions are pinned in
  `gradle/libs.versions.toml` to AGP 9.x / Compose BOM 2026.04.01 /
  Kotlin 2.3.0. Adding a new lib mid-build risks a chain of upgrades and
  breaks the Phase 0 contract.
- **Don't modify `contracts/` or `models/`** — frozen post-Phase-0; every
  other phase depends on the exact shapes.
- **Don't skip git hooks (`--no-verify`) or signing.**
- **Don't commit secrets.** `local.properties` is git-ignored; keep the
  Unsplash and ElevenLabs keys there. Don't paste them into source.
- **Don't commit the `.litertlm` model.** Git-ignored (`*.litertlm`,
  `models/`). Each dev downloads it locally.
- **Don't use the Qualcomm QAIRT SDK `.so` files** in `jniLibs/`. They are
  QNN 1.6.0 and crash with native SIGABRT under LiteRT-LM. Always use the
  set from the `litert-samples` repo.
- **Don't rely on the emulator for AI testing** — no NPU, no model. Use
  the S25 Ultra demo device for anything past plain UI.

---

## 16. Quick reference: things that are "already correct"

- AGP 9.2.0 bundles Kotlin — no separate `org.jetbrains.kotlin.android`
  plugin. Compose compiler is a Gradle plugin (`org.jetbrains.kotlin.plugin.compose`),
  not a `composeOptions` block.
- `BuildConfig.UNSPLASH_ACCESS_KEY` and `BuildConfig.ELEVENLABS_API_KEY`
  are already wired via `buildConfigField` in `app/build.gradle.kts`.
- `packaging { jniLibs { useLegacyPackaging = true } }` is set so QNN can
  `dlopen` `.so` files off disk.
- `ndk { abiFilters += "arm64-v8a" }` is set, so the APK doesn't try to
  ship x86 / armeabi variants of the QNN libs.

---

## 17. Quick test checklist after any change

1. `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`.
2. `adb install -r app/build/outputs/apk/debug/app-debug.apk` → `Success`.
3. App launches, lands on `CameraScreen`. PreviewView is visible (or asks
   for permissions on first run).
4. Coaching bubble cycles through "Tilt the camera up a little" → "Tilt the
   camera down a bit" → "Move slightly to the left" → "Pan a touch to the
   right" → "Hold still" → "Got it — that turned out great" every 4s,
   sitting just above the **Do it for me / Coach me** toggle.
5. Tapping the toggle visibly switches the highlighted chip; tapping the
   flash icon top-left cycles OFF → ON → AUTO.
6. (On real device only) Logcat shows
   `Found model at: /storage/emulated/0/Android/data/com.vantage/files/gemma-4-E2B-it_qualcomm_sm8750.litertlm`
   and `MainExecutorSettings: backend: NPU` and `Gemma engine ready`.

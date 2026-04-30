# Vantage — Development Phases

> Read Spec.md first. This doc is the execution plan for 4 devs over ~25 hours.

---

## DAG

```
Phase 0 — Scaffolding
        |
        ├── Phase 1A — Camera + Filters
        ├── Phase 1B — AI Engine
        └── Phase 1C — UI + Voice + Unsplash
                        |
                 Phase 2 — Integration
                        |
                 Phase 3 — Polish + Submit
```

Phases 1A, 1B, and 1C have zero dependency on each other. They only need Phase 0 merged. Phase 2 integrates everything once all three are merged.

---

## Branching Rules

- Phase 0: push directly to `main`
- Phases 1A/1B/1C: branch off `main`, open a PR when done, lead merges
- Phase 2: branch `integration` off `main`, all devs commit to it, merge to `main` when stable
- Phase 3: everyone commits to `main` directly

Post in the team channel when you open a PR. Post again when you pick up a new task.

---

## Timeline

```
12:00 PM   Phase 0 starts
 2:00 PM   Phase 0 merged → 1A, 1B, 1C start in parallel
 8:00 PM   1A, 1B, 1C target merge → Phase 2 starts
12:00 AM   Phase 2 merged → Phase 3 starts
 9:00 AM   Feature freeze
 1:00 PM   Submit
```

---

## Phase 0 — Scaffolding ✅ COMPLETE

**Status:** Done. All code, interfaces, models, Gradle config, manifest, and theme are in the repo.

The next developer only needs to set up their machine and clone. No code changes required for Phase 0.

### What's already in the repo

```
app/src/main/java/com/vantage/
  contracts/              ← 5 frozen interfaces: ICameraEngine, IFilterEngine, IAICoach, IVoiceSystem, IUnsplashClient
  models/                 ← 7 frozen data classes: CameraSettings, CoachingResult, FilterType, AppMode, ChatMessage, UnsplashPhoto, CameraUiState
  viewmodel/              ← CameraViewModel stub with all action function stubs
  ui/theme/               ← Theme.kt (dark theme) + Colors.kt (palette)
  ui/screens/             ← LoadingScreen.kt + CameraScreen.kt (placeholder)
  VantageApplication.kt   ← Application class (engine init stub)
  MainActivity.kt         ← Single activity, NavHost with loading → camera

```

Build config: AGP 9.2.0, Gradle 9.4.1, Kotlin 2.3.0, Compose BOM 2026.04.01, all Phase 1A/1B/1C dependencies pre-declared in `gradle/libs.versions.toml`. Namespace is `com.vantage`.

### Developer machine setup

Every dev follows these steps on their own machine before touching any code. Takes about 20–30 minutes on a fresh machine. Do this before cloning the repo.

---

#### Step 1 — Install Git

**Mac:**
```bash
# Install Homebrew first if you don't have it
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Then install Git
brew install git
```

**Windows:**
Download and install Git from https://git-scm.com/download/win. During install, select "Git from the command line and also from 3rd-party software" and "Use Windows' default console window". After install, open PowerShell and verify:
```powershell
git --version
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt-get update && sudo apt-get install -y git
```

---

#### Step 2 — Install Java 21

Everyone should use **Java 21** so the team is on the same version.

**Mac:**
```bash
brew install --cask temurin@21

# Set JAVA_HOME and update PATH — add these to ~/.zshrc:
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# If you have an older Java (e.g. openjdk@11) linked via Homebrew, remove it from PATH.
# Check ~/.zshrc for lines like: export PATH="/opt/homebrew/opt/openjdk@11/bin:$PATH"
# and delete them, otherwise the old version takes priority.

# Verify
java -version   # should say 21.x.x
```

**Windows:**
1. Download Adoptium Temurin 21 from https://adoptium.net/temurin/releases/?version=21
2. Run the installer. Check "Set JAVA_HOME variable" during install.
3. Open a new PowerShell window and verify:
```powershell
java -version   # should say 21.x.x
echo $env:JAVA_HOME   # should point to your JDK 21 folder
```

**Linux:**
```bash
sudo apt-get install -y openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' >> ~/.bashrc
source ~/.bashrc
java -version   # should say 21.x.x
```

---

#### Step 3 — Install Android Studio

Android Studio bundles the Android SDK, ADB, emulator, and Gradle. Install it even if you plan to use a different editor for code — you need the SDK tools it provides.

**All platforms:**
1. Download Android Studio from https://developer.android.com/studio
2. Run the installer and follow the setup wizard
3. On first launch, let the wizard download the default SDK components (click "Next" through everything)
4. Once open, go to **Settings → Appearance & Behavior → System Settings → Android SDK**
5. Under **SDK Platforms**, check:
   - Android 16 (API 36) — latest stable, used as compileSdk
   - Android 12 (API 31) — minSdk for NPU support
6. Under **SDK Tools**, check:
   - Android SDK Build-Tools (latest)
   - Android SDK Platform-Tools
   - NDK (Side by side) — select version 27.0.12077973
7. Click **Apply** and let everything download (~3–5 GB total)

**Add ADB to your PATH so you can use it from the terminal:**

Mac/Linux — add to `~/.zshrc` or `~/.bashrc`:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk          # Mac
# export ANDROID_HOME=$HOME/Android/Sdk               # Linux
export PATH=$ANDROID_HOME/platform-tools:$PATH
```
Then run `source ~/.zshrc` (or restart terminal).

Windows — add to your system environment variables:
- `ANDROID_HOME` = `C:\Users\YourName\AppData\Local\Android\Sdk`
- Add `%ANDROID_HOME%\platform-tools` to your `Path` variable

Verify ADB works:
```bash
adb version   # should print Android Debug Bridge version 1.x.x
```

---

#### Step 4 — Enable USB Debugging on the Samsung Galaxy S25 Ultra

Do this on the physical device, not on your computer.

1. Open **Settings → About phone → Software information**
2. Tap **Build number** 7 times until you see "Developer mode has been turned on"
3. Go back to **Settings → Developer options**
4. Enable **USB debugging**
5. Enable **Stay awake** (keeps screen on while charging — useful during development)
6. Plug the phone into your computer with a USB-C cable
7. On the phone, tap **Allow** when the "Allow USB debugging?" dialog appears
8. On your computer, verify the device is recognized:
```bash
adb devices
# Should show something like:
# List of devices attached
# R3CN1234ABC    device
```

If the device shows as `unauthorized`, unplug and replug, then tap Allow on the phone again.

> **Samsung-specific:** If ADB doesn't detect the device on Windows, you may need the Samsung USB driver. Download from https://developer.samsung.com/mobile/android-usb-driver.html

---

#### Step 5 — Clone the repo and configure local settings

```bash
git clone <your-repo-url>
cd Vantage
```

The project already has a `local.properties` with `sdk.dir` set. You need to add your API keys. Open `local.properties` and add:

```
UNSPLASH_ACCESS_KEY=your_key_here
ELEVENLABS_API_KEY=your_key_here
```

If `local.properties` doesn't exist (fresh clone), create it:

**Mac/Linux:**
```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties        # Mac
# echo "sdk.dir=$HOME/Android/Sdk" > local.properties              # Linux
echo "UNSPLASH_ACCESS_KEY=REPLACE_ME" >> local.properties
echo "ELEVENLABS_API_KEY=REPLACE_ME" >> local.properties
```

**Windows:**
```
sdk.dir=C:\Users\YourName\AppData\Local\Android\Sdk
UNSPLASH_ACCESS_KEY=REPLACE_ME
ELEVENLABS_API_KEY=REPLACE_ME
```

Then replace `REPLACE_ME` with your actual keys (see Steps 7 and the ElevenLabs section in Spec.md).

---

#### Step 6 — Copy NPU native libraries into the project

Seven `.so` files are required for the Qualcomm NPU backend. **They must all come from the `litert-samples` repo** — do not use the Qualcomm QAIRT SDK versions (those are QNN 1.6.0 which is incompatible; the dispatch library requires ≥ 1.8.0).

```bash
# Sparse-clone just the jniLibs folder from litert-samples
git clone --depth=1 --filter=blob:none --sparse \
  https://github.com/google-ai-edge/litert-samples.git /tmp/litert-samples
cd /tmp/litert-samples
git sparse-checkout set compiled_model_api/qualcomm/llm_chatbot_npu/app/src/main/jniLibs/arm64-v8a

# Copy all .so files into the project
cp compiled_model_api/qualcomm/llm_chatbot_npu/app/src/main/jniLibs/arm64-v8a/*.so \
   /path/to/Vantage/app/src/main/jniLibs/arm64-v8a/
```

The required files are:
- `libLiteRtDispatch_Qualcomm.so` — NPU dispatch bridge (must be from litert-samples)
- `libGemmaModelConstraintProvider.so` — Gemma constraint loader
- `libQnnHtp.so`, `libQnnHtpV79Skel.so`, `libQnnHtpV79Stub.so`, `libQnnSystem.so` — QNN HTP runtime (must be ≥ 1.8.0, from litert-samples)

Verify all six are present:
```bash
ls app/src/main/jniLibs/arm64-v8a/
# Should show all 6 (plus libQnnHtpV79CalculatorStub.so if present — that's fine)
```

---

#### Step 7 — Download the Gemma 4 E2B model and push to device

The model file is ~2.8 GB and git-ignored, so each dev needs to download it once. Only one person needs to push it to the shared demo device.

**Important:** Use the **SM8750 (Snapdragon 8 Elite)** variant for the S25 Ultra. This is the NPU-optimized version.

1. Install the HuggingFace CLI if you don't have it:
```bash
pip install huggingface-hub
```

2. Log in (free account required, sign up at https://huggingface.co/join):
```bash
huggingface-cli login
```

3. Accept the model license at https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

4. Download the SM8750-specific model:
```bash
huggingface-cli download litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it_qualcomm_sm8750.litertlm --local-dir models
```

5. Push it to the device:
```bash
# Make sure your S25 Ultra is plugged in and USB debugging is on
adb shell mkdir -p /sdcard/Android/data/com.vantage/files/
adb push models/gemma-4-E2B-it_qualcomm_sm8750.litertlm /sdcard/Android/data/com.vantage/files/gemma-4-E2B-it_qualcomm_sm8750.litertlm

# This will take a few minutes. Verify it landed:
adb shell ls -lh /sdcard/Android/data/com.vantage/files/gemma-4-E2B-it_qualcomm_sm8750.litertlm
```

> **Why this path?** Android 13+ (scoped storage) blocks apps from reading arbitrary files in `/sdcard/Download`. The app-specific external directory `/sdcard/Android/data/com.vantage/files/` is always readable by the app without any extra permissions, and writable by ADB.

> **Do NOT download `gemma-4-E2B-it.litertlm`** (the generic variant). It runs on GPU only and is significantly slower than the SM8750 variant which uses the NPU.

---

#### Step 8 — Get your Unsplash API key

1. Go to https://unsplash.com/join and create a free account
2. Go to https://unsplash.com/oauth/applications
3. Click **New Application**
4. Accept the API Use and Guidelines
5. Name it `Vantage`, add a short description
6. Scroll down and copy the **Access Key** (not the Secret Key)
7. Open `local.properties` and replace `REPLACE_ME` with your Access Key:
```
UNSPLASH_ACCESS_KEY=your_actual_key_here
```

The free tier allows 50 requests/hour which is plenty for development and the demo.

---

#### Step 9 — First build and install

**Mac/Linux:**
```bash
./gradlew assembleDebug          # builds the APK (~2 min first time, downloads deps)
./gradlew installDebug           # installs on the connected S25 Ultra
```

**Windows:**
```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

The app should launch on the S25 Ultra. You'll see the "Vantage / AI Camera Co-Pilot" loading screen. The model isn't wired up yet, so it stays on the loading screen. That's expected. Phase 1B wires up the model loading.

---

#### Step 10 — Open in Android Studio (optional but recommended)

1. Open Android Studio
2. Click **Open** and select the `Vantage` folder
3. Wait for Gradle sync to complete (bottom progress bar)
4. In the top toolbar, select the **app** run configuration and your device from the device dropdown
5. Click the green **Run** button or press `Shift+F10`

Alternatively, VS Code with the **Kotlin** and **Android** extensions works fine for editing. But use Android Studio's Logcat for debugging — it's much better than anything else.

---

#### Setup verification checklist

Run through this before picking up your phase. If anything fails, fix it before writing code.

```bash
java -version                    # must say 21.x.x
adb version                      # must print a version number
adb devices                      # must show your S25 Ultra as "device" (not "unauthorized")
ls app/src/main/jniLibs/arm64-v8a/libLiteRtDispatch_Qualcomm.so   # must exist
ls app/src/main/jniLibs/arm64-v8a/libGemmaModelConstraintProvider.so   # must exist
./gradlew assembleDebug          # must succeed with BUILD SUCCESSFUL
./gradlew installDebug           # must install on device without error
adb shell ls /sdcard/Android/data/com.vantage/files/gemma-4-E2B-it_qualcomm_sm8750.litertlm   # must find the file
```

All eight green? You're ready. Pick up your phase.

---

### Automated setup script (Mac/Linux only)

If you're on Mac or Linux and want to skip the manual steps above, Dev A provides a `setup.sh` that automates Steps 2–5 and Step 8. Windows devs follow the manual steps above — PowerShell automation for Android SDK setup is fragile and not worth the risk on hackathon timelines.

```bash
#!/bin/bash
# Vantage automated setup — Mac and Linux only
# Run AFTER completing Step 1 (Git) and Step 3 (Android Studio) manually
# Usage: bash setup.sh

set -e
echo "=== Vantage Setup ==="

# ── Java 17 check ────────────────────────────────────────────────────────────
if ! java -version 2>&1 | grep -q "version \"17"; then
  echo "Installing Java 17..."
  if [[ "$OSTYPE" == "darwin"* ]]; then
    brew install --cask temurin@17
  else
    sudo apt-get install -y openjdk-17-jdk
  fi
fi
echo "Java OK: $(java -version 2>&1 | head -1)"

# ── ANDROID_HOME check ───────────────────────────────────────────────────────
if [[ "$OSTYPE" == "darwin"* ]]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
else
  export ANDROID_HOME="$HOME/Android/Sdk"
fi

if [ ! -d "$ANDROID_HOME" ]; then
  echo "ERROR: Android SDK not found at $ANDROID_HOME"
  echo "Install Android Studio first: https://developer.android.com/studio"
  exit 1
fi

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
echo "Android SDK found at $ANDROID_HOME"

# ── SDK components ───────────────────────────────────────────────────────────
echo "Ensuring SDK components are installed..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager   "platforms;android-36"   "platforms;android-31"   "build-tools;36.0.0"   "platform-tools"   "ndk;27.0.12077973"
echo "SDK components OK."

# ── local.properties ─────────────────────────────────────────────────────────
if [ ! -f local.properties ]; then
  echo "sdk.dir=$ANDROID_HOME" > local.properties
  echo "UNSPLASH_ACCESS_KEY=REPLACE_ME" >> local.properties
  echo ""
  echo ">>> ACTION REQUIRED: set your Unsplash key in local.properties"
  echo "    Get one free at: https://unsplash.com/developers"
fi

# ── Gradle first build ───────────────────────────────────────────────────────
echo "Running first build (~2 min, downloads Gradle dependencies)..."
./gradlew assembleDebug
echo "Build OK."

# ── ADB + device check ───────────────────────────────────────────────────────
echo ""
if adb devices | grep -q "device$"; then
  echo "Device connected: $(adb devices | grep device$ | awk '{print $1}')"

  MODEL_PATH="/sdcard/Download/gemma-4-E2B-it_qualcomm_sm8750.litertlm"
  if adb shell ls "$MODEL_PATH" > /dev/null 2>&1; then
    echo "Gemma model found on device."
  else
    echo ""
    echo ">>> ACTION REQUIRED: Gemma model not on device."
    echo "    1. Download from: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
    echo "    2. Push it:       adb push gemma-4-E2B-it_qualcomm_sm8750.litertlm $MODEL_PATH"
  fi
else
  echo "No device connected. Plug in the S25 Ultra, enable USB debugging, and run:"
  echo "  adb devices   (should show your device)"
fi

echo ""
echo "=== Setup complete ==="
echo "Run: ./gradlew installDebug"
echo "Then pick up your phase from Phases.md"
```

### Gradle setup — already configured

All dependencies are pre-declared in `gradle/libs.versions.toml` and `app/build.gradle.kts`. Other devs should never have to touch the Gradle files.

> **Note:** AGP 9.x bundles Kotlin, so no separate `org.jetbrains.kotlin.android` plugin is needed.
> Compose compiler is a Gradle plugin (`org.jetbrains.kotlin.plugin.compose`), not a `composeOptions` block.

**Key versions (from `gradle/libs.versions.toml`):**

| Dependency | Version |
|-----------|---------|
| AGP | 9.2.0 |
| Compose BOM | 2026.04.01 |
| Compose Compiler Plugin | 2.3.0 |
| Activity Compose | 1.13.0 |
| Lifecycle | 2.10.0 |
| Navigation Compose | 2.9.8 |
| Camera2 | 1.6.0 |
| Coil Compose | 2.7.0 |
| OkHttp | 4.12.0 |
| Coroutines | 1.10.2 |

**Build config:** `namespace = "com.vantage"`, `compileSdk = 36`, `minSdk = 31`, `targetSdk = 35`. BuildConfig fields for `UNSPLASH_ACCESS_KEY` and `ELEVENLABS_API_KEY` are read from `local.properties`.

### AndroidManifest — all permissions pre-declared

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<application ...>
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>
    <!-- Required for CPU↔Hexagon DSP communication (NPU backend) -->
    <uses-native-library android:name="libcdsprpc.so" android:required="false"/>
</application>
```

### `.gitignore` — pre-configured

```
# Secrets
local.properties

# Model files (too large for git, ~2.8 GB)
*.litertlm
models/

# Build outputs
/build
app/build
/captures
.gradle

# IDE
.idea/
*.iml
```

### What was delivered (reference)

**All shared interfaces** — these are contracts every other phase implements. Do not change them without a team discussion.

```kotlin
// ICameraEngine.kt
interface ICameraEngine {
    fun openCamera(surfaceTexture: SurfaceTexture, onReady: () -> Unit)
    fun applySettings(settings: CameraSettings)
    fun capturePreviewFrame(onFrame: (filePath: String) -> Unit)
    fun capturePhoto(onSaved: (Uri) -> Unit)
    fun close()
}

// IFilterEngine.kt
interface IFilterEngine {
    fun setFilter(filter: FilterType)
    fun getCurrentFilter(): FilterType
}

// IAICoach.kt
interface IAICoach {
    suspend fun initialize(context: Context, modelPath: String)
    fun isReady(): Boolean
    suspend fun analyzeFrame(framePath: String): CoachingResult
    suspend fun sendVoiceCommand(text: String): CoachingResult
    suspend fun matchInspoStyle(description: String, framePath: String): CoachingResult
    fun close()
}

// IVoiceSystem.kt
interface IVoiceSystem {
    fun speak(message: String, onDone: (() -> Unit)? = null)
    fun startListening(onResult: (String) -> Unit, onError: () -> Unit)
    fun stopListening()
    fun shutdown()
}

// IUnsplashClient.kt
interface IUnsplashClient {
    suspend fun searchPhotos(query: String, perPage: Int = 6): Result<List<UnsplashPhoto>>
}
```

**All shared data models:**

```kotlin
// CameraSettings.kt
data class CameraSettings(
    val zoom: Float? = null,           // 1.0 = no zoom, 0.5–10.0
    val iso: Int? = null,              // 0 = auto, 50–3200
    val shutterSpeed: Int? = null,     // denominator: 250 = 1/250s, 0 = auto
    val whiteBalance: String? = null,  // "auto","daylight","cloudy","tungsten","fluorescent","shade"
    val focusDistance: Float? = null,  // diopters, -1 = autofocus
    val exposureComp: Float? = null    // stops, -3.0 to +3.0
)

// CoachingResult.kt
data class CoachingResult(
    val sceneDescription: String = "",
    val cameraSettings: CameraSettings = CameraSettings(),
    val filter: FilterType = FilterType.NATURAL,
    val userActions: List<String> = emptyList(),
    val voiceMessage: String = "",
    val unsplashQuery: String = "",
    val readyToCapture: Boolean = false
)

// FilterType.kt
enum class FilterType(val displayName: String) {
    NATURAL("Natural"), WARM("Warm"), COOL("Cool"), NOIR("Noir"),
    VIVID("Vivid"), DRAMATIC("Dramatic"), SILVERTONE("Silvertone"),
    CINEMATIC("Cinematic"), VINTAGE("Vintage"), MUTED("Muted"),
    FADE("Fade"), MONO("Mono")
}

// AppMode.kt
enum class AppMode { DO_IT_FOR_ME, COACH_ME }

// ChatMessage.kt
data class ChatMessage(val text: String, val isFromUser: Boolean)

// UnsplashPhoto.kt — already in Spec.md Section 10

// CameraUiState.kt
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
    val errorMessage: String? = null
)
```

**`CameraViewModel`** stub with all state fields and empty function stubs for every user action (`onAIButtonTapped`, `onModeToggled`, `onFilterSelected`, `onInspoPhotoSelected`, `onMicButtonHeld`, `onMicButtonReleased`, `onManualShutter`, `onCountdownComplete`). Phase 2 fills these in.

**`MainActivity.kt`** hosts a `NavHost` with two destinations: `loading` and `camera`. Navigates from `loading` to `camera` when `uiState.modelLoaded` becomes true.

**`LoadingScreen.kt`** shows "Vantage / AI Camera Co-Pilot" with a progress bar. **`CameraScreen.kt`** is a black placeholder, replaced in Phase 1C.

**Gradle, Manifest, theme, .gitignore** are all fully configured. See the Gradle setup section above.

### How to verify Phase 0 on your machine

After cloning and setting up `local.properties`, run:

```bash
./gradlew assembleDebug          # must say BUILD SUCCESSFUL
```

If it passes, you're ready to pick up your phase. No other Phase 0 work is needed.

---

## Phase 1A — Camera + Filters

**Branch:** `phase/1a-camera-filters` | **Time:** ~5–6 hours | **Needs:** Phase 0

### Directory

```
app/src/main/java/com/vantage/
  camera/
    CameraEngine.kt        ← implements ICameraEngine
    PhotoSaver.kt          ← saves captured photo to MediaStore
    FrameCapture.kt        ← grabs a 640×480 preview frame as JPEG for AI
  filters/
    FilterEngine.kt        ← implements IFilterEngine
    FilterRenderer.kt      ← GLSurfaceView.Renderer with GLSL shaders
    Shaders.kt             ← all 12 fragment shader strings
    FilterGLSurfaceView.kt ← custom GLSurfaceView, exposes getCameraSurface()
```

### What to build

**Camera (`CameraEngine.kt`):**
- `openCamera()`: open main rear camera via `CameraManager`, start a repeating preview session targeting the `SurfaceTexture` from the GL view
- `applySettings()`: implement every field in `CameraSettings` — zoom via `CONTROL_ZOOM_RATIO`, ISO via `SENSOR_SENSITIVITY`, shutter via `SENSOR_EXPOSURE_TIME` (1_000_000_000L / denominator), white balance via `CONTROL_AWB_MODE`, focus via `LENS_FOCUS_DISTANCE` (-1 = `AF_MODE_CONTINUOUS_PICTURE`), exposure comp via `CONTROL_AE_EXPOSURE_COMPENSATION`. Call `setRepeatingRequest()` after every change.
- `capturePreviewFrame()`: use an `ImageReader` (640×480 JPEG) as a second session target, trigger one capture, write bytes to `cacheDir/preview_frame.jpg`, call `onFrame(path)`
- `capturePhoto()`: full-resolution `ImageReader` capture, save to `MediaStore`, call `onSaved(uri)`
- `close()`: abort, close session, close device

**Filters (`FilterEngine.kt` + `FilterRenderer.kt`):**
- All 12 GLSL fragment shaders in `Shaders.kt` (see Spec.md Section 9 for the color math for each filter)
- `FilterRenderer` uses `GL_TEXTURE_EXTERNAL_OES` to sample the camera's `SurfaceTexture` directly
- Filter switching: recompile + relink fragment shader program, applied on the GL thread via `queueEvent {}`
- `FilterGLSurfaceView` exposes `getCameraSurface(): Surface` — this is what `CameraEngine.openCamera()` targets
- Applied filter baked into captured photo via Android's `ColorMatrixColorFilter` as a bitmap post-process after `capturePhoto()` saves the raw JPEG

### Done when

- Live viewfinder renders on screen through the GL surface
- Swiping between all 12 filters visibly changes the preview with no flash or lag
- `capturePreviewFrame()` produces a valid JPEG at the returned path (check with file explorer)
- `capturePhoto()` saves a photo with the active filter to Samsung Gallery
- Manual zoom, white balance, and ISO changes are visible on the viewfinder

---

## Phase 1B — AI Engine ✅ COMPLETE

**Branch:** `main` | **Status:** Done. `GemmaEngine` is live on NPU and confirmed working on device.

### What was built

```
app/src/main/java/com/vantage/
  ai/
    GemmaEngine.kt     ← Engine wrapper: init, describeImage, close
```

`GemmaEngine` handles everything: model discovery, ADSP env var setup, `EngineConfig` with NPU/GPU/CPU backends, conversation creation, and streaming inference via `sendMessageAsync`.

**Key implementation details:**

- `ADSP_LIBRARY_PATH` and `LD_LIBRARY_PATH` must be set via `Os.setenv` to `nativeLibraryDir` before `Engine()` is called. Without this, the QNN manager cannot locate `libQnnHtpV79Skel.so` on the Hexagon DSP and aborts with a native SIGABRT.
- `EngineConfig` takes `backend = Backend.NPU(nativeLibDir)`, `visionBackend = Backend.GPU()`, `audioBackend = Backend.CPU()`. All three are required.
- `engine.createConversation()` takes no arguments.
- Messages are sent as `Message.user(Contents.of(Content.ImageFile(...), Content.Text(...)))` via `conversation.sendMessageAsync(msg).collect { ... }`.
- Model search order: `getExternalFilesDir(null)` → `Download` folder → `filesDir` → `/data/local/tmp`. Prefers `*sm8750*` variant if multiple `.litertlm` files are found.

**Confirmed working in Logcat:**
```
D Vantage: Found model at: /storage/emulated/0/Android/data/com.vantage/files/gemma-4-E2B-it_qualcomm_sm8750.litertlm
I native: MainExecutorSettings: backend: NPU
D Vantage: Gemma engine ready
```

### Still to wire up (Phase 2)

- `GemmaEngine.describeImage()` needs to be connected to `CameraViewModel` and the live coaching loop
- `CoachingSession.kt`, `CameraToolSet.kt`, `ToolCallParser.kt`, `SystemPrompts.kt` still need to be written for the full tool-calling coaching flow
- `VantageApplication.onCreate()` needs to trigger `GemmaEngine.initialize()` and update loading progress

---

## Phase 1C — UI + Voice + Unsplash

**Branch:** `phase/1c-ui-voice-unsplash` | **Time:** ~5–6 hours | **Needs:** Phase 0

### Directory

```
app/src/main/java/com/vantage/
  ui/
    screens/
      CameraScreen.kt        ← main camera screen, all composables assembled
      LoadingScreen.kt       ← progress bar during model load
    components/
      Viewfinder.kt          ← AndroidView wrapping FilterGLSurfaceView
      BottomControls.kt      ← shutter, gallery thumb, AI sparkle button
      ModeToggle.kt          ← "Do it for me" / "Coach me" toggle
      MicButton.kt           ← hold-to-speak mic button
      FilterStrip.kt         ← horizontal LazyRow of filter chips
      InspoRow.kt            ← horizontal row of Unsplash thumbnails
      ChatBubbleList.kt      ← animated chat bubbles overlaid on viewfinder
      OverlayGuides.kt       ← rule-of-thirds grid + directional arrows
      CountdownOverlay.kt    ← 3-2-1 countdown with green border glow
  voice/
    VoiceSystem.kt           ← implements IVoiceSystem (TTS + STT)
  api/
    UnsplashClientImpl.kt    ← implements IUnsplashClient
    UnsplashApiService.kt    ← OkHttp + JSON parsing
```

### What to build

**UI (`CameraScreen.kt`)** — layout top to bottom:
1. `InspoRow` — visible only when `isCoachingActive && inspoPhotos.isNotEmpty()`, slides in from top with `AnimatedVisibility`
2. `Viewfinder` (fills remaining space) — layered inside:
   - `OverlayGuides` — rule-of-thirds grid (4 lines at 33%/66%, white 15% alpha) + amber directional arrows parsed from `pendingUserActions`
   - `ChatBubbleList` — last 3 messages max, AI left-aligned dark bubble, user right-aligned blue bubble, each fades after 6 seconds
   - `CountdownOverlay` — large centered countdown number, green viewfinder border glow when `readyToCapture`
   - Selected inspo photo as 56×56dp PIP in top-right corner when `selectedInspoPhoto != null`
3. `FilterStrip` — `LazyRow` of 12 chips, 40×40dp with filter name below, active chip highlighted blue
4. `BottomControls` — gallery thumb (36dp square), shutter button (56dp circle, glows blue when `isCoachingActive`), AI sparkle button (36dp circle, filled blue when active)
5. `ModeToggle` + `MicButton`

**`LoadingScreen.kt`:** app name + progress bar reading `modelLoadProgress` + status text. Navigates to camera when `modelLoaded` flips true.

**Design constants** (from Spec.md Section 6) — all in `Colors.kt`, never hardcoded:
- Background: `#000000`
- AI accent: `#2979FF`
- Coaching accent / arrows: `#FFB300`
- Ready / capture: `#4CAF50`
- Chat bubble bg: `#1A1A1A` at 88% alpha

**Voice (`VoiceSystem.kt`)** implements `IVoiceSystem`:
- TTS: `TextToSpeech`, `Locale.US`, `speechRate = 1.05f`. `speak()` uses `QUEUE_FLUSH` so new messages always interrupt old ones. `onDone` via `UtteranceProgressListener`.
- STT: `SpeechRecognizer`, `LANGUAGE_MODEL_FREE_FORM`. `startListening()` fires `onResult(transcript)` on success, `onError()` on failure. `stopListening()` destroys the recognizer.

**Unsplash (`UnsplashClientImpl.kt`)** implements `IUnsplashClient`:
- OkHttp GET to `https://api.unsplash.com/search/photos?query={query}&per_page={n}&client_id={key}`
- Parse response with `org.json.JSONObject` (no extra dep needed)
- Map to `UnsplashPhoto` — append `?utm_source=vantage&utm_medium=referral` to all profile URLs
- Cache last 10 queries in a `LinkedHashMap` to respect the 50 req/hr limit
- Always return `Result.success` or `Result.failure`, never throw

In Phase 1C, all ViewModel calls are stubs from Phase 0 — UI components call them but nothing happens yet. That's fine. The UI just needs to be structurally complete and correct, ready for Phase 2 to wire it up.

### Done when

- `LoadingScreen` renders, transitions to `CameraScreen` when `modelLoaded` becomes true
- All UI components render with no crash against empty/default `CameraUiState`
- Dark theme is consistent — no white backgrounds anywhere, no hardcoded colors
- `VoiceSystem.speak("Test")` plays audio through the device speaker
- Holding the mic button and speaking returns a transcript in Logcat
- `UnsplashClientImpl.searchPhotos("coffee latte")` returns at least 1 real `UnsplashPhoto` with a valid `thumbUrl`
- Unsplash photos load in `InspoRow` using Coil when `inspoPhotos` is non-empty

---

## Phase 2 — Integration

**Branch:** `integration` | **Time:** ~4 hours | **Needs:** 1A + 1B + 1C all merged

All logic wiring happens in `CameraViewModel.kt`. No other files change significantly in this phase. Other devs are on standby to fix bugs in their own modules as they surface during integration.

### What to wire

**1. Model loading into the app lifecycle**
`VantageApplication.onCreate()` calls `ModelPathHelper.findModel()`, then launches a coroutine to call `VantageEngine.initialize()`, updating `uiState.modelLoadProgress` with a fake animation from 0f to 1f over ~10 seconds, then setting `modelLoaded = true`.

**2. Camera + filter engine initialized in ViewModel**
`CameraViewModel` holds instances of `CameraEngine` and `FilterEngine`. On `onSurfaceReady(surface)`, calls `cameraEngine.openCamera(surface, onReady = { /* start preview */ })`.

**3. `onAIButtonTapped()` — the main loop**
```kotlin
fun onAIButtonTapped() {
    if (uiState.value.isCoachingActive) {
        // Stop
        analysisJob?.cancel()
        uiState.update { it.copy(isCoachingActive = false) }
        return
    }
    // Start
    uiState.update { it.copy(isCoachingActive = true) }
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

**4. `applyCoachingResult(result)` — the central dispatcher**
```kotlin
private fun applyCoachingResult(result: CoachingResult) {
    // Update state
    uiState.update { it.copy(
        chatMessages = it.chatMessages + ChatMessage(result.voiceMessage, false),
        pendingUserActions = result.userActions,
        currentFilter = result.filter,
        readyToCapture = result.readyToCapture
    )}

    // Apply camera settings (DO_IT_FOR_ME only)
    if (uiState.value.appMode == AppMode.DO_IT_FOR_ME) {
        cameraEngine.applySettings(result.cameraSettings)
    }

    // Apply filter
    filterEngine.setFilter(result.filter)

    // Speak
    if (result.voiceMessage.isNotBlank()) {
        voiceSystem.speak(result.voiceMessage)
    }

    // Fetch Unsplash inspo
    if (result.unsplashQuery.isNotBlank()) {
        fetchInspoPhotos(result.unsplashQuery)
    }

    // Auto-capture
    if (result.readyToCapture) {
        startCountdown()
    }
}
```

**5. Voice command wiring (`onMicButtonHeld`)**
```kotlin
fun onMicButtonHeld() {
    uiState.update { it.copy(isListening = true) }
    voiceSystem.startListening(
        onResult = { text ->
            uiState.update { it.copy(
                isListening = false,
                chatMessages = it.chatMessages + ChatMessage(text, true)
            )}
            viewModelScope.launch {
                val result = aiCoach.sendVoiceCommand(text)
                applyCoachingResult(result)
            }
        },
        onError = {
            uiState.update { it.copy(isListening = false) }
            voiceSystem.speak("Sorry, didn't catch that. Try again.")
        }
    )
}
```

**6. Inspo selection (`onInspoPhotoSelected`)**
```kotlin
fun onInspoPhotoSelected(photo: UnsplashPhoto) {
    uiState.update { it.copy(selectedInspoPhoto = photo) }
    viewModelScope.launch {
        cameraEngine.capturePreviewFrame { framePath ->
            launch {
                val result = aiCoach.matchInspoStyle(
                    description = photo.altDescription.ifBlank { "the style of this reference photo" },
                    framePath = framePath
                )
                applyCoachingResult(result)
            }
        }
    }
}
```

**7. Auto-capture (`startCountdown` + `onCountdownComplete`)**
- `startCountdown()`: launches a coroutine that counts 3→2→1, updating `countdownValue` in state each second
- `onCountdownComplete()`: stops the analysis loop, calls `cameraEngine.capturePhoto { uri → ... }`, plays shutter sound, speaks "Got it! That turned out great.", resets coaching state

**8. Manual shutter, mode toggle, filter selection** — straightforward, fill in the stubs

### Done when

- End-to-end flow works: tap AI → camera analyzes → voice speaks → settings change → Unsplash loads
- Voice command "make it warmer" changes filter and speaks a response
- Tapping an inspo photo changes the coaching direction
- Auto-capture fires when Gemma returns `readyToCapture = true`
- Manual shutter button captures and saves a photo
- "Coach Me" mode shows instructions instead of auto-applying settings
- No memory leaks (analysis loop cancelled on ViewModel cleared)

---

## Phase 3 — Polish + Submit

**Branch:** commit to `main` | **Time:** ~4–5 hours | **Needs:** Phase 2 merged

### Submission and docs
- Complete `README.md` (setup instructions, team names/emails, run steps, architecture diagram)
- Submit on Devpost with the full feature description
- Final `assembleRelease` build + install on demo device
- Run through the full demo script end-to-end at least twice
- Prepare screen recording backup

### Camera + filters
- Test all 12 filters on device — fix any that look visually wrong
- Test every `CameraSettings` field — verify zoom, WB, ISO all apply correctly
- Confirm captured photos save to gallery with filter applied
- Add comments to `CameraEngine.kt` and `FilterRenderer.kt`

### AI engine
- Tune `SystemPrompts.kt` if Gemma's tone or tool call reliability needs adjustment
- Test with varied scenes: food, landscape, portrait, low-light
- Measure and record model load time and average inference time per frame for the demo
- Add comments to `CoachingSession.kt` and `CameraToolSet.kt`

### UI + voice + Unsplash
- Polish animations: filter chip transitions, chat bubble slide-in, inspo row slide-down
- Test voice in a noisy room — confirm chat bubbles still work if STT fails
- Test Unsplash offline — confirm inspo row gracefully disappears, no crash
- Verify Unsplash attribution is visible and correct
- Add comments to `VoiceSystem.kt` and `UnsplashClientImpl.kt`

### Submission checklist

- [ ] `./gradlew assembleRelease` passes
- [ ] APK installs cleanly on S25 Ultra from a fresh uninstall
- [ ] All demo acts work on the physical device
- [ ] GitHub repo is public with MIT license in the About section
- [ ] README has all required fields (names, emails, setup, run steps)
- [ ] Devpost submitted with text description and GitHub link
- [ ] Model pre-loaded on demo device
- [ ] Screen recording backup ready

---

## Directory Summary

| Directory | Phase | Frozen after |
|-----------|-------|--------------|
| `contracts/` | Phase 0 | Phase 0 |
| `models/` | Phase 0 | Phase 0 |
| `viewmodel/` | Phase 0, 2 | — |
| `ui/theme/` | Phase 0, 1C | Phase 1C |
| `ui/screens/` | Phase 1C | — |
| `ui/components/` | Phase 1C | — |
| `camera/` | Phase 1A | — |
| `filters/` | Phase 1A | — |
| `ai/` | Phase 1B | — |
| `voice/` | Phase 1C | — |
| `api/` | Phase 1C | — |

`contracts/` and `models/` are frozen after Phase 0. Any interface change requires agreement from the whole team since it affects all phases.


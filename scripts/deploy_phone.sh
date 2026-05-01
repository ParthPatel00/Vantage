#!/usr/bin/env bash
# Vantage — phone deploy + preflight
#
# Build, push, install, grant perms, and launch on the connected Samsung Galaxy
# S25 Ultra. Run from the repo root: bash scripts/deploy_phone.sh
#
# What it does, in order:
#   1. Verify exactly one device is connected and it's a real device (not the AVD).
#   2. Build the debug APK (incremental).
#   3. Push the Gemma model if it isn't already on the device. Looks for the
#      file in ./models/ first, then a few common Downloads locations.
#   4. Install the APK with -r.
#   5. Grant CAMERA + RECORD_AUDIO at runtime so the first launch lands on the
#      camera screen instead of the permission prompt.
#   6. Launch the app and tail the Vantage logcat tags so you see the NPU /
#      Gemma init lines as they happen.
#
# Stops on the first failure with a readable message.

set -euo pipefail

ADB="C:/Users/adam_/AppData/Local/Android/Sdk/platform-tools/adb.exe"
APK="app/build/outputs/apk/debug/app-debug.apk"
MODEL_NAME="gemma-4-E2B-it_qualcomm_sm8750.litertlm"
DEVICE_MODEL_PATH="/sdcard/Android/data/com.vantage/files/${MODEL_NAME}"

# 1. Device sanity ----------------------------------------------------------------
echo "==> Checking adb devices..."
DEVICES=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
COUNT=$(echo "$DEVICES" | grep -c . || true)
if [[ "$COUNT" -eq 0 ]]; then
  echo "ERROR: no device connected. Plug in the S25 Ultra and enable USB debugging."
  exit 1
fi
if [[ "$COUNT" -gt 1 ]]; then
  echo "ERROR: multiple devices/emulators attached. Disconnect the emulator before running this:"
  echo "$DEVICES"
  exit 1
fi
SERIAL=$(echo "$DEVICES" | head -1)
if [[ "$SERIAL" == emulator-* ]]; then
  echo "ERROR: only the emulator is connected ($SERIAL). Plug in the S25 Ultra."
  exit 1
fi
echo "    device: $SERIAL"

# Verify it's actually a Samsung-ish phone before pushing the model
DEVICE_MODEL=$("$ADB" -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')
DEVICE_BOARD=$("$ADB" -s "$SERIAL" shell getprop ro.product.board | tr -d '\r')
echo "    model: $DEVICE_MODEL  board: $DEVICE_BOARD"

# 2. Build ------------------------------------------------------------------------
echo "==> Building debug APK..."
./gradlew :app:assembleDebug -q
[[ -f "$APK" ]] || { echo "ERROR: APK not produced at $APK"; exit 1; }
echo "    apk: $APK ($(du -h "$APK" | awk '{print $1}'))"

# 3. Model push -------------------------------------------------------------------
echo "==> Checking Gemma model on device..."
"$ADB" -s "$SERIAL" shell mkdir -p /sdcard/Android/data/com.vantage/files/ >/dev/null
if "$ADB" -s "$SERIAL" shell ls "$DEVICE_MODEL_PATH" >/dev/null 2>&1; then
  REMOTE_SIZE=$("$ADB" -s "$SERIAL" shell stat -c %s "$DEVICE_MODEL_PATH" | tr -d '\r')
  echo "    model already on device ($REMOTE_SIZE bytes), skipping push"
else
  echo "    model not on device, looking for it locally..."
  LOCAL_MODEL=""
  for candidate in \
      "models/$MODEL_NAME" \
      "$HOME/Downloads/$MODEL_NAME" \
      "/c/Users/$USER/Downloads/$MODEL_NAME"; do
    if [[ -f "$candidate" ]]; then LOCAL_MODEL="$candidate"; break; fi
  done
  if [[ -z "$LOCAL_MODEL" ]]; then
    cat <<EOF
ERROR: cannot find $MODEL_NAME locally. Download it from HuggingFace first:
    pip install huggingface-hub
    huggingface-cli login
    huggingface-cli download litert-community/gemma-4-E2B-it-litert-lm \\
        $MODEL_NAME --local-dir models
Then re-run this script.
EOF
    exit 1
  fi
  echo "    pushing $LOCAL_MODEL → $DEVICE_MODEL_PATH (this is ~2.8 GB, several minutes)..."
  "$ADB" -s "$SERIAL" push "$LOCAL_MODEL" "$DEVICE_MODEL_PATH"
fi

# 4. Install ----------------------------------------------------------------------
echo "==> Installing APK..."
"$ADB" -s "$SERIAL" install -r "$APK"

# 5. Grant runtime permissions ----------------------------------------------------
echo "==> Granting runtime permissions..."
"$ADB" -s "$SERIAL" shell pm grant com.vantage android.permission.CAMERA
"$ADB" -s "$SERIAL" shell pm grant com.vantage android.permission.RECORD_AUDIO

# 6. Launch + tail logs -----------------------------------------------------------
echo "==> Launching..."
"$ADB" -s "$SERIAL" shell am force-stop com.vantage
"$ADB" -s "$SERIAL" shell monkey -p com.vantage -c android.intent.category.LAUNCHER 1 >/dev/null

cat <<EOF

App launched on $SERIAL ($DEVICE_MODEL).

Tailing logcat for Vantage activity. Watch for:
  D/Vantage: Found model at: $DEVICE_MODEL_PATH
  I/native:  MainExecutorSettings: backend: NPU
  D/Vantage: Gemma engine ready

Then long-press the mic button to fire the inspiration tool, or tap the mic
once and say "find me poses to do" for the real STT path. Press Ctrl+C to stop.

EOF

"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" logcat -v brief Vantage:V VoiceSystem:V CameraToolSet:V Unsplash:V native:I AndroidRuntime:E '*:S'

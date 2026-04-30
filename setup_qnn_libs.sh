#!/bin/bash
# Copy QNN runtime libraries from a local QAIRT SDK install into jniLibs.
# Usage: ./setup_qnn_libs.sh /path/to/qairt/2.x.x.xxxxxx
#
# Download QAIRT SDK from: https://www.qualcomm.com/developer/software/qualcomm-ai-runtime
# SM8750 (Snapdragon 8 Elite) uses Hexagon v79.

set -e

QAIRT_DIR="${1:?Usage: $0 /path/to/qairt/2.x.x.xxxxxx}"
DEST="$(dirname "$0")/app/src/main/jniLibs/arm64-v8a"

echo "Copying QNN libraries from $QAIRT_DIR to $DEST"

# Core runtime (same for all HTP versions)
cp -f "$QAIRT_DIR/lib/aarch64-android/libQnnHtp.so"        "$DEST/"
cp -f "$QAIRT_DIR/lib/aarch64-android/libQnnSystem.so"     "$DEST/"
cp -f "$QAIRT_DIR/lib/aarch64-android/libQnnHtpPrepare.so" "$DEST/"

# SM8750 = Snapdragon 8 Elite = Hexagon v79
cp -f "$QAIRT_DIR/lib/aarch64-android/libQnnHtpV79Stub.so"      "$DEST/"
cp -f "$QAIRT_DIR/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so" "$DEST/"

echo "Done. Files in $DEST:"
ls -lh "$DEST"

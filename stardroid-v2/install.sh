#!/bin/bash

# Install Sky Map v2 APK to connected device
# Usage: ./install.sh [-d] [-p] [--fdroid]
#   -d: Install debug build (default: release)
#   -p: Install to phone (USB device)
#   --fdroid: Install F-Droid build (default: GMS)

BUILD_TYPE="release"
ADB_FLAGS=""
FDROID=false

for arg in "$@"; do
  case $arg in
    -d)
      BUILD_TYPE="debug"
      ;;
    -p)
      ADB_FLAGS="-d"
      ;;
    --fdroid)
      FDROID=true
      ;;
  esac
done

if [ "$FDROID" = true ]; then
  APK_PATH="app/build/outputs/apk/fdroid/${BUILD_TYPE}/app-fdroid-${BUILD_TYPE}.apk"
else
  APK_PATH="app/build/outputs/apk/gms/${BUILD_TYPE}/app-gms-${BUILD_TYPE}.apk"
fi

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
  echo "Error: APK not found at $APK_PATH"
  echo "Run './build.sh' first to build the ${BUILD_TYPE} APK"
  exit 1
fi

echo "Installing ${BUILD_TYPE} build to device..."
adb $ADB_FLAGS install -r "$APK_PATH"

if [ $? -eq 0 ]; then
  echo "Successfully installed ${BUILD_TYPE} build"
else
  echo "Installation failed"
  exit 1
fi

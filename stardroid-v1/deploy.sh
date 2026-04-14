#!/bin/bash

# Deploy Sky Map APK to connected device
# Usage: ./deploy.sh [-d] [-p]
#   -d: Deploy debug build (default: release)
#   -p: Deploy to phone (USB device)

BUILD_TYPE="release"
ADB_FLAGS=""

# Parse command line arguments
while getopts "dp" opt; do
  case $opt in
    d)
      BUILD_TYPE="debug"
      ;;
    p)
      ADB_FLAGS="-d"
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
  esac
done

APK_PATH="app/build/outputs/apk/gms/${BUILD_TYPE}/app-gms-${BUILD_TYPE}.apk"

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
  echo "Error: APK not found at $APK_PATH"
  echo "Run './gradlew assembleGms${BUILD_TYPE^}' first to build the ${BUILD_TYPE} APK"
  exit 1
fi

echo "Deploying ${BUILD_TYPE} build to device..."
adb $ADB_FLAGS install -r "$APK_PATH"

if [ $? -eq 0 ]; then
  echo "Successfully deployed ${BUILD_TYPE} build"
else
  echo "Deployment failed"
  exit 1
fi

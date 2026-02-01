#!/bin/bash

# Deploy Sky Map APK to connected device
# Usage: ./deploy.sh [-d]
#   -d: Deploy debug build (default: release)

BUILD_TYPE="release"

# Parse command line arguments
if [ "$1" = "-d" ]; then
  BUILD_TYPE="debug"
fi

APK_PATH="app/build/outputs/apk/gms/${BUILD_TYPE}/app-gms-${BUILD_TYPE}.apk"

# Check if APK exists
if [ ! -f "$APK_PATH" ]; then
  echo "Error: APK not found at $APK_PATH"
  echo "Run './gradlew assembleGms${BUILD_TYPE^}' first to build the ${BUILD_TYPE} APK"
  exit 1
fi

echo "Deploying ${BUILD_TYPE} build to device..."
adb install -r "$APK_PATH"

if [ $? -eq 0 ]; then
  echo "Successfully deployed ${BUILD_TYPE} build"
else
  echo "Deployment failed"
  exit 1
fi

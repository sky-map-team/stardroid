#!/bin/bash

# Uninstall Sky Map APK from connected device
# Usage: ./undeploy.sh [-d] [-e]
#   -p: Uninstall from phone (USB device)
#   -e: Uninstall from emulator

ADB_FLAGS=""

for arg in "$@"; do
  case $arg in
    -p)
      ADB_FLAGS="-d"
      ;;
    -e)
      ADB_FLAGS="-e"
      ;;
  esac
done

adb $ADB_FLAGS uninstall com.google.android.stardroid

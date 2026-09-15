#!/bin/bash

# Uninstall Sky Map v2 from connected device
# Usage: ./uninstall.sh [-p]
#   -p: Uninstall from phone (USB device)

ADB_FLAGS=""

for arg in "$@"; do
  case $arg in
    -p)
      ADB_FLAGS="-d"
      ;;
  esac
done

adb $ADB_FLAGS uninstall com.google.android.stardroid

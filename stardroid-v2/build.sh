#!/bin/bash
set -euo pipefail

FDROID=false
DEBUG=false

for arg in "$@"
do
    case $arg in
        --fdroid)
        FDROID=true
        shift
        ;;
        -d)
        DEBUG=true
        shift
        ;;
    esac
done

if [ "$FDROID" = true ]; then
  if [ "$DEBUG" = true ]; then
    ./gradlew :app:assembleFdroidDebug
  else
    ./gradlew :app:assembleFdroidRelease
  fi
else
  if [ "$DEBUG" = true ]; then
    ./gradlew :app:assembleGmsDebug
  else
    ./gradlew :app:assembleGmsRelease
  fi
fi

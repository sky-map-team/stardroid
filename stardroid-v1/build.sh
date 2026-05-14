#!/bin/bash
set -euo pipefail
FULL=false
FDROID=false
DEBUG=false

for arg in "$@"
do
    case $arg in
        --full)
        FULL=true
        shift
        ;;
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

# Temporary script to regenerate the data and build the app.
if [ "$FULL" = true ]; then
  # TODO(jontayler): retire it once gradle can do this or
  # or we get rid of the data generation step.
  ./gradlew clean :tools:installDist
  (cd tools
  # Gah. Gradle gets the classpath for the tools wrong.  Fix:
  sed -i -e 's#CLASSPATH=#CLASSPATH=$APP_HOME/lib/:#g' build/install/datagen/bin/datagen
  rm -f build/install/datagen/bin/datagen-e
  ./generate.sh
  ./binary.sh)
  if [ "$DEBUG" = false ] && [ "$FDROID" = false ]; then
    ./gradlew :app:bundleGmsRelease
  fi
fi

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

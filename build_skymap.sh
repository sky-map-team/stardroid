#!/bin/sh -e
QUICK=false
FDROID=false

for arg in "$@"
do
    case $arg in
        --quick)
        QUICK=true
        shift
        ;;
        --fdroid)
        FDROID=true
        shift
        ;;
    esac
done

# Temporary script to regenerate the data and build the app.
if [ "$QUICK" = false ]; then
  # TODO(jontayler): retire it once gradle can do this or
  # or we get rid of the data generation step.
  ./gradlew clean :tools:installDist
  (cd tools
  # Gah. Gradle gets the classpath for the tools wrong.  Fix:
  sed -i -e 's#CLASSPATH=#CLASSPATH=$APP_HOME/lib/:#g' build/install/datagen/bin/datagen
  rm -f build/install/datagen/bin/datagen-e
  ./generate.sh
  ./binary.sh)
fi

if [ "$FDROID" = true ]; then
  # Create a backup to restore later
  cp app/build.gradle app/build.gradle.bak

  # These sed commands create app/build.gradle-e on macOS
  sed -i -e '/gmsImplementation/d' app/build.gradle
  sed -i -e '/com.google.gms.google-services/d' app/build.gradle

  # Run build, ensuring we restore the backup even if it fails
  set +e
  ./gradlew :app:assembleFdroid
  BUILD_EXIT_CODE=$?
  set -e

  # Restore original file and remove the -e backups
  mv app/build.gradle.bak app/build.gradle
  rm -f app/build.gradle-e

  if [ $BUILD_EXIT_CODE -ne 0 ]; then
    exit $BUILD_EXIT_CODE
  fi
else
  ./gradlew :app:assembleGms
fi
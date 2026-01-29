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
  ./gradlew :app:assembleFdroid
else
  ./gradlew :app:assembleGms
fi
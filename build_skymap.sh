#!/bin/bash
# Temporary script to regenerate the data and build the app.
# TODO(jontayler): retire it once gradle can do this or
# or we get rid of the data generation step.
./gradlew clean assemble installDist
cd tools
# Gah. Gradle gets the classpath for the tools wrong.  Fix:
sed -i -e 's/app-release.apk//g' build/install/datagen/bin/datagen
./generate.sh
./rewrite.sh
./binary.sh
cd ..
./gradlew assemble


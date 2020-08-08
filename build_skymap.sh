#!/bin/sh -e
# Temporary script to regenerate the data and build the app.
# TODO(jontayler): retire it once gradle can do this or
# or we get rid of the data generation step.
./gradlew clean assembleGms installDist
(cd tools
# Gah. Gradle gets the classpath for the tools wrong.  Fix:	# Gah. Gradle gets the classpath for the tools wrong.  Fix:
sed -i -e 's#CLASSPATH=#CLASSPATH=$APP_HOME/lib/:#g' build/install/datagen/bin/datagen
./generate.sh
./rewrite.sh
./binary.sh)
./gradlew assembleGms

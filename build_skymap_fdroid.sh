#!/bin/sh -e
# Temporary script to regenerate the data and build the app.
# TODO(jontayler): retire it once gradle can do this or
# or we get rid of the data generation step.
sed -i -e 's/gms/fdroid/' tools/build.gradle
sed -i -e '/gmsCompile/d' app/build.gradle
sed -i -e '/com.google.gms.google-services/d' app/build.gradle
./gradlew clean assembleFdroid installDist
(cd tools
# Gah. Gradle gets the classpath for the tools wrong.  Fix:
sed -i -e 's#CLASSPATH=#CLASSPATH=$APP_HOME/lib/:#g' build/install/datagen/bin/datagen
./generate.sh
./binary.sh)
./gradlew assembleFdroid

#!/bin/sh -e
sed -i -e '/gmsCompile/d' app/build.gradle
sed -i -e '/com.google.gms.google-services/d' app/build.gradle
./gradlew assembleFdroid

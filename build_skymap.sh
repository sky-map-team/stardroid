#!/bin/bash
# Temporary script to regenerate the data and build the app.
# TODO(jontayler): retire it once gradle can do this or
# or we get rid of the data generation step.
./gradlew assemble
cd tools
./generate.sh
./rewrite.sh
./binary.sh
cd ..
./gradlew assemble


#!/bin/bash

ROOT=".."
ROOT_TOOLS="$ROOT/tools"
ROOT_APP="$ROOT/app"

CLASSPATH="\
build/classes/main:\
$ROOT_APP/build/intermediates/classes/release:\
libs/protobuf-java-2.6.1.jar\
"


java -cp $CLASSPATH com.google.android.stardroid.data.AsciiProtoRewriter stars_R.ascii
java -cp $CLASSPATH com.google.android.stardroid.data.AsciiProtoRewriter messier_R.ascii
java -cp $CLASSPATH com.google.android.stardroid.data.AsciiProtoRewriter constellations_R.ascii

#!/bin/bash

ROOT=".."
ROOT_TOOLS="$ROOT/tools"
ROOT_APP="$ROOT/app"

CLASSPATH="\
build/classes/main:\
$ROOT_APP/build/intermediates/classes/release:\
libs/protobuf-java-2.6.1.jar\
"

DATA_DIR=data

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiProtoRewriter $DATA_DIR/stars_R.ascii
java -cp $CLASSPATH com.google.android.stardroid.data.AsciiProtoRewriter $DATA_DIR/messier_R.ascii
java -cp $CLASSPATH com.google.android.stardroid.data.AsciiProtoRewriter $DATA_DIR/constellations_R.ascii

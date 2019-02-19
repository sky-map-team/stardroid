#!/bin/bash

ROOT=".."
ROOT_TOOLS="$ROOT/tools"
ROOT_APP="$ROOT/app"

CLASSPATH="\
build/classes/main:\
$ROOT_APP/build/intermediates/classes/release:\
libs/protobuf-java-2.6.1.jar\
"

IN_DATA_DIR=data

OUT_DATA_DIR=$ROOT_APP/src/main/assets

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter $IN_DATA_DIR/constellations.ascii
mv $IN_DATA_DIR/constellations.binary $OUT_DATA_DIR

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter $IN_DATA_DIR/stars.ascii
mv $IN_DATA_DIR/stars.binary $OUT_DATA_DIR

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter $IN_DATA_DIR/messier.ascii
mv $IN_DATA_DIR/messier.binary $OUT_DATA_DIR


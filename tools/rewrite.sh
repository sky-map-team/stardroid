#!/bin/bash

ROOT=".."
ROOT_TOOLS="$ROOT/tools"
ROOT_APP="$ROOT/app"

CLASSPATH="\
$ROOT_TOOLS/bin:\
$ROOT_APP/bin/classes:\
$ROOT_TOOLS/libs/protobuf-java-2.6.1.jar"


DATADIR=$ROOT_APP/assets/

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiProtoRewriter stars_R.ascii
java -cp $CLASSPATH com.google.android.stardroid.data.AsciiProtoRewriter messier_R.ascii
java -cp $CLASSPATH com.google.android.stardroid.data.AsciiProtoRewriter constellations_R.ascii

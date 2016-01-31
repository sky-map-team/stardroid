#!/bin/bash

ROOT=".."
ROOT_TOOLS="$ROOT/tools"
ROOT_APP="$ROOT/app"

CLASSPATH="\
$ROOT_TOOLS/bin:\
$ROOT_APP/bin/classes:\
$ROOT_TOOLS/libs/protobuf-java-2.6.1.jar\
"

DATADIR=$ROOT_APP/assets/

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter constellations.ascii
cp constellations.binary $DATADIR

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter stars.ascii
cp stars.binary $DATADIR

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter messier.ascii
cp messier.binary $DATADIR


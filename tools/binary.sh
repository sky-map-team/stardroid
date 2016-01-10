#!/bin/bash

ROOT="../../../../../../.."
ROOT_TOOLS="$ROOT/tools"
ROOT_APP="$ROOT/app"
# Following are needed by John because of his silly workspace set up
#ROOT="/Users/johntaylor/Documents/workspace"
#ROOT_TOOLS="$ROOT/Stardroid-unix4-tools"
#ROOT_APP="$ROOT/Stardroid-unix4"

CLASSPATH="\
$ROOT_TOOLS/bin:\
$ROOT_APP/bin:\
$ROOT_TOOLS/libs/protobuf-java-2.3.0.jar\
"

DATADIR=$ROOT_APP/assets/

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter constellations.ascii
cp constellations.binary $DATADIR

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter stars.ascii
cp stars.binary $DATADIR

java -cp $CLASSPATH com.google.android.stardroid.data.AsciiToBinaryProtoWriter messier.ascii
cp messier.binary $DATADIR


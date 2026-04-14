#!/bin/bash

ROOT=".."
ROOT_TOOLS="$ROOT/tools"
ROOT_APP="$ROOT/app"

TOOL=build/install/datagen/bin/datagen

IN_DATA_DIR=data

OUT_DATA_DIR=$ROOT_APP/src/main/assets

$TOOL Binary $IN_DATA_DIR/constellations.ascii
mv $IN_DATA_DIR/constellations.binary $OUT_DATA_DIR

$TOOL Binary $IN_DATA_DIR/stars.ascii
mv $IN_DATA_DIR/stars.binary $OUT_DATA_DIR

$TOOL Binary $IN_DATA_DIR/deep_sky_objects.ascii
mv $IN_DATA_DIR/deep_sky_objects.binary $OUT_DATA_DIR


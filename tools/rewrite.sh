#!/bin/bash
TOOL=build/install/datagen/bin/datagen
DATA_DIR=data

$TOOL Rewrite $DATA_DIR/stars_R.ascii
$TOOL Rewrite $DATA_DIR/messier_R.ascii
$TOOL Rewrite $DATA_DIR/constellations_R.ascii

#!/bin/bash

CLASSPATH="\
bin:\
../app/bin/classes:\
libs/protobuf-java-2.6.1.jar\
"

# Converts CSV and KML formats to ascii protocol buffers.
java -cp $CLASSPATH \
com.google.android.stardroid.data.StellarProtoWriter data/stardata_names.txt stars

java -cp $CLASSPATH \
com.google.android.stardroid.data.ConstellationProtoWriter data/constellation_names_and_lines.kml constellations

java -cp $CLASSPATH \
com.google.android.stardroid.data.MessierProtoWriter data/messier.csv messier


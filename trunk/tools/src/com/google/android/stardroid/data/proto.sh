#!/bin/bash

FILE="../../../../../../../app/src/com/google/android/stardroid/source/proto/source.proto"

# Reads the original source.proto file, strips out the PROTO_LITE
# option and then recompiles the proto as SourceFullProto.  This
# allows us to use the ASCII parsing tools for protocol buffers with
# out messages.
sed -e "s/option optimize_for/\/\/option optimize_for/" $FILE \
 | sed -e "s/\"SourceProto\"/\"SourceFullProto\"/" \
 | sed -e "s/\/\/ optional string REMOVE/optional string REMOVE/" \
> source_full.proto

protoc --java_out="../../../../../" source_full.proto

CLASSPATH="\
../../../../../../bin:\
../../../../../../../app/bin:\
../../../../../../libs/protobuf-java-2.3.0.jar\
"

# Converts CSV and KML formats to ascii protocol buffers.
java -cp $CLASSPATH \
com.google.android.stardroid.data.StellarProtoWriter stardata_names.txt stars

java -cp $CLASSPATH \
com.google.android.stardroid.data.ConstellationProtoWriter constellation_names_and_lines.kml constellations
java -cp $CLASSPATH \
com.google.android.stardroid.data.MessierProtoWriter messier.csv messier


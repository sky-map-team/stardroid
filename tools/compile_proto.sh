#!/bin/bash


# Reads the original source.proto file, strips out the PROTO_LITE
# option and then recompiles the proto as SourceFullProto.  This
# allows us to use the ASCII parsing tools for protocol buffers with
# out messages.  Also reinstate the field we'll use to temporarily
# hold the string name.

FILE="../app/src/com/google/android/stardroid/source/proto/source.proto"

sed -e "s/option optimize_for/\/\/option optimize_for/" $FILE \
 | sed -e "s/\"SourceProto\"/\"SourceFullProto\"/" \
 | sed -e "s/\/\/ optional string REMOVE/optional string REMOVE/" \
> source_full.proto

protoc --java_out="src" source_full.proto


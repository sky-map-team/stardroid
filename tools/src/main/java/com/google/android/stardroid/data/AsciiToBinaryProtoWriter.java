// Copyright 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


package com.google.android.stardroid.data;

import com.google.common.io.Closeables;
import com.google.protobuf.TextFormat;
import com.google.android.stardroid.source.proto.SourceProto.AstronomicalSourcesProto;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/**
 * Class for writing Ascii protocol buffers in binary format.
 *
 * @author Brent Bryan
 */
public class AsciiToBinaryProtoWriter {

  public static void main(String[] args) throws IOException {
    if (args.length != 1 || !args[0].endsWith(".ascii")) {
      System.out.println("Usage: AsciiToBinaryProtoWriter <inputprefix>.ascii");
      System.exit(1);
    }

    FileReader in = null;
    FileOutputStream out = null;

    try {
      in = new FileReader(args[0]);
      AstronomicalSourcesProto.Builder builder = AstronomicalSourcesProto.newBuilder();
      TextFormat.merge(in, builder);

      out = new FileOutputStream(args[0].substring(0, args[0].length() - 6) + ".binary");

      AstronomicalSourcesProto sources = builder.build();
      System.out.println("Source count " + sources.getSourceCount());
      sources.writeTo(out);
    } finally {
      Closeables.closeQuietly(in);
      Closeables.close(out, false);
    }
  }
}

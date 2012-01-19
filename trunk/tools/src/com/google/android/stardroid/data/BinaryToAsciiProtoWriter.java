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

import com.google.android.stardroid.base.Closeables;
import com.google.android.stardroid.source.proto.SourceFullProto.AstronomicalSourcesProto;
import com.google.protobuf.TextFormat;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Class for converting binary protocol buffers into ascii protocol buffers.
 *
 * @author Brent Bryan
 */
public class BinaryToAsciiProtoWriter {
  public static void main(String[] args) throws IOException {
    if (args.length != 1 || args[0].endsWith(".binary")) {
      System.out.println("Usage: BinaryToAsciiProtoWriter <inputprefix>.binary");
      System.exit(1);
    }

    FileInputStream in = null;
    FileWriter out = null;

    try {
      in = new FileInputStream(args[0]);
      AstronomicalSourcesProto sources = AstronomicalSourcesProto.parseFrom(in);

      out = new FileWriter(args[0].substring(0, args[0].length() - 7) + ".ascii");
      TextFormat.print(sources, out);
    } finally {
      Closeables.closeSilently(in);
      Closeables.closeSilently(out);
    }
  }
}

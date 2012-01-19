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

import android.content.res.Resources;

import com.google.android.stardroid.base.Closeables;
import com.google.android.stardroid.layers.BinarySourceIO;
import com.google.android.stardroid.source.LineSource;
import com.google.android.stardroid.source.PointSource;
import com.google.android.stardroid.source.TextSource;
import com.google.android.stardroid.source.impl.LineSourceImpl;
import com.google.android.stardroid.source.impl.PointSourceImpl;
import com.google.android.stardroid.source.impl.TextSourceImpl;
import com.google.android.stardroid.source.proto.ProtobufAstronomicalSource;
import com.google.android.stardroid.source.proto.SourceProto.AstronomicalSourceProto;
import com.google.android.stardroid.source.proto.SourceProto.AstronomicalSourcesProto;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for converting binary protocol buffers to the original binary form.
 *
 * @author Brent Bryan
 */
public class BinaryProtoToBinaryIOWriter {
  private static final Resources RESOURCES = Resources.getSystem();

  private static void writeSources(AstronomicalSourcesProto sourcesProto, DataOutputStream out)
      throws IOException {

    List<ProtobufAstronomicalSource> sources = new ArrayList<ProtobufAstronomicalSource>();
    for (AstronomicalSourceProto sourceProto : sourcesProto.getSourceList()) {
      ProtobufAstronomicalSource source = new ProtobufAstronomicalSource(sourceProto, RESOURCES);
      for (TextSource label : source.getLabels()) {
        BinarySourceIO.writeSource((TextSourceImpl) label, out);
      }

      for (PointSource point : source.getPoints()) {
        BinarySourceIO.writeSource((PointSourceImpl) point, out);
      }

      for (LineSource line : source.getLines()) {
        BinarySourceIO.writeSource((LineSourceImpl) line, out);
      }
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1 || args[0].endsWith(".ascii")) {
      System.out.println("Usage: BinaryProtoiToBinaryIOWriter <inputprefix>.binary");
      System.exit(1);
    }

    FileInputStream in = null;
    DataOutputStream out = null;

    try {
      in = new FileInputStream(args[0]);
      AstronomicalSourcesProto.Builder builder = AstronomicalSourcesProto.newBuilder();
      builder.mergeFrom(in);

      String filename = args[0].substring(0, args[0].length() - 7) + ".bin_old";
      out = new DataOutputStream(new FileOutputStream(filename));
      writeSources(builder.build(), out);
    } finally {
      Closeables.closeSilently(in);
      Closeables.closeSilently(out);
    }
  }
}

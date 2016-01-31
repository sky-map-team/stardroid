// Copyright 2008  Google Inc.
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

package com.google.android.stardroid.layers;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;

import android.util.Log;

import com.google.android.stardroid.source.impl.TextSourceImpl;
import com.google.android.stardroid.source.impl.PointSourceImpl;
import com.google.android.stardroid.source.impl.LineSourceImpl;
import com.google.android.stardroid.source.impl.AbstractSource;
import com.google.android.stardroid.units.GeocentricCoordinates;
import com.google.android.stardroid.units.RaDec;
import com.google.android.stardroid.util.MiscUtil;

/**
 * Utility class for serializing sources.
 *
 * @author Brent Bryan
 */
public class BinarySourceIO {
  public final static String TAG = MiscUtil.getTag(BinarySourceIO.class);
  private final static int POINT_SOURCE = 0;
  private final static int LABEL_SOURCE = 1;
  private final static int POLYLINE_SOURCE = 2;

  public static void readSourcesInto(int providerId, DataInputStream in,
                                     ArrayList<? super TextSourceImpl> textSources,
                                     ArrayList<? super PointSourceImpl> pointSources,
                                     ArrayList<? super LineSourceImpl> polyLineSources) throws IOException {
    try {
      while (true) {
        int type = in.readInt();
        switch (type) {
          case POINT_SOURCE:
            pointSources.add(readPointSource(providerId, in));
            break;

          case LABEL_SOURCE:
            textSources.add(readLabelSource(providerId, in));
            break;

          case POLYLINE_SOURCE:
            polyLineSources.add(readPolyLineSource(providerId, in));
            break;

          default:
            throw new RuntimeException("Unknown Source Type: "+type);
        }
      }
    } catch (EOFException e) {
      Log.d(TAG, "File finished");
    }
  }

  public static void writeSource(AbstractSource s, DataOutputStream out) throws IOException {
    if (s instanceof PointSourceImpl) {
      out.writeInt(POINT_SOURCE);
      writePointSource((PointSourceImpl) s, out);
    } else if (s instanceof TextSourceImpl) {
      out.writeInt(LABEL_SOURCE);
      writeLabelSource((TextSourceImpl) s, out);
    } else if (s instanceof LineSourceImpl) {
      out.writeInt(POLYLINE_SOURCE);
      writePolyLineSource((LineSourceImpl) s, out);
    }
  }

  /////////////////////
  private static void writePointSource(PointSourceImpl s, DataOutputStream out) throws IOException {
    out.writeInt(s.getColor());
    out.writeInt(s.getSize());
    RaDec raDec = RaDec.getInstance(s.getLocation());
    out.writeFloat(raDec.ra);
    out.writeFloat(raDec.dec);
  }

  private static PointSourceImpl readPointSource(int providerId, DataInputStream in) throws IOException {
    int color = in.readInt();
    int size = in.readInt();
    float ra = in.readFloat();
    float dec = in.readFloat();

    return new PointSourceImpl(ra, dec, color, size);
  }

  private static void writeLabelSource(TextSourceImpl s, DataOutputStream out) throws IOException {
    RaDec raDec = RaDec.getInstance(s.getLocation());
    out.writeFloat(raDec.ra);
    out.writeFloat(raDec.dec);
    out.writeInt(s.getColor());
    out.writeUTF(s.label);
  }

  private static TextSourceImpl readLabelSource(int providerId, DataInputStream in)
      throws IOException {
    float ra = in.readFloat();
    float dec = in.readFloat();
    int labelColor = in.readInt();
    String label = in.readUTF();
    return new TextSourceImpl(ra, dec, label, labelColor);
  }

  private static LineSourceImpl readPolyLineSource(int providerId, DataInputStream in)
      throws IOException {
    int numVertices = in.readInt();

    int color = in.readInt();
    LineSourceImpl s = new LineSourceImpl(color);
    for (int i = 0; i < numVertices; i++) {
      float ra = in.readFloat();
      float dec = in.readFloat();
      RaDec raDec = new RaDec(ra, dec);
      s.raDecs.add(raDec);
      s.vertices.add(GeocentricCoordinates.getInstance(raDec));
    }
    return s;
  }

  private static void writePolyLineSource(LineSourceImpl s, DataOutputStream out)
      throws IOException {
    out.writeInt(s.raDecs.size());
    out.writeInt(s.getColor());
    for (RaDec raDec : s.raDecs) {
      out.writeFloat(raDec.ra);
      out.writeFloat(raDec.dec);
    }
  }
}

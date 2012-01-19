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

import android.graphics.Color;

import com.google.android.stardroid.source.proto.SourceFullProto.AstronomicalSourceProto;
import com.google.android.stardroid.source.proto.SourceFullProto.GeocentricCoordinatesProto;
import com.google.android.stardroid.source.proto.SourceFullProto.LabelElementProto;
import com.google.android.stardroid.source.proto.SourceFullProto.PointElementProto;
import com.google.android.stardroid.util.StarAttributeCalculator;

import java.io.IOException;

/**
 * Class for reading the stellar csv file and writing the contents to a protocol
 * buffer.
 *
 * @author Brent Bryan
 */
public class StellarProtoWriter extends AbstractProtoWriter {
  private static final int STAR_COLOR = 0xcfcccf;

  @Override
  protected AstronomicalSourceProto getSourceFromLine(String line, int count) {
    // name, mag, ra, dec
    String[] tokens = line.split(",");
    if (tokens.length != 7) {
      throw new RuntimeException("Found " + tokens.length + ".  Expected 7.");
    }

    String name = tokens[0];
    float magnitude = Float.parseFloat(tokens[1]);
    float dec = Float.parseFloat(tokens[2]);
    float ra = Float.parseFloat(tokens[3]);

    if (magnitude >= 5.0f) {
      return null;
    }

    int color = StarAttributeCalculator.getColor(magnitude, Color.WHITE);
    int size = StarAttributeCalculator.getSize(magnitude);
    AstronomicalSourceProto.Builder builder = AstronomicalSourceProto.newBuilder();

    PointElementProto.Builder pointBuilder = PointElementProto.newBuilder();
    pointBuilder.setColor(color);
    GeocentricCoordinatesProto coords = getCoords(ra, dec);
    pointBuilder.setLocation(coords);
    pointBuilder.setSize(size);
    builder.addPoint(pointBuilder);

    if (name != null && name.trim().equals("")) {
      LabelElementProto.Builder labelBuilder = LabelElementProto.newBuilder();
      labelBuilder.setColor(STAR_COLOR);
      labelBuilder.setLocation(getCoords(ra, dec));
      labelBuilder.setStringIndex(0);
      builder.addLabel(labelBuilder);
    }
    builder.setSearchLocation(coords);
    return builder.build();
  }

  public static void main(String[] args) throws IOException {
    new StellarProtoWriter().run(args);
  }
}

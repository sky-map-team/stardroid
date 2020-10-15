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

import java.io.IOException;
import java.util.List;

import com.google.android.stardroid.source.proto.SourceProto.AstronomicalSourceProto;
import com.google.android.stardroid.source.proto.SourceProto.GeocentricCoordinatesProto;
import com.google.android.stardroid.source.proto.SourceProto.LabelElementProto;
import com.google.android.stardroid.source.proto.SourceProto.PointElementProto;


/**
 * Class for reading the messier csv file and writing the contents to protocol
 * buffers.
 *
 * @author brent@google.com (Brent Bryan)
 */
public class MessierAsciiProtoWriter extends AbstractAsciiProtoWriter {
  // TODO(mrhector): verify colors
  private static final int LABEL_COLOR = 0x48a841; // argb
  private static final int POINT_COLOR = 0x48a841; // abgr (!)
  private static final int POINT_SIZE = 3;

  @Override
  protected AstronomicalSourceProto getSourceFromLine(String line, int index) {
    // name, type, RA(h), dec(degrees), magnitude, size, ngc, constellation,
    // names, common name
    // Of these, only name(0), ra(2), & dec(3) are used.
    if (line.startsWith("Object,Type")) {
      return null;
    }

    // TODO(brent): Add image shapes here?

    String[] tokens = line.split(",");

    // Convert from hours to degrees.
    float ra = 15 * Float.parseFloat(tokens[2]);
    float dec = Float.parseFloat(tokens[3]);
    float magnitude = 4.9f;

    AstronomicalSourceProto.Builder sourceBuilder = AstronomicalSourceProto.newBuilder();
    GeocentricCoordinatesProto coords = getCoords(ra, dec);

    LabelElementProto.Builder labelBuilder = LabelElementProto.newBuilder();
    labelBuilder.setColor(LABEL_COLOR);
    labelBuilder.setLocation(coords);
    List<String> rKeysForName = rKeysFromName(tokens[0]);
    if (!rKeysForName.isEmpty()) {
      labelBuilder.setStringsStrId(rKeysForName.get(0));
    }
    sourceBuilder.addLabel(labelBuilder);

    PointElementProto.Builder pointBuilder = PointElementProto.newBuilder();
    pointBuilder.setColor(POINT_COLOR);
    pointBuilder.setLocation(coords);
    pointBuilder.setSize(POINT_SIZE);
    // TODO(johntaylor): consider setting messier object shape
    sourceBuilder.addPoint(pointBuilder);
    for (String rKey : rKeysForName) {
      sourceBuilder.addNameStrIds(rKey);
    }

    sourceBuilder.setSearchLocation(coords);
    return sourceBuilder.build();
  }

  public static void main(String[] args) throws IOException {
    new MessierAsciiProtoWriter().run(args);
  }
}

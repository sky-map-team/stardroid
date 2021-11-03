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

package com.google.android.stardroid.source.proto;

import android.content.res.Resources;
import android.util.Log;

import com.google.android.stardroid.R;
import com.google.android.stardroid.source.AbstractAstronomicalSource;
import com.google.android.stardroid.source.LinePrimitive;
import com.google.android.stardroid.source.PointPrimitive;
import com.google.android.stardroid.source.TextPrimitive;
import com.google.android.stardroid.source.proto.SourceProto.AstronomicalSourceProto;
import com.google.android.stardroid.source.proto.SourceProto.GeocentricCoordinatesProto;
import com.google.android.stardroid.source.proto.SourceProto.LabelElementProto;
import com.google.android.stardroid.source.proto.SourceProto.LineElementProto;
import com.google.android.stardroid.source.proto.SourceProto.PointElementProto;
import com.google.android.stardroid.math.CoordinateManipulationsKt;
import com.google.android.stardroid.math.Vector3;
import com.google.android.stardroid.util.MiscUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the
 * {@link com.google.android.stardroid.source.AstronomicalSource} interface
 * from objects serialized as protocol buffers.
 *
 * @author Brent Bryan
 */
public class ProtobufAstronomicalSource extends AbstractAstronomicalSource {
  private static final String TAG = MiscUtil.getTag(ProtobufAstronomicalSource.class);

  private static final Map<SourceProto.Shape, PointPrimitive.Shape> shapeMap =
    new HashMap<SourceProto.Shape, PointPrimitive.Shape>();

  // Ideally we'd get this from Context.getPackageName but for some reason passing it in as a
  // string via the contructor results in it always being null when I need it. Buggered if
  // I know why - it's certainly a concern. Hopefully this class won't be around for much longer.
  public static final String PACKAGE = "com.google.android.stardroid";

  static {
    shapeMap.put(SourceProto.Shape.CIRCLE, PointPrimitive.Shape.CIRCLE);
    shapeMap.put(SourceProto.Shape.STAR, PointPrimitive.Shape.CIRCLE);
    shapeMap.put(SourceProto.Shape.ELLIPTICAL_GALAXY, PointPrimitive.Shape.ELLIPTICAL_GALAXY);
    shapeMap.put(SourceProto.Shape.SPIRAL_GALAXY, PointPrimitive.Shape.SPIRAL_GALAXY);
    shapeMap.put(SourceProto.Shape.IRREGULAR_GALAXY, PointPrimitive.Shape.IRREGULAR_GALAXY);
    shapeMap.put(SourceProto.Shape.LENTICULAR_GALAXY, PointPrimitive.Shape.LENTICULAR_GALAXY);
    shapeMap.put(SourceProto.Shape.GLOBULAR_CLUSTER, PointPrimitive.Shape.GLOBULAR_CLUSTER);
    shapeMap.put(SourceProto.Shape.OPEN_CLUSTER, PointPrimitive.Shape.OPEN_CLUSTER);
    shapeMap.put(SourceProto.Shape.NEBULA, PointPrimitive.Shape.NEBULA);
    shapeMap.put(SourceProto.Shape.HUBBLE_DEEP_FIELD, PointPrimitive.Shape.HUBBLE_DEEP_FIELD);
  }

  private final AstronomicalSourceProto proto;
  private final Resources resources;

  // Lazily construct the names.
  private ArrayList<String> names;

  public ProtobufAstronomicalSource(AstronomicalSourceProto originalProto, Resources resources) {
    this.resources = resources;
    // Not ideal to be doing this in the constructor. TODO(john): investigate which threads
    // this is all happening on.
    this.proto = processStringIds(originalProto);
  }

  /**
   * The data files contain only the text version of the string Ids. Looking them up
   * by this id will be expensive so precalculate any integer ids. See the datageneration
   * design doc for an explanation.
   */
  private AstronomicalSourceProto processStringIds(AstronomicalSourceProto proto) {
    AstronomicalSourceProto.Builder processed = proto.toBuilder();
    for (String strId : proto.getNameStrIdsList()) {
      processed.addNameIntIds(toInt(strId));
    }
    // <rant>
    // Work around Google's clumsy protocol buffer API. For some inexplicable reason the current
    // version lacks the getFooBuilderList described here:
    // https://developers.google.com/protocol-buffers/docs/reference/java-generated#fields
    // </rant>
    List<LabelElementProto> newLabels = new ArrayList<>(processed.getLabelCount());
    for (LabelElementProto label : processed.getLabelList()) {
      LabelElementProto.Builder labelBuilder = label.toBuilder();
      labelBuilder.setStringsIntId(toInt(label.getStringsStrId()));
      newLabels.add(labelBuilder.build());
    }
    processed.clearLabel();
    processed.addAllLabel(newLabels);
    return processed.build();
  }

  private int toInt(String stringId) {
    int resourceId = resources.getIdentifier(stringId, "string", PACKAGE);
    return resourceId == 0 ? R.string.missing_label : resourceId;
  }

  @Override
  public synchronized ArrayList<String> getNames() {
    if (names == null) {
      names = new ArrayList<String>(proto.getNameIntIdsCount());
      for (int id : proto.getNameIntIdsList()) {
        names.add(resources.getString(id));
      }
    }
    return names;
  }

  @Override
  public Vector3 getSearchLocation() {
    return getCoords(proto.getSearchLocation());
  }

  @Override
  public List<PointPrimitive> getPoints() {
    if (proto.getPointCount() == 0) {
      return Collections.<PointPrimitive>emptyList();
    }
    ArrayList<PointPrimitive> points = new ArrayList<>(proto.getPointCount());
    for (PointElementProto element : proto.getPointList()) {
      points.add(new PointPrimitive(getCoords(element.getLocation()),
          element.getColor(), element.getSize(), shapeMap.get(element.getShape())));
    }
    return points;
  }

  @Override
  public List<TextPrimitive> getLabels() {
    if (proto.getLabelCount() == 0) {
      return Collections.<TextPrimitive>emptyList();
    }
    ArrayList<TextPrimitive> points = new ArrayList<TextPrimitive>(proto.getLabelCount());
    for (LabelElementProto element : proto.getLabelList()) {
      Log.d(TAG, "Label " + element.getStringsIntId() + " : " + element.getStringsStrId());
      points.add(new TextPrimitive(getCoords(element.getLocation()),
          resources.getString(element.getStringsIntId()),
          element.getColor(), element.getOffset(), element.getFontSize()));
    }
    return points;
  }

  @Override
  public List<LinePrimitive> getLines() {
    if (proto.getLineCount() == 0) {
      return Collections.<LinePrimitive>emptyList();
    }
    ArrayList<LinePrimitive> points = new ArrayList<>(proto.getLineCount());
    for (LineElementProto element : proto.getLineList()) {
      ArrayList<Vector3> vertices =
          new ArrayList<>(element.getVertexCount());
      for (GeocentricCoordinatesProto elementVertex : element.getVertexList()) {
        vertices.add(getCoords(elementVertex));
      }
      points.add(new LinePrimitive(element.getColor(), vertices, element.getLineWidth()));
    }
    return points;
  }

  private static Vector3 getCoords(GeocentricCoordinatesProto proto) {
    return CoordinateManipulationsKt.getGeocentricCoords(proto.getRightAscension(), proto.getDeclination());
  }
}

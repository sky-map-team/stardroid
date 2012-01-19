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
import com.google.android.stardroid.source.proto.SourceFullProto.AstronomicalSourceProto;
import com.google.android.stardroid.source.proto.SourceFullProto.AstronomicalSourcesProto;
import com.google.android.stardroid.source.proto.SourceFullProto.GeocentricCoordinatesProto;
import com.google.android.stardroid.source.proto.SourceFullProto.LabelElementProto;
import com.google.android.stardroid.source.proto.SourceFullProto.LineElementProto;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Class for reading the constellation KML file and writing the contents to a
 * protocol buffer
 *
 * @author Brent Bryan
 */
public class ConstellationProtoWriter {
  private static String[] CONSTELLATION_ARRAY =
      {"Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra", "Scorpius", "Sagittarius",
          "Capricornus", "Aquarius", "Pisces", "Ophiuchus", "Orion", "Cassiopeia", "Ursa Major",
          "Draco", "Crux", "Pegasus", "Andromeda", "Ursa Minor", "Canis Major", "Perseus",
          "Hercules", "Aquila", "Cygnus", "Lyra", "Bootes", "Eridanus", "Carina", "Vela", "Puppis",
          "Centaurus", "Auriga", "Pavo", "Hydrus", "Phoenix", "Piscis Austrinus", "Grus", "Cetus",
          "Lupus", "Tucana"};

  private static final HashSet<String> CONSTELLATIONS =
      new HashSet<String>(Arrays.asList(CONSTELLATION_ARRAY));

  public static int LABEL_COLOR = 0x80c97cb2;

  public static List<LabelElementProto> readLabels(String filename) {
    List<LabelElementProto> result = new ArrayList<LabelElementProto>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(new File(filename)));

      String s;
      int num = 0;
      while ((s = in.readLine()) != null) {
        s = s.trim();
        if (!s.equals("<Placemark>")) continue;

        s = in.readLine().trim();
        if (s.indexOf("<name>") < 0) continue;

        String name = s.substring(6, s.length() - 7);
        in.readLine(); // style url line.
        in.readLine(); // Point
        s = in.readLine().trim();
        if (s.indexOf("<coordinates>") < 0 && s.indexOf("</coordinates>") < 0) {
          throw new RuntimeException("Unexpected coordinate line: " + s);
        }
        s = s.substring(13, s.length() - 14);
        String[] tokens = s.split(",");
        float ra = getRa(Float.parseFloat(tokens[0]));
        float dec = Float.parseFloat(tokens[1]);

        if (CONSTELLATIONS.remove(name)) {
          LabelElementProto.Builder builder = LabelElementProto.newBuilder();
          builder.setColor(LABEL_COLOR);
          builder.setStringIndex(0);
          builder.setLocation(getCoords(ra, dec));
          result.add(builder.build());
        }
      }
      in.close();

      if (!CONSTELLATIONS.isEmpty()) {
        int i = 0;
        for (String constellation : CONSTELLATIONS) {
          System.out.printf("Missing: %d/%d %s\n", ++i, CONSTELLATION_ARRAY.length, constellation);
        }

        throw new RuntimeException();
      }

      System.out.println(num);
      return result;
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    return null;
  }

  private static float getRa(float ra) {
    float result = ra + 180;
    return result;
  }

  private static GeocentricCoordinatesProto getCoords(float ra, float dec) {
    return GeocentricCoordinatesProto.newBuilder()
        .setDeclination(dec)
        .setRightAscension(ra)
        .build();
  }

  private static AstronomicalSourcesProto.Builder readLines(String filename) {
    List<AstronomicalSourceProto.Builder> list = new ArrayList<AstronomicalSourceProto.Builder>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(new File(filename)));

      String s;
      int num = 0;
      while ((s = in.readLine()) != null) {
        s = s.trim();
        if (!s.equals("<LineString>")) continue;
        in.readLine(); // tessellate
        in.readLine(); // altitude
        s = in.readLine().trim();
        if (!s.equals("<coordinates>")) {
          throw new RuntimeException("Unexpected coordinate line: " + s);
        }

        LineElementProto.Builder builder = LineElementProto.newBuilder();
        builder.setColor(LABEL_COLOR);
        while (!(s = in.readLine().trim()).equals("</coordinates>")) {
          String[] tokens = s.split(",");
          float ra = getRa(Float.parseFloat(tokens[0]));
          float dec = Float.parseFloat(tokens[1]);
          builder.addVertex(getCoords(ra, dec));
        }
        addToList(list, builder);
      }

      System.out.println(num + " " + list.size());
      AstronomicalSourcesProto.Builder result = AstronomicalSourcesProto.newBuilder();
      for (AstronomicalSourceProto.Builder builder : list) {
        result.addSource(builder);
      }
      return result;
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    return null;
  }

  private static void addToList(List<AstronomicalSourceProto.Builder> list,
      LineElementProto.Builder newLine) {

    for (AstronomicalSourceProto.Builder constellation : list) {
      for (LineElementProto line : constellation.getLineList()) {
        if (sharePoint(line, newLine)) {
          constellation.addLine(newLine);
          return;
        }
      }
    }

    list.add(AstronomicalSourceProto.newBuilder().addLine(newLine));
  }

  private static boolean sharePoint(LineElementProto p1, LineElementProto.Builder p2) {
    for (GeocentricCoordinatesProto v1 : p1.getVertexList()) {
      for (GeocentricCoordinatesProto v2 : p2.getVertexList()) {
        if (getDistance(v1, v2) < 0.001) {
          return true;
        }
      }
    }
    return false;
  }

  private static double getDistance(GeocentricCoordinatesProto p1, GeocentricCoordinatesProto p2) {
    double t = 0.0;
    t += (p1.getRightAscension() - p2.getRightAscension()) *
        (p1.getRightAscension() - p2.getRightAscension());
    t += (p1.getDeclination() - p2.getDeclination()) * (p1.getDeclination() - p2.getDeclination());
    return Math.sqrt(t);
  }

  private static AstronomicalSourcesProto combineLabelsConstellations(
      List<LabelElementProto> labels, AstronomicalSourcesProto.Builder constellations) {
    AstronomicalSourcesProto.Builder result = AstronomicalSourcesProto.newBuilder();
    for (AstronomicalSourceProto constellation : constellations.getSourceList()) {
      ClosestConstellation cc = new ClosestConstellation();
      for (LineElementProto line : constellation.getLineList()) {
        for (GeocentricCoordinatesProto vertex : line.getVertexList()) {
          cc = checkDistances(vertex, labels, cc);
        }
      }

      AstronomicalSourceProto.Builder builder = AstronomicalSourceProto.newBuilder();
      builder.mergeFrom(constellation);
      if (cc.label != null) {
        builder.addLabel(cc.label);
        builder.setSearchLocation(cc.label.getLocation());
      }
      result.addSource(builder);
    }
    return result.build();
  }

  /** Returns the label which is closest to the given constellation center. */
  private static ClosestConstellation checkDistances(GeocentricCoordinatesProto coords,
      List<LabelElementProto> labels, ClosestConstellation cc) {

    for (LabelElementProto s : labels) {
      double dist = getDistance(coords, s.getLocation());
      if (dist < cc.distance) {
        cc.label = s;
        cc.distance = dist;
      }
    }
    return cc;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.out.println("Usage: BinaryConstellationWriter <inputfile> <outputprefix>");
      System.exit(0);
    }

    args[0] = args[0].trim();
    args[1] = args[1].trim();

    List<LabelElementProto> labels = readLabels(args[0]);
    AstronomicalSourcesProto.Builder constellations = readLines(args[0]);
    AstronomicalSourcesProto sources = combineLabelsConstellations(labels, constellations);

    FileOutputStream out = null;
    try {
      out = new FileOutputStream(args[1] + ".binary");
      sources.writeTo(out);
    } finally {
      Closeables.closeSilently(out);
    }

    PrintWriter writer = null;
    try {
      writer = new PrintWriter(new FileWriter(args[1] + ".ascii"));
      writer.append(sources.toString());
    } finally {
      Closeables.closeSilently(out);
    }

    System.out.println("Successfully wrote " + sources.getSourceCount() + " sources.");
  }

  private static class ClosestConstellation {
    LabelElementProto label = null;
    double distance = Double.MAX_VALUE;
  }
}

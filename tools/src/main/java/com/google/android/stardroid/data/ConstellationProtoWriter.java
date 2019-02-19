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
import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.source.proto.SourceFullProto.AstronomicalSourceProto;
import com.google.android.stardroid.source.proto.SourceFullProto.AstronomicalSourcesProto;
import com.google.android.stardroid.source.proto.SourceFullProto.GeocentricCoordinatesProto;
import com.google.android.stardroid.source.proto.SourceFullProto.LabelElementProto;
import com.google.android.stardroid.source.proto.SourceFullProto.LineElementProto;

import java.io.BufferedReader;
import java.io.File;
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
      new HashSet<>(Arrays.asList(CONSTELLATION_ARRAY));

  private static int LABEL_COLOR = 0x80c97cb2;

  private static final String NAME_DELIMITER = "[|]+";
  /**
   * Get the string id form of the object name (that is, of the form R.string.foo).
   * @param name object name
   */
  private static String rKeyFromName(String name) {
    return "R.string." + name.replaceAll(" ", "_").toLowerCase();
  }

  /**
   * Gets the list of constellation names.  First will be used for label, rest
   * as search terms.
   *
   * @param nameList pipe-separated object names
   */
  private static List<String> namesFromList(String nameList) {
    return Lists.asList(nameList.split(NAME_DELIMITER));
  }

  public static List<LabelWithSynonyms> readLabels(String filename) {
    List<LabelWithSynonyms> result = new ArrayList<>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(new File(filename)));

      String s;
      int num = 0;
      while ((s = in.readLine()) != null) {
        s = s.trim();
        if (!s.equals("<Placemark>")) continue;

        s = in.readLine().trim();
        if (s.indexOf("<name>") < 0) continue;

        String namesList = s.substring(6, s.length() - 7);
        List<String> names = namesFromList(namesList);
        if (names.isEmpty()) {
          throw new RuntimeException("Bad constellation name line " + s);
        }
        String labelName = names.get(0);

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


        if (CONSTELLATIONS.remove(labelName)) {
          LabelElementProto.Builder labelBuilder = LabelElementProto.newBuilder();
          labelBuilder.setColor(LABEL_COLOR);
          labelBuilder.setREMOVEStringIndex(rKeyFromName(labelName));
          labelBuilder.setLocation(getCoords(ra, dec));
          LabelWithSynonyms labelWithSynonyms = new LabelWithSynonyms(labelBuilder.build(), names);
          result.add(labelWithSynonyms);
          num++;
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

      System.out.println("Number of constellation names added: " + num);

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
    List<AstronomicalSourceProto.Builder> sourceList = new ArrayList<>();
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

        LineElementProto.Builder lineElementBuilder = LineElementProto.newBuilder();
        lineElementBuilder.setColor(LABEL_COLOR);
        while (!(s = in.readLine().trim()).equals("</coordinates>")) {
          String[] tokens = s.split(",");
          float ra = getRa(Float.parseFloat(tokens[0]));
          float dec = Float.parseFloat(tokens[1]);
          lineElementBuilder.addVertex(getCoords(ra, dec));
        }
        addToList(sourceList, lineElementBuilder);
      }

      System.out.println(num + " " + sourceList.size());
      AstronomicalSourcesProto.Builder result = AstronomicalSourcesProto.newBuilder();
      for (AstronomicalSourceProto.Builder sourceBuilder : sourceList) {
        result.addSource(sourceBuilder);
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
      List<LabelWithSynonyms> labels, AstronomicalSourcesProto.Builder constellations) {
    AstronomicalSourcesProto.Builder result = AstronomicalSourcesProto.newBuilder();
    for (AstronomicalSourceProto constellation : constellations.getSourceList()) {
      ClosestConstellation cc = new ClosestConstellation();
      for (LineElementProto line : constellation.getLineList()) {
        for (GeocentricCoordinatesProto vertex : line.getVertexList()) {
          cc = checkDistances(vertex, labels, cc);
        }
      }

      AstronomicalSourceProto.Builder sourceBuilder = AstronomicalSourceProto.newBuilder();
      sourceBuilder.mergeFrom(constellation);
      if (cc.label != null) {
        sourceBuilder.addLabel(cc.label);
        sourceBuilder.setSearchLocation(cc.label.getLocation());
        for (String name : cc.synonyms) {
          sourceBuilder.addREMOVENameIds(rKeyFromName(name));
        }
      }
      result.addSource(sourceBuilder);
    }
    return result.build();
  }

  /** Returns the label which is closest to the given constellation center. */
  private static ClosestConstellation checkDistances(GeocentricCoordinatesProto coords,
      List<LabelWithSynonyms> labels, ClosestConstellation cc) {

    for (LabelWithSynonyms l : labels) {
      double dist = getDistance(coords, l.label.getLocation());
      if (dist < cc.distance) {
        cc.label = l.label;
        cc.distance = dist;
        cc.synonyms = l.synonyms;
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

    List<LabelWithSynonyms> labels = readLabels(args[0]);
    AstronomicalSourcesProto.Builder constellations = readLines(args[0]);
    AstronomicalSourcesProto sources = combineLabelsConstellations(labels, constellations);

    PrintWriter writer = null;
    try {
      writer = new PrintWriter(new FileWriter(args[1] + "_R.ascii"));
      writer.append(sources.toString());
    } finally {
      Closeables.closeSilently(writer);
    }

    System.out.println("Successfully wrote " + sources.getSourceCount() + " sources.");
  }

  private static class ClosestConstellation {
    LabelElementProto label = null;
    double distance = Double.MAX_VALUE;
    List<String> synonyms;
  }

  private static class LabelWithSynonyms {
    LabelWithSynonyms(LabelElementProto label, List<String> synonyms) {
      this.label = label;
      this.synonyms = synonyms;
    }
    LabelElementProto label;
    List<String> synonyms;
  }
}

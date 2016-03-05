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

import com.google.android.stardroid.base.Lists;
import com.google.android.stardroid.source.proto.SourceFullProto.AstronomicalSourceProto;
import com.google.android.stardroid.source.proto.SourceFullProto.AstronomicalSourcesProto;
import com.google.android.stardroid.source.proto.SourceFullProto.GeocentricCoordinatesProto;
import com.google.android.stardroid.source.proto.SourceFullProto.LabelElementProto;
import com.google.android.stardroid.source.proto.SourceFullProto.LineElementProto;
import com.google.common.io.Closeables;

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
import java.util.Set;

/**
 * Class for reading the constellation KML file and writing the contents to a
 * protocol buffer
 *
 * @author Brent Bryan
 */
public class ConstellationProtoWriter {
  private static final double ANGULAR_TOLERANCE_FOR_COINCIDENCE = 0.001;
  private static String[] CONSTELLATION_ARRAY_WHITELIST =
      {
      "Andromeda",
      "Aquarius",
      "Aquila",
      "Aries",
      "Auriga",
      "Bootes",
      "Cancer",
      "Canis Major",
      "Capricornus",
      "Carina",
      "Cassiopeia",
      "Centaurus",
      "Cetus",
      "Crux",
      "Cygnus",
      "Draco",
      "Eridanus",
      "Gemini",
      "Grus",
      "Hercules",
      "Hydrus",
      "Leo",
      "Libra",
      "Lupus",
      "Lyra",
      "Ophiuchus",
      "Orion",
      "Pavo",
      "Pegasus",
      "Perseus",
      "Phoenix",
      "Pisces",
      "Piscis Austrinus",
      "Puppis",
      "Sagittarius",
      "Scorpius",
      "Taurus",
      "Tucana",
      "Ursa Major",
      "Ursa Minor",
      "Vela",
      "Virgo",
      };

  private static final HashSet<String> CONSTELLATION_WHITELIST =
      new HashSet<>(Arrays.asList(CONSTELLATION_ARRAY_WHITELIST));

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
    List<String> namesToTranslate = new ArrayList<>();
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


        if (CONSTELLATION_WHITELIST.remove(labelName)) {
          System.out.println("Adding label for " + labelName);
          LabelElementProto.Builder labelBuilder = LabelElementProto.newBuilder();
          labelBuilder.setColor(LABEL_COLOR);
          labelBuilder.setREMOVEStringIndex(rKeyFromName(labelName));
          labelBuilder.setLocation(getCoords(ra, dec));
          LabelWithSynonyms labelWithSynonyms = new LabelWithSynonyms(labelBuilder.build(), names);
          result.add(labelWithSynonyms);
          num++;
        } else {
          System.out.println("Not adding label for " + labelName);
          namesToTranslate.add(labelName);
        }

      }
      in.close();

      if (!CONSTELLATION_WHITELIST.isEmpty()) {
        int i = 0;
        for (String constellation : CONSTELLATION_WHITELIST) {
          System.out.printf("Missing: %d/%d %s\n", ++i, CONSTELLATION_ARRAY_WHITELIST.length, constellation);
        }

        throw new RuntimeException();
      }

      System.out.println("Number of constellation names added: " + num);
      System.out.println("Missing translations: ");
      for (String name : namesToTranslate) {
        System.out.println(String.format(
            "<string name=\"%s\" translation_description=\"Name of the %s constellation\">%s</string>",
            rKeyFromName(name), name, name));
      }

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
        if (naiveAngularDistanceBetweenPoints(v1, v2) < ANGULAR_TOLERANCE_FOR_COINCIDENCE) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Calculates the supposed angular distance between two points.
   * It's not going to work near the poles - two identical points could have 90 deg dec and
   * different RAs and this would calculate them as distant.
   */
  private static double naiveAngularDistanceBetweenPoints(
      GeocentricCoordinatesProto p1, GeocentricCoordinatesProto p2) {
    double t = 0.0;
    t += (p1.getRightAscension() - p2.getRightAscension()) *
        (p1.getRightAscension() - p2.getRightAscension());
    t += (p1.getDeclination() - p2.getDeclination()) * (p1.getDeclination() - p2.getDeclination());
    return Math.sqrt(t);
  }

  private static AstronomicalSourcesProto combineLabelsConstellations(
      Set<LabelWithSynonyms> labels, AstronomicalSourcesProto.Builder constellations) {
    AstronomicalSourcesProto.Builder result = AstronomicalSourcesProto.newBuilder();
    System.out.println("Combining " + labels.size() + " labels with "
        + constellations.getSourceList().size() + " constellations");
    for (AstronomicalSourceProto constellation : constellations.getSourceList()) {
      GeocentricCoordinatesProto centroid = getNaiveCentroid(constellation);
      double closestDistanceSoFar = Double.MAX_VALUE;
      LabelWithSynonyms closestLabelSoFar = null;
      for (LabelWithSynonyms label : labels) {
        double labelConstellationDistance = naiveAngularDistanceBetweenPoints(
            label.label.getLocation(), centroid);
        if (labelConstellationDistance < closestDistanceSoFar) {
          closestDistanceSoFar = labelConstellationDistance;
          closestLabelSoFar = label;
        }
      }
      AstronomicalSourceProto.Builder sourceBuilder = AstronomicalSourceProto.newBuilder();
      sourceBuilder.mergeFrom(constellation);
      if (closestLabelSoFar != null) {
        labels.remove(closestLabelSoFar);
        sourceBuilder.addLabel(closestLabelSoFar.label);
        sourceBuilder.setSearchLocation(closestLabelSoFar.label.getLocation());
        for (String name : closestLabelSoFar.synonyms) {
          sourceBuilder.addREMOVENameIds(rKeyFromName(name));
        }
      }
      result.addSource(sourceBuilder);
    }
    return result.build();
  }

  private static GeocentricCoordinatesProto getNaiveCentroid(
      AstronomicalSourceProto constellation) {
    double ra = 0;
    double dec = 0;
    int count = 0;
    for (LineElementProto line : constellation.getLineList()) {
      for (GeocentricCoordinatesProto vertex : line.getVertexList()) {
        ra += vertex.getRightAscension();
        dec += vertex.getDeclination();
        count++;
      }
    }
    GeocentricCoordinatesProto result = GeocentricCoordinatesProto.newBuilder()
        .setDeclination((float) (dec / count)).setRightAscension((float) ra / count).build();
    return result;
  }

  /**
   * Processes the constellation kml and turns it into a proto buffer.  Does a naive
   * job of matching constellation labels to the constellations.  For each constellation we
   * find the nearest label that hasn't yet been assigned.  This just guarantees that all
   * the labels will be used - some are certainly going to be applied to the wrong
   * constellation.
   * TODO(johntaylor): correct the constellation name assignment before we allow individual
   * constellations to be selected in the app.
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.out.println("Usage: ConstellationWriter <inputfile> <outputprefix>");
      System.exit(0);
    }

    args[0] = args[0].trim();
    args[1] = args[1].trim();

    List<LabelWithSynonyms> labels = readLabels(args[0]);
    System.out.println("number of labels: " + labels.size());
    AstronomicalSourcesProto.Builder constellations = readLines(args[0]);
    System.out.println("number of constellations: " + constellations.getSourceList().size());
    AstronomicalSourcesProto sources = combineLabelsConstellations(
        new HashSet(labels), constellations);

    PrintWriter writer = null;
    try {
      writer = new PrintWriter(new FileWriter(args[1] + "_R.ascii"));
      writer.append(sources.toString());
    } finally {
      Closeables.close(writer, false);
    }

    System.out.println("Successfully wrote " + sources.getSourceCount() + " sources.");
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

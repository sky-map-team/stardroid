package com.google.android.stardroid.data.deprecated;

import com.google.android.stardroid.layers.BinarySourceIO;
import com.google.android.stardroid.source.impl.AbstractSource;
import com.google.android.stardroid.source.impl.LineSourceImpl;
import com.google.android.stardroid.source.impl.TextSourceImpl;
import com.google.android.stardroid.units.RaDec;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class BinaryConstellationWriter {
  private static String[] majorConstellations = {"Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo", "Libra",
    "Scorpius", "Sagittarius", "Capricornus", "Aquarius", "Pisces", "Ophiuchus", "Orion",
    "Cassiopeia", "Ursa Major", "Draco", "Crux", "Pegasus", "Andromeda", "Ursa Minor", "Canis Major",
    "Perseus", "Hercules", "Aquila", "Cygnus", "Lyra", "Bootes", "Eridanus",
    "Carina", "Vela", "Puppis", "Centaurus", "Auriga", "Pavo", "Hydrus", "Phoenix", "Piscis Austrinus",
    "Grus", "Cetus", "Lupus", "Tucana"};

  private static HashSet<String> constellations = new HashSet<String>(Arrays.asList(majorConstellations));


  public static int labelColor = 0x80c97cb2;

  private static List<AbstractSource> readLabels(String filename) {
    List<AbstractSource> result = new ArrayList<AbstractSource>();
    try {
      BufferedReader in = new BufferedReader(new FileReader(new File(filename)));

      String s;
      int num = 0;
      while ((s = in.readLine()) != null) {
        s = s.trim();
        if (!s.equals("<Placemark>")) continue;

        s = in.readLine().trim();
        if (s.indexOf("<name>") < 0) continue;

        String name = s.substring(6, s.length()-7);
        in.readLine(); // style url line.
        in.readLine(); // Point
        s = in.readLine().trim();
        if (s.indexOf("<coordinates>") < 0 && s.indexOf("</coordinates>") < 0) {
          throw new RuntimeException("Unexpected coordinate line: "+s);
        }
        s = s.substring(13, s.length()-14);
        String[] tokens = s.split(",");
        float ra = getRa(Float.parseFloat(tokens[0]));
        float dec = Float.parseFloat(tokens[1]);

        if (constellations.remove(name)) {
          result.add(new TextSourceImpl(ra, dec, name, labelColor));
        }
      }
      in.close();

      if (!constellations.isEmpty()) {
        int i = 0;
        for (String constellation : constellations) {
          System.out.printf("Missing: %d/%d %s\n", ++i, majorConstellations.length, constellation);
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

  private static List<List<LineSourceImpl>> readLines(String filename) {
    List<List<LineSourceImpl>> result = new ArrayList<List<LineSourceImpl>>();
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
          throw new RuntimeException("Unexpected coordinate line: "+s);
        }

        LineSourceImpl pls = new LineSourceImpl(labelColor);
        while (!(s = in.readLine().trim()).equals("</coordinates>")) {
          String[] tokens = s.split(",");
          float ra = getRa(Float.parseFloat(tokens[0]));
          float dec = Float.parseFloat(tokens[1]);
          RaDec d = new RaDec(ra, dec);
          pls.raDecs.add(d);
        }
        addToList(result, pls);
      }

      System.out.println(num+" "+result.size());
      return result;
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    return null;
  }

  private static void addToList(List<List<LineSourceImpl>> list, LineSourceImpl s) {
    for (List<LineSourceImpl> constellation : list) {
      for (LineSourceImpl line : constellation) {
        if (sharePoint(line, s)) {
          constellation.add(s);
          return;
        }
      }
    }
    List<LineSourceImpl> newConstellation = new ArrayList<LineSourceImpl>();
    newConstellation.add(s);
    list.add(newConstellation);
  }

  private static boolean sharePoint(LineSourceImpl s1, LineSourceImpl s2) {
    for (RaDec raDec1 : s1.raDecs) {
      for (RaDec raDec2 : s2.raDecs) {
        if (getDistance(raDec1, raDec2) < 0.001) {
          return true;
        }
      }
    }
    return false;
  }

  private static void combineLabelsConstellations(List<AbstractSource> labels, List<List<LineSourceImpl>> constellations) {
    for (List<LineSourceImpl> constellation : constellations) {
      ClosestConstellation cc = new ClosestConstellation();
      for (LineSourceImpl line : constellation) {
        for (RaDec vertex : line.raDecs) {
          cc = checkDistances(vertex, labels, cc);
        }
      }

      /*
      for (PolyLineSource line : constellation) {
        line.magnitude = cc.source.magnitude;
      }
      */
    }
  }

  private static ClosestConstellation checkDistances(RaDec raDec, List<AbstractSource> labels,
      ClosestConstellation cc) {

    for (AbstractSource s : labels) {
      RaDec thatRaDec = RaDec.getInstance(s.getLocation());
      double dist = getDistance(thatRaDec, raDec);
      if (dist < cc.distance) {
        cc.source = s;
        cc.distance = dist;
      }
    }
    return cc;
  }

  private static double getDistance(RaDec raDec1, RaDec raDec2) {
    double t = 0.0;
    t += (raDec1.ra - raDec2.ra)*(raDec1.ra - raDec2.ra);
    t += (raDec1.dec - raDec2.dec)*(raDec1.dec - raDec2.dec);
    return Math.sqrt(t);
  }

  static class ClosestConstellation {
    AbstractSource source = null;
    double distance = Double.MAX_VALUE;
  }

  private static void writeLabels(List<AbstractSource> labels, DataOutputStream out) throws IOException {
    for (AbstractSource s : labels) {
      BinarySourceIO.writeSource(s, out);
    }
    System.out.println("wrote: "+labels.size()+" labels");
  }

  private static void writeLines(List<List<LineSourceImpl>> constellations, DataOutputStream out) throws IOException {
    int num = 0;
    for (List<LineSourceImpl> constellation : constellations) {
      for (LineSourceImpl s: constellation) {
        BinarySourceIO.writeSource(s, out);
        num++;
      }
    }
    System.out.println("wrote: "+num+" lines");
  }

  public static void main(String[] args) {
    if (args.length != 2) {
      System.out.println("Usage: BinaryConstellationWriter <infile> <outputfile>");
      System.exit(0);
    }

    args[0] = args[0].trim();
    args[1] = args[1].trim();

    List<AbstractSource> labels = readLabels(args[0]);
    List<List<LineSourceImpl>> constellations = readLines(args[0]);
    combineLabelsConstellations(labels, constellations);

    try {
      DataOutputStream out = new DataOutputStream(new FileOutputStream(new File(args[1])));
      writeLabels(labels, out);
      writeLines(constellations, out);
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }
}

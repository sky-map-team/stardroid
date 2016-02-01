package com.google.android.stardroid.data.deprecated;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Color;

import com.google.android.stardroid.source.impl.AbstractSource;
import com.google.android.stardroid.source.impl.PointSourceImpl;
import com.google.android.stardroid.source.impl.TextSourceImpl;
import com.google.android.stardroid.util.StarAttributeCalculator;


public class BinaryStarWriter extends AbstractBinaryWriter {
  private static final int STAR_COLOR = 0xcfcccf;


  // Helper methods for generating Sources.
  private AbstractSource newStar(float magnitude, float ra, float dec) {
    int color = StarAttributeCalculator.getColor(magnitude, Color.WHITE);
    int size = StarAttributeCalculator.getSize(magnitude);
    PointSourceImpl s = new PointSourceImpl(ra, dec,  color, size);
    return s;
  }

  private List<AbstractSource> newNamedStar(float magnitude, float ra, float dec, String name) {
    List<AbstractSource> result = new ArrayList<AbstractSource>();
    result.add(newStar(magnitude, ra, dec));
    result.add(new TextSourceImpl(ra, dec, name, STAR_COLOR));
    return result;
  }

  @Override
  protected List<AbstractSource> getSourcesFromLine(String line, int count) {
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
      return new ArrayList<AbstractSource>();
    }

    if (name == null || name.trim().equals("")) {
      List<AbstractSource> result = new ArrayList<AbstractSource>();
      result.add(newStar(magnitude, ra, dec));
      return result;
    } else {
      return newNamedStar(magnitude, ra, dec, name);
    }
  }

  public static void main(String[] args) {
    new BinaryStarWriter().run(args);
  }
}
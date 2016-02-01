package com.google.android.stardroid.data.deprecated;

import com.google.android.stardroid.source.impl.AbstractSource;
import com.google.android.stardroid.source.impl.PointSourceImpl;
import com.google.android.stardroid.source.impl.TextSourceImpl;

import java.util.ArrayList;
import java.util.List;


public class BinaryMessierWriter extends AbstractBinaryWriter {
  private final int messierSize = 3;

  @Override
  protected List<AbstractSource> getSourcesFromLine(String line, int count) {
    // name, type, RA(h), dec(degrees), magnitude, size, ngc, constellation, names, common name
    // Of these, only name(0), ra(2), & dec(3) are used.
    if (line.startsWith("Object,Type")) {
      return new ArrayList<AbstractSource>();
    }

    String[] tokens = line.split(",");
    int labelColor = 0x48a841;  // argb
    int pointColor = 0x48a841;  // abgr (!)

    // Convert from hours to degrees.
    float ra = 15*Float.parseFloat(tokens[2]);
    float dec = Float.parseFloat(tokens[3]);

    List<AbstractSource> result = new ArrayList<AbstractSource>();
    result.add(new TextSourceImpl(ra, dec, tokens[0], labelColor));
    result.add(new PointSourceImpl(ra, dec, pointColor, messierSize));
    return result;
  }

  public static void main(String[] args) {
    new BinaryMessierWriter().run(args);
  }
}
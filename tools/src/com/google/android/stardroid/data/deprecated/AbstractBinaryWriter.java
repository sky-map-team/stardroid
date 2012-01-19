package com.google.android.stardroid.data.deprecated;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import com.google.android.stardroid.layers.BinarySourceIO;
import com.google.android.stardroid.source.impl.AbstractSource;


public abstract class AbstractBinaryWriter {

  public AbstractBinaryWriter() {
  }

  public void writeBinaryFile(String inputFilename, String outputFilename) {
    BufferedReader in = null;
    DataOutputStream out = null;

    try {
      in = new BufferedReader(new FileReader(new File(inputFilename)));
      out = new DataOutputStream(new FileOutputStream(new File(outputFilename)));

      writeBinaryFile(in, out);

      in.close();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (in != null) try {
        in.close();
      } catch (IOException e) {
      }

      if (out != null) try {
        out.close();
      } catch (IOException e) {
      }
    }
  }

  protected abstract List<AbstractSource> getSourcesFromLine(String line, int count);

  public void writeBinaryFile(BufferedReader in, DataOutputStream out) throws IOException {
    String line;
    int count = 0;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.equals("")) continue;

      List<AbstractSource> sources = getSourcesFromLine(line, count);
      for (AbstractSource s : sources) {
        BinarySourceIO.writeSource(s, out);
        count++;
      }
    }
    System.out.println("Successfully wrote "+count+" sources.");
  }

  public void run(String[] args) {
    if (args.length != 2) {
      System.out.printf("Usage: %s <inputfile> <outputfile>", this.getClass().getCanonicalName());
      System.exit(1);
    }
    args[0] = args[0].trim();
    args[1] = args[1].trim();

    // System.out.println("Input File: "+args[0]);
    // System.out.println("Output File: "+args[1]);
    writeBinaryFile(args[0], args[1]);
    // readBinaryFile(args[1]);
  }
}
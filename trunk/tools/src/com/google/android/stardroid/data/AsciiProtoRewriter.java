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

import com.google.android.stardroid.R;
import com.google.android.stardroid.base.Closeables;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Runs through the ascii protocol buffers and replaces all R.string.<constant>
 * strings with the true int values.
 *
 * @author Brent Bryan
 */
public class AsciiProtoRewriter {

  // No instances
  private AsciiProtoRewriter() {}

  private static void printFields() {
    for (Field f : R.string.class.getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers())) {
        System.out.println(f.getName());
      }
    }
  }

  private static int getString(String s) {
    try {
      String name = s.substring(9);
      Field f = R.string.class.getDeclaredField(name);
      // System.out.println("replacing: " + s);
      return f.getInt(null);
    } catch (SecurityException e) {
      printFields();
      throw new RuntimeException("Did not find: " + s, e);
    } catch (NoSuchFieldException e) {
      printFields();
      throw new RuntimeException("Did not find: " + s, e);
    } catch (IllegalArgumentException e) {
      printFields();
      throw new RuntimeException("Did not find: " + s, e);
    } catch (IllegalAccessException e) {
      printFields();
      throw new RuntimeException("Did not find: " + s, e);
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1 || !args[0].endsWith("_R.ascii")) {
      System.out.println("Usage: AsciiToBinaryProtoWriter <inputprefix>_R.ascii");
      System.exit(1);
    }

    String inputFile = args[0];
    String outputFile = args[0].replaceAll("_R.ascii", ".ascii");

    BufferedReader in = null;
    PrintWriter out = null;
    try {
      in = new BufferedReader(new FileReader(inputFile));
      out = new PrintWriter(new FileWriter(outputFile));

      String s;
      while ((s = in.readLine()) != null) {
        if (s.contains("REMOVE")) {
          continue;
        }

        String[] tokens = s.split("\\s+");
        for (String token : tokens) {
          if (token.startsWith("R.string")) {
            int value = getString(token);
            s = s.replaceAll(token, "" + value);
          }
        }
        out.println(s);
      }
    } finally {
      Closeables.closeSilently(in);
      Closeables.closeSilently(out);
    }
  }
}

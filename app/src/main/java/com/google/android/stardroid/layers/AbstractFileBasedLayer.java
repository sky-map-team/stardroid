// Copyright 2009 Google Inc.
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

package com.google.android.stardroid.layers;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType;
import com.google.android.stardroid.source.AstronomicalSource;
import com.google.android.stardroid.source.proto.ProtobufAstronomicalSource;
import com.google.android.stardroid.source.proto.SourceProto.AstronomicalSourceProto;
import com.google.android.stardroid.source.proto.SourceProto.AstronomicalSourcesProto;
import com.google.android.stardroid.util.Blog;
import com.google.android.stardroid.util.MiscUtil;
import com.google.common.io.Closeables;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Implementation of the {@link Layer} interface which reads its data from
 * a file during the {@link Layer#initialize} method.
 *
 * @author Brent Bryan
 * @author John Taylor
 */

public abstract class AbstractFileBasedLayer extends AbstractSourceLayer {
  private static final String TAG = MiscUtil.getTag(AbstractFileBasedLayer.class);
  private static final Executor BACKGROUND_EXECUTOR = Executors.newFixedThreadPool(1);

  private final AssetManager assetManager;
  private final String fileName;
  private final List<AstronomicalSource> fileSources = new ArrayList<>();

  public AbstractFileBasedLayer(AssetManager assetManager, Resources resources, String fileName) {
    super(resources, false);
    this.assetManager = assetManager;
    this.fileName = fileName;
  }

  @Override
  public synchronized void initialize() {
    BACKGROUND_EXECUTOR.execute(new Runnable() {
      public void run() {
        readSourceFile(fileName);
        AbstractFileBasedLayer.super.initialize();
      }
    });
  }

  @Override
  protected void initializeAstroSources(ArrayList<AstronomicalSource> sources) {
    sources.addAll(fileSources);
  }

  private void readSourceFile(String sourceFilename) {
    Log.d(TAG, "Loading Proto File: " + sourceFilename + "...");
    InputStream in = null;
    try {
      in = assetManager.open(sourceFilename, AssetManager.ACCESS_BUFFER);
      AstronomicalSourcesProto.Builder builder = AstronomicalSourcesProto.newBuilder();
      builder.mergeFrom(in);

      for (AstronomicalSourceProto proto : builder.build().getSourceList()) {
        fileSources.add(new ProtobufAstronomicalSource(proto, getResources()));
      }
      Log.d(TAG, "Found: " + fileSources.size() + " sources");
      String s = String.format("Finished Loading: %s | Found %s sourcs.\n",
          sourceFilename, fileSources.size());
       Blog.d(this, s);

       refreshSources(EnumSet.of(UpdateType.Reset));
    } catch (IOException e) {
      Log.e(TAG, "Unable to open " + sourceFilename);
    } finally {
      Closeables.closeQuietly(in);
    }

  }
}

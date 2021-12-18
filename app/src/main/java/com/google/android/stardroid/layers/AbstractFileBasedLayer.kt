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
package com.google.android.stardroid.layers

import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.renderables.AstronomicalRenderable
import com.google.android.stardroid.renderables.proto.ProtobufAstronomicalRenderable
import com.google.android.stardroid.source.proto.SourceProto
import com.google.android.stardroid.util.MiscUtil
import com.google.common.io.Closeables
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Implementation of the [Layer] interface which reads its data from
 * a file during the [Layer.initialize] method.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
abstract class AbstractFileBasedLayer(
    private val assetManager: AssetManager,
    resources: Resources,
    private val fileName: String
) : AbstractRenderablesLayer(resources, false) {
    private val fileSources: MutableList<AstronomicalRenderable> = ArrayList()
    @Synchronized
    override fun initialize() {
        BACKGROUND_EXECUTOR.execute {
            readSourceFile(fileName)
            super@AbstractFileBasedLayer.initialize()
        }
    }

    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.addAll(fileSources)
    }

    private fun readSourceFile(sourceFilename: String) {
        Log.d(TAG, "Loading Proto File: $sourceFilename...")
        var inputStream: InputStream? = null
        try {
            inputStream = assetManager.open(sourceFilename, AssetManager.ACCESS_BUFFER)
            val parser = SourceProto.AstronomicalSourcesProto.parser()
            val sources = parser.parseFrom(inputStream)
            for (proto in sources.sourceList) {
                fileSources.add(
                    ProtobufAstronomicalRenderable(
                        proto,
                        resources
                    )
                )
            }
            Log.d(TAG, "Found: " + fileSources.size + " sources")
            val s = String.format(
                "Finished Loading: %s | Found %s sourcs.\n",
                sourceFilename, fileSources.size
            )
            Log.d(TAG, s)
            refreshSources(EnumSet.of(UpdateType.Reset))
        } catch (e: IOException) {
            Log.e(TAG, "Unable to open $sourceFilename")
        } finally {
            Closeables.closeQuietly(inputStream)
        }
    }

    companion object {
        private val TAG = MiscUtil.getTag(AbstractFileBasedLayer::class.java)
        private val BACKGROUND_EXECUTOR: Executor = Executors.newFixedThreadPool(1)
    }
}
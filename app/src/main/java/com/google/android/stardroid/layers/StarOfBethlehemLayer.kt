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
package com.google.android.stardroid.layers

import android.content.res.Resources
import android.util.Log
import com.google.android.stardroid.R
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.source.AbstractAstronomicalRenderable
import com.google.android.stardroid.source.AstronomicalRenderable
import com.google.android.stardroid.source.ImagePrimitive
import com.google.android.stardroid.source.Renderable
import com.google.android.stardroid.util.MiscUtil
import java.util.*

/**
 * A [Layer] specially for Christmas.
 *
 * @author John Taylor
 */
class StarOfBethlehemLayer(private val model: AstronomerModel, resources: Resources) :
    AbstractSourceLayer(resources, true) {
    override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
        sources.add(StarOfBethlehemSource(model, resources))
    }

    override val layerDepthOrder: Int
        get() = 40

    // TODO(brent): Remove this.
    override val preferenceId: String
        get() = "source_provider.0"
    override val layerName: String
        get() = "Easter Egg"
    protected override val layerNameId: Int
        protected get() = R.string.show_stars_pref

    private class StarOfBethlehemSource(private val model: AstronomerModel, resources: Resources?) :
        AbstractAstronomicalRenderable() {
        private val imageSources: MutableList<ImagePrimitive> = ArrayList()
        private var lastUpdateTimeMs = 0L
        private val coords: Vector3
        private val theImage: ImagePrimitive
        private fun updateStar() {
            lastUpdateTimeMs = model.time.time
            // We will only show the star if it's Christmas Eve.
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = model.time.time
            theImage.setUpVector(UP)
            // TODO(johntaylor): consider varying the sizes by scaling factor as time progresses.
            if (calendar[Calendar.MONTH] == Calendar.DECEMBER
                && calendar[Calendar.DAY_OF_MONTH] == 25
            ) {
                Log.d(TAG, "Showing Easter Egg")
                theImage.setImageId(R.drawable.star_of_b)
                val zenith = model.zenith
                val east = model.east
                coords.assign(
                    (zenith.x + 2 * east.x) / 3,
                    (zenith.y + 2 * east.y) / 3,
                    (zenith.z + 2 * east.z) / 3
                )
                theImage.setUpVector(zenith)
            } else {
                theImage.setImageId(R.drawable.blank)
            }
        }

        override fun initialize(): Renderable {
            updateStar()
            return this
        }

        override fun update(): EnumSet<UpdateType> {
            val updateTypes = EnumSet.noneOf(UpdateType::class.java)
            if (Math.abs(model.time.time - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
                updateStar()
                updateTypes.add(UpdateType.UpdateImages)
                updateTypes.add(UpdateType.UpdatePositions)
            }
            return updateTypes
        }

        override fun getImages(): List<ImagePrimitive> {
            return imageSources
        }

        companion object {
            private val UP = Vector3(0.0f, 1.0f, 0.0f)
            private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_MINUTE
            private const val SCALE_FACTOR = 0.03f
        }

        init {
            coords = Vector3(1f, 0f, 0f)
            // star_off2 is a 1pxX1px image that should be invisible.
            // We'd prefer not to show any image except on the Christmas dates, but there
            // appears to be a bug in the renderer in that new images added later don't get
            // picked up, even if we return UpdateType.Reset.
            theImage = ImagePrimitive(coords, resources, R.drawable.blank, UP, SCALE_FACTOR)
            imageSources.add(theImage)
        }
    }

    companion object {
        val TAG = MiscUtil.getTag(StarOfBethlehemLayer::class.java)
    }
}
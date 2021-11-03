// Copyright 2011 Google Inc.
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

import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.math.Vector3.assign
import com.google.android.stardroid.space.Universe.solarSystemObjectFor
import com.google.android.stardroid.space.CelestialObject.getRaDec
import android.content.res.AssetManager
import com.google.android.stardroid.layers.AbstractSourceLayer
import com.google.android.stardroid.source.AstronomicalSource
import com.google.android.stardroid.layers.AbstractFileBasedLayer
import com.google.android.stardroid.source.proto.ProtobufAstronomicalSource
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.util.MiscUtil
import com.google.android.stardroid.renderer.RendererControllerBase.RenderManager
import com.google.android.stardroid.renderer.RendererController
import com.google.android.stardroid.layers.AbstractLayer
import com.google.android.stardroid.renderer.RendererController.AtomicSection
import com.google.android.stardroid.renderer.util.UpdateClosure
import com.google.android.stardroid.source.TextPrimitive
import com.google.android.stardroid.source.PointPrimitive
import com.google.android.stardroid.source.LinePrimitive
import com.google.android.stardroid.source.ImagePrimitive
import com.google.android.stardroid.renderer.RendererControllerBase
import com.google.android.stardroid.search.PrefixStore
import com.google.android.stardroid.layers.AbstractSourceLayer.SourceUpdateClosure
import com.google.android.stardroid.source.Sources
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.renderer.util.AbstractUpdateClosure
import com.google.android.stardroid.layers.EclipticLayer.EclipticSource
import com.google.android.stardroid.R
import com.google.android.stardroid.source.AbstractAstronomicalSource
import com.google.android.stardroid.layers.GridLayer.GridSource
import com.google.android.stardroid.math.RaDec
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.layers.HorizonLayer.HorizonSource
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.layers.IssLayer.IssSource
import com.google.android.stardroid.layers.IssLayer.OrbitalElementsGrabber
import kotlin.Throws
import com.google.android.stardroid.ephemeris.OrbitalElements
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import android.text.format.DateFormat
import com.google.android.stardroid.layers.LayerManager
import com.google.android.stardroid.search.SearchTermsProvider.SearchTerm
import com.google.android.stardroid.layers.MeteorShowerLayer.Shower
import com.google.android.stardroid.layers.MeteorShowerLayer
import com.google.android.stardroid.layers.MeteorShowerLayer.MeteorRadiantSource
import com.google.android.stardroid.ephemeris.Planet
import com.google.android.stardroid.ephemeris.PlanetSource
import com.google.android.stardroid.layers.SkyGradientLayer
import com.google.android.stardroid.layers.StarOfBethlehemLayer.StarOfBethlehemSource
import com.google.android.stardroid.layers.StarOfBethlehemLayer
import java.util.*

/**
 * A [Layer] to show well-known meteor showers.
 *
 * @author John Taylor
 */
class MeteorShowerLayer(private val model: AstronomerModel, resources: Resources) :
    AbstractSourceLayer(resources, true) {
    private val showers: MutableList<Shower> = ArrayList()

    /**
     * Represents a meteor shower.
     */
    private class Shower(
        name: String?, val nameId: Int, val radiant: Vector3,
        val start: Date, val peak: Date, val end: Date, val peakMeteorsPerHour: Int
    )

    private fun initializeShowers() {
        // A list of all the meteor showers with > 10 per hour
        // Source: http://www.imo.net/calendar/2011#table5
        // Note the zero-based month. 10=November
        // Actual start for Quadrantids is December 28 - but we can't cross a year boundary.
        showers.add(
            Shower(
                "quadrantids",
                R.string.quadrantids, getGeocentricCoords(230f, 49f),
                Date(ANY_OLD_YEAR, 0, 1),
                Date(ANY_OLD_YEAR, 0, 4),
                Date(ANY_OLD_YEAR, 0, 12),
                120
            )
        )
        showers.add(
            Shower(
                "lyrids",
                R.string.lyrids, getGeocentricCoords(271f, 34f),
                Date(ANY_OLD_YEAR, 3, 16),
                Date(ANY_OLD_YEAR, 3, 22),
                Date(ANY_OLD_YEAR, 3, 25),
                18
            )
        )
        showers.add(
            Shower(
                "e-aquariids",
                R.string.aquariids, getGeocentricCoords(338f, -1f),
                Date(ANY_OLD_YEAR, 3, 19),
                Date(ANY_OLD_YEAR, 4, 6),
                Date(ANY_OLD_YEAR, 4, 28),
                70
            )
        )
        showers.add(
            Shower(
                "d-aquariids",
                R.string.deltaaquariids, getGeocentricCoords(340f, -16f),
                Date(ANY_OLD_YEAR, 6, 12),
                Date(ANY_OLD_YEAR, 6, 30),
                Date(ANY_OLD_YEAR, 7, 23),
                16
            )
        )
        showers.add(
            Shower(
                "perseids",
                R.string.perseids, getGeocentricCoords(48f, 58f),
                Date(ANY_OLD_YEAR, 6, 17),
                Date(ANY_OLD_YEAR, 7, 13),
                Date(ANY_OLD_YEAR, 7, 24),
                100
            )
        )
        showers.add(
            Shower(
                "orionids",
                R.string.orionids, getGeocentricCoords(95f, 16f),
                Date(ANY_OLD_YEAR, 9, 2),
                Date(ANY_OLD_YEAR, 9, 21),
                Date(ANY_OLD_YEAR, 10, 7),
                25
            )
        )
        showers.add(
            Shower(
                "leonids",
                R.string.leonids, getGeocentricCoords(152f, 22f),
                Date(ANY_OLD_YEAR, 10, 6),
                Date(ANY_OLD_YEAR, 10, 18),
                Date(ANY_OLD_YEAR, 10, 30),
                20
            )
        )
        showers.add(
            Shower(
                "puppid-velids",
                R.string.puppidvelids, getGeocentricCoords(123f, -45f),
                Date(ANY_OLD_YEAR, 11, 1),
                Date(ANY_OLD_YEAR, 11, 7),
                Date(ANY_OLD_YEAR, 11, 15),
                10
            )
        )
        showers.add(
            Shower(
                "geminids",
                R.string.geminids, getGeocentricCoords(112f, 33f),
                Date(ANY_OLD_YEAR, 11, 7),
                Date(ANY_OLD_YEAR, 11, 14),
                Date(ANY_OLD_YEAR, 11, 17),
                120
            )
        )
        showers.add(
            Shower(
                "ursides",
                R.string.ursids, getGeocentricCoords(217f, 76f),
                Date(ANY_OLD_YEAR, 11, 17),
                Date(ANY_OLD_YEAR, 11, 23),
                Date(ANY_OLD_YEAR, 11, 26),
                10
            )
        )
    }

    override fun initializeAstroSources(sources: ArrayList<AstronomicalSource>) {
        for (shower in showers) {
            sources.add(MeteorRadiantSource(model, shower, resources))
        }
    }

    override val layerDepthOrder: Int
        get() = 80
    override val preferenceId: String
        get() = "source_provider.6"
    override val layerName: String
        get() = "Meteor Showers"
    protected override val layerNameId: Int
        protected get() = R.string.show_meteors_pref

    private class MeteorRadiantSource(
        private val model: AstronomerModel,
        private val shower: Shower,
        resources: Resources?
    ) : AbstractAstronomicalSource() {
        private val imagePrimitives: MutableList<ImagePrimitive> = ArrayList()
        private val labelPrimitives: MutableList<TextPrimitive> = ArrayList()
        private var lastUpdateTimeMs = 0L
        private val theImage: ImagePrimitive
        private val label: TextPrimitive
        private val name: String
        private val searchNames: MutableList<String> = ArrayList()
        override fun getNames(): List<String> {
            return searchNames
        }

        override fun getSearchLocation(): Vector3 {
            return shower.radiant
        }

        private fun updateShower() {
            lastUpdateTimeMs = model.time.time
            // We will only show the shower if it's the right time of year.
            val now = model.time
            // Standardize on the same year as we stored for the showers.
            now.year = ANY_OLD_YEAR
            theImage.setUpVector(UP)
            // TODO(johntaylor): consider varying the sizes by scaling factor as time progresses.
            if (now.after(shower.start) && now.before(shower.end)) {
                label.text = name
                val percentToPeak: Double
                percentToPeak = if (now.before(shower.peak)) {
                    (now.time - shower.start.time).toDouble() /
                            (shower.peak.time - shower.start.time)
                } else {
                    (shower.end.time - now.time).toDouble() /
                            (shower.end.time - shower.peak.time)
                }
                // Not sure how best to calculate number of meteors - use linear interpolation for now.
                val numberOfMeteorsPerHour = shower.peakMeteorsPerHour * percentToPeak
                if (numberOfMeteorsPerHour > METEOR_THRESHOLD_PER_HR) {
                    theImage.setImageId(R.drawable.meteor2_screen)
                } else {
                    theImage.setImageId(R.drawable.meteor1_screen)
                }
            } else {
                label.text = " "
                theImage.setImageId(R.drawable.blank)
            }
        }

        override fun initialize(): Sources {
            updateShower()
            return this
        }

        override fun update(): EnumSet<UpdateType> {
            val updateTypes = EnumSet.noneOf(UpdateType::class.java)
            if (Math.abs(model.time.time - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
                updateShower()
                updateTypes.add(UpdateType.Reset)
            }
            return updateTypes
        }

        override fun getImages(): List<ImagePrimitive> {
            return imagePrimitives
        }

        override fun getLabels(): List<TextPrimitive> {
            return labelPrimitives
        }

        companion object {
            private const val LABEL_COLOR = 0xf67e81
            private val UP = Vector3(0.0f, 1.0f, 0.0f)
            private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_DAY
            private const val SCALE_FACTOR = 0.03f
        }

        init {
            name = resources!!.getString(shower.nameId)
            // Not sure what the right user experience should be here.  Should we only show up
            // in the search results when the shower is visible?  For now, just ensure
            // that it's obvious from the search label.
            val startDate = DateFormat.format("MMM dd", shower.start)
            val endDate = DateFormat.format("MMM dd", shower.end)
            searchNames.add("$name ($startDate-$endDate)")
            // blank is a 1pxX1px image that should be invisible.
            // We'd prefer not to show any image except on the shower dates, but there
            // appears to be a bug in the renderer/layer interface in that Update values are not
            // respected.  Ditto the label.
            // TODO(johntaylor): fix the bug and remove this blank image
            theImage = ImagePrimitive(shower.radiant, resources, R.drawable.blank, UP, SCALE_FACTOR)
            imagePrimitives.add(theImage)
            label = TextPrimitive(shower.radiant, name, LABEL_COLOR)
            labelPrimitives.add(label)
        }
    }

    companion object {
        private const val ANY_OLD_YEAR = 100 // = year 2000

        /** Number of meteors per hour for the larger graphic  */
        private const val METEOR_THRESHOLD_PER_HR = 10.0
    }

    init {
        initializeShowers()
    }
}
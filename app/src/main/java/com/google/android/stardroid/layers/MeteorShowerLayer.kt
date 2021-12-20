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

import android.content.res.Resources
import android.text.format.DateFormat
import com.google.android.stardroid.R
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.*
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.util.dateFromUtcHmd
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.abs

/**
 * A [Layer] to show well-known meteor showers.
 *
 * @author John Taylor
 */
class MeteorShowerLayer(private val model: AstronomerModel, resources: Resources) :
  AbstractRenderablesLayer(resources, true) {
  private val showers: MutableList<Shower> = ArrayList()

  /**
   * Represents a meteor shower.
   */
  private data class Shower(
    val nameId: Int, val radiant: Vector3,
    val start: Date, val peak: Date, val end: Date, val peakMeteorsPerHour: Int
  )

  private fun initializeShowers() {
    // A list of all the meteor showers with > 10 per hour
    // Source: http://www.imo.net/calendar/2011#table5
    // Note the zero-based month. 10=November
    // Actual start for Quadrantids is December 28 - but we can't cross a year boundary.
    showers.add(
      Shower(
        R.string.quadrantids, getGeocentricCoords(230f, 49f),
        dateFromUtcHmd(ANY_OLD_YEAR, 1, 1),
        dateFromUtcHmd(ANY_OLD_YEAR, 1, 4),
        dateFromUtcHmd(ANY_OLD_YEAR, 1, 12),
        120
      )
    )
    showers.add(
      Shower(
        R.string.lyrids, getGeocentricCoords(271f, 34f),
        dateFromUtcHmd(ANY_OLD_YEAR, 4, 16),
        dateFromUtcHmd(ANY_OLD_YEAR, 4, 22),
        dateFromUtcHmd(ANY_OLD_YEAR, 4, 25),
        18
      )
    )
    showers.add(
      Shower(
        R.string.aquariids, getGeocentricCoords(338f, -1f),
        dateFromUtcHmd(ANY_OLD_YEAR, 4, 19),
        dateFromUtcHmd(ANY_OLD_YEAR, 5, 6),
        dateFromUtcHmd(ANY_OLD_YEAR, 5, 28),
        70
      )
    )
    showers.add(
      Shower(
        R.string.deltaaquariids, getGeocentricCoords(340f, -16f),
        dateFromUtcHmd(ANY_OLD_YEAR, 7, 12),
        dateFromUtcHmd(ANY_OLD_YEAR, 7, 30),
        dateFromUtcHmd(ANY_OLD_YEAR, 8, 23),
        16
      )
    )
    showers.add(
      Shower(
        R.string.perseids, getGeocentricCoords(48f, 58f),
        dateFromUtcHmd(ANY_OLD_YEAR, 7, 17),
        dateFromUtcHmd(ANY_OLD_YEAR, 8, 13),
        dateFromUtcHmd(ANY_OLD_YEAR, 8, 24),
        100
      )
    )
    showers.add(
      Shower(
        R.string.orionids, getGeocentricCoords(95f, 16f),
        dateFromUtcHmd(ANY_OLD_YEAR, 10, 2),
        dateFromUtcHmd(ANY_OLD_YEAR, 10, 21),
        dateFromUtcHmd(ANY_OLD_YEAR, 11, 7),
        25
      )
    )
    showers.add(
      Shower(
        R.string.leonids, getGeocentricCoords(152f, 22f),
        dateFromUtcHmd(ANY_OLD_YEAR, 11, 6),
        dateFromUtcHmd(ANY_OLD_YEAR, 11, 18),
        dateFromUtcHmd(ANY_OLD_YEAR, 11, 30),
        20
      )
    )
    showers.add(
      Shower(
        R.string.puppidvelids, getGeocentricCoords(123f, -45f),
        dateFromUtcHmd(ANY_OLD_YEAR, 12, 1),
        dateFromUtcHmd(ANY_OLD_YEAR, 12, 7),
        dateFromUtcHmd(ANY_OLD_YEAR, 12, 15),
        10
      )
    )
    showers.add(
      Shower(
        R.string.geminids, getGeocentricCoords(112f, 33f),
        dateFromUtcHmd(ANY_OLD_YEAR, 12, 6),
        dateFromUtcHmd(ANY_OLD_YEAR, 12, 14),
        dateFromUtcHmd(ANY_OLD_YEAR, 12, 17),
        120
      )
    )
    showers.add(
      Shower(
        R.string.ursids, getGeocentricCoords(217f, 76f),
        dateFromUtcHmd(ANY_OLD_YEAR, 12, 16),
        dateFromUtcHmd(ANY_OLD_YEAR, 12, 23),
        dateFromUtcHmd(ANY_OLD_YEAR, 12, 26),
        10
      )
    )
  }

  override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
    for (shower in showers) {
      sources.add(MeteorRadiantRenderable(model, shower, resources))
    }
  }

  override val layerDepthOrder = 80
  override val preferenceId = "source_provider.6"
  override val layerName = "Meteor Showers"
  override val layerNameId = R.string.show_meteors_pref

  private class MeteorRadiantRenderable(
    private val model: AstronomerModel,
    private val shower: Shower,
    resources: Resources
  ) : AbstractAstronomicalRenderable() {
    override val labels: MutableList<TextPrimitive> = ArrayList()
    override val images: MutableList<ImagePrimitive> = ArrayList()
    private var lastUpdateTimeMs = 0L
    private val theImage: ImagePrimitive
    private val label: TextPrimitive
    private val name = resources.getString(shower.nameId)
    override val names: MutableList<String> = ArrayList()
    override val searchLocation: Vector3
      get() = shower.radiant

    private fun updateShower() {
      lastUpdateTimeMs = model.time.time
      // We will only show the shower if it's the right time of year.
      val localDate = model.time.toInstant()
        .atZone(ZoneId.of("UTC"))
        .toLocalDate();
      // Standardize on the same year as we stored for the showers.
      var now = dateFromUtcHmd(ANY_OLD_YEAR, localDate.monthValue, localDate.dayOfMonth)

      theImage.setUpVector(UP)
      // TODO(johntaylor): consider varying the sizes by scaling factor as time progresses.
      if (now.after(shower.start) && now.before(shower.end)) {
        isVisible = true
        label.text = name
        val percentToPeak = if (now.before(shower.peak)) {
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
        isVisible = false
        label.text = " "
        theImage.setImageId(R.drawable.blank)
      }
    }

    override fun initialize(): Renderable {
      updateShower()
      return this
    }

    override fun update(): EnumSet<UpdateType> {
      val updateTypes = EnumSet.noneOf(UpdateType::class.java)
      if (abs(model.time.time - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
        updateShower()
        updateTypes.add(UpdateType.Reset)
      }
      return updateTypes
    }

    companion object {
      private const val LABEL_COLOR = 0xf67e81
      private val UP = Vector3(0.0f, 1.0f, 0.0f)
      private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_DAY
      private const val SCALE_FACTOR = 0.03f
    }

    init {
      // Not sure what the right user experience should be here.  Should we only show up
      // in the search results when the shower is visible?  For now, just ensure
      // that it's obvious from the search label.
      val startDate = DateFormat.format("MMM dd", shower.start)
      val endDate = DateFormat.format("MMM dd", shower.end)
      names.add("$name ($startDate-$endDate)")
      // blank is a 1pxX1px image that should be invisible.
      // We'd prefer not to show any image except on the shower dates, but there
      // appears to be a bug in the renderer/layer interface in that Update values are not
      // respected.  Ditto the label.
      // TODO(johntaylor): fix the bug and remove this blank image
      theImage = ImagePrimitive(shower.radiant, resources, R.drawable.blank, UP, SCALE_FACTOR)
      images.add(theImage)
      label = TextPrimitive(shower.radiant, name, LABEL_COLOR)
      labels.add(label)
    }
  }

  companion object {
    private const val ANY_OLD_YEAR = 2000

    /** Number of meteors per hour for the larger graphic  */
    private const val METEOR_THRESHOLD_PER_HR = 10.0
  }

  init {
    initializeShowers()
  }
}
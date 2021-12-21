
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
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.stardroid.R
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.RaDec.Companion.decDegreesFromDMS
import com.google.android.stardroid.math.RaDec.Companion.raDegreesFromHMS
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.*
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.util.MiscUtil
import com.google.android.stardroid.util.dateFromUtcHmd
import java.util.*
import kotlin.math.abs

private const val METEOR_SOURCE_PROVIDER = "source_provider.6"

/**
 * A [Layer] to show the occasional transient comet.
 *
 * @author John Taylor
 */
// Some of this might eventually get generalized for other 'interpolatable' objects.
class CometsLayer(private val model: AstronomerModel, resources: Resources) :
  AbstractRenderablesLayer(resources, true) {
  private val comets = ArrayList<Comet>()

  @RequiresApi(Build.VERSION_CODES.O)
  data class TimeEntry(
    val date: Date,
    val raDeg: Float,
    val decDeg: Float,
    val mag: Float
  )

  /**
   * Simple linear interpolation.
   * Nothing fancy - just linear scan so O(N) in the number of entries.
   */
  class Interpolator(private val xs: List<Long>, private val ys: List<Float>) {
    init {
      if (xs.size != ys.size) throw IllegalArgumentException("Arrays must be of same length")
      if (xs.size < 2) throw IllegalArgumentException("Must have at least two entries")
    }

    fun interpolate(x: Long): Float {
      if (x < xs.first() || x > xs.last()) throw IllegalArgumentException("Input out of bounds")
      for (i in 0..this.xs.size - 2) {
        if (x >= xs[i] && x <= xs[i + 1]) {
          return ((x - xs[i]) * ys[i + 1] + (xs[i + 1] - x) * ys[i]) / (xs[i + 1] - xs[i])
        }
      }
      throw IllegalArgumentException("Ran off the end - this shouldn't happen")
    }
  }

  private class Comet(
    val nameId: Int, val searchAltNameId: Int, private val positions: List<TimeEntry>
  ) {
    val start: Date
    val end: Date
    val xInterpolator: Interpolator
    val yInterpolator: Interpolator
    val zInterpolator: Interpolator
    val magInterpolator: Interpolator

    fun pos(time: Date): Vector3 {
      if (time.before(start) || time.after(end)) {
        // Comet shouldn't be visible, but we'll pin it to the start location.
        return getGeocentricCoords(positions.first().raDeg, positions.first().decDeg)
      }
      return Vector3(
        xInterpolator.interpolate(time.time),
        yInterpolator.interpolate(time.time),
        zInterpolator.interpolate(time.time)
      ).normalizedCopy()
    }

    init {
      if (positions.isEmpty()) throw IllegalStateException("Comet has no positions")
      start = positions.first().date
      end = positions.last().date
      var previous = start
      // TODO(johntaylor): quick and dirty for now, but do something more kotlin-ey later.
      // Maybe with sequences. I don't have time right now.
      val times = mutableListOf<Long>()
      val xValues = mutableListOf<Float>()
      val yValues = mutableListOf<Float>()
      val zValues = mutableListOf<Float>()
      val mags = mutableListOf<Float>()
      for (entry in positions) {
        if (entry.date.before(previous)) throw java.lang.IllegalStateException(
          "Comet dates not " +
              "in ascending order"
        )
        previous = entry.date
        times.add(entry.date.time)
        val geoCentricCoords = getGeocentricCoords(entry.raDeg, entry.decDeg)
        xValues.add(geoCentricCoords.x)
        yValues.add(geoCentricCoords.y)
        zValues.add(geoCentricCoords.z)
        mags.add(entry.mag)
      }
      xInterpolator = Interpolator(times, xValues)
      yInterpolator = Interpolator(times, yValues)
      zInterpolator = Interpolator(times, zValues)
      magInterpolator = Interpolator(times, mags)
    }
  }

  private fun initializeComets() {
    comets.add(
      Comet(
        R.string.comet_leonard,
        R.string.comet_leonard_alt,
        listOf(
          TimeEntry(
            dateFromUtcHmd(2021, 12, 11),
            raDegreesFromHMS(16f, 18f, 29f),
            decDegreesFromDMS(7f, 08f, 17f),
            8f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 12),
            raDegreesFromHMS(16f, 49f, 54f),
            decDegreesFromDMS(1f, 32f, 35f),
            8f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 13),
            raDegreesFromHMS(17f, 22f, 18f),
            decDegreesFromDMS(-4f, 18f, 41f),
            8f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 14),
            raDegreesFromHMS(17f, 54f, 23f),
            decDegreesFromDMS(-9f, 57f, 38f),
            8f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 15),
            raDegreesFromHMS(18f, 24f, 50f),
            decDegreesFromDMS(-15f, 00f, 35f),
            8f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 16),
            raDegreesFromHMS(18f, 52f, 44f),
            decDegreesFromDMS(-19f, 15f, 14f),
            8f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 17),
            raDegreesFromHMS(19f, 17f, 35f),
            decDegreesFromDMS(-22f, 40f, 24f),
            8f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 18),
            raDegreesFromHMS(19f, 39f, 19f),
            decDegreesFromDMS(-25f, 21f, 51f),
            8f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 19),
            raDegreesFromHMS(19f, 58f, 04f),
            decDegreesFromDMS(-27f, 27f, 36f),
            9f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 20),
            raDegreesFromHMS(20f, 14f, 07f),
            decDegreesFromDMS(-29f, 05f, 35f),
            9f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 21),
            raDegreesFromHMS(20f, 27f, 50f),
            decDegreesFromDMS(-30f, 22f, 22f),
            9f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 22),
            raDegreesFromHMS(20f, 39f, 30f),
            decDegreesFromDMS(-31f, 23f, 09f),
            9f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 23),
            raDegreesFromHMS(20f, 49f, 29f),
            decDegreesFromDMS(-32f, 11f, 50f),
            9.23f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 24),
            raDegreesFromHMS(20f, 58f, 00f),
            decDegreesFromDMS(-32f, 51f, 30f),
            9.36f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 25),
            raDegreesFromHMS(21f, 05f, 18f),
            decDegreesFromDMS(-33f, 23f, 44f),
            9.48f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 26),
            raDegreesFromHMS(21f, 11f, 32f),
            decDegreesFromDMS(-33f, 50f, 39f),
            9.60f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 27),
            raDegreesFromHMS(21f, 16f, 53f),
            decDegreesFromDMS(-34f, 13f, 17f),
            9.71f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 28),
            raDegreesFromHMS(21f, 22f, 31f),
            decDegreesFromDMS(-34f, 20f, 38f),
            9.71f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 29),
            raDegreesFromHMS(21f, 26f, 22f),
            decDegreesFromDMS(-34f, 37f, 11f),
            9.71f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 30),
            raDegreesFromHMS(21f, 29f, 39f),
            decDegreesFromDMS(-34f, 51f, 29f),
            9.71f
          ),
          TimeEntry(
            dateFromUtcHmd(2021, 12, 31),
            raDegreesFromHMS(21f, 32f, 27f),
            decDegreesFromDMS(-35f, 03f, 55f),
            9.71f
          ),
        )
      )
    )
  }

  override fun initializeAstroSources(sources: ArrayList<AstronomicalRenderable>) {
    for (comet in comets) {
      sources.add(CometRenderable(model, comet, resources))
    }
  }

  override val layerDepthOrder = 80

  // This is the same as the meteor layer.
  override val preferenceId = METEOR_SOURCE_PROVIDER
  override val layerName = "Comets"
  override val layerNameId = R.string.show_comet_layer_pref

  private class CometRenderable(
    private val model: AstronomerModel,
    private val comet: Comet,
    resources: Resources
  ) : AbstractAstronomicalRenderable() {
    // TODO(johntaylor): why are we overriding these properties?
    override val labels = ArrayList<TextPrimitive>()
    override val images = ArrayList<ImagePrimitive>()
    private var lastUpdateTimeMs = 0L
    private val theImage: ImagePrimitive
    private val label: TextPrimitive
    private val name = resources.getString(comet.nameId)
    private val searchAltName = resources.getString(comet.searchAltNameId)
    override val names = ArrayList<String>()
    private var coords: Vector3
    override var searchLocation = Vector3.zero()
      private set

    private fun updateComets() {
      lastUpdateTimeMs = model.time.time
      // We will only show the comet between certain times.
      val now = model.time
      theImage.setUpVector(UP)
      if (now.after(comet.start) && now.before(comet.end)) {
        label.text = name
        theImage.setImageId(R.drawable.comet)
        isVisible = true
        // TODO(johntaylor): wait...are we really updating the image positions by dipping into
        // the coordinate object we pass in? That's terrible.
        // Must assign here as a result.
        coords.assign(comet.pos(now))
        searchLocation = comet.pos(now)
      } else {
        // TODO(johntaylor): we need a better solution than just blanking out the object!
        label.text = " "
        theImage.setImageId(R.drawable.blank)
        isVisible = false
      }
    }

    override fun initialize(): Renderable {
      updateComets()
      return this
    }

    override fun update(): EnumSet<UpdateType> {
      val updateTypes = EnumSet.noneOf(UpdateType::class.java)
      if (abs(model.time.time - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
        updateComets()
        updateTypes.add(UpdateType.UpdateImages)
        updateTypes.add(UpdateType.Reset)
      }
      return updateTypes
    }

    companion object {
      private const val LABEL_COLOR = 0xf67e81
      private val UP = Vector3(0.0f, 1.0f, 0.0f)
      private const val UPDATE_FREQ_MS = 1L * TimeConstants.MILLISECONDS_PER_HOUR
      private const val SCALE_FACTOR = 0.03f
      val TAG = MiscUtil.getTag(CometsLayer::class.java)
    }

    init {
      names.add(name)
      // Our search just does prefix matching right now, so provide alternative names
      // to compensate.
      names.add(searchAltName)
      // blank is a 1pxX1px image that should be invisible.
      // We'd prefer not to show any image except on the shower dates, but there
      // appears to be a bug in the renderer/layer interface in that Update values are not
      // respected.  Ditto the label.
      // TODO(johntaylor): fix the bug and remove this blank image
      coords = comet.pos(model.time)
      theImage = ImagePrimitive(coords, resources, R.drawable.blank, UP, SCALE_FACTOR)
      images.add(theImage)
      label = TextPrimitive(coords, name, LABEL_COLOR)
      labels.add(label)
    }
  }

  init {
    initializeComets()
  }
}
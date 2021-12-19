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
import com.google.android.stardroid.R
import com.google.android.stardroid.base.TimeConstants
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.RaDec.Companion.decDegreesFromDMS
import com.google.android.stardroid.math.RaDec.Companion.raDegreesFromHMS
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.*
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
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
  private val comets: MutableList<Comet> = ArrayList()

  private data class TimeEntry(
    val date: Date,
    val raDeg: Float,
    val decDeg: Float,
    val mag: Float
  )

  /**
   * Simple linear interpolation.
   * Nothing fancy - just linear scan so O(N) in the number of entries.
   */
  class Interpolator<A, B>(val xs: List<A>, val ys: List<B>)
      where A : Number, A : Comparable<A>, B : Number {
    init {
      if (xs.size != ys.size) throw IllegalArgumentException("Arrays must be of same length")
      if (xs.size < 2) throw IllegalArgumentException("Must have at least two entries")
    }

    fun interpolate(x: A) : B {
      if (x < xs.first() || x > xs.last()) throw IllegalArgumentException("Input out of bounds")
      for (i in 0..this.xs.size - 2) {
        if (x >= xs[i] && x <= xs[i + 1]) {
          return ((x - xs[i]) * ys[i] + (xs[i + 1] - x) * ys[i + 1]) / (xs[i + 1] - xs[i])
        }
      }
      throw IllegalArgumentException("Ran off the end - this shouldn't happen")
    }
  }

  private class Comet(
    val nameId: Int, positions: List<TimeEntry>
  ) {
    val pos = Vector3.zero()

    val start: Date
    val end: Date
    var xInterpolator: Interpolator<Long, Float>
    var yInterpolator: Interpolator<Long, Float>
    var zInterpolator: Interpolator<Long, Float>
    var magInterpolator: Interpolator<Long, Float>

    init {
      if (positions.isEmpty()) throw IllegalStateException("Comet has no positions")
      start = positions.first().date
      end = positions.last().date
      var previous = start
      // TODO(johntaylor): quick and dirty for now, but do something more kotliney later.
      // Maybe with sequences. I don't have time right now.
      var times = mutableListOf<Long>()
      var xValues = mutableListOf<Float>()
      var yValues = mutableListOf<Float>()
      var zValues = mutableListOf<Float>()
      var mags = mutableListOf<Float>()
      for (entry in positions) {
        if (entry.date.before(previous)) throw java.lang.IllegalStateException("Comet dates not in ascending order")
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
        listOf(
          TimeEntry(
            Date(2021, 11, 5),
            raDegreesFromHMS(1f, 2f, 3f),
            decDegreesFromDMS(1f, 2f, 3f),
            10f
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
    override val names = ArrayList<String>()
    override val searchLocation: Vector3
      get() = comet.pos

    private fun updateShower() {
      lastUpdateTimeMs = model.time.time
      // We will only show the comet between certain times.
      val now = model.time
      theImage.setUpVector(UP)
      if (now.after(comet.start) && now.before(comet.end)) {
        label.text = name
        theImage.setImageId(R.drawable.earth) // temp placeholder!
      } else {
        // TODO(johntaylor): we need a better solution than just blanking out the object!
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
      names.add(name)
      // blank is a 1pxX1px image that should be invisible.
      // We'd prefer not to show any image except on the shower dates, but there
      // appears to be a bug in the renderer/layer interface in that Update values are not
      // respected.  Ditto the label.
      // TODO(johntaylor): fix the bug and remove this blank image
      theImage = ImagePrimitive(comet.pos, resources, R.drawable.blank, UP, SCALE_FACTOR)
      images.add(theImage)
      label = TextPrimitive(comet.pos, name, LABEL_COLOR)
      labels.add(label)
    }
  }

  init {
    initializeComets()
  }
}
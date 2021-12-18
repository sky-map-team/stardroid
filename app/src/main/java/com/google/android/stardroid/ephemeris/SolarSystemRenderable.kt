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
package com.google.android.stardroid.ephemeris

import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import com.google.android.stardroid.base.Lists
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.heliocentricCoordinatesFromOrbitalElements
import com.google.android.stardroid.math.updateFromRaDec
import com.google.android.stardroid.renderables.*
import com.google.android.stardroid.renderer.RendererObjectManager.UpdateType
import com.google.android.stardroid.space.SolarSystemObject
import com.google.android.stardroid.space.Universe
import java.util.*

/**
 * Implementation of the
 * [AstronomicalRenderable] for planets.
 *
 * @author Brent Bryan
 */
class SolarSystemRenderable(
    private val solarSystemBody: SolarSystemBody, resources: Resources,
    model: AstronomerModel, prefs: SharedPreferences
) : AbstractAstronomicalRenderable() {
    private val pointPrimitives = ArrayList<PointPrimitive>()
    private val imagePrimitives = ArrayList<ImagePrimitive>()
    private val labelPrimitives = ArrayList<TextPrimitive>()
    private val resources: Resources
    private val model: AstronomerModel
    private val name: String
    private val preferences: SharedPreferences
    private val currentCoords = Vector3(0f, 0f, 0f)
    private val solarSystemObject: SolarSystemObject
    private var earthCoords: Vector3
    private var imageId = -1
    private var lastUpdateTimeMs = 0L
    private val universe = Universe()
    override val names: List<String>
        get() = Lists.asList(name)
    override val searchLocation: Vector3
        get() = currentCoords

    private fun updateCoords(time: Date) {
        lastUpdateTimeMs = time.time
        // TODO(johntaylor): figure out why we do this - presumably to make sure the images
        // are orientated correctly taking into account the Earth's orbital plane.
        // I'm not sure we're doing this right though.
        earthCoords = heliocentricCoordinatesFromOrbitalElements(SolarSystemBody.Earth.getOrbitalElements(time))
        currentCoords.updateFromRaDec(universe.getRaDec(solarSystemBody, time))
        for (imagePrimitives in imagePrimitives) {
            imagePrimitives.setUpVector(earthCoords)
        }
    }

    override fun initialize(): Renderable {
        val time = model.time
        updateCoords(time)
        imageId = solarSystemObject.getImageResourceId(time)
        if (solarSystemBody === SolarSystemBody.Moon) {
            imagePrimitives.add(
                ImagePrimitive(
                    currentCoords, resources, imageId, earthCoords,
                    solarSystemObject.getPlanetaryImageSize()
                )
            )
        } else {
            val usePlanetaryImages = preferences.getBoolean(SHOW_PLANETARY_IMAGES, true)
            if (usePlanetaryImages || solarSystemBody === SolarSystemBody.Sun) {
                imagePrimitives.add(
                    ImagePrimitive(
                        currentCoords, resources, imageId, UP,
                        solarSystemObject.getPlanetaryImageSize()
                    )
                )
            } else {
                pointPrimitives.add(PointPrimitive(currentCoords, PLANET_COLOR, PLANET_SIZE))
            }
        }
        labelPrimitives.add(TextPrimitive(currentCoords, name, PLANET_LABEL_COLOR))
        return this
    }

    override fun update(): EnumSet<UpdateType> {
        val updates = EnumSet.noneOf(UpdateType::class.java)
        val modelTime = model.time
        if (Math.abs(modelTime.time - lastUpdateTimeMs) > solarSystemObject.getUpdateFrequencyMs()) {
            updates.add(UpdateType.UpdatePositions)
            // update location
            updateCoords(modelTime)

            // For moon only:
            if (solarSystemBody === SolarSystemBody.Moon && !imagePrimitives.isEmpty()) {
                // Update up vector.
                imagePrimitives[0].setUpVector(earthCoords)

                // update image:
                val newImageId = solarSystemObject.getImageResourceId(modelTime)
                if (newImageId != imageId) {
                    imageId = newImageId
                    imagePrimitives[0].setImageId(imageId)
                    updates.add(UpdateType.UpdateImages)
                }
            }
        }
        return updates
    }

    override val images: List<ImagePrimitive>
        get() = imagePrimitives
    override val labels: List<TextPrimitive>
        get() = labelPrimitives
    override val points: List<PointPrimitive>
        get() = pointPrimitives

    companion object {
        private const val PLANET_SIZE = 3
        private val PLANET_COLOR = Color.argb(20, 129, 126, 246)
        private const val PLANET_LABEL_COLOR = 0xf67e81
        private const val SHOW_PLANETARY_IMAGES = "show_planetary_images"
        private val UP = Vector3(0.0f, 1.0f, 0.0f)
    }

    init {
        solarSystemObject = universe.solarSystemObjectFor(solarSystemBody)
        this.resources = resources
        this.model = model
        name = resources.getString(solarSystemObject.getNameResourceId())
        preferences = prefs
        earthCoords = heliocentricCoordinatesFromOrbitalElements(
            SolarSystemBody.Earth.getOrbitalElements(model.time))
    }
}
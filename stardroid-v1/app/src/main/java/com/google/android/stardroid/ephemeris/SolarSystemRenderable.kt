/*
 * Copyright (C) 2010 Google Inc.
 * Copyright (c) 2026 Penterakt LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.stardroid.ephemeris

import android.content.SharedPreferences
import android.content.res.Resources
import com.google.android.stardroid.R
import com.google.android.stardroid.base.Lists
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.convertToEquatorialCoordinates
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
    private var lastImageScale = Float.NaN
    private val universe = Universe()
    override val names: List<String>
        get() = Lists.asList(name)
    override val searchLocation: Vector3
        get() = currentCoords

    private fun updateCoords(time: Date) {
        lastUpdateTimeMs = time.time
        earthCoords = heliocentricCoordinatesFromOrbitalElements(SolarSystemBody.Earth.getOrbitalElements(time))
        currentCoords.updateFromRaDec(
            universe.getTopocentricRaDec(solarSystemBody, time, model.location)
        )
        for (imagePrimitive in imagePrimitives) {
            imagePrimitive.setUpVector(getUpVectorForImage())
        }
    }

    // Called every frame from update(), independent of the position-update throttle below: this
    // is cheap enough to run every time, and unlike position, size needs to react on the very
    // next frame both to the true-size setting being toggled and to the Moon's true size
    // changing continuously between perigee and apogee - waiting for the (up to hourly, for
    // planets) position-update interval would leave it visibly stale. Returns true if the scale
    // actually changed, so the caller knows whether the renderer needs telling.
    private fun updateScale(time: Date): Boolean {
        val scale = getImageScale(time)
        if (scale == lastImageScale) return false
        lastImageScale = scale
        for (imagePrimitive in imagePrimitives) {
            imagePrimitive.setScale(scale)
        }
        return true
    }

    // For the Moon, convert earthCoords from ecliptic to equatorial coordinates
    // to match the Moon's position coordinate system. This ensures the illuminated
    // side of the moon image faces toward the sun.
    private fun getUpVectorForImage(): Vector3 {
        return if (solarSystemBody === SolarSystemBody.Moon) {
            convertToEquatorialCoordinates(earthCoords)
        } else {
            earthCoords
        }
    }

    // Read every call so the Moon's true size (which changes continuously between perigee and
    // apogee) stays current, and so toggling the setting takes effect on the next frame without
    // needing a preference-change listener.
    private fun getImageScale(time: Date): Float {
        return if (preferences.getBoolean(SHOW_TRUE_SIZE, false)) {
            solarSystemObject.getTrueAngularRadius(time)
        } else {
            solarSystemObject.getPlanetaryImageSize()
        }
    }

    override fun initialize(): Renderable {
        val time = model.time
        updateCoords(time)
        imageId = solarSystemObject.getImageResourceId(time)
        lastImageScale = getImageScale(time)
        imagePrimitives.add(
            ImagePrimitive(
                currentCoords, resources, imageId, getUpVectorForImage(), lastImageScale
            )
        )
        labelPrimitives.add(
            TextPrimitive(currentCoords, name, resources.getColor(R.color.sky_label, null))
        )
        return this
    }

    override fun update(): EnumSet<UpdateType> {
        val updates = EnumSet.noneOf(UpdateType::class.java)
        val modelTime = model.time
        if (updateScale(modelTime)) {
            updates.add(UpdateType.UpdatePositions)
        }
        if (Math.abs(modelTime.time - lastUpdateTimeMs) > solarSystemObject.getUpdateFrequencyMs()) {
            updates.add(UpdateType.UpdatePositions)
            // update location
            updateCoords(modelTime)

            // For moon only:
            if (solarSystemBody === SolarSystemBody.Moon && !imagePrimitives.isEmpty()) {
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

    companion object {
        private const val SHOW_TRUE_SIZE = "show_true_size"
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
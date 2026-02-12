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
package com.google.android.stardroid.renderables.proto

import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import com.google.android.stardroid.R
import com.google.android.stardroid.math.Vector3
import com.google.android.stardroid.math.getGeocentricCoords
import com.google.android.stardroid.renderables.AbstractAstronomicalRenderable
import com.google.android.stardroid.renderables.ImagePrimitive
import com.google.android.stardroid.renderables.LinePrimitive
import com.google.android.stardroid.renderables.PointPrimitive
import com.google.android.stardroid.renderables.TextPrimitive
import com.google.android.stardroid.source.proto.SourceProto
import com.google.android.stardroid.util.MiscUtil
import java.util.*

/**
 * Implementation of the
 * [AstronomicalRenderable] interface
 * from objects serialized as protocol buffers.
 *
 * @author Brent Bryan
 */
class ProtobufAstronomicalRenderable(
    originalProto: SourceProto.AstronomicalSourceProto,
    private val resources: Resources,
    private val preferences: SharedPreferences? = null
) : AbstractAstronomicalRenderable() {
    companion object {
        private val TAG = MiscUtil.getTag(ProtobufAstronomicalRenderable::class.java)
        private val shapeMap: MutableMap<SourceProto.Shape, PointPrimitive.Shape> = HashMap()

        // Ideally we'd get this from Context.getPackageName but for some reason passing it in as a
        // string via the contructor results in it always being null when I need it. Buggered if
        // I know why - it's certainly a concern. Hopefully this class won't be around for much longer.
        const val PACKAGE = "com.google.android.stardroid"
        private fun getCoords(proto: SourceProto.GeocentricCoordinatesProto): Vector3 {
            return getGeocentricCoords(proto.rightAscension, proto.declination)
        }

        init {
            shapeMap[SourceProto.Shape.CIRCLE] = PointPrimitive.Shape.CIRCLE
            shapeMap[SourceProto.Shape.STAR] = PointPrimitive.Shape.STAR
            shapeMap[SourceProto.Shape.OPEN_CLUSTER] = PointPrimitive.Shape.OPEN_CLUSTER
            shapeMap[SourceProto.Shape.GLOBULAR_CLUSTER] = PointPrimitive.Shape.GLOBULAR_CLUSTER
            shapeMap[SourceProto.Shape.DIFFUSE_NEBULA] = PointPrimitive.Shape.DIFFUSE_NEBULA
            shapeMap[SourceProto.Shape.PLANETARY_NEBULA] = PointPrimitive.Shape.PLANETARY_NEBULA
            shapeMap[SourceProto.Shape.SUPERNOVA_REMNANT] = PointPrimitive.Shape.SUPERNOVA_REMNANT
            shapeMap[SourceProto.Shape.GALAXY] = PointPrimitive.Shape.GALAXY
            shapeMap[SourceProto.Shape.OTHER] = PointPrimitive.Shape.OTHER
        }

        // Map shape types to drawable resources
        // For now, all Messier shapes use R.drawable.messier, but this can be
        // extended to use different resources per shape type
        private fun getDrawableForShape(shape: PointPrimitive.Shape?): Int {
            return when (shape) {
                PointPrimitive.Shape.OPEN_CLUSTER -> R.drawable.open_cluster
                PointPrimitive.Shape.GLOBULAR_CLUSTER -> R.drawable.globular_cluster
                PointPrimitive.Shape.DIFFUSE_NEBULA -> R.drawable.diffuse_nebula
                PointPrimitive.Shape.PLANETARY_NEBULA -> R.drawable.planetary_nebula
                PointPrimitive.Shape.SUPERNOVA_REMNANT -> R.drawable.supernova_remnant
                PointPrimitive.Shape.GALAXY -> R.drawable.galaxy
                PointPrimitive.Shape.OTHER -> R.drawable.other
                else -> R.drawable.galaxy
            }
        }

        private const val SHOW_MESSIER_IMAGES = "show_messier_images"
        private const val IMAGE_SCALE = 0.01f  // Same as Mars/Venus/Mercury
        private val UP = Vector3(0.0f, 1.0f, 0.0f)
    }

    private val proto: SourceProto.AstronomicalSourceProto

    /**
     * The data files contain only the text version of the string Ids. Looking them up
     * by this id will be expensive so precalculate any integer ids. See the datageneration
     * design doc for an explanation.
     */
    private fun processStringIds(proto: SourceProto.AstronomicalSourceProto): SourceProto.AstronomicalSourceProto {
        val processed = proto.toBuilder()
        for (strId in proto.nameStrIdsList) {
            processed.addNameIntIds(toInt(strId))
        }
        // <rant>
        // Work around Google's clumsy protocol buffer API. For some inexplicable reason the current
        // version lacks the getFooBuilderList described here:
        // https://developers.google.com/protocol-buffers/docs/reference/java-generated#fields
        // </rant>
        val newLabels: MutableList<SourceProto.LabelElementProto> = ArrayList(processed.labelCount)
        for (label in processed.labelList) {
            val labelBuilder = label.toBuilder()
            labelBuilder.stringsIntId = toInt(label.stringsStrId)
            newLabels.add(labelBuilder.build())
        }
        processed.clearLabel()
        processed.addAllLabel(newLabels)
        return processed.build()
    }

    private fun toInt(stringId: String): Int {
        val resourceId = resources.getIdentifier(stringId, "string", PACKAGE)
        return if (resourceId == 0) R.string.missing_label else resourceId
    }

    override val names: MutableList<String> = ArrayList()
/*       get() {
            if (names.isEmpty()) {
                //names.
                //names = ArrayList(proto.nameIntIdsCount)
                for (id in proto.nameIntIdsList) {
                    names!!.add(resources.getString(id))
                }
            }
            return names!!
        }

    @Synchronized
    override fun getNames(): ArrayList<String> {
        if (names == null) {
            names = ArrayList(proto.nameIntIdsCount)
            for (id in proto.nameIntIdsList) {
                names!!.add(resources.getString(id))
            }
        }
        return names!!
    }
*/
    override val searchLocation: Vector3
        get() = getCoords(proto.searchLocation)

    override val points: List<PointPrimitive>
        get() {
            if (proto.pointCount == 0) {
                return emptyList<PointPrimitive>()
            }

            // If showing Messier images, return empty (points shown as images instead)
            val showMessierImages = preferences?.getBoolean(SHOW_MESSIER_IMAGES, true) ?: false
            if (showMessierImages && isMessierObject()) {
                return emptyList<PointPrimitive>()
            }

            val points = ArrayList<PointPrimitive>(proto.pointCount)
            for (element in proto.pointList) {
                points.add(
                    PointPrimitive(
                        getCoords(element.location),
                        element.color, element.size, shapeMap[element.shape]
                    )
                )
            }
            return points
        }
    override val labels: List<TextPrimitive>
        get() {
            if (proto.labelCount == 0) {
                return emptyList<TextPrimitive>()
            }
            val points = ArrayList<TextPrimitive>(proto.labelCount)
            for (element in proto.labelList) {
                Log.d(TAG, "Label " + element.stringsIntId + " : " + element.stringsStrId)
                points.add(
                    TextPrimitive(
                        getCoords(element.location),
                        resources.getString(element.stringsIntId),
                        element.color, element.offset, element.fontSize
                    )
                )
            }
            return points
        }
    override val lines: List<LinePrimitive>
        get() {
            if (proto.lineCount == 0) {
                return emptyList<LinePrimitive>()
            }
            val points = ArrayList<LinePrimitive>(proto.lineCount)
            for (element in proto.lineList) {
                val vertices = ArrayList<Vector3>(element.vertexCount)
                for (elementVertex in element.vertexList) {
                    vertices.add(getCoords(elementVertex))
                }
                points.add(LinePrimitive(element.color, vertices, element.lineWidth))
            }
            return points
        }

    override val images: List<ImagePrimitive>
        get() {
            // Only create images for Messier objects when preference is enabled
            val showMessierImages = preferences?.getBoolean(SHOW_MESSIER_IMAGES, true) ?: false
            if (!showMessierImages || !isMessierObject() || proto.pointCount == 0) {
                return emptyList<ImagePrimitive>()
            }

            val images = ArrayList<ImagePrimitive>(proto.pointCount)
            for (element in proto.pointList) {
                val shape = shapeMap[element.shape]
                val drawableId = getDrawableForShape(shape)
                images.add(
                    ImagePrimitive(
                        getCoords(element.location),
                        resources,
                        drawableId,
                        UP,
                        IMAGE_SCALE
                    )
                )
            }
            return images
        }

    private fun isMessierObject(): Boolean {
        // Messier objects are identified by their shape type
        // Check if any points have a Messier-specific shape
        // (This is obviously a terrible way to do it).
        if (proto.pointCount == 0) return false

        for (element in proto.pointList) {
            val shape = shapeMap[element.shape]
            if (shape == PointPrimitive.Shape.OPEN_CLUSTER ||
                shape == PointPrimitive.Shape.GLOBULAR_CLUSTER ||
                shape == PointPrimitive.Shape.DIFFUSE_NEBULA ||
                shape == PointPrimitive.Shape.PLANETARY_NEBULA ||
                shape == PointPrimitive.Shape.SUPERNOVA_REMNANT ||
                shape == PointPrimitive.Shape.GALAXY ||
                shape == PointPrimitive.Shape.OTHER) {
                return true
            }
        }
        return false
    }

    init {
        // Not ideal to be doing this in the constructor. TODO(john): investigate which threads
        // this is all happening on.
        proto = processStringIds(originalProto)
        for (id in proto.nameIntIdsList) {
            names.add(resources.getString(id))
        }
    }
}
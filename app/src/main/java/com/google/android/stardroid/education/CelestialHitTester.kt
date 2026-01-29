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
package com.google.android.stardroid.education

import android.util.Log
import com.google.android.stardroid.control.AstronomerModel
import com.google.android.stardroid.layers.LayerManager
import com.google.android.stardroid.math.DEGREES_TO_RADIANS
import com.google.android.stardroid.math.MathUtils
import com.google.android.stardroid.math.RADIANS_TO_DEGREES
import com.google.android.stardroid.math.Vector3
import javax.inject.Inject
import kotlin.math.abs

/**
 * Handles hit testing for celestial objects based on screen tap coordinates.
 *
 * This class converts screen coordinates to celestial coordinates and finds
 * the nearest supported celestial object to the tap point.
 */
class CelestialHitTester @Inject constructor(
    private val astronomerModel: AstronomerModel,
    private val layerManager: LayerManager,
    private val objectInfoRegistry: ObjectInfoRegistry
) {
    /**
     * Finds the celestial object at or near the given screen coordinates.
     *
     * @param screenX X coordinate of the tap in pixels
     * @param screenY Y coordinate of the tap in pixels
     * @param screenWidth Width of the screen in pixels
     * @param screenHeight Height of the screen in pixels
     * @return ObjectInfo if an object is found near the tap point, null otherwise
     */
    fun findObjectAtScreenPosition(
        screenX: Float,
        screenY: Float,
        screenWidth: Int,
        screenHeight: Int
    ): ObjectInfo? {
        // Get current view parameters
        val pointing = astronomerModel.pointing
        val fieldOfView = astronomerModel.fieldOfView

        // Convert screen coordinates to a direction vector in celestial coordinates
        val tapDirection = screenToDirection(
            screenX, screenY,
            screenWidth, screenHeight,
            pointing.lineOfSight,
            pointing.perpendicular,
            fieldOfView
        )

        // Find the nearest supported object to this direction
        var bestMatch: ObjectInfo? = null
        var bestAngularDistance = TAP_THRESHOLD_DEGREES

        for (objectId in objectInfoRegistry.supportedObjectIds) {
            // Get the localized search name (e.g., "Sol" in Portuguese for "sun")
            val searchName = objectInfoRegistry.getSearchName(objectId)
            if (searchName == null) {
                Log.d(TAG, "No search name for object: $objectId")
                continue
            }

            val objectDirection = getObjectDirection(searchName)
            if (objectDirection == null) {
                Log.d(TAG, "Could not find direction for: $searchName (id: $objectId)")
                continue
            }

            // Calculate angular distance between tap and object
            val angularDistance = angularDistanceDegrees(tapDirection, objectDirection)
            Log.d(TAG, "Object $objectId ($searchName): distance = $angularDistance deg")

            if (angularDistance < bestAngularDistance) {
                bestAngularDistance = angularDistance
                bestMatch = objectInfoRegistry.getInfo(objectId)
            }
        }

        if (bestMatch != null) {
            Log.d(TAG, "Found object ${bestMatch.id} at $bestAngularDistance deg")
        } else {
            Log.d(TAG, "No object found near tap location")
        }

        return bestMatch
    }

    /**
     * Converts screen coordinates to a direction vector in celestial coordinates.
     *
     * The screen coordinate system has (0,0) at top-left, with X increasing right
     * and Y increasing down. The celestial coordinate system uses unit vectors.
     */
    private fun screenToDirection(
        screenX: Float,
        screenY: Float,
        screenWidth: Int,
        screenHeight: Int,
        lookDir: Vector3,
        upDir: Vector3,
        fieldOfViewDegrees: Float
    ): Vector3 {
        // Normalize screen coordinates to [-1, 1] range
        // Screen Y is inverted (0 at top, increases downward)
        val normalizedX = (2f * screenX / screenWidth) - 1f
        val normalizedY = 1f - (2f * screenY / screenHeight)

        // Calculate the right vector (perpendicular to both look and up)
        // Using cross product: right = look x up
        val rightDir = lookDir * upDir
        rightDir.normalize()

        // Calculate angular offsets based on field of view
        // Assuming a roughly square aspect ratio for simplicity
        val fovRadians = fieldOfViewDegrees * DEGREES_TO_RADIANS
        val halfFov = fovRadians / 2f

        // Scale by aspect ratio
        val aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
        val horizontalOffset = normalizedX * MathUtils.tan(halfFov) * aspectRatio
        val verticalOffset = normalizedY * MathUtils.tan(halfFov)

        // Compute the direction vector using Vector3 operators
        // direction = lookDir + horizontalOffset * rightDir + verticalOffset * upDir
        val direction = lookDir + (rightDir * horizontalOffset) + (upDir * verticalOffset)
        direction.normalize()

        return direction
    }

    /**
     * Gets the direction vector to a celestial object by its search name.
     */
    private fun getObjectDirection(searchName: String): Vector3? {
        val searchResults = layerManager.searchByObjectName(searchName)
        // Find the first non-null search result
        val firstResult = searchResults.firstOrNull { it != null } ?: return null

        // Use the coordinates from the search result
        val coords = firstResult.coords()
        val direction = Vector3(coords.x, coords.y, coords.z)
        direction.normalize()
        return direction
    }

    /**
     * Calculates the angular distance in degrees between two direction vectors.
     */
    private fun angularDistanceDegrees(v1: Vector3, v2: Vector3): Float {
        // Use dot product: cos(angle) = v1 · v2 (for unit vectors)
        val dotProduct = v1.x * v2.x + v1.y * v2.y + v1.z * v2.z

        // Clamp to [-1, 1] to handle floating point errors
        val clampedDot = dotProduct.coerceIn(-1f, 1f)

        // Convert to degrees
        val angleRadians = MathUtils.acos(clampedDot)
        return abs(angleRadians * RADIANS_TO_DEGREES)
    }

    companion object {
        private const val TAG = "CelestialHitTester"

        /**
         * Maximum angular distance in degrees for a tap to register as hitting an object.
         * This is generous to account for touch imprecision and varying zoom levels.
         */
        private const val TAP_THRESHOLD_DEGREES = 5f
    }
}

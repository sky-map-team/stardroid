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

import com.google.android.stardroid.util.processImage

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

/**
 * Registry that loads and provides educational information about celestial objects.
 * Data is loaded from a JSON file in assets and string resources for i18n support.
 */
class ObjectInfoRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetManager: AssetManager
) {
    private val objectInfoMap: Map<String, ObjectInfoEntry> by lazy { loadFromAssets() }

    /** Cached index of virtual objects (lowercase name → id + suggestion), built once. */
    private val virtualObjectIndex: Map<String, Pair<String, VirtualObjectSuggestion>> by lazy {
        objectInfoMap.entries
            .filter { it.value.parentObjectId != null }
            .mapNotNull { (id, entry) ->
                val name = getSearchName(id) ?: return@mapNotNull null
                name.lowercase() to Pair(id, VirtualObjectSuggestion(name, entry.searchSubtext ?: ""))
            }
            .toMap()
    }

/**
     * Returns the set of object IDs that have educational content available.
     */
    val supportedObjectIds: Set<String>
        get() = objectInfoMap.keys

    /**
     * Gets the educational info for a celestial object.
     *
     * @param objectId The object identifier (e.g., "mars", "sirius")
     * @return ObjectInfo with localized content, or null if not found
     */
    fun getInfo(objectId: String): ObjectInfo? {
        val entry = objectInfoMap[objectId.lowercase()] ?: return null
        val resources = context.resources
        val packageName = context.packageName

        val nameResId = resources.getIdentifier(entry.nameKey, "string", packageName)
        val descResId = resources.getIdentifier(entry.descriptionKey, "string", packageName)
        val funFactResId = resources.getIdentifier(entry.funFactKey, "string", packageName)

        if (nameResId == 0 || descResId == 0 || funFactResId == 0) {
            Log.w(TAG, "Missing string resources for object: $objectId")
            return null
        }

        // Get optional scientific data strings
        val distance = getOptionalString(entry.distanceKey)
        val size = getOptionalString(entry.sizeKey)
        val mass = getOptionalString(entry.massKey)

        val imagePath = processImage(
            objectId, entry.imageKey?.let { "celestial_images/$it" })

        return ObjectInfo(
            id = objectId,
            name = resources.getString(nameResId),
            description = resources.getString(descResId),
            funFact = resources.getString(funFactResId),
            type = parseObjectType(entry.type),
            distance = distance,
            size = size,
            mass = mass,
            spectralClass = entry.spectralClass,
            magnitude = entry.magnitude,
            imagePath = imagePath,
            imageCredit = entry.imageCredit,
            parentObjectId = entry.parentObjectId,
            seeAlso = entry.seeAlso
        )
    }

    /**
     * Returns all objects that have an associated image, sorted by display name.
     * Used to populate the image gallery.
     */
    fun getAllWithImages(): List<ObjectInfo> {
        return objectInfoMap.entries
            .filter { it.value.imageKey != null }
            .mapNotNull { getInfo(it.key) }
            .sortedBy { it.name }
    }

    /**
     * Checks if educational info is available for the given object.
     */
    fun hasInfo(objectId: String): Boolean {
        return objectInfoMap.containsKey(objectId.lowercase())
    }

    /**
     * Gets the localized search name for a celestial object.
     * This is the name used by the LayerManager for searching.
     *
     * @param objectId The object identifier (e.g., "mars", "sirius")
     * @return The localized name (e.g., "Marte" in Portuguese), or null if not found
     */
    fun getSearchName(objectId: String): String? {
        val entry = objectInfoMap[objectId.lowercase()] ?: return null
        val resources = context.resources
        val packageName = context.packageName

        val nameResId = resources.getIdentifier(entry.nameKey, "string", packageName)
        if (nameResId == 0) {
            Log.w(TAG, "Missing name string resource for object: $objectId")
            return null
        }

        return resources.getString(nameResId)
    }

    fun getVirtualObjectsMatchingPrefix(prefix: String?): List<VirtualObjectSuggestion> {
        val lowerPrefix = prefix?.lowercase() ?: return emptyList()
        return virtualObjectIndex.entries
            .filter { it.key.startsWith(lowerPrefix) }
            .map { it.value.second }
    }

    fun getVirtualObjectByName(name: String?): ObjectInfo? {
        return virtualObjectIndex[name?.lowercase()]?.let { (id, _) -> getInfo(id) }
    }

    private fun getOptionalString(key: String?): String? {
        if (key == null) return null
        val resources = context.resources
        val packageName = context.packageName
        val resId = resources.getIdentifier(key, "string", packageName)
        return if (resId != 0) resources.getString(resId) else null
    }

    private fun parseObjectType(typeString: String): ObjectType {
        return when (typeString.lowercase()) {
            "planet" -> ObjectType.PLANET
            "star" -> ObjectType.STAR
            "moon" -> ObjectType.MOON
            "dwarf_planet" -> ObjectType.DWARF_PLANET
            "nebula" -> ObjectType.NEBULA
            "galaxy" -> ObjectType.GALAXY
            "cluster" -> ObjectType.CLUSTER
            "constellation" -> ObjectType.CONSTELLATION
            "black_hole" -> ObjectType.BLACK_HOLE
            "meteor_shower" -> ObjectType.METEOR_SHOWER
            else -> {
                Log.w(TAG, "Unknown object type: $typeString, defaulting to STAR")
                ObjectType.STAR
            }
        }
    }

    private fun loadFromAssets(): Map<String, ObjectInfoEntry> {
        return try {
            val inputStream = assetManager.open(ASSET_FILE_NAME)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            parseJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load object info from assets", e)
            emptyMap()
        }
    }

    private fun parseJson(jsonString: String): Map<String, ObjectInfoEntry> {
        val result = mutableMapOf<String, ObjectInfoEntry>()
        val root = JSONObject(jsonString)
        val objects = root.getJSONObject("objects")

        for (objectId in objects.keys()) {
            val obj = objects.getJSONObject(objectId)
            val seeAlsoArray = obj.optJSONArray("seeAlso")
            val seeAlsoList = if (seeAlsoArray != null) {
                (0 until seeAlsoArray.length()).map { seeAlsoArray.getString(it) }
            } else emptyList()
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            result[objectId.lowercase()] = ObjectInfoEntry(
                nameKey = obj.getString("nameKey"),
                descriptionKey = obj.getString("descriptionKey"),
                funFactKey = obj.getString("funFactKey"),
                type = obj.optString("type", "star"),
                distanceKey = obj.optString("distanceKey", null),
                sizeKey = obj.optString("sizeKey", null),
                massKey = obj.optString("massKey", null),
                spectralClass = obj.optString("spectralClass", null),
                magnitude = obj.optString("magnitude", null),
                imageKey = obj.optString("imageKey", null),
                imageCredit = obj.optString("imageCredit", null),
                parentObjectId = obj.optString("parentObjectId", null),
                seeAlso = seeAlsoList,
                searchSubtext = obj.optString("searchSubtext", null)
            )
        }

        Log.i(TAG, "Loaded educational info for ${result.size} objects")
        return result
    }

    /** A search suggestion for a virtual object: its display name and the subtitle shown in search. */
    data class VirtualObjectSuggestion(val name: String, val subtext: String)

    companion object {
        private const val TAG = "ObjectInfoRegistry"
        private const val ASSET_FILE_NAME = "object_info.json"
    }
}

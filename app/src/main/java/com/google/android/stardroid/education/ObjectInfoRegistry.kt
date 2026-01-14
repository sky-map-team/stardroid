// Copyright 2024 Google Inc.
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
package com.google.android.stardroid.education

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject
import javax.inject.Inject

/**
 * Registry that loads and provides educational information about celestial objects.
 * Data is loaded from a JSON file in assets and string resources for i18n support.
 */
class ObjectInfoRegistry @Inject constructor(
    private val context: Context,
    private val assetManager: AssetManager
) {
    private val objectInfoMap: Map<String, ObjectInfoEntry> by lazy { loadFromAssets() }

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

        return ObjectInfo(
            id = objectId,
            name = resources.getString(nameResId),
            description = resources.getString(descResId),
            funFact = resources.getString(funFactResId)
        )
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

    private fun loadFromAssets(): Map<String, ObjectInfoEntry> {
        return try {
            val jsonString = assetManager.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() }
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
            result[objectId.lowercase()] = ObjectInfoEntry(
                nameKey = obj.getString("nameKey"),
                descriptionKey = obj.getString("descriptionKey"),
                funFactKey = obj.getString("funFactKey")
            )
        }

        Log.i(TAG, "Loaded educational info for ${result.size} objects")
        return result
    }

    companion object {
        private const val TAG = "ObjectInfoRegistry"
        private const val ASSET_FILE_NAME = "object_info.json"
    }
}

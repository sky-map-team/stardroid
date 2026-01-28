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

/**
 * Types of celestial objects that can have educational info.
 */
enum class ObjectType {
    PLANET,
    STAR,
    MOON,
    DWARF_PLANET,
    NEBULA,
    GALAXY,
    CLUSTER,
    CONSTELLATION
}

/**
 * Data class representing educational information about a celestial object.
 *
 * @property id The unique identifier for the object (e.g., "mars", "sirius")
 * @property name The localized display name of the object
 * @property description A short description of what the object is (1-2 sentences)
 * @property funFact An interesting fact about the object
 * @property type The type of celestial object
 * @property distance The distance to the object (localized, e.g., "4.24 light-years")
 * @property size The size of the object (localized, e.g., "1.4M km diameter")
 * @property mass The mass of the object (localized, e.g., "1.989 × 10³⁰ kg")
 * @property spectralClass The spectral classification for stars (e.g., "G2V", "M1.5Iab")
 * @property magnitude The apparent magnitude (e.g., "−1.46", "6.5")
 */
data class ObjectInfo(
    val id: String,
    val name: String,
    val description: String,
    val funFact: String,
    val type: ObjectType = ObjectType.STAR,
    val distance: String? = null,
    val size: String? = null,
    val mass: String? = null,
    val spectralClass: String? = null,
    val magnitude: String? = null
)

/**
 * Internal data class for JSON deserialization of object info entries.
 * Maps string resource keys to actual resource IDs.
 */
internal data class ObjectInfoEntry(
    val nameKey: String,
    val descriptionKey: String,
    val funFactKey: String,
    val type: String = "star",
    val distanceKey: String? = null,
    val sizeKey: String? = null,
    val massKey: String? = null,
    val spectralClass: String? = null,
    val magnitude: String? = null
)

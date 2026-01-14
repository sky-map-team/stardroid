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

/**
 * Data class representing educational information about a celestial object.
 *
 * @property id The unique identifier for the object (e.g., "mars", "sirius")
 * @property name The localized display name of the object
 * @property description A short description of what the object is (1-2 sentences)
 * @property funFact An interesting fact about the object
 */
data class ObjectInfo(
    val id: String,
    val name: String,
    val description: String,
    val funFact: String
)

/**
 * Internal data class for JSON deserialization of object info entries.
 * Maps string resource keys to actual resource IDs.
 */
internal data class ObjectInfoEntry(
    val nameKey: String,
    val descriptionKey: String,
    val funFactKey: String
)

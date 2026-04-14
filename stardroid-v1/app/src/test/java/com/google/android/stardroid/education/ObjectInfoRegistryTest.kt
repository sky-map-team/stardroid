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
import android.content.res.Resources
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Tests for [ObjectInfoRegistry].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ObjectInfoRegistryTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockAssetManager: AssetManager

    @Mock
    private lateinit var mockResources: Resources

    private lateinit var registry: ObjectInfoRegistry

    private val testJson = """
        {
          "version": 2,
          "objects": {
            "sun": {
              "nameKey": "sun",
              "descriptionKey": "object_info_sun_description",
              "funFactKey": "object_info_sun_funfact",
              "type": "star",
              "distanceKey": "object_info_sun_distance",
              "sizeKey": "object_info_sun_size",
              "massKey": "object_info_sun_mass",
              "spectralClass": "G2V",
              "magnitude": "-26.74"
            },
            "mars": {
              "nameKey": "mars",
              "descriptionKey": "object_info_mars_description",
              "funFactKey": "object_info_mars_funfact",
              "type": "planet",
              "distanceKey": "object_info_mars_distance",
              "sizeKey": "object_info_mars_size",
              "massKey": "object_info_mars_mass",
              "imageKey": "planets/hubble_mars.jpg",
              "imageCredit": "NASA/ESA/Hubble"
            },
            "m42": {
              "nameKey": "m42",
              "descriptionKey": "object_info_m42_description",
              "funFactKey": "object_info_m42_funfact",
              "type": "nebula",
              "magnitude": "4.0"
            }
          }
        }
    """.trimIndent()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockContext.packageName).thenReturn("com.google.android.stardroid")

        val inputStream = ByteArrayInputStream(testJson.toByteArray())
        `when`(mockAssetManager.open("object_info.json")).thenReturn(inputStream)

        // Mock string resources for Sun
        `when`(mockResources.getIdentifier("sun", "string", "com.google.android.stardroid"))
            .thenReturn(1)
        `when`(mockResources.getIdentifier(
            "object_info_sun_description", "string", "com.google.android.stardroid"))
            .thenReturn(2)
        `when`(mockResources.getIdentifier(
            "object_info_sun_funfact", "string", "com.google.android.stardroid"))
            .thenReturn(3)
        `when`(mockResources.getIdentifier(
            "object_info_sun_distance", "string", "com.google.android.stardroid"))
            .thenReturn(10)
        `when`(mockResources.getIdentifier(
            "object_info_sun_size", "string", "com.google.android.stardroid"))
            .thenReturn(11)
        `when`(mockResources.getIdentifier(
            "object_info_sun_mass", "string", "com.google.android.stardroid"))
            .thenReturn(12)

        // Mock string resources for Mars
        `when`(mockResources.getIdentifier("mars", "string", "com.google.android.stardroid"))
            .thenReturn(4)
        `when`(mockResources.getIdentifier(
            "object_info_mars_description", "string", "com.google.android.stardroid"))
            .thenReturn(5)
        `when`(mockResources.getIdentifier(
            "object_info_mars_funfact", "string", "com.google.android.stardroid"))
            .thenReturn(6)
        `when`(mockResources.getIdentifier(
            "object_info_mars_distance", "string", "com.google.android.stardroid"))
            .thenReturn(13)
        `when`(mockResources.getIdentifier(
            "object_info_mars_size", "string", "com.google.android.stardroid"))
            .thenReturn(14)
        `when`(mockResources.getIdentifier(
            "object_info_mars_mass", "string", "com.google.android.stardroid"))
            .thenReturn(15)

        // Mock string resources for M42
        `when`(mockResources.getIdentifier("m42", "string", "com.google.android.stardroid"))
            .thenReturn(7)
        `when`(mockResources.getIdentifier(
            "object_info_m42_description", "string", "com.google.android.stardroid"))
            .thenReturn(8)
        `when`(mockResources.getIdentifier(
            "object_info_m42_funfact", "string", "com.google.android.stardroid"))
            .thenReturn(9)

        // Set string values
        `when`(mockResources.getString(1)).thenReturn("Sun")
        `when`(mockResources.getString(2)).thenReturn("Our star")
        `when`(mockResources.getString(3)).thenReturn("Very hot")
        `when`(mockResources.getString(4)).thenReturn("Mars")
        `when`(mockResources.getString(5)).thenReturn("Red planet")
        `when`(mockResources.getString(6)).thenReturn("Has Olympus Mons")
        `when`(mockResources.getString(7)).thenReturn("Orion Nebula")
        `when`(mockResources.getString(8)).thenReturn("A star-forming region")
        `when`(mockResources.getString(9)).thenReturn("Visible to naked eye")
        `when`(mockResources.getString(10)).thenReturn("150M km")
        `when`(mockResources.getString(11)).thenReturn("1.4M km")
        `when`(mockResources.getString(12)).thenReturn("1.989 × 10³⁰ kg")
        `when`(mockResources.getString(13)).thenReturn("228M km")
        `when`(mockResources.getString(14)).thenReturn("6,779 km")
        `when`(mockResources.getString(15)).thenReturn("6.39 × 10²³ kg")

        registry = ObjectInfoRegistry(mockContext, mockAssetManager)
    }

    @Test
    fun testSupportedObjectIds() {
        val ids = registry.supportedObjectIds
        assertThat(ids).containsExactly("sun", "mars", "m42")
    }

    @Test
    fun testHasInfo_existingObject() {
        assertThat(registry.hasInfo("sun")).isTrue()
        assertThat(registry.hasInfo("mars")).isTrue()
        assertThat(registry.hasInfo("m42")).isTrue()
    }

    @Test
    fun testHasInfo_nonExistingObject() {
        assertThat(registry.hasInfo("jupiter")).isFalse()
        assertThat(registry.hasInfo("unknown")).isFalse()
    }

    @Test
    fun testHasInfo_caseInsensitive() {
        assertThat(registry.hasInfo("SUN")).isTrue()
        assertThat(registry.hasInfo("Sun")).isTrue()
        assertThat(registry.hasInfo("MARS")).isTrue()
    }

    @Test
    fun testGetInfo_starWithFullData() {
        val info = registry.getInfo("sun")

        assertThat(info).isNotNull()
        assertThat(info!!.id).isEqualTo("sun")
        assertThat(info.name).isEqualTo("Sun")
        assertThat(info.description).isEqualTo("Our star")
        assertThat(info.funFact).isEqualTo("Very hot")
        assertThat(info.type).isEqualTo(ObjectType.STAR)
        assertThat(info.distance).isEqualTo("150M km")
        assertThat(info.size).isEqualTo("1.4M km")
        assertThat(info.mass).isEqualTo("1.989 × 10³⁰ kg")
        assertThat(info.spectralClass).isEqualTo("G2V")
        assertThat(info.magnitude).isEqualTo("-26.74")
        assertThat(info.imagePath).isNull()
        assertThat(info.imageCredit).isNull()
    }

    @Test
    fun testGetInfo_planetWithData() {
        val info = registry.getInfo("mars")

        assertThat(info).isNotNull()
        assertThat(info!!.type).isEqualTo(ObjectType.PLANET)
        assertThat(info.distance).isEqualTo("228M km")
        assertThat(info.size).isEqualTo("6,779 km")
        assertThat(info.mass).isEqualTo("6.39 × 10²³ kg")
        assertThat(info.spectralClass).isNull()
        assertThat(info.magnitude).isNull()
        assertThat(info.imagePath).isEqualTo("celestial_images/planets/hubble_mars.jpg")
        assertThat(info.imageCredit).isEqualTo("NASA/ESA/Hubble")
    }

    @Test
    fun testGetInfo_nebulaWithPartialData() {
        val info = registry.getInfo("m42")

        assertThat(info).isNotNull()
        assertThat(info!!.type).isEqualTo(ObjectType.NEBULA)
        assertThat(info.distance).isNull()
        assertThat(info.size).isNull()
        assertThat(info.mass).isNull()
        assertThat(info.spectralClass).isNull()
        assertThat(info.magnitude).isEqualTo("4.0")
    }

    @Test
    fun testGetInfo_nonExistingObject() {
        val info = registry.getInfo("jupiter")
        assertThat(info).isNull()
    }

    @Test
    fun testGetSearchName() {
        val searchName = registry.getSearchName("sun")
        assertThat(searchName).isEqualTo("Sun")
    }

    @Test
    fun testGetSearchName_nonExistingObject() {
        val searchName = registry.getSearchName("jupiter")
        assertThat(searchName).isNull()
    }
}

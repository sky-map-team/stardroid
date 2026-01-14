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
          "version": 1,
          "objects": {
            "sun": {
              "nameKey": "sun",
              "descriptionKey": "object_info_sun_description",
              "funFactKey": "object_info_sun_funfact"
            },
            "mars": {
              "nameKey": "mars",
              "descriptionKey": "object_info_mars_description",
              "funFactKey": "object_info_mars_funfact"
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

        // Mock string resources
        `when`(mockResources.getIdentifier("sun", "string", "com.google.android.stardroid"))
            .thenReturn(1)
        `when`(mockResources.getIdentifier(
            "object_info_sun_description", "string", "com.google.android.stardroid"))
            .thenReturn(2)
        `when`(mockResources.getIdentifier(
            "object_info_sun_funfact", "string", "com.google.android.stardroid"))
            .thenReturn(3)
        `when`(mockResources.getIdentifier("mars", "string", "com.google.android.stardroid"))
            .thenReturn(4)
        `when`(mockResources.getIdentifier(
            "object_info_mars_description", "string", "com.google.android.stardroid"))
            .thenReturn(5)
        `when`(mockResources.getIdentifier(
            "object_info_mars_funfact", "string", "com.google.android.stardroid"))
            .thenReturn(6)

        `when`(mockResources.getString(1)).thenReturn("Sun")
        `when`(mockResources.getString(2)).thenReturn("Our star")
        `when`(mockResources.getString(3)).thenReturn("Very hot")
        `when`(mockResources.getString(4)).thenReturn("Mars")
        `when`(mockResources.getString(5)).thenReturn("Red planet")
        `when`(mockResources.getString(6)).thenReturn("Has Olympus Mons")

        registry = ObjectInfoRegistry(mockContext, mockAssetManager)
    }

    @Test
    fun testSupportedObjectIds() {
        val ids = registry.supportedObjectIds
        assertThat(ids).containsExactly("sun", "mars")
    }

    @Test
    fun testHasInfo_existingObject() {
        assertThat(registry.hasInfo("sun")).isTrue()
        assertThat(registry.hasInfo("mars")).isTrue()
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
    fun testGetInfo_existingObject() {
        val info = registry.getInfo("sun")

        assertThat(info).isNotNull()
        assertThat(info!!.id).isEqualTo("sun")
        assertThat(info.name).isEqualTo("Sun")
        assertThat(info.description).isEqualTo("Our star")
        assertThat(info.funFact).isEqualTo("Very hot")
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

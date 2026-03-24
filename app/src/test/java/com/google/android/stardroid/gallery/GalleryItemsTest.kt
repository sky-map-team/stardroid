package com.google.android.stardroid.gallery

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import com.google.android.stardroid.education.ObjectInfoRegistry
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
 * Verifies that [ObjectInfoRegistry.getAllWithImages] correctly filters to objects with images.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GalleryItemsTest {

    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockAssetManager: AssetManager
    @Mock private lateinit var mockResources: Resources

    private lateinit var registry: ObjectInfoRegistry

    /** Test JSON with one object that has an image and one that does not. */
    private val testJson = """
        {
          "version": 2,
          "objects": {
            "sun": {
              "nameKey": "sun",
              "descriptionKey": "object_info_sun_description",
              "funFactKey": "object_info_sun_funfact",
              "type": "star"
            },
            "mars": {
              "nameKey": "mars",
              "descriptionKey": "object_info_mars_description",
              "funFactKey": "object_info_mars_funfact",
              "type": "planet",
              "imageKey": "planets/hubble_mars.webp",
              "imageCredit": "NASA/ESA/Hubble"
            },
            "m42": {
              "nameKey": "m42",
              "descriptionKey": "object_info_m42_description",
              "funFactKey": "object_info_m42_funfact",
              "type": "nebula",
              "imageKey": "deep_sky_objects/orion_nebula.webp",
              "imageCredit": "NASA/Hubble"
            }
          }
        }
    """.trimIndent()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mockContext.resources).thenReturn(mockResources)
        `when`(mockContext.packageName).thenReturn("com.google.android.stardroid")
        `when`(mockAssetManager.open("object_info.json"))
            .thenReturn(ByteArrayInputStream(testJson.toByteArray()))

        // Sun string resources
        `when`(mockResources.getIdentifier("sun", "string", "com.google.android.stardroid")).thenReturn(1)
        `when`(mockResources.getIdentifier("object_info_sun_description", "string", "com.google.android.stardroid")).thenReturn(2)
        `when`(mockResources.getIdentifier("object_info_sun_funfact", "string", "com.google.android.stardroid")).thenReturn(3)
        `when`(mockResources.getString(1)).thenReturn("Sun")
        `when`(mockResources.getString(2)).thenReturn("Our star")
        `when`(mockResources.getString(3)).thenReturn("Very hot")

        // Mars string resources
        `when`(mockResources.getIdentifier("mars", "string", "com.google.android.stardroid")).thenReturn(4)
        `when`(mockResources.getIdentifier("object_info_mars_description", "string", "com.google.android.stardroid")).thenReturn(5)
        `when`(mockResources.getIdentifier("object_info_mars_funfact", "string", "com.google.android.stardroid")).thenReturn(6)
        `when`(mockResources.getString(4)).thenReturn("Mars")
        `when`(mockResources.getString(5)).thenReturn("Red planet")
        `when`(mockResources.getString(6)).thenReturn("Has Olympus Mons")

        // M42 string resources
        `when`(mockResources.getIdentifier("m42", "string", "com.google.android.stardroid")).thenReturn(7)
        `when`(mockResources.getIdentifier("object_info_m42_description", "string", "com.google.android.stardroid")).thenReturn(8)
        `when`(mockResources.getIdentifier("object_info_m42_funfact", "string", "com.google.android.stardroid")).thenReturn(9)
        `when`(mockResources.getString(7)).thenReturn("Orion Nebula")
        `when`(mockResources.getString(8)).thenReturn("A star-forming region")
        `when`(mockResources.getString(9)).thenReturn("Visible to the naked eye")

        registry = ObjectInfoRegistry(mockContext, mockAssetManager)
    }

    @Test
    fun getAllWithImages_returnsOnlyObjectsWithImages() {
        val items = registry.getAllWithImages()

        assertThat(items).isNotEmpty()
        // Sun has no imageKey — must be excluded
        assertThat(items.map { it.id }).doesNotContain("sun")
        // Mars and M42 have imageKey — must be included
        assertThat(items.map { it.id }).containsAtLeast("mars", "m42")
    }

    @Test
    fun getAllWithImages_allItemsHaveNonBlankImagePath() {
        val items = registry.getAllWithImages()

        for (item in items) {
            assertThat(item.imagePath).isNotNull()
            assertThat(item.imagePath).isNotEmpty()
        }
    }

    @Test
    fun getAllWithImages_imagePathHasCelestialImagesPrefix() {
        val items = registry.getAllWithImages()

        for (item in items) {
            assertThat(item.imagePath).startsWith("celestial_images/")
        }
    }

    @Test
    fun getAllWithImages_isSortedByName() {
        val items = registry.getAllWithImages()

        val names = items.map { it.name }
        assertThat(names).isInOrder()
    }

    @Test
    fun getAllWithImages_countMatchesObjectsWithImageKey() {
        // In the test JSON: 2 objects have imageKey (mars, m42), 1 does not (sun)
        assertThat(registry.getAllWithImages()).hasSize(2)
    }
}

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
package com.google.android.stardroid.gallery

import android.content.res.Resources
import com.google.android.stardroid.R
import java.util.*

/**
 * A collection of gallery images.
 *
 * @author John Taylor
 */
class HardcodedGallery internal constructor(private val resources: Resources) : Gallery {
  override val galleryImages: List<GalleryImage>

  /**
   * Adds an image from assets to the gallery with an internationalized search term.
   */
  private fun add(
    images: ArrayList<GalleryImage>, assetPath: String,
    nameId: Int, searchTermId: Int
  ) {
    images.add(
      GalleryImage(
        assetPath = assetPath,
        name = getString(nameId),
        searchTerm = getString(searchTermId)
      )
    )
  }

  private fun createImages(): ArrayList<GalleryImage> {
    val galleryImages = ArrayList<GalleryImage>()
    // Note the internationalized names in places.  Be sure that if the
    // search term is internationalized in the search index then it is here too.
    add(galleryImages, "celestial_images/messenger_11_07_39.jpg", R.string.mercury, R.string.mercury)
    add(galleryImages, "celestial_images/hubble_venus_clouds_tops.jpg", R.string.venus, R.string.venus)
    add(galleryImages, "celestial_images/hubble_mars.jpg", R.string.mars, R.string.mars)
    add(galleryImages, "celestial_images/hubble_jupiter.jpg", R.string.jupiter, R.string.jupiter)
    add(galleryImages, "celestial_images/hubble_saturn.jpg", R.string.saturn, R.string.saturn)
    add(galleryImages, "celestial_images/hubble_uranus.jpg", R.string.uranus, R.string.uranus)
    add(galleryImages, "celestial_images/hubble_neptune.jpg", R.string.neptune, R.string.neptune)
    add(galleryImages, "celestial_images/nh_pluto_in_false_color.jpg", R.string.pluto, R.string.pluto)
    add(galleryImages, "celestial_images/hubble_m1.jpg", R.string.crab_nebula, R.string.crab_nebula)
    add(galleryImages, "celestial_images/hubble_m13.jpg", R.string.hercules_gc, R.string.hercules_gc)
    add(galleryImages, "celestial_images/hubble_m16.jpg", R.string.eagle_nebula, R.string.eagle_nebula)
    add(galleryImages, "celestial_images/kennett_m31.jpg", R.string.andromeda_galaxy, R.string.andromeda_galaxy)
    add(galleryImages, "celestial_images/hubble_m45.jpg", R.string.pleiades, R.string.pleiades)
    add(galleryImages, "celestial_images/hubble_m51a.jpg", R.string.whirlpool_galaxy, R.string.whirlpool_galaxy)
    add(galleryImages, "celestial_images/hubble_m57.jpg", R.string.ring_nebula, R.string.ring_nebula)
    add(galleryImages, "celestial_images/hubble_m101.jpg", R.string.pinwheel_galaxy, R.string.pinwheel_galaxy)
    add(galleryImages, "celestial_images/hubble_m104.jpg", R.string.sombrero_galaxy, R.string.sombrero_galaxy)
    add(galleryImages, "celestial_images/hubble_catseyenebula.jpg", R.string.cats_eye_nebula, R.string.cats_eye_nebula)
    add(galleryImages, "celestial_images/hubble_omegacentauri.jpg", R.string.omega_centauri, R.string.omega_centauri)
    add(galleryImages, "celestial_images/hubble_orion.jpg", R.string.orion_nebula, R.string.orion_nebula)
    add(galleryImages, "celestial_images/hubble_ultra_deep_field.jpg", R.string.hubble_deep_field, R.string.hubble_deep_field)
    add(galleryImages, "celestial_images/hubble_v838.jpg", R.string.v838_mon, R.string.v838_mon)
    return galleryImages
  }

  private fun getString(id: Int): String {
    return resources.getString(id)
  }

  init {
    galleryImages = Collections.unmodifiableList(createImages())
  }
}

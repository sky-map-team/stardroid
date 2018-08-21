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

package com.google.android.stardroid.gallery;

import android.content.res.Resources;

import com.google.android.stardroid.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A collection of gallery images.
 *
 * @author John Taylor
 */
public class HardcodedGallery implements Gallery {
  private List<GalleryImage> images;
  private Resources resources;

  HardcodedGallery(Resources resources) {
    this.resources = resources;
    images = Collections.unmodifiableList(createImages());
  }

  public List<GalleryImage> getGalleryImages() {
    return images;
  }

  /**
   * Adds an image to the gallery, but using an internationalized search term.
   * Note, that for this to work the internationalized name _must_ be in the
   * search index.
   */
  private final void add(ArrayList<GalleryImage> images, int imageId,
      int nameId, int searchTermId) {
    images.add(new GalleryImage(imageId, getString(nameId), getString(searchTermId)));
  }

  private ArrayList<GalleryImage> createImages() {
    ArrayList<GalleryImage> galleryImages = new ArrayList<GalleryImage>();
    // Note the internationalized names in places.  Be sure that if the
    // search term is internationalized in the search index then it is here too.
    add(galleryImages, R.drawable.messenger_11_07_39, R.string.mercury, R.string.mercury);
    add(galleryImages, R.drawable.hubble_venus_clouds_tops, R.string.venus, R.string.venus);
    add(galleryImages, R.drawable.hubble_mars, R.string.mars, R.string.mars);
    add(galleryImages, R.drawable.hubble_jupiter, R.string.jupiter, R.string.jupiter);
    add(galleryImages, R.drawable.hubble_saturn, R.string.saturn, R.string.saturn);
    add(galleryImages, R.drawable.hubble_uranus, R.string.uranus, R.string.uranus);
    add(galleryImages, R.drawable.hubble_neptune, R.string.neptune, R.string.neptune);
    add(galleryImages, R.drawable.nh_pluto_in_false_color, R.string.pluto, R.string.pluto);
    add(galleryImages, R.drawable.hubble_m1, R.string.crab_nebula, R.string.crab_nebula);
    add(galleryImages, R.drawable.hubble_m13, R.string.hercules_gc, R.string.hercules_gc);
    add(galleryImages, R.drawable.hubble_m16, R.string.eagle_nebula, R.string.eagle_nebula);
    add(galleryImages, R.drawable.kennett_m31, R.string.andromeda_galaxy, R.string.andromeda_galaxy);
    add(galleryImages, R.drawable.hubble_m45, R.string.pleiades, R.string.pleiades);
    add(galleryImages, R.drawable.hubble_m51a, R.string.whirlpool_galaxy, R.string.whirlpool_galaxy);
    add(galleryImages, R.drawable.hubble_m57, R.string.ring_nebula, R.string.ring_nebula);
    add(galleryImages, R.drawable.hubble_m101, R.string.pinwheel_galaxy, R.string.pinwheel_galaxy);
    add(galleryImages, R.drawable.hubble_m104, R.string.sombrero_galaxy, R.string.sombrero_galaxy);
    add(galleryImages, R.drawable.hubble_catseyenebula, R.string.cats_eye_nebula, R.string.cats_eye_nebula);
    add(galleryImages, R.drawable.hubble_omegacentauri, R.string.omega_centauri, R.string.omega_centauri);
    add(galleryImages, R.drawable.hubble_orion, R.string.orion_nebula, R.string.orion_nebula);
    add(galleryImages, R.drawable.hubble_ultra_deep_field, R.string.hubble_deep_field, R.string.hubble_deep_field);
    add(galleryImages, R.drawable.hubble_v838, R.string.v838_mon, R.string.v838_mon);
    return galleryImages;
  }

  private String getString(int id) {
    return resources.getString(id);
  }
}

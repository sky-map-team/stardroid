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

/**
 * Constructs galleries.
 *
 * @author John Taylor
 */
object GalleryFactory {
  private var gallery: Gallery? = null

  /**
   * Returns the gallery.  This will usually be a singleton.
   */
  @JvmStatic
  @Synchronized
  fun getGallery(resources: Resources): Gallery? {
    if (gallery == null) {
      gallery = HardcodedGallery(resources)
    }
    return gallery
  }
}
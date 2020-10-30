// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.views.source;

import java.util.List;

import com.google.android.stardroid.source.ImageSource;
import com.google.android.stardroid.source.LineSource;
import com.google.android.stardroid.source.PointSource;
import com.google.android.stardroid.source.TextSource;

/**
 * Defines a simple listener interface which responds to changes in the sources and 
 * updates the corresponding views.
 * 
 * 
 * @author Brent Bryan
 */
public interface ViewSourceListener {

  /**
   * Sets all of the PointSources for a specific id in response to one or more
   * source changing values. Changes include addition of new object, updates of
   * current objects, or deletion of current objects. Any PointSources set
   * previously with the same id value will be overwritten.
   * 
   * @param id a unique identify integer for this set of point sources.
   * @param s a list of PointSources which should be used.
   */
  void setPointSources(int id, List<PointSource> s);

  /**
   * Sets all of the TextSources for a specific id in response to one or more
   * source changing values. Changes include addition of new object, updates of
   * current objects, or deletion of current objects. Any TextSources set
   * previously with the same id value will be overwritten.
   * 
   * @param id a unique identify integer for this set of point sources.
   * @param s a list of TextSources which should be used.
   */
  void setTextSources(int id, List<TextSource> s);

  /**
   * Sets all of the ImageSources for a specific id in response to one or more
   * source changing values. Changes include addition of new object, updates of
   * current objects, or deletion of current objects. Any ImageSources set
   * previously with the same id value will be overwritten.
   * 
   * @param id a unique identify integer for this set of point sources.
   * @param s a list of ImageSources which should be used.
   */
  void setImageSources(int id, List<ImageSource> s);

  /**
   * Sets all of the PolyLineSources for a specific id in response to one or more
   * source changing values. Changes include addition of new object, updates of
   * current objects, or deletion of current objects. Any PolyLineSources set
   * previously with the same id value will be overwritten.
   * 
   * @param id a unique identify integer for this set of point sources.
   * @param s a list of PolyLineSources which should be used.
   */
  void setPolyLineSources(int id, List<LineSource> s);
}

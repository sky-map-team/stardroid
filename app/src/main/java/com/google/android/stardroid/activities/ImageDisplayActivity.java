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

package com.google.android.stardroid.activities;

import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.gallery.GalleryFactory;
import com.google.android.stardroid.gallery.GalleryImage;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import java.util.List;

import javax.inject.Inject;

/**
 * Shows an image to the user and allows them to search for it.
 *
 * @author John Taylor
 *
 */
public class ImageDisplayActivity extends InjectableActivity {
  private static final String TAG = MiscUtil.getTag(ImageDisplayActivity.class);
  private static final int ERROR_MAGIC_NUMBER = -1;
  private GalleryImage selectedImage;
  private ActivityLightLevelManager activityLightLevelManager;
  @Inject
  Analytics analytics;

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    getApplicationComponent().inject(this);
    setContentView(R.layout.imagedisplay);
    activityLightLevelManager = new ActivityLightLevelManager(
        new ActivityLightLevelChanger(this, null),
        PreferenceManager.getDefaultSharedPreferences(this));
    Intent intent = getIntent();
    Log.d(TAG, intent.toString());
    int position  = intent.getIntExtra(ImageGalleryActivity.IMAGE_ID, ERROR_MAGIC_NUMBER);
    if (position == ERROR_MAGIC_NUMBER) {
      Log.e(TAG, "No position was provided with the intent - aborting.");
      finish();
    }

    List<GalleryImage> galleryImages = GalleryFactory.getGallery(getResources()).getGalleryImages();
    selectedImage = galleryImages.get(position);
    ImageView imageView = (ImageView) findViewById(R.id.gallery_image);
    imageView.setImageResource(selectedImage.imageId);
    TextView label = (TextView) findViewById(R.id.gallery_image_title);
    label.setText(selectedImage.name);
    Button backButton = (Button) findViewById(R.id.gallery_image_back_btn);
    backButton.setOnClickListener(this::goBack);
    Button searchButton = (Button) findViewById(R.id.gallery_image_search_btn);
    searchButton.setOnClickListener(this::doSearch);

  }

  @Override
  public void onStart() {
    super.onStart();
  }

  @Override
  public void onResume() {
    super.onResume();
    activityLightLevelManager.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
    activityLightLevelManager.onPause();
  }

  public void doSearch(View source) {
    Log.d(TAG, "Do Search");
    // We must ensure that all the relevant layers are actually visible or the search might
    // fail.  This is rather hacky.
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    Editor editor = sharedPreferences.edit();
    String[] keys = { "source_provider.0",  // Stars
                      "source_provider.2",  // Messier
                      "source_provider.3" };  // Planets
    for (String key : keys) {
      if (!sharedPreferences.getBoolean(key , false)) {
        editor.putBoolean(key, true);
      }
    }
    editor.commit();

    Intent queryIntent = new Intent();
    queryIntent.setAction(Intent.ACTION_SEARCH);
    queryIntent.putExtra(SearchManager.QUERY, selectedImage.searchTerm);
    queryIntent.setClass(ImageDisplayActivity.this, DynamicStarMapActivity.class);
    startActivity(queryIntent);
  }

  public void goBack(View source) {
    Log.d(TAG, "Go back");
    finish();
  }
}

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

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Gallery;
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
 * Displays a series of images to the user.  Selecting an image
 * invokes Sky Map Search.
 *
 * @author John Taylor
 */
public class ImageGalleryActivity extends InjectableActivity {
  /** The index of the image id Intent extra.*/
  public static final String IMAGE_ID = "image_id";

  private static final String TAG = MiscUtil.getTag(ImageGalleryActivity.class);
  private List<GalleryImage> galleryImages;

  private ActivityLightLevelManager activityLightLevelManager;
  @Inject
  Analytics analytics;

  private class ImageAdapter extends BaseAdapter {
    public int getCount() {
      return galleryImages.size();
    }

    public Object getItem(int position) {
      return position;
    }
    public long getItemId(int position) {
      return position;
    }

    /**
     * Returns a new ImageView to be displayed, depending on the position passed.
     */
    public View getView(int position, View convertView, ViewGroup parent) {
      Log.d(TAG, "Get view called for position "+ position);
      ViewGroup imagePanel;
      if (convertView != null && convertView instanceof ViewGroup) {
        imagePanel = (ViewGroup) convertView;
      } else {
        imagePanel = (ViewGroup) getLayoutInflater().inflate(
            R.layout.imagedisplaypanel, parent, false);
      }
      GalleryImage galleryImage = galleryImages.get(position);
      ImageView imageView = (ImageView) imagePanel.findViewById(R.id.image_gallery_image);
      imageView.setImageResource(galleryImage.imageId);
      TextView imageLabel = (TextView) imagePanel.findViewById(R.id.image_gallery_title);
      imageLabel.setText(galleryImage.name);
      return imagePanel;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getApplicationComponent().inject(this);
    setContentView(R.layout.imagegallery);
    activityLightLevelManager = new ActivityLightLevelManager(
        new ActivityLightLevelChanger(this, null),
        PreferenceManager.getDefaultSharedPreferences(this));
    this.galleryImages = GalleryFactory.getGallery(getResources()).getGalleryImages();
    addImagesToGallery();
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

  private void addImagesToGallery() {
    Gallery gallery = (Gallery) findViewById(R.id.image_gallery);
    ImageAdapter imageAdapter = new ImageAdapter();
    gallery.setAdapter(imageAdapter);
    gallery.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        showImage(position);
      }
  });
  }

  /**
   * Starts the display image activity, and overrides the transition animation.
   */
  private void showImage(int position) {
    Intent intent = new Intent(ImageGalleryActivity.this, ImageDisplayActivity.class);
    intent.putExtra(ImageGalleryActivity.IMAGE_ID, position);
    startActivity(intent);
    overridePendingTransition(R.anim.fadein, R.anim.fastzoom);
  }
}

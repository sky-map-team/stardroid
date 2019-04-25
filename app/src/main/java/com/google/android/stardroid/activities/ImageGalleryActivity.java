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
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.util.ActivityLightLevelChanger;
import com.google.android.stardroid.activities.util.ActivityLightLevelManager;
import com.google.android.stardroid.gallery.Gallery;
import com.google.android.stardroid.gallery.GalleryFactory;
import com.google.android.stardroid.gallery.GalleryImage;
import com.google.android.stardroid.util.Analytics;
import com.google.android.stardroid.util.MiscUtil;

import java.util.ArrayList;
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

  private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.MyViewHolder> {


    class MyViewHolder extends RecyclerView.ViewHolder {

      ImageView galleryImage;
      TextView galleryTitle;
      LinearLayout galleryItemLayout;
      MyViewHolder(View v) {
        super(v);

        this.galleryImage = v.findViewById(R.id.image_gallery_image);
        this.galleryTitle = v.findViewById(R.id.image_gallery_title);
        this.galleryItemLayout = v.findViewById(R.id.galleryItemLayout);
      }
    }

    @Override
    public ImageAdapter.MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      View view = LayoutInflater.from(parent.getContext())
              .inflate(R.layout.imagedisplaypanel, parent, false);
      return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ImageAdapter.MyViewHolder holder, final int position) {

      holder.galleryImage.setImageResource(galleryImages.get(position).imageId);
      holder.galleryTitle.setText(galleryImages.get(position).name);

      holder.galleryItemLayout.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          showImage(position);
        }
      });
    }

    public long getItemId(int position) {
      return position;
    }

    @Override
    public int getItemCount() {
      return galleryImages.size();
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
    analytics.trackPageView(Analytics.IMAGE_GALLERY_ACTIVITY);
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

    RecyclerView mRecyclerView = findViewById(R.id.gallery_list);
    RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
    mRecyclerView.setLayoutManager(mLayoutManager);
    ImageAdapter imageAdapter = new ImageAdapter();
    mRecyclerView.setAdapter(imageAdapter);
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

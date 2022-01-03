package com.google.android.stardroid.activities;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.StardroidApplication;

/**
 * Base class for all activities injected by Dagger.
 *
 * Created by johntaylor on 4/9/16.
 */
public abstract class InjectableActivity extends AppCompatActivity {
  protected ApplicationComponent getApplicationComponent() {
    return ((StardroidApplication) getApplication()).getApplicationComponent();
  }
}

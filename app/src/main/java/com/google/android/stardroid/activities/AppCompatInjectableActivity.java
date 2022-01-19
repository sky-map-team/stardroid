package com.google.android.stardroid.activities;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.stardroid.ApplicationComponent;
import com.google.android.stardroid.StardroidApplication;

public abstract class AppCompatInjectableActivity extends AppCompatActivity {
  protected ApplicationComponent getApplicationComponent() {
    return ((StardroidApplication) getApplication()).getApplicationComponent();
  }
}
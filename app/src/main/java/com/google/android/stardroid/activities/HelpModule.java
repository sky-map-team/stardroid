package com.google.android.stardroid.activities;

import android.content.Context;

import com.google.android.stardroid.inject.PerActivity;

import dagger.Module;
import dagger.Provides;

/**
 * Created by johntaylor on 2/17/22.
 */
@Module
public class HelpModule {
  private HelpActivity activity;
  public HelpModule(HelpActivity activity) {
    this.activity = activity;
  }

  @Provides
  @PerActivity
  Context provideContext() {
    return activity;
  }
}

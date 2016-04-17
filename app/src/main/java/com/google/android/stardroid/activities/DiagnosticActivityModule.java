package com.google.android.stardroid.activities;

import dagger.Module;

/**
 * Created by johntaylor on 4/15/16.
 */
@Module
public class DiagnosticActivityModule {
  private DiagnosticActivity activity;

  public DiagnosticActivityModule(DiagnosticActivity activity) {
    this.activity = activity;
  }
}

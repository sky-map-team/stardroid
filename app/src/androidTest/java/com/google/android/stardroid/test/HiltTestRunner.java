package com.google.android.stardroid.test;

import android.app.Application;
import android.content.Context;
import androidx.test.runner.AndroidJUnitRunner;
import dagger.hilt.android.testing.HiltTestApplication;

/**
 * A custom runner to set up the instrumented application class for Hilt tests.
 */
public final class HiltTestRunner extends AndroidJUnitRunner {

  @Override
  public Application newApplication(ClassLoader cl, String className, Context context)
      throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    return super.newApplication(cl, "com.google.android.stardroid.test.StardroidTestApplication_Application", context);
  }
}

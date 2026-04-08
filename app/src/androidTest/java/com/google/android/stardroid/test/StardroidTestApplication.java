package com.google.android.stardroid.test;

import com.google.android.stardroid.StardroidApplication;
import dagger.hilt.android.testing.CustomTestApplication;

/**
 * A custom test application that extends StardroidApplication to satisfy dependencies
 * that require the specific application class.
 */
@CustomTestApplication(StardroidApplication.class)
public interface StardroidTestApplication {
}

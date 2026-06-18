package com.google.android.stardroid

import dagger.hilt.android.HiltAndroidApp

/**
 * The Hilt-enabled main application class.
 */
@HiltAndroidApp
class MainApplication : StardroidApplication() {
  override fun onCreate() {
    super.onCreate()
    performHiltInitialization()
  }
}

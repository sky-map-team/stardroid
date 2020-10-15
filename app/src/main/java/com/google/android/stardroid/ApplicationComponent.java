package com.google.android.stardroid;

import android.accounts.AccountManager;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;

import com.google.android.stardroid.activities.EditSettingsActivity;
import com.google.android.stardroid.activities.ImageDisplayActivity;
import com.google.android.stardroid.activities.ImageGalleryActivity;
import com.google.android.stardroid.control.AstronomerModel;
import com.google.android.stardroid.control.MagneticDeclinationCalculator;
import com.google.android.stardroid.layers.LayerManager;
import com.google.android.stardroid.search.SearchTermsProvider;
import com.google.android.stardroid.util.AnalyticsInterface;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Component;

/**
 * Dagger component.
 * Created by johntaylor on 3/26/16.
 */
@Singleton
@Component(modules={ApplicationModule.class})
public interface ApplicationComponent {
  // What we expose to dependent components
  StardroidApplication provideStardroidApplication();
  SharedPreferences provideSharedPreferences();
  SensorManager provideSensorManager();
  ConnectivityManager provideConnectivityManager();
  AstronomerModel provideAstronomerModel();
  LocationManager provideLocationManager();
  LayerManager provideLayerManager();
  AccountManager provideAccountManager();
  AnalyticsInterface provideAnaytics();
  @Named("zero") MagneticDeclinationCalculator provideMagDec1();
  @Named("real") MagneticDeclinationCalculator provideMagDec2();

  // Who can we inject
  void inject(StardroidApplication app);
  void inject(EditSettingsActivity activity);
  void inject(ImageDisplayActivity activity);
  void inject(ImageGalleryActivity activity);
  void inject(SearchTermsProvider provider);
}

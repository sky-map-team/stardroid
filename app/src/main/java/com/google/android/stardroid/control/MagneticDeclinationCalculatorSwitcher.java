// Copyright 2009 Google Inc.
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

package com.google.android.stardroid.control;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;

import com.google.android.stardroid.util.MiscUtil;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Aggregates the RealMagneticDeclinationCalculator and the
 * ZeroMagneticDeclinationCalculator and switches them in the AstronomerModel.
 *
 * @author John Taylor
 */
// TODO(johntaylor): add a unit test once we've reworked the tests to use
// the Android environment (so we can access SharePreferences).
public class MagneticDeclinationCalculatorSwitcher implements OnSharedPreferenceChangeListener {
  private static final String KEY = "use_magnetic_correction";
  private static final String TAG = MiscUtil.getTag(MagneticDeclinationCalculatorSwitcher.class);

  private MagneticDeclinationCalculator realCalculator;
  private MagneticDeclinationCalculator zeroCalculator;
  private AstronomerModel model;

  /**
   * Constructs a new MagneticDeclinationCalculatorSwitcher.
   *
   * @param model the object in which to swap the calculator
   * @param preferences a SharedPreferences object which will indicate which
   * calculator to use.
   */
  @Inject
  public MagneticDeclinationCalculatorSwitcher(
      AstronomerModel model,
      SharedPreferences preferences,
      @Named("zero") MagneticDeclinationCalculator zeroCalculator,
      @Named("real") MagneticDeclinationCalculator realCalculator) {
    this.zeroCalculator = zeroCalculator;
    this.realCalculator = realCalculator;
    this.model = model;
    preferences.registerOnSharedPreferenceChangeListener(this);
    setTheModelsCalculator(preferences);
  }

  private void setTheModelsCalculator(SharedPreferences preferences) {
    boolean useRealCalculator = preferences.getBoolean(KEY, true);
    if (useRealCalculator) {
      model.setMagneticDeclinationCalculator(realCalculator);
    } else {
      model.setMagneticDeclinationCalculator(zeroCalculator);
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    // TODO(johntaylor): investigate the preferences API - currently we have too
    // many classes all hanging off SharedPreferences.
    if (KEY.equals(key)) {
      Log.i(TAG, "Magnetic declination preference changed");
      setTheModelsCalculator(sharedPreferences);
    }

  }
}

package com.google.android.stardroid.test;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.rule.GrantPermissionRule;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.CompassCalibrationActivity;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.activities.util.FullscreenControlsManager;
import com.google.android.stardroid.control.LocationController;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressBack;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.Matchers.not;

public class DynamicStarMapActivityTest {

  private static class SetupRule extends ExternalResource {
    @Override
    protected void before() throws Throwable {
      // We have to set preferences very early otherwise the app starts and doesn't pick them up.
      Context context = getInstrumentation().getTargetContext();
      SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
      editor.putBoolean(CompassCalibrationActivity.DONT_SHOW_CALIBRATION_DIALOG, true);
      editor.commit();
    };

    @Override
    protected void after() {
      // code to tear down the external resource
    };
  }

  private SetupRule firstRule = new SetupRule();

  @Rule
  public ActivityScenarioRule<DynamicStarMapActivity> testRule =
      new ActivityScenarioRule(DynamicStarMapActivity.class);

  @Rule
  public RuleChain chain = RuleChain.outerRule(firstRule).around(testRule);

  // For other great ideas about the permissions dialogs see
  // https://alexzh.com/ui-testing-of-android-runtime-permissions/
  @Rule
  public androidx.test.rule.GrantPermissionRule permissionRule = GrantPermissionRule.grant(
      android.Manifest.permission.ACCESS_FINE_LOCATION
  );

  @Before
  public void disableCalibrationDialog() {
    Context context = getInstrumentation().getTargetContext();
    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
    editor.putBoolean(CompassCalibrationActivity.DONT_SHOW_CALIBRATION_DIALOG, true);
    editor.putBoolean(LocationController.NO_AUTO_LOCATE, true);  // This disables the Google Play Services check
    editor.commit();
  }

  private static final String TAG = "STARTEST";

  //@Test
  public void testSkyMapTouchControlsShowAndThenGo() throws Exception {
    // Wait for initial controls to go away. This is bad.
    // Perhaps use idling resources?
    Log.w(TAG, "Waiting....");
    Thread.sleep(FullscreenControlsManager.INITIALLY_SHOW_CONTROLS_FOR_MILLIS * 2);
    Log.w(TAG, "Click");
    onView(withId(R.id.skyrenderer_view)).check(matches(isDisplayed()));
    onView(withId(R.id.main_sky_view_root)).perform(click());
    // Espresso should make this kind of crap unnecessary - investigate what's going on...
    // we probably have some ill behaved animation.
    Thread.sleep(100);
    // Not obvious why IsDisplayed not working here?
    onView(withId(R.id.layer_buttons_control)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
    Log.w(TAG, "Is visible? Waiting");
    onView(withId(R.id.main_sky_view_root)).perform(click());
    Thread.sleep(100);
    onView(withId(R.id.layer_buttons_control)).check(matches(not(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE))));
  }
}

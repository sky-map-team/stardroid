package com.google.android.stardroid.test;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.rule.GrantPermissionRule;

import com.google.android.stardroid.ApplicationConstants;
import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.CompassCalibrationActivity;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.activities.util.FullscreenControlsManager;
import com.google.android.stardroid.control.LocationController;

import org.junit.Before;
import org.junit.Ignore;
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
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static org.hamcrest.Matchers.not;

import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.action.GeneralLocation;

import android.app.SearchManager;
import android.view.View;

import java.util.concurrent.atomic.AtomicBoolean;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiSelector;

import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy;
import tools.fastlane.screengrab.locale.LocaleTestRule;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@HiltAndroidTest
public class DynamicStarMapActivityTest {
  @org.junit.ClassRule
  public static final LocaleTestRule localeTestRule = new LocaleTestRule();

  // For other great ideas about the permissions dialogs see
  // https://alexzh.com/ui-testing-of-android-runtime-permissions/
  // For other great ideas about the permissions dialogs see
  // https://alexzh.com/ui-testing-of-android-runtime-permissions/
  private final GrantPermissionRule permissionRule = GrantPermissionRule.grant(
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.CHANGE_CONFIGURATION);

  private final SetupRule firstRule = new SetupRule();

  @Rule(order = 0)
  public final HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  private final ActivityScenarioRule<DynamicStarMapActivity> testRule = new ActivityScenarioRule<>(
          DynamicStarMapActivity.class);

  @Rule(order = 1)
  public RuleChain chain = RuleChain.outerRule(permissionRule)
          .around(firstRule)
          .around(testRule);


  private static class SetupRule extends ExternalResource {
    @Override
    protected void before() throws Throwable {
      // We have to set preferences very early otherwise the app starts and doesn't
      // pick them up.
      Context context = getInstrumentation().getTargetContext();
      SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
      editor.putBoolean(CompassCalibrationActivity.DONT_SHOW_CALIBRATION_DIALOG, true);
      // This just gets overriden by the app on startup editor.putBoolean("auto_mode",
      // false);
      editor.putString(ApplicationConstants.LATITUDE_PREF_KEY, "37.7749");
      editor.putString(ApplicationConstants.LONGITUDE_PREF_KEY, "-122.4194");
      editor.commit();
    };

    @Override
    protected void after() {
      // code to tear down the external resource
    };
  }

  @Before
  public void disableCalibrationDialog() {
    Context context = getInstrumentation().getTargetContext();
    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
    editor.putBoolean(CompassCalibrationActivity.DONT_SHOW_CALIBRATION_DIALOG, true);
    editor.putBoolean(ApplicationConstants.NO_AUTO_LOCATE_PREF_KEY, true); // This disables the Google Play Services check
    editor.commit();

    Screengrab.setDefaultScreenshotStrategy(new UiAutomatorScreenshotStrategy());
  }

  private static final String TAG = "STARTEST";

  @Ignore("Flaky on emulator: causes emulator to crash mid-test")
  @Test
  public void testSkyMapTouchControlsShowAndThenGo() throws Exception {
    // Wait for initial controls to go away. This is bad.
    // Perhaps use idling resources?
    Log.w(TAG, "Waiting....");
    /*
    Thread.sleep(FullscreenControlsManager.INITIALLY_SHOW_CONTROLS_FOR_MILLIS * 2);
    Log.w(TAG, "Click");
    onView(withId(R.id.skyrenderer_view)).check(matches(isDisplayed()));
    onView(withId(R.id.main_sky_view_root)).perform(click());
    // Espresso should make this kind of crap unnecessary - investigate what's going
    // on...
    // we probably have some ill behaved animation.
    Thread.sleep(100);
    // Not obvious why IsDisplayed not working here?
    onView(withId(R.id.layer_buttons_control)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
    Log.w(TAG, "Is visible? Waiting");
    onView(withId(R.id.main_sky_view_root)).perform(click());
    Thread.sleep(100);
    onView(withId(R.id.layer_buttons_control))
        .check(matches(not(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE))));
     */
  }

  private void ensureControlsVisible() {
    AtomicBoolean isVisible = new AtomicBoolean(false);
    testRule.getScenario().onActivity(activity -> {
      View layerButtons = activity.findViewById(R.id.layer_buttons_control);
      isVisible.set(layerButtons != null && layerButtons.getVisibility() == View.VISIBLE);

      if (!isVisible.get()) {
        FullscreenControlsManager fcm = activity.getFullscreenControlsManager();
        if (fcm != null) {
          fcm.toggleControls();
        }
      }
    });
    if (!isVisible.get()) {
      try {
        Thread.sleep(1500);
      } catch (Exception ignored) {
      }
    }
  }

  private void waitForView(org.hamcrest.Matcher<View> viewMatcher, long timeoutMs) {
    long endTime = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < endTime) {
      try {
        onView(viewMatcher).check(matches(isDisplayed()));
        return;
      } catch (Throwable t) {
        try {
          Thread.sleep(200);
        } catch (Exception ignored) {
        }
      }
    }
    // Final check, will throw the exception if still not found
    onView(viewMatcher).check(matches(isDisplayed()));
  }

  private void switchToManualMode() {
    try {
      ensureControlsVisible();

      // Click the manual/auto toggle to switch to manual mode
      // (...note this assumes we're in auto mode...)
      onView(withId(R.id.manual_auto_toggle)).perform(click());
      Thread.sleep(500);
    } catch (Exception e) {
      // Ignore
    }
  }

  @ScreengrabTest
  @Test
  public void testSearchCircleScreenshot() throws Exception {
    Thread.sleep(FullscreenControlsManager.INITIALLY_SHOW_CONTROLS_FOR_MILLIS * 2);
    Context context = getInstrumentation().getTargetContext();
    Intent intent = new Intent(context, DynamicStarMapActivity.class);
    intent.setAction(Intent.ACTION_SEARCH);
    intent.putExtra(SearchManager.QUERY, context.getString(R.string.mars));
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    context.startActivity(intent);
    ensureControlsVisible(); // because it looks nicer.

    stupidlyLongWaitForToastsToClear();
    Screengrab.screenshot("4_search_circle_mars");
  }

  @ScreengrabTest
  @Test
  public void testMapWithJupiterScreenshot() throws Exception {
    Thread.sleep(FullscreenControlsManager.INITIALLY_SHOW_CONTROLS_FOR_MILLIS * 2);
    switchToManualMode();
    Context context = getInstrumentation().getTargetContext();
    Intent intent = new Intent(context, DynamicStarMapActivity.class);
    intent.setAction(Intent.ACTION_SEARCH);
    intent.putExtra(SearchManager.QUERY, context.getString(R.string.jupiter));
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    context.startActivity(intent);

    Thread.sleep(2000); // Wait for the search target to appear and settle
    // Cancel the search UI
    onView(withId(R.id.cancel_search_button)).perform(click());
    stupidlyLongWaitForToastsToClear();

    Screengrab.screenshot("1_map_jupiter");
  }

  private void stupidlyLongWaitForToastsToClear() throws InterruptedException {
    Thread.sleep(20000);
  }

  private void clickActionBarItem(int viewId, int stringId) {
    ensureControlsVisible();
    try {
      onView(withId(viewId)).perform(click());
    } catch (Throwable t) {
      try {
        androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(getInstrumentation().getTargetContext());
        Thread.sleep(500);
      } catch (Exception e) {
      }
      try {
        String localizedText = getInstrumentation().getTargetContext().getString(stringId);
        onView(withText(localizedText)).perform(click());
      } catch (Throwable t2) {
        // Fallback to English if localization is broken (Screengrab locale switching is
        // sometimes flaky)
        if (stringId == R.string.menu_gallery) {
          onView(withText("Gallery")).perform(click());
        } else if (stringId == R.string.menu_time) {
          onView(withText("Time Travel")).perform(click());
        } else {
          throw t2;
        }
      }
    }
  }

  @ScreengrabTest
  @Test
  public void testGalleryScrolledToJupiterScreenshot() throws Exception {
    Thread.sleep(FullscreenControlsManager.INITIALLY_SHOW_CONTROLS_FOR_MILLIS * 2);
    onView(withId(R.id.skyrenderer_view)).check(matches(isDisplayed()));

    // Open Gallery
    clickActionBarItem(R.id.menu_item_gallery, R.string.menu_gallery);

    Thread.sleep(5000); // Wait for gallery to load

    // Scroll to Jupiter
    String jupiterText = getInstrumentation().getTargetContext().getString(R.string.jupiter);
    // We have seen trouble with this scrolling working on devies with non-US
    // locales
    // Somehow just changing fr-FR to fr OR adding this log statement magically
    // fixed it...but further investigation might be needed.
    Log.w(TAG, "Scrolling to " + jupiterText);

    onView(withId(R.id.gallery_grid)).perform(RecyclerViewActions.scrollTo(hasDescendant(withText(jupiterText))));
    stupidlyLongWaitForToastsToClear();

    Screengrab.screenshot("2_gallery_scrolled");
  }

  @ScreengrabTest
  @Test
  public void testExposedCraniumNebulaInfoCardScreenshot() throws Exception {
    Thread.sleep(FullscreenControlsManager.INITIALLY_SHOW_CONTROLS_FOR_MILLIS * 2);
    switchToManualMode();
    Context context = getInstrumentation().getTargetContext();
    Intent intent = new Intent(context, DynamicStarMapActivity.class);
    intent.setAction(Intent.ACTION_SEARCH);
    intent.putExtra(SearchManager.QUERY, context.getString(R.string.exposed_cranium_nebula));
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    context.startActivity(intent);

    Thread.sleep(2000); // Wait for the search target to appear and settle
    // Cancel the search UI
    onView(withId(R.id.cancel_search_button)).perform(click());
    Thread.sleep(2000);

    // Click to bring up info card
    onView(withId(R.id.main_sky_view_root)).perform(click());
    stupidlyLongWaitForToastsToClear();

    Screengrab.screenshot("3_exposed_cranium_info");
  }

  @ScreengrabTest
  @Test
  public void testOrionAndTimeTravelScreenshot() throws Exception {
    Thread.sleep(FullscreenControlsManager.INITIALLY_SHOW_CONTROLS_FOR_MILLIS * 2);
    switchToManualMode();
    Context context = getInstrumentation().getTargetContext();
    Intent intent = new Intent(context, DynamicStarMapActivity.class);
    intent.setAction(Intent.ACTION_SEARCH);
    intent.putExtra(SearchManager.QUERY, context.getString(R.string.orion));
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    context.startActivity(intent);

    Thread.sleep(2000); // Wait for search target to appear
    onView(withId(R.id.cancel_search_button)).perform(click());
    Thread.sleep(500);

    clickActionBarItem(R.id.menu_item_time, R.string.menu_time);
    Thread.sleep(1000);

    // Click "Start from now"
    onView(withId(R.id.timeTravelGo)).perform(click());
    stupidlyLongWaitForToastsToClear();

    Screengrab.screenshot("5_orion_time_travel");
  }
}

package com.google.android.stardroid.test;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.google.android.stardroid.activities.SplashScreenActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * End-to-end tests covering the new-user startup path:
 * SplashScreen → EULA → WarmWelcome → permission → DynamicStarMap.
 *
 * <p>Tests use UiAutomator rather than Espresso for dialog interaction because Espresso loses
 * window focus on edge-to-edge dialogs on Android 15+ (API 35+). See
 * https://github.com/sky-map-team/stardroid/issues/605.
 */
@HiltAndroidTest
public class StartUpTest {

  private static final String PKG = "com.google.android.stardroid";
  private static final long DIALOG_TIMEOUT_MS = 10_000;

  @Rule(order = 0)
  public final HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  private final PreferenceCleanerRule preferenceCleanerRule = new PreferenceCleanerRule();

  private final ActivityScenarioRule<SplashScreenActivity> activityRule =
      new ActivityScenarioRule<>(SplashScreenActivity.class);

  @Rule(order = 1)
  public final RuleChain chain =
      RuleChain.outerRule(preferenceCleanerRule).around(activityRule);

  /** Clears shared preferences before each test so every test starts as a "new user". */
  private static class PreferenceCleanerRule extends ExternalResource {
    @Override
    protected void before() {
      Context context = getInstrumentation().getTargetContext();
      SharedPreferences.Editor editor =
          PreferenceManager.getDefaultSharedPreferences(context).edit();
      editor.clear();
      editor.commit();
    }
  }

  private UiDevice device() {
    return UiDevice.getInstance(getInstrumentation());
  }

  @Test
  public void appLaunches_eulaDialogIsShown() {
    UiObject2 eula = device().wait(
        Until.findObject(By.res(PKG, "eula_webview")), DIALOG_TIMEOUT_MS);
    assertThat("EULA dialog should be shown on first launch", eula, notNullValue());
  }
}

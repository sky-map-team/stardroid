package com.google.android.stardroid.test;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.SplashScreenActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/*
If you're running this on your phone and you get an error about
"NoActivityResumed" check you've unlocked your phone.
 */
@HiltAndroidTest
public class SplashScreenActivityTest {
  public static final String COM_GOOGLE_ANDROID_STARDROID = "com.google.android.stardroid";
  @Rule
  public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  private static class PreferenceCleanerRule extends ExternalResource {
    @Override
    protected void before() throws Throwable {
      Context context = getInstrumentation().getTargetContext();
      SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
      editor.clear();
      editor.commit();
    };

    @Override
    protected void after() {
      // code to tear down the external resource
    };
  }

  private final PreferenceCleanerRule preferenceCleanerRule = new PreferenceCleanerRule();

  private final ActivityScenarioRule<SplashScreenActivity> testRule =
      new ActivityScenarioRule<>(SplashScreenActivity.class);

  @Rule
  public RuleChain chain = RuleChain.outerRule(preferenceCleanerRule).around(testRule);

  /**
   * Tests that accepting T&Cs shows the What's New dialog.
   */
  @Test
  public void showsTutorialThenWhatsNewAfterTandCs_newUser() throws InterruptedException {
    UiDevice device = UiDevice.getInstance(getInstrumentation());
    long timeout = 5000;
    device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "eula_webview")), timeout);

    // Use UiAutomator to click dialog buttons — Espresso's inRoot(isDialog()) requires the dialog
    // window to have focus, which is not guaranteed on API 35+ due to edge-to-edge enforcement.
    device.findObject(By.res("android", "button1")).click();
    device.wait(Until.hasObject(By.res(
            COM_GOOGLE_ANDROID_STARDROID, "warm_welcome_viewpager")), timeout);

    onView(withId(R.id.warm_welcome_viewpager)).check(matches(isDisplayed()));
    onView(withId(R.id.welcome_slide_1_root)).check(matches(isDisplayed()));
    onView(withId(R.id.btn_next_finish)).perform(click());
    onView(withId(R.id.welcome_slide_2_root)).check(matches(isDisplayed()));
    onView(withId(R.id.btn_next_finish)).perform(click());
    onView(withId(R.id.welcome_slide_3_root)).check(matches(isDisplayed()));
    onView(withId(R.id.btn_next_finish)).perform(click());
    device.wait(Until.hasObject(By.res(
            COM_GOOGLE_ANDROID_STARDROID, "whatsnew_webview")), timeout);

    assertThat(device.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "whatsnew_webview")), is(true));
  }

  /**
   * Tests that declining T&Cs closes the app.
   */
  @Test
  public void showNoAcceptTandCs() throws InterruptedException {
    UiDevice device = UiDevice.getInstance(getInstrumentation());
    long timeout = 5000;
    device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "eula_webview")), timeout);

    // Use UiAutomator to click dialog buttons — Espresso's inRoot(isDialog()) requires the dialog
    // window to have focus, which is not guaranteed on API 35+ due to edge-to-edge enforcement.
    device.findObject(By.res("android", "button2")).click();
    device.wait(Until.gone(By.res(COM_GOOGLE_ANDROID_STARDROID, "eula_webview")), timeout);
    assertThat(testRule.getScenario().getState(), equalTo(Lifecycle.State.DESTROYED));
  }

  @Test
  public void useAppContext() {
      // Context of the app under test.
      Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
      assertThat(appContext.getPackageName(), is("com.google.android.stardroid"));
  }
}

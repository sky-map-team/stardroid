package com.google.android.stardroid.test;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.SplashScreenActivity;

import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;

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
   * ViewAction that waits for a view to be laid out with non-zero height.
   * This replaces Thread.sleep() with a polling approach that integrates with Espresso's
   * main thread looping.
   */
  private static ViewAction waitForLayout() {
    return new ViewAction() {
      @Override
      public Matcher<View> getConstraints() {
        // Use any View - we'll wait for it to be laid out
        return org.hamcrest.Matchers.any(View.class);
      }

      @Override
      public String getDescription() {
        return "wait for view to be laid out with non-zero height";
      }

      @Override
      public void perform(UiController uiController, View view) {
        // Poll until the view has a non-zero height (max ~5 seconds)
        int maxAttempts = 100;
        for (int i = 0; i < maxAttempts && view.getHeight() == 0; i++) {
          uiController.loopMainThreadForAtLeast(50);
        }
      }
    };
  }

  /**
   * Tests that accepting T&Cs shows the What's New dialog.
   *
   * Note: This test is skipped on Android 15+ (API 35+) due to a known issue with
   * edge-to-edge enforcement that prevents Espresso from getting window focus on dialogs.
   * See: https://github.com/sky-map-team/stardroid/issues/605. (Android edge-to-edge dialog focus issue)
   */
  @Test
  public void showsTutorialThenWhatsNewAfterTandCs_newUser() throws InterruptedException {
    UiDevice device = UiDevice.getInstance(getInstrumentation());
    long timeout = 5000; // 5 seconds max wait
    device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "eula_webview")), timeout);

    onView(withId(R.id.eula_webview)).inRoot(isDialog()).perform(waitForLayout());
    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
    device.wait(Until.hasObject(By.res(
            COM_GOOGLE_ANDROID_STARDROID, "warm_welcome_viewpager")), timeout);
    //Thread.sleep(4000);

    onView(withId(R.id.warm_welcome_viewpager)).check(matches(isDisplayed()));
    onView(withId(R.id.welcome_slide_1_root)).check(matches(isDisplayed()));
    onView(withId(R.id.btn_next_finish)).perform(click());
    onView(withId(R.id.welcome_slide_2_root)).check(matches(isDisplayed()));
    onView(withId(R.id.btn_next_finish)).perform(click());
    onView(withId(R.id.welcome_slide_3_root)).check(matches(isDisplayed()));
    onView(withId(R.id.btn_next_finish)).perform(click());
    device.wait(Until.hasObject(By.res(
            COM_GOOGLE_ANDROID_STARDROID, "whatsnew_webview")), timeout);

    // What's new?
    onView(withId(R.id.whatsnew_webview)).inRoot(isDialog()).check(matches(isDisplayed()));
  }

  /**
   * Tests that declining T&Cs closes the app.
   *
   * Note: This test is skipped on Android 15+ (API 35+) due to a known issue with
   * edge-to-edge enforcement that prevents Espresso from getting window focus on dialogs.
   */
  @Test
  public void showNoAcceptTandCs() throws InterruptedException {
    UiDevice device = UiDevice.getInstance(getInstrumentation());
    long timeout = 5000; // 5 seconds max wait
    device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "eula_webview")), timeout);

    // Wait for the WebView to be laid out (it starts with height=0)
    onView(withId(R.id.eula_webview)).inRoot(isDialog()).perform(waitForLayout());
    onView(withId(R.id.eula_webview)).inRoot(isDialog()).check(matches(isDisplayed()));
    // Decline button
    onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click());
    // Wait for activity to finish
    //Thread.sleep(5000);
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

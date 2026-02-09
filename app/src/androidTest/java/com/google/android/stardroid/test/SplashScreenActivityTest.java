package com.google.android.stardroid.test;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.SplashScreenActivity;

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

/*
If you're running this on your phone and you get an error about
"NoActivityResumed" check you've unlocked your phone.
 */

public class SplashScreenActivityTest {
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

  private PreferenceCleanerRule preferenceCleanerRule = new PreferenceCleanerRule();

  private ActivityScenarioRule<SplashScreenActivity> testRule =
      new ActivityScenarioRule(SplashScreenActivity.class);

  @Rule
  public RuleChain chain = RuleChain.outerRule(preferenceCleanerRule).around(testRule);

  /**
   * Tests that accepting T&Cs shows the What's New dialog.
   *
   * Note: This test is skipped on Android 15+ (API 35+) due to a known issue with
   * edge-to-edge enforcement that prevents Espresso from getting window focus on dialogs.
   * See: https://issuetracker.google.com/issues/... (Android edge-to-edge dialog focus issue)
   */
  @Test
  public void showsWhatsNewAfterTandCs_newUser() throws InterruptedException {
    // Skip on Android 15+ due to edge-to-edge window focus issues with Espresso
    Assume.assumeTrue("Skipping on Android 15+ due to edge-to-edge dialog focus issues",
        Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM);

    // Wait for the WebView to finish loading and layout (it starts with height=0)
    Thread.sleep(2000);
    onView(withId(R.id.eula_webview)).inRoot(isDialog()).check(matches(isDisplayed()));
    Thread.sleep(2000);
    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
    // The fadeout animation takes 3000ms, so we need to wait longer than that.
    Thread.sleep(4000);
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
    // Skip on Android 15+ due to edge-to-edge window focus issues with Espresso
    Assume.assumeTrue("Skipping on Android 15+ due to edge-to-edge dialog focus issues",
        Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM);

    Log.d("TESTTEST", "Doing test");
    // Wait for the WebView to finish loading and layout (it starts with height=0)
    Thread.sleep(2000);
    onView(withId(R.id.eula_webview)).inRoot(isDialog()).check(matches(isDisplayed()));
    // Decline button
    onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click());
    // Sigh. There seems nothing better here.
    Thread.sleep(5000);
    assertThat(testRule.getScenario().getState(), equalTo(Lifecycle.State.DESTROYED));
  }

  @Test
  public void useAppContext() {
      // Context of the app under test.
      Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
      assertThat(appContext.getPackageName(), is("com.google.android.stardroid"));
  }
}
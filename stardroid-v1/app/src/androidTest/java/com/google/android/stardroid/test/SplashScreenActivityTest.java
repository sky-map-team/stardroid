package com.google.android.stardroid.test;

import android.app.AlertDialog;
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
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;

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
   * Clicks an EULA dialog button on the main thread. UiAutomator cannot reliably find
   * AlertDialog buttons in the accessibility tree on AOSP system images (API 35+ edge-to-edge
   * enforcement means dialog windows don't get focus; older AOSP images expose buttons
   * inconsistently). Going through the fragment manager is robust across all variants.
   */
  private void clickEulaButton(int whichButton) {
    testRule.getScenario().onActivity(activity -> {
      EulaDialogFragment eula = (EulaDialogFragment) activity.getSupportFragmentManager()
          .findFragmentByTag(EulaDialogFragment.class.getSimpleName());
      if (eula != null && eula.getDialog() != null) {
        ((AlertDialog) eula.getDialog()).getButton(whichButton).performClick();
      }
    });
    getInstrumentation().waitForIdleSync();
  }

  /**
   * Tests that accepting T&Cs shows the What's New dialog.
   */
  @Test
  public void showsTutorialThenWhatsNewAfterTandCs_newUser() {
    UiDevice device = UiDevice.getInstance(getInstrumentation());
    long timeout = 5000;
    device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "eula_webview")), timeout);

    clickEulaButton(AlertDialog.BUTTON_POSITIVE);
    device.wait(Until.hasObject(By.res(
            COM_GOOGLE_ANDROID_STARDROID, "warm_welcome_viewpager")), timeout);

    onView(withId(R.id.warm_welcome_viewpager)).check(matches(isDisplayed()));
    onView(withId(R.id.welcome_slide_1_root)).check(matches(isDisplayed()));
    onView(withId(R.id.btn_next_finish)).perform(click());
    device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "welcome_slide_2_root")), timeout);
    onView(withId(R.id.welcome_slide_2_root)).check(matches(isDisplayed()));
    onView(withId(R.id.btn_next_finish)).perform(click());
    device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "welcome_slide_3_root")), timeout);
    onView(withId(R.id.welcome_slide_3_root)).check(matches(isDisplayed()));
    onView(withId(R.id.btn_next_finish)).perform(click());
    // Check for the What's New dialog by its title — more reliable than looking for
    // the WebView, which may not appear in the accessibility tree on API 35+.
    assertThat("What's New dialog should appear after warm welcome",
        device.wait(Until.hasObject(By.text("What's new")), timeout), is(true));
  }

  /**
   * Tests that declining T&Cs closes the app.
   */
  @Test
  public void showNoAcceptTandCs() {
    UiDevice device = UiDevice.getInstance(getInstrumentation());
    long timeout = 5000;
    device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "eula_webview")), timeout);

    clickEulaButton(AlertDialog.BUTTON_NEGATIVE);
    device.wait(Until.gone(By.res(COM_GOOGLE_ANDROID_STARDROID, "eula_webview")), timeout);
    getInstrumentation().waitForIdleSync();
    assertThat(testRule.getScenario().getState(), equalTo(Lifecycle.State.DESTROYED));
  }

  @Test
  public void useAppContext() {
      // Context of the app under test.
      Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
      assertThat(appContext.getPackageName(), is("com.google.android.stardroid"));
  }
}

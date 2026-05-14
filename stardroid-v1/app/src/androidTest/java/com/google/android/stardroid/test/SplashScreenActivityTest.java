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
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.google.android.stardroid.activities.SplashScreenActivity;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;

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
  private static final long TIMEOUT_MS = 10_000;

  @Rule
  public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  private static class PreferenceCleanerRule extends ExternalResource {
    @Override
    protected void before() throws Throwable {
      Context context = getInstrumentation().getTargetContext();
      SharedPreferences.Editor editor =
          PreferenceManager.getDefaultSharedPreferences(context).edit();
      editor.clear();
      editor.commit();
    }

    @Override
    protected void after() {}
  }

  private final PreferenceCleanerRule preferenceCleanerRule = new PreferenceCleanerRule();

  private final ActivityScenarioRule<SplashScreenActivity> testRule =
      new ActivityScenarioRule<>(SplashScreenActivity.class);

  @Rule
  public RuleChain chain = RuleChain.outerRule(preferenceCleanerRule).around(testRule);

  /**
   * Polls the fragment manager until the EULA dialog is showing, then clicks the specified button
   * directly on the main thread. This avoids UiAutomator (WebViews aren't in the accessibility
   * tree on API 35+) and Espresso (windows lack focus on API 36 AOSP emulators).
   */
  private void clickEulaButton(int whichButton) throws InterruptedException {
    long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      final boolean[] ready = {false};
      testRule.getScenario().onActivity(activity -> {
        EulaDialogFragment eula = (EulaDialogFragment) activity.getSupportFragmentManager()
            .findFragmentByTag(EulaDialogFragment.class.getSimpleName());
        if (eula != null && eula.getDialog() != null && eula.getDialog().isShowing()) {
          ready[0] = true;
        }
      });
      if (ready[0]) break;
      Thread.sleep(200);
    }

    testRule.getScenario().onActivity(activity -> {
      EulaDialogFragment eula = (EulaDialogFragment) activity.getSupportFragmentManager()
          .findFragmentByTag(EulaDialogFragment.class.getSimpleName());
      if (eula != null && eula.getDialog() != null) {
        ((AlertDialog) eula.getDialog()).getButton(whichButton).performClick();
      }
    });
    getInstrumentation().waitForIdleSync();
  }

  /** Polls until the activity reaches DESTROYED state or the timeout elapses. */
  private void waitForActivityDestroyed() throws InterruptedException {
    long deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      if (testRule.getScenario().getState() == Lifecycle.State.DESTROYED) return;
      Thread.sleep(200);
    }
  }

  /**
   * Tests that accepting T&Cs shows the What's New dialog.
   * Uses only UiAutomator (no Espresso) because API 36 AOSP emulator windows lack focus,
   * which causes RootViewWithoutFocusException in Espresso.
   */
  @Test
  public void showsTutorialThenWhatsNewAfterTandCs_newUser() throws InterruptedException {
    UiDevice device = UiDevice.getInstance(getInstrumentation());

    clickEulaButton(AlertDialog.BUTTON_POSITIVE);

    assertThat("warm welcome viewpager should appear",
        device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "warm_welcome_viewpager")), TIMEOUT_MS),
        is(true));
    assertThat("slide 1 should be shown",
        device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "welcome_slide_1_root")), TIMEOUT_MS),
        is(true));

    UiObject2 nextBtn = device.findObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "btn_next_finish"));
    nextBtn.click();
    assertThat("slide 2 should be shown",
        device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "welcome_slide_2_root")), TIMEOUT_MS),
        is(true));

    nextBtn = device.findObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "btn_next_finish"));
    nextBtn.click();
    assertThat("slide 3 should be shown",
        device.wait(Until.hasObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "welcome_slide_3_root")), TIMEOUT_MS),
        is(true));

    nextBtn = device.findObject(By.res(COM_GOOGLE_ANDROID_STARDROID, "btn_next_finish"));
    nextBtn.click();

    assertThat("What's New dialog should appear after warm welcome",
        device.wait(Until.hasObject(By.text("What's new")), TIMEOUT_MS), is(true));
  }

  /**
   * Tests that declining T&Cs closes the app.
   */
  @Test
  public void showNoAcceptTandCs() throws InterruptedException {
    clickEulaButton(AlertDialog.BUTTON_NEGATIVE);
    waitForActivityDestroyed();
    assertThat(testRule.getScenario().getState(), equalTo(Lifecycle.State.DESTROYED));
  }

  @Test
  public void useAppContext() {
    Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertThat(appContext.getPackageName(), is("com.google.android.stardroid"));
  }
}

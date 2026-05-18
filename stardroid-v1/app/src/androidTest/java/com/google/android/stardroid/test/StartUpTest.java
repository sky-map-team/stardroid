package com.google.android.stardroid.test;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.activities.SplashScreenActivity;
import com.google.android.stardroid.activities.WarmWelcomeActivity;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.activities.dialogs.LocationPermissionRationaleDialogFragment;
import com.google.android.stardroid.activities.dialogs.WhatsNewDialogFragment;

import androidx.viewpager2.widget.ViewPager2;

import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * End-to-end tests covering the new-user startup path:
 * SplashScreen → EULA → WarmWelcome → permission → DynamicStarMap.
 *
 * <p>Assertions and interactions go through {@link ActivityScenario#onActivity} and the
 * {@link androidx.fragment.app.FragmentManager} rather than UiAutomator. On Android 15+ (API 35+)
 * edge-to-edge dialog windows can be unreachable by accessibility-based queries used by
 * UiAutomator, which made the previous Espresso/UiAutomator suite flaky in CI. Driving the
 * dialog directly from the activity sidesteps that race.
 * See https://github.com/sky-map-team/stardroid/issues/605.
 */
@HiltAndroidTest
public class StartUpTest {

  private static final String PKG = "com.google.android.stardroid";
  private static final long TIMEOUT_MS = 10_000;
  /** Splash fade animation is ~3s; allow a generous margin on slow CI emulators. */
  private static final long SPLASH_TIMEOUT_MS = 20_000;
  private static final String EULA_TAG = EulaDialogFragment.class.getSimpleName();
  private static final String WHATS_NEW_TAG = WhatsNewDialogFragment.class.getSimpleName();
  private static final String LOCATION_RATIONALE_TAG =
      LocationPermissionRationaleDialogFragment.class.getSimpleName();

  @Rule
  public final HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  private ActivityScenario<SplashScreenActivity> scenario;

  @Before
  public void setUp() {
    Context context = getInstrumentation().getTargetContext();
    SharedPreferences.Editor editor =
        PreferenceManager.getDefaultSharedPreferences(context).edit();
    editor.clear();
    editor.commit();
    scenario = ActivityScenario.launch(SplashScreenActivity.class);
  }

  @After
  public void tearDown() {
    if (scenario != null) {
      scenario.close();
      scenario = null;
    }
  }

  @Test
  public void appLaunches_eulaDialogIsShown() {
    waitForFragment(EULA_TAG, TIMEOUT_MS);
  }

  @Test
  public void eulaAccepted_warmWelcomeIsShown() {
    waitForFragment(EULA_TAG, TIMEOUT_MS);
    clickDialogButton(EULA_TAG, DialogInterface.BUTTON_POSITIVE);
    // The splash fade animation runs (~3s) before WarmWelcomeActivity is launched.
    waitForActivityResumed(WarmWelcomeActivity.class, SPLASH_TIMEOUT_MS);
  }

  @Test
  public void warmWelcome_swipingThroughAllSlides_reachesFinalSlide() {
    waitForFragment(EULA_TAG, TIMEOUT_MS);
    clickDialogButton(EULA_TAG, DialogInterface.BUTTON_POSITIVE);
    waitForActivityResumed(WarmWelcomeActivity.class, SPLASH_TIMEOUT_MS);

    // Three slides; click "Next" twice to advance to the last one.
    clickNextOnWarmWelcome();
    waitForWarmWelcomeSlide(1, TIMEOUT_MS);
    clickNextOnWarmWelcome();
    waitForWarmWelcomeSlide(2, TIMEOUT_MS);
  }

  @Test
  public void newUserCompletesOnboarding_reachesSkyMapAndRequestsLocation() {
    waitForFragment(EULA_TAG, TIMEOUT_MS);
    clickDialogButton(EULA_TAG, DialogInterface.BUTTON_POSITIVE);
    waitForActivityResumed(WarmWelcomeActivity.class, SPLASH_TIMEOUT_MS);

    // Slides 0 -> 1 -> 2.
    clickNextOnWarmWelcome();
    waitForWarmWelcomeSlide(1, TIMEOUT_MS);
    clickNextOnWarmWelcome();
    waitForWarmWelcomeSlide(2, TIMEOUT_MS);
    // Finishing slide 2 marks the warm welcome seen and shows the What's New dialog.
    clickNextOnWarmWelcome();
    waitForFragment(WHATS_NEW_TAG, TIMEOUT_MS);

    // Dismissing What's New launches DynamicStarMapActivity.
    clickDialogButton(WHATS_NEW_TAG, DialogInterface.BUTTON_NEGATIVE);
    waitForActivityResumed(DynamicStarMapActivity.class, TIMEOUT_MS);

    // The sky map asks for a location with an in-app rationale dialog before triggering the
    // system permission request.
    waitForFragment(LOCATION_RATIONALE_TAG, TIMEOUT_MS);
  }

  @Test
  public void eulaDeclined_finishesActivity() {
    waitForFragment(EULA_TAG, TIMEOUT_MS);
    clickDialogButton(EULA_TAG, DialogInterface.BUTTON_NEGATIVE);
    waitForState(Lifecycle.State.DESTROYED, TIMEOUT_MS);
    assertThat(
        "Activity should be destroyed after EULA decline",
        scenario.getState(),
        equalTo(Lifecycle.State.DESTROYED));
  }

  /**
   * Polls the currently-resumed {@link FragmentActivity}'s FragmentManager until a fragment with
   * {@code tag} appears.
   */
  private void waitForFragment(String tag, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      boolean[] found = new boolean[1];
      getInstrumentation()
          .runOnMainSync(
              () -> {
                FragmentActivity host = currentResumedActivity(FragmentActivity.class);
                if (host != null) {
                  found[0] = host.getSupportFragmentManager().findFragmentByTag(tag) != null;
                }
              });
      if (found[0]) return;
      sleep(100);
    }
    fail("Fragment with tag '" + tag + "' was not shown within " + timeoutMs + "ms");
  }

  /**
   * Triggers a click on the AlertDialog button inside the named dialog fragment hosted by the
   * currently-resumed FragmentActivity.
   */
  private void clickDialogButton(String tag, int whichButton) {
    getInstrumentation()
        .runOnMainSync(
            () -> {
              FragmentActivity host = currentResumedActivity(FragmentActivity.class);
              if (host == null) {
                fail("No resumed FragmentActivity to dispatch click to");
                return;
              }
              DialogFragment fragment =
                  (DialogFragment) host.getSupportFragmentManager().findFragmentByTag(tag);
              if (fragment == null) {
                fail("Dialog fragment '" + tag + "' should be present");
                return;
              }
              AlertDialog dialog = (AlertDialog) fragment.getDialog();
              dialog.getButton(whichButton).performClick();
            });
  }

  /** Performs a click on the warm welcome's "Next/Finish" button on the main thread. */
  private void clickNextOnWarmWelcome() {
    getInstrumentation()
        .runOnMainSync(
            () -> {
              WarmWelcomeActivity activity = currentResumedActivity(WarmWelcomeActivity.class);
              if (activity == null) {
                fail("WarmWelcomeActivity is not currently resumed");
                return;
              }
              activity.findViewById(R.id.btn_next_finish).performClick();
            });
  }

  /** Polls until the warm-welcome view pager reaches {@code position}. */
  private void waitForWarmWelcomeSlide(int position, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      int[] current = new int[] {-1};
      getInstrumentation()
          .runOnMainSync(
              () -> {
                WarmWelcomeActivity activity = currentResumedActivity(WarmWelcomeActivity.class);
                if (activity != null) {
                  ViewPager2 pager = activity.findViewById(R.id.warm_welcome_viewpager);
                  if (pager != null) current[0] = pager.getCurrentItem();
                }
              });
      if (current[0] == position) return;
      sleep(100);
    }
    fail("Warm welcome did not reach slide index " + position + " within " + timeoutMs + "ms");
  }

  /** Returns the currently-resumed activity of the given type, or null if none. */
  @SuppressWarnings("unchecked")
  private <T extends Activity> T currentResumedActivity(Class<T> activityClass) {
    Collection<Activity> resumed =
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
    for (Activity a : resumed) {
      if (activityClass.isInstance(a)) return (T) a;
    }
    return null;
  }

  /** Polls until an activity of {@code activityClass} reaches the RESUMED stage. */
  private void waitForActivityResumed(Class<? extends Activity> activityClass, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      boolean[] found = new boolean[1];
      getInstrumentation()
          .runOnMainSync(() -> found[0] = currentResumedActivity(activityClass) != null);
      if (found[0]) return;
      sleep(100);
    }
    fail(activityClass.getSimpleName() + " did not reach RESUMED within " + timeoutMs + "ms");
  }

  private void waitForState(Lifecycle.State target, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (scenario.getState() == target) return;
      sleep(100);
    }
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}

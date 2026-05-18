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
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.UiDevice;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.DynamicStarMapActivity;
import com.google.android.stardroid.activities.SplashScreenActivity;
import com.google.android.stardroid.activities.WarmWelcomeActivity;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;
import com.google.android.stardroid.activities.dialogs.LocationPermissionRationaleDialogFragment;
import com.google.android.stardroid.activities.dialogs.WhatsNewDialogFragment;

import androidx.viewpager2.widget.ViewPager2;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;

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
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
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

  /**
   * Activity classes that have reached {@link Stage#RESUMED} at any point during the test.
   * Captured via a lifecycle callback so the test thread can observe even if the activity
   * pauses again quickly (e.g. when a system dialog steals focus right after onResume).
   */
  private final Set<Class<?>> classesEverResumed =
      ConcurrentHashMap.newKeySet();

  /** Activities that are currently alive (in any stage prior to DESTROYED). */
  private final Set<Activity> liveActivities = ConcurrentHashMap.newKeySet();

  private final ActivityLifecycleCallback resumedRecorder =
      (activity, stage) -> {
        if (stage == Stage.RESUMED) {
          classesEverResumed.add(activity.getClass());
        }
        if (stage == Stage.PRE_ON_CREATE || stage == Stage.CREATED) {
          liveActivities.add(activity);
        } else if (stage == Stage.DESTROYED) {
          liveActivities.remove(activity);
        }
      };

  @Before
  public void setUp() {
    // Permissions cannot be revoked here: pm revoke kills the target app process, which is also
    // the host of the test instrumentation. Instead the suite relies on @FixMethodOrder so the
    // single test that grants ACCESS_COARSE_LOCATION runs last.
    Context context = getInstrumentation().getTargetContext();
    SharedPreferences.Editor editor =
        PreferenceManager.getDefaultSharedPreferences(context).edit();
    editor.clear();
    editor.commit();
    classesEverResumed.clear();
    liveActivities.clear();
    ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(resumedRecorder);
    scenario = ActivityScenario.launch(SplashScreenActivity.class);
  }

  @After
  public void tearDown() {
    ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(resumedRecorder);
    // The scenario only tracks SplashScreenActivity; the new-user flow chains forward to
    // WarmWelcomeActivity and DynamicStarMapActivity. DynamicStarMapActivity is doing heavy
    // OpenGL + star-data loading on the main thread, so runOnMainSync from here would block
    // until that work drains and JUnit's per-test timeout fires. Post the finishes async and
    // let the main looper run them when it is free.
    // Finish stragglers (most importantly DynamicStarMapActivity) so they do not hog the main
    // looper across tests. The activity set is captured by the lifecycle callback so reading it
    // is non-blocking; Activity#finish is safe to call from any thread.
    for (Activity a : liveActivities) {
      if (!a.isFinishing()) {
        a.finish();
      }
    }
    // Skip ActivityScenario#close: it would runOnMainSync to walk SplashScreenActivity to
    // DESTROYED, which has already happened naturally before the flow chained forward.
    scenario = null;
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
  public void newUserCompletesOnboarding_reachesSkyMap() {
    runOnboardingThroughWhatsNew();
    waitForActivityResumed(DynamicStarMapActivity.class, TIMEOUT_MS);
  }

  /**
   * Named with a {@code z} prefix so {@link FixMethodOrder} runs it last: pm grant cannot be
   * undone from within the test process (pm revoke kills it), so granting must not bleed into
   * tests that expect the rationale dialog.
   */
  @Test
  public void zzGrantedLocationPermission_reachesSkyMapWithoutRationale()
      throws IOException {
    // Grant before reaching DynamicStarMapActivity so the rationale dialog is not shown.
    UiDevice.getInstance(getInstrumentation())
        .executeShellCommand("pm grant " + PKG + " android.permission.ACCESS_COARSE_LOCATION");

    runOnboardingThroughWhatsNew();
    waitForActivityResumed(DynamicStarMapActivity.class, TIMEOUT_MS);

    // Give the activity a moment to settle, then assert the rationale dialog is not shown.
    sleep(1000);
    assertFragmentAbsent(LOCATION_RATIONALE_TAG);
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

  /** Runs the new-user onboarding flow up through dismissing the What's New dialog. */
  private void runOnboardingThroughWhatsNew() {
    waitForFragment(EULA_TAG, TIMEOUT_MS);
    clickDialogButton(EULA_TAG, DialogInterface.BUTTON_POSITIVE);
    waitForActivityResumed(WarmWelcomeActivity.class, SPLASH_TIMEOUT_MS);
    clickNextOnWarmWelcome();
    waitForWarmWelcomeSlide(1, TIMEOUT_MS);
    clickNextOnWarmWelcome();
    waitForWarmWelcomeSlide(2, TIMEOUT_MS);
    clickNextOnWarmWelcome();
    waitForFragment(WHATS_NEW_TAG, TIMEOUT_MS);
    clickDialogButton(WHATS_NEW_TAG, DialogInterface.BUTTON_NEGATIVE);
  }

  /** Asserts that no fragment with {@code tag} is currently shown on the resumed activity. */
  private void assertFragmentAbsent(String tag) {
    boolean[] found = new boolean[1];
    getInstrumentation()
        .runOnMainSync(
            () -> {
              FragmentActivity host = currentResumedActivity(FragmentActivity.class);
              if (host != null) {
                found[0] = host.getSupportFragmentManager().findFragmentByTag(tag) != null;
              }
            });
    if (found[0]) {
      fail("Fragment '" + tag + "' was unexpectedly present");
    }
  }

  /** Polls until the named fragment is no longer attached. */
  private void waitForFragmentGone(String tag, long timeoutMs) {
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
      if (!found[0]) return;
      sleep(100);
    }
    fail("Fragment '" + tag + "' was still present after " + timeoutMs + "ms");
  }

  /**
   * Clicks a view inside the currently-resumed activity's dialog (or content view). Used to
   * dispatch clicks on dialog buttons that are part of a custom layout rather than standard
   * AlertDialog buttons.
   */
  private void clickViewOnResumedActivity(int viewId) {
    getInstrumentation()
        .runOnMainSync(
            () -> {
              FragmentActivity host = currentResumedActivity(FragmentActivity.class);
              if (host == null) {
                fail("No resumed FragmentActivity to dispatch click to");
                return;
              }
              // Look for the view in the topmost dialog fragments first.
              for (androidx.fragment.app.Fragment f :
                  host.getSupportFragmentManager().getFragments()) {
                if (f instanceof DialogFragment) {
                  android.app.Dialog dialog = ((DialogFragment) f).getDialog();
                  if (dialog != null) {
                    android.view.View v = dialog.findViewById(viewId);
                    if (v != null) {
                      v.performClick();
                      return;
                    }
                  }
                }
              }
              android.view.View v = host.findViewById(viewId);
              if (v != null) {
                v.performClick();
                return;
              }
              fail("View with id " + viewId + " not found on resumed activity or its dialogs");
            });
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

  /**
   * Polls until an activity of {@code activityClass} has reached the RESUMED stage at any point
   * (recorded by {@link #resumedRecorder}). Reading the recorder is non-blocking, so this still
   * works when the main thread is saturated loading data and {@code runOnMainSync} would stall.
   */
  private void waitForActivityResumed(Class<? extends Activity> activityClass, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (classesEverResumed.contains(activityClass)) return;
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

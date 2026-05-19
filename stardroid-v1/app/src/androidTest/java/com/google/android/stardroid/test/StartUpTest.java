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
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.SplashScreenActivity;
import com.google.android.stardroid.activities.WarmWelcomeActivity;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * End-to-end tests covering the new-user startup path: SplashScreen → EULA → WarmWelcome.
 *
 * <p>Assertions and interactions go through {@link ActivityScenario#onActivity} and the
 * {@link androidx.fragment.app.FragmentManager} rather than UiAutomator. On Android 15+ (API 35+)
 * edge-to-edge dialog windows can be unreachable by accessibility-based queries used by
 * UiAutomator, which made the previous Espresso/UiAutomator suite flaky in CI. Driving the
 * dialogs directly from the activity sidesteps that race.
 * See https://github.com/sky-map-team/stardroid/issues/605.
 *
 * <p>The tests stop at the warm welcome rather than continuing into DynamicStarMapActivity:
 * the sky map saturates the main looper loading star data and initialising OpenGL, which made
 * the test runner unable to tear the activity down within its window on the slow CI emulator
 * (the emulator was kicked out of adb during gradle's post-test cleanup).
 */
@HiltAndroidTest
public class StartUpTest {

  private static final long TIMEOUT_MS = 10_000;
  /** Splash fade animation is ~3s; allow a generous margin on slow CI emulators. */
  private static final long SPLASH_TIMEOUT_MS = 20_000;
  private static final String EULA_TAG = EulaDialogFragment.class.getSimpleName();

  @Rule
  public final HiltAndroidRule hiltRule = new HiltAndroidRule(this);

  private ActivityScenario<SplashScreenActivity> scenario;

  /**
   * Activity classes that have reached {@link Stage#RESUMED} at any point during the test.
   * Captured via a lifecycle callback so the test thread can observe even if the activity
   * pauses again quickly (e.g. when a system dialog steals focus right after onResume).
   */
  private final Set<Class<?>> classesEverResumed = ConcurrentHashMap.newKeySet();

  /** Activities currently alive (created but not yet destroyed) — used for teardown. */
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
    // Finish stragglers (e.g. WarmWelcomeActivity left visible by a test). Activity#finish is
    // thread-safe and the live-activity set is maintained by the lifecycle callback, so this
    // doesn't need to round-trip through the main looper.
    for (Activity a : liveActivities) {
      if (!a.isFinishing()) {
        a.finish();
      }
    }
    // Skip ActivityScenario#close: it would runOnMainSync to walk the splash to DESTROYED, which
    // has already happened naturally once the EULA accept flow chained forward to WarmWelcome.
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
  public void eulaDeclined_finishesActivity() {
    waitForFragment(EULA_TAG, TIMEOUT_MS);
    clickDialogButton(EULA_TAG, DialogInterface.BUTTON_NEGATIVE);
    waitForState(Lifecycle.State.DESTROYED, TIMEOUT_MS);
    assertThat(
        "Activity should be destroyed after EULA decline",
        scenario.getState(),
        equalTo(Lifecycle.State.DESTROYED));
  }

  @Test
  public void warmWelcome_swipingThroughAllSlides_reachesFinalSlide() {
    waitForFragment(EULA_TAG, TIMEOUT_MS);
    clickDialogButton(EULA_TAG, DialogInterface.BUTTON_POSITIVE);
    waitForActivityResumed(WarmWelcomeActivity.class, SPLASH_TIMEOUT_MS);

    // Three slides; click "Next" twice to advance from slide 0 to slide 2.
    clickNextOnWarmWelcome();
    waitForWarmWelcomeSlide(1, TIMEOUT_MS);
    clickNextOnWarmWelcome();
    waitForWarmWelcomeSlide(2, TIMEOUT_MS);
  }

  /** Polls the currently-resumed FragmentActivity's FragmentManager for the named tag. */
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

  /** Performs a click on an AlertDialog button hosted by the named DialogFragment. */
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
   * Polls until an activity of {@code activityClass} has reached RESUMED at any point. Reading
   * the lifecycle-callback-maintained set is non-blocking, which keeps the wait honest even if
   * the main thread later saturates and an Instrumentation#runOnMainSync poll would stall.
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

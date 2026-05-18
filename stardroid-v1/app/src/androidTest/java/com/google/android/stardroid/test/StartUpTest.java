package com.google.android.stardroid.test;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;

import android.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;

import com.google.android.stardroid.activities.SplashScreenActivity;
import com.google.android.stardroid.activities.dialogs.EulaDialogFragment;

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
  private static final String EULA_TAG = EulaDialogFragment.class.getSimpleName();

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
  public void eulaDeclined_finishesActivity() {
    waitForFragment(EULA_TAG, TIMEOUT_MS);
    clickDialogButton(EULA_TAG, DialogInterface.BUTTON_NEGATIVE);
    waitForState(Lifecycle.State.DESTROYED, TIMEOUT_MS);
    assertThat(
        "Activity should be destroyed after EULA decline",
        scenario.getState(),
        equalTo(Lifecycle.State.DESTROYED));
  }

  /** Polls the host activity's FragmentManager until a fragment with {@code tag} appears. */
  private void waitForFragment(String tag, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      boolean[] found = new boolean[1];
      scenario.onActivity(
          activity ->
              found[0] = activity.getSupportFragmentManager().findFragmentByTag(tag) != null);
      if (found[0]) return;
      sleep(100);
    }
    fail("Fragment with tag '" + tag + "' was not shown within " + timeoutMs + "ms");
  }

  /** Triggers a click on the AlertDialog button inside the named dialog fragment. */
  private void clickDialogButton(String tag, int whichButton) {
    scenario.onActivity(
        activity -> {
          DialogFragment fragment =
              (DialogFragment) activity.getSupportFragmentManager().findFragmentByTag(tag);
          if (fragment == null) {
            fail("Dialog fragment '" + tag + "' should be present");
          }
          AlertDialog dialog = (AlertDialog) fragment.getDialog();
          dialog.getButton(whichButton).performClick();
        });
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

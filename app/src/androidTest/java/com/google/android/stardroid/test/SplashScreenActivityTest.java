package com.google.android.stardroid.test;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.SplashScreenActivity;

import org.junit.Rule;
import org.junit.Test;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;

/*
If you're running this on your phone and you get an error about
"NoActivityResumed" check you've unlocked your phone.
 */

public class SplashScreenActivityTest {
  @Rule
  public ActivityScenarioRule<SplashScreenActivity> testRule =
      new ActivityScenarioRule(SplashScreenActivity.class);

  @Test
  public void showsTermsAndConditions_newUser() {
    Context context = getInstrumentation().getTargetContext();
    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
    editor.clear();
    editor.commit();
    // Pick up the changed preferences.
    testRule.getScenario().moveToState(Lifecycle.State.RESUMED);
    onView(withId(R.id.eula_box_text)).inRoot(isDialog()).check(matches(isDisplayed()));
  }

  @Test
  public void showsSplashScreenAfterTocAccept() {
    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
   // onView(withId(R.id.splash)).check(matches(isDisplayed()));
    onView(withId(R.id.whats_new_box_text)).check(matches(isDisplayed()));
  }

  @Test
  public void useAppContext() {
      // Context of the app under test.
      Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
      assertEquals("com.google.android.stardroid", appContext.getPackageName());
  }
}
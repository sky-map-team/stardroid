package com.google.android.stardroid.test;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.SplashScreenActivity;

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

  @Test
  public void showsWhatsNewAfterTandCs_newUser() throws InterruptedException {
    onView(withId(R.id.eula_box_text)).inRoot(isDialog()).check(matches(isDisplayed()));
    Thread.sleep(2000);
    onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
    // TODO: figure out how to dispense with crap like hand-tuned waiting times.
    Thread.sleep(2000);
    // Can't detect this since the UI is still changing.
    // TODO: figure out how we could.
    //onView(withId(R.id.splash)).check(matches(isDisplayed()));
    onView(withId(R.id.whats_new_box_text)).check(matches(isDisplayed()));
  }

  @Test
  public void showNoAcceptTandCs() throws InterruptedException {
    Log.d("TESTTEST", "Doing test");
    onView(withId(R.id.eula_box_text)).inRoot(isDialog()).check(matches(isDisplayed()));
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
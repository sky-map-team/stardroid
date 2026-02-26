package com.google.android.stardroid.test;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.google.android.stardroid.R;
import com.google.android.stardroid.activities.CompassCalibrationActivity;
import com.google.android.stardroid.activities.SplashScreenActivity;
import com.google.android.stardroid.control.LocationController;

import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import tools.fastlane.screengrab.Screengrab;
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

public class ScreenshotTest {

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    );

    private UiDevice device;

    @Before
    public void setUp() {
        device = UiDevice.getInstance(getInstrumentation());
        Context context = getInstrumentation().getTargetContext();
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.clear();
        // Disable calibration dialog and auto-locate to keep the UI clean
        editor.putBoolean(CompassCalibrationActivity.DONT_SHOW_CALIBRATION_DIALOG, true);
        editor.putBoolean(LocationController.NO_AUTO_LOCATE, true);
        editor.commit();

        Screengrab.setDefaultScreenshotStrategy(new UiAutomatorScreenshotStrategy());

        // Handle Locale
        Bundle extras = InstrumentationRegistry.getArguments();
        if (extras.containsKey("testLocale")) {
            String testLocale = extras.getString("testLocale");
            System.setProperty("testLocale", testLocale);

            // Manually set the locale for this process
            String[] parts = testLocale.split("-");
            Locale locale = parts.length == 2 ? new Locale(parts[0], parts[1]) : new Locale(parts[0]);
            Locale.setDefault(locale);

            Resources res = context.getResources();
            Configuration config = res.getConfiguration();
            config.setLocale(locale);
            res.updateConfiguration(config, res.getDisplayMetrics());

            Log.d("SCREENSHOT", "Setting locale to: " + locale.toString());

            // Also update the target context
            Context targetContext = getInstrumentation().getTargetContext();
            Resources targetRes = targetContext.getResources();
            Configuration targetConfig = targetRes.getConfiguration();
            targetConfig.setLocale(locale);
            targetRes.updateConfiguration(targetConfig, targetRes.getDisplayMetrics());
            Log.d("SCREENSHOT", "Current Locale: " + Locale.getDefault().toString());
        }
    }

    @Test
    public void takeStoreScreenshots() throws InterruptedException {
        // Skip on Android 15+ due to edge-to-edge dialog focus issues with Espresso
        Assume.assumeTrue("Skipping on Android 15+ due to edge-to-edge dialog focus issues",
                Build.VERSION.SDK_INT < 35);

        ActivityScenario.launch(SplashScreenActivity.class);

        // 1. Accept EULA
        // Using UI Automator to find the button by resource ID is more robust than Espresso's inRoot(isDialog())
        UiObject acceptButton = device.findObject(new UiSelector().resourceId("android:id/button1"));
        if (acceptButton.waitForExists(5000)) {
            try {
                acceptButton.click();
            } catch (Exception e) {
                // Ignore
            }
        }

        // 2. Dismiss What's New
        UiObject okButton = device.findObject(new UiSelector().resourceId("android:id/button2"));
        if (okButton.waitForExists(5000)) {
            try {
                okButton.click();
            } catch (Exception e) {
                // Ignore
            }
        }

        // 3. Wait for transition to DynamicStarMapActivity
        Thread.sleep(3000);

        // 4. Tap to show layer buttons
        onView(withId(R.id.main_sky_view_root)).perform(click());
        Thread.sleep(1000); // Wait for animation

        // 5. Verify layer buttons are visible
        onView(withId(R.id.layer_buttons_control)).check(matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));

        // 6. Take the screenshot
        Screengrab.screenshot("01_main_screen_with_layers");
        Thread.sleep(2000); // Wait for the screenshot to be saved
    }
}

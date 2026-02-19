// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.stardroid.test

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.google.android.stardroid.R
import com.google.android.stardroid.activities.CompassCalibrationActivity
import com.google.android.stardroid.activities.DynamicStarMapActivity
import com.google.android.stardroid.control.LocationController
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * Tests for Activity navigation and lifecycle.
 *
 * These tests verify that activities can be launched, navigated between,
 * and that state is preserved correctly across the lifecycle.
 */
class ActivityNavigationTest {

    /**
     * Rule to set up preferences before the activity launches.
     */
    private class SetupRule : ExternalResource() {
        override fun before() {
            val context = getInstrumentation().targetContext
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            // Disable dialogs that would interfere with tests
            editor.putBoolean(CompassCalibrationActivity.DONT_SHOW_CALIBRATION_DIALOG, true)
            editor.putBoolean(LocationController.NO_AUTO_LOCATE, true)
            // Accept EULA so we skip the splash screen
            editor.putString("EULA", "accepted")
            editor.commit()
        }

        override fun after() {
            // Clean up preferences after test
            val context = getInstrumentation().targetContext
            PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        }
    }

    private val setupRule = SetupRule()

    private val activityRule = ActivityScenarioRule(DynamicStarMapActivity::class.java)

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(setupRule).around(activityRule)

    // Note: We don't use GrantPermissionRule because:
    // 1. It has issues on Android 14+ (SecurityException)
    // 2. We set NO_AUTO_LOCATE = true, so the app doesn't need location permission
    // The app will show a permission denied dialog if needed, but that's fine for these tests

    @Before
    fun setup() {
        // Additional setup if needed
    }

    @Test
    fun mainActivity_displaysRenderer() {
        // Verify the main sky renderer view is displayed
        onView(withId(R.id.skyrenderer_view)).check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_hasCorrectLifecycleState() {
        // Activity should be in RESUMED state
        // Note: Don't call scenario.state from within onActivity - it causes threading issues
        assertThat(
            activityRule.scenario.state,
            equalTo(Lifecycle.State.RESUMED)
        )
    }

    @Test
    fun activityRecreation_preservesLifecycle() {
        // First, verify the activity is running
        activityRule.scenario.onActivity { }

        // Recreate the activity (simulates configuration change)
        activityRule.scenario.recreate()

        // Force the test to wait until the NEW instance is ready
        activityRule.scenario.onActivity {
            // Just being here means the new activity has reached a stable state
        }

        // Verify the activity is still in RESUMED state
        assertThat(
            activityRule.scenario.state,
            equalTo(Lifecycle.State.RESUMED)
        )

        // Verify the main view is still displayed
        onView(withId(R.id.skyrenderer_view)).check(matches(isDisplayed()))
    }

    @Test
    fun preferencesSetInRule_areRespected() {
        // Verify our setup rule's preferences are being used
        val context = getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        assertThat(
            prefs.getBoolean(CompassCalibrationActivity.DONT_SHOW_CALIBRATION_DIALOG, false),
            equalTo(true)
        )
    }

    @Test
    fun activityRecreation_preservesPreferences() {
        // Set a test preference
        val context = getInstrumentation().targetContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("test_pref_key", true).commit()

        // Recreate the activity
        activityRule.scenario.recreate()

        // Verify preference is still set
        val prefsAfter = PreferenceManager.getDefaultSharedPreferences(context)
        assertThat(
            prefsAfter.getBoolean("test_pref_key", false),
            equalTo(true)
        )
    }
}

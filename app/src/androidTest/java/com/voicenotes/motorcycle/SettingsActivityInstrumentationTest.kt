package com.voicenotes.motorcycle

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal instrumentation test: Launch SettingsActivity and assert that the
 * request permissions button is displayed. This is a deterministic smoke test
 * useful for verifying that instrumentation test plumbing and Firebase Test
 * Lab integration are working.
 */
@RunWith(AndroidJUnit4::class)
class SettingsActivityInstrumentationTest {

    @Test
    fun settingsActivity_showsRequestPermissionsButton() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            // Wait for activity to be in resumed state and assert the view is visible
            onView(withId(R.id.requestPermissionsButton)).check(matches(isDisplayed()))
        }
    }
}

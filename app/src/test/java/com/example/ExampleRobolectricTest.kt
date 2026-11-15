package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createEmptyComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SweepPulse", appName)
  }

  @Test
  fun `main activity launches without crashing when firebase is uninitialized`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    FirebaseApp.getApps(context).forEach { it.delete() }
    ActivityScenario.launch(MainActivity::class.java).use {
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("firebase_missing_banner").performScrollTo().assertIsDisplayed()
      composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    }
  }
}

package com.example.aidocumentscanner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Intentionally small high-value UI smoke tests.
 *
 * Camera/OpenCV/PDF-engine workflows still require dedicated device/manual tests;
 * this class verifies that the release-critical navigation shell renders and that
 * Settings -> Privacy survives Compose/navigation changes.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule =
        createAndroidComposeRule<
            MainActivity
        >()

    @Test
    fun home_rendersPrimaryProductActions() {
        composeRule
            .onNodeWithText("DocuScan")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Start scanning"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Search")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "PDF Tools"
            )
            .assertIsDisplayed()
    }

    @Test
    fun settings_opensPrivacyDashboard() {
        composeRule
            .onNodeWithContentDescription(
                "Settings"
            )
            .performClick()

        composeRule
            .onNodeWithText(
                "Settings"
            )
            .assertIsDisplayed()

        composeRule
            .onNodeWithText(
                "Privacy & data"
            )
            .performClick()

        composeRule
            .onNodeWithText(
                "What DocuScan accesses, stores and deletes"
            )
            .assertIsDisplayed()
    }
}

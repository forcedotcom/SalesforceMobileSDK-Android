package com.salesforce.samples.authflowtester.pageObjects

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector

/**
 * Handles Chrome Custom Tab interactions.
 * UiAutomator is required here because Chrome runs in a separate process
 * that Espresso and Compose Test APIs cannot access.
 */
class ChromePageObject {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun isAdvAuth(): Boolean {
        val continueButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/signin_fre_dismiss_button")
        )
        val noButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/negative_button")
        )
        val toolbar = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/toolbar")
        )

        if (continueButton.waitForExists(TIMEOUT * 3)) {
            Log.i(TAG, "Accepting chrome terms and signing in.")
            continueButton.click()
            if (noButton.waitForExists(TIMEOUT)) {
                noButton.click()
            }
        }

        return toolbar.waitForExists(TIMEOUT)
    }

    fun dismissSavePasswordDialog() {
        val infoBar = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/infobar_message")
        )
        val neverButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/button_secondary")
        )
        infoBar.waitForExists(TIMEOUT)
        if (neverButton.waitForExists(TIMEOUT)) {
            neverButton.click()
        }
    }

    fun tapCloseButton() {
        val closeButton = device.findObject(
            UiSelector().resourceId("com.android.chrome:id/close_button")
        )
        if (closeButton.waitForExists(TIMEOUT)) {
            closeButton.click()
        }
    }

    companion object {
        private const val TAG = "ChromePageObject"
        private const val TIMEOUT = 5_000L
    }
}

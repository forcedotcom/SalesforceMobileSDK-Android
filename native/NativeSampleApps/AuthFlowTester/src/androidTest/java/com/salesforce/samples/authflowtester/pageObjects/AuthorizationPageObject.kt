package com.salesforce.samples.authflowtester.pageObjects

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector

/**
 * Handles the OAuth authorization "Allow" button and Chrome Custom Tab
 * interactions that occur after login.
 * Uses UiAutomator because these elements may appear in Chrome's process.
 */
class AuthorizationPageObject {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun tapAllowIfPresent() {
        with(ChromePageObject()) {
            if (isAdvAuth()) {
                dismissSavePasswordDialog()
            }
        }

        try {
            val allowButton = UiSelector().className("android.widget.Button").text("Allow")
            device.findObject(allowButton).click()
        } catch (_: Exception) {
            Log.i(TAG, "Could not find 'Allow' button.")
        }
    }

    companion object {
        private const val TAG = "AuthorizationPageObject"
    }
}

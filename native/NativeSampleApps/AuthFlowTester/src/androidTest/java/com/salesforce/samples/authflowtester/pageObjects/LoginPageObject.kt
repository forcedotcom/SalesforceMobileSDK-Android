package com.salesforce.samples.authflowtester.pageObjects

import android.util.Log
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.clearElement
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.DriverAtoms.webKeys
import androidx.test.espresso.web.webdriver.Locator

/**
 * Page object for the Salesforce login WebView.
 * Uses Espresso WebView APIs since the login form is an in-app WebView
 * embedded via AndroidView in the SDK's LoginActivity Compose layout.
 */
class LoginPageObject {

    fun setUsername(name: String) {
        retryWebAction {
            Log.i(TAG, "Setting username.")
            onWebView()
                .forceJavascriptEnabled()
                .withElement(findElement(Locator.ID, USERNAME_ID))
                .perform(clearElement())
                .perform(webKeys(name))
        }
    }

    fun setPassword(password: String) {
        retryWebAction {
            Log.i(TAG, "Setting password.")
            onWebView()
                .forceJavascriptEnabled()
                .withElement(findElement(Locator.ID, PASSWORD_ID))
                .perform(clearElement())
                .perform(webKeys(password))
        }
    }

    fun tapLogin() {
        Thread.sleep(BRIEF_DELAY)
        retryWebAction {
            Log.i(TAG, "Tapping login.")
            onWebView()
                .forceJavascriptEnabled()
                .withElement(findElement(Locator.ID, LOGIN_BUTTON_ID))
                .perform(webClick())
        }
    }

    /** Retries a WebView action until it succeeds or times out. */
    private fun <T> retryWebAction(
        timeoutMs: Long = DEFAULT_TIMEOUT,
        action: () -> T,
    ): T {
        val endTime = System.currentTimeMillis() + timeoutMs
        var lastException: Exception? = null
        while (System.currentTimeMillis() < endTime) {
            try {
                return action()
            } catch (e: Exception) {
                lastException = e
                Thread.sleep(RETRY_INTERVAL)
            }
        }
        throw AssertionError(
            "WebView action failed after ${timeoutMs}ms",
            lastException,
        )
    }

    companion object {
        private const val TAG = "LoginPageObject"
        private const val USERNAME_ID = "username"
        private const val PASSWORD_ID = "password"
        private const val LOGIN_BUTTON_ID = "Login"
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val RETRY_INTERVAL = 500L
        private const val BRIEF_DELAY = 2_000L
    }
}

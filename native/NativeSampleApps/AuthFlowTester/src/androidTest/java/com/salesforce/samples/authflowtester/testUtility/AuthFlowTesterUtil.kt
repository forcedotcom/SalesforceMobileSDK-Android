package com.salesforce.samples.authflowtester.testUtility

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

// TODO: Verify this is actually a cold boot!
/**
 * Kills the app process and relaunches from scratch.
 * This forces Application.onCreate() to re-run, ensuring tokens
 * are read from persistent storage rather than held in memory.
 */
fun coldRestart() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val packageName = context.packageName

    // Press Home so the app goes to background (am kill only works on background apps)
    device.pressHome()
    Thread.sleep(1_000)

    // Kill the app process (not force-stop, so instrumentation survives)
    device.executeShellCommand("am kill $packageName")
    Thread.sleep(1_000)

    // Relaunch the app
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)!!
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
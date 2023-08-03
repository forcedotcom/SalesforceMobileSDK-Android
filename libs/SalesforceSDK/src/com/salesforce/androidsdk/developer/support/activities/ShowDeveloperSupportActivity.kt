package com.salesforce.androidsdk.developer.support.activities

import androidx.appcompat.app.AppCompatActivity

/**
 * An Activity that, when started, finishes immediately and shows the developer
 * support dialog from the previous Activity.  SalesforceSDKManager implements
 * the logic for observing the Activity lifecycle and showing the developer
 * support dialog.
 */
class ShowDeveloperSupportActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()

        // Finish immediately.
        finish()
    }
}

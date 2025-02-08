package com.salesforce.androidsdk.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.ui.components.PickerBottomSheet
import com.salesforce.androidsdk.ui.components.PickerStyle


/**
 * This class provides UI to switch between existing signed in user accounts,
 * or add a new account. This screen is popped off the activity stack
 * once the account switch is made.
 */
open class AccountSwitcherActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set content
        setContent {
            MaterialTheme(colorScheme = SalesforceSDKManager.getInstance().colorScheme()) {
                PickerBottomSheet(PickerStyle.UserAccountPicker)
            }
        }
    }
}
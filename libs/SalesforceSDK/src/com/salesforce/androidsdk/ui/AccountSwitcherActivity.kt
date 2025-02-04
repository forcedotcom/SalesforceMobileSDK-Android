package com.salesforce.androidsdk.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.salesforce.androidsdk.ui.components.PickerBottomSheet
import com.salesforce.androidsdk.ui.components.PickerStyle
import com.salesforce.androidsdk.ui.theme.LoginWebviewTheme


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
            /* TODO: Update with support for light, dark and system themes: W-17687751 */
            LoginWebviewTheme {
                PickerBottomSheet(PickerStyle.UserAccountPicker)
            }
        }
    }
}
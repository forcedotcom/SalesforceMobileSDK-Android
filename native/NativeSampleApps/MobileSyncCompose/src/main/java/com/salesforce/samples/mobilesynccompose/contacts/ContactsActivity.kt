package com.salesforce.samples.mobilesynccompose.contacts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.window.layout.WindowMetricsCalculator
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactActivityContent
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityViewModel
import com.salesforce.samples.mobilesynccompose.contacts.vm.DefaultContactActivityViewModel
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.core.ui.toWindowSizeRestrictions

class ContactsActivity : ComponentActivity() {
    private lateinit var vm: ContactActivityViewModel

    @Suppress("UNCHECKED_CAST")
    private val vmFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DefaultContactActivityViewModel() as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this, vmFactory)
            .get(DefaultContactActivityViewModel::class.java)

        setContent {
            val density = LocalDensity.current
            val windowSizeRestrictions = remember(LocalConfiguration.current) {
                val size = WindowMetricsCalculator.getOrCreate()
                    .computeCurrentWindowMetrics(this)
                    .bounds
                    .toComposeRect()
                    .size

                with(density) {
                    size.toDpSize().toWindowSizeRestrictions()
                }
            }

            SalesforceMobileSDKAndroidTheme {
                ContactActivityContent(
                    layoutRestrictions = LayoutRestrictions(windowSizeRestrictions),
                    vm = vm
                )
            }
        }
    }
}

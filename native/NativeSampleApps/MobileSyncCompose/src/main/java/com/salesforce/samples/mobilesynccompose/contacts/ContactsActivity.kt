package com.salesforce.samples.mobilesynccompose.contacts

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.window.layout.WindowMetricsCalculator
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.SalesforceActivityDelegate
import com.salesforce.androidsdk.ui.SalesforceActivityInterface
import com.salesforce.samples.mobilesynccompose.contacts.model.ContactsRepo
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactActivityContent
import com.salesforce.samples.mobilesynccompose.contacts.ui.TempContactObject
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactActivityViewModel
import com.salesforce.samples.mobilesynccompose.contacts.vm.DefaultContactActivityViewModel
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.core.ui.toWindowSizeRestrictions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ContactsActivity : ComponentActivity(), SalesforceActivityInterface {
    private lateinit var vm: ContactActivityViewModel
    private lateinit var salesforceActivityDelegate: SalesforceActivityDelegate

    @Suppress("UNCHECKED_CAST")
    private val vmFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DefaultContactActivityViewModel(
                contactsRepo = object : ContactsRepo {
                    override val contactUpdates: Flow<List<TempContactObject>> = flowOf(
                        (0..100).map { TempContactObject(it, "Name $it", "Title $it") }
                    )
                }
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this, vmFactory)
            .get(DefaultContactActivityViewModel::class.java)

        salesforceActivityDelegate = SalesforceActivityDelegate(this).also { it.onCreate() }

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

    override fun onResume() {
        super.onResume()
        salesforceActivityDelegate.onResume(true)
    }

    override fun onDestroy() {
        salesforceActivityDelegate.onDestroy()
        super.onDestroy()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return salesforceActivityDelegate.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    override fun onResume(client: RestClient?) {
        // TODO use this entry point as the time to launch sync operations b/c at this point the rest client is ready.
    }

    override fun onLogoutComplete() {
        // TODO SalesforceActivity has this as a no-op, but I don't understand why - gkotula 2022-02-03
        /* no-op */
    }

    override fun onUserSwitched() {
        salesforceActivityDelegate.onResume(true)
    }
}

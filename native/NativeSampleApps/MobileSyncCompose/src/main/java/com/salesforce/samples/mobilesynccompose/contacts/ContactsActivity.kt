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
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.ui.SalesforceActivityDelegate
import com.salesforce.androidsdk.ui.SalesforceActivityInterface
import com.salesforce.samples.mobilesynccompose.contacts.events.ContactsActivityMenuEventHandler
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactsActivityContent
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.contacts.vm.DefaultContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.core.ui.toWindowSizeRestrictions
import com.salesforce.samples.mobilesynccompose.model.contacts.DefaultContactsRepo

class ContactsActivity : ComponentActivity(),
    SalesforceActivityInterface,
    ContactsActivityMenuEventHandler {

    private lateinit var vm: ContactsActivityViewModel
    private lateinit var salesforceActivityDelegate: SalesforceActivityDelegate

    @Suppress("UNCHECKED_CAST")
    private val vmFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // TODO Use Hilt to inject this
            return DefaultContactsActivityViewModel(
                DefaultContactsRepo(
                    MobileSyncSDKManager.getInstance().userAccountManager.currentUser
                )
            ) as T
        }
    }

//    init {
//        lifecycleScope.launchWhenStarted {
//            for (event in vm.inspectDbClickEvents) {
//                startActivity(
//                    SmartStoreInspectorActivity.getIntent(
//                        this@ContactsActivity,
//                        false,
//                        null
//                    )
//                )
//            }
//        }
//
//        lifecycleScope.launchWhenStarted {
//            for (event in vm.logoutClickEvents) {
//                MobileSyncSDKManager.getInstance().logout(this@ContactsActivity)
//            }
//        }
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this, vmFactory)
            .get(DefaultContactsActivityViewModel::class.java)

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
                ContactsActivityContent(
                    layoutRestrictions = LayoutRestrictions(windowSizeRestrictions),
                    menuEventHandler = this,
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
        vm.sync()
    }

    override fun onLogoutComplete() {
        // TODO is there any cleanup required after logout?
        /* no-op */
    }

    override fun onUserSwitched() {
        salesforceActivityDelegate.onResume(true)
    }

    override fun inspectDbClicked() {
        TODO("Not yet implemented")
    }

    override fun logoutClicked() {
        TODO("Not yet implemented")
    }

    override fun switchUserClicked() {
        TODO("Not yet implemented")
    }

    override fun syncClicked() {
        TODO("Not yet implemented")
    }
}

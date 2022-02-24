package com.salesforce.samples.mobilesynccompose.contacts

import android.content.Intent
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
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity
import com.salesforce.androidsdk.ui.SalesforceActivityDelegate
import com.salesforce.androidsdk.ui.SalesforceActivityInterface
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactsActivityContent
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.contacts.vm.DefaultContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.core.ui.LayoutRestrictions
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.core.ui.toWindowSizeRestrictions
import com.salesforce.samples.mobilesynccompose.model.contacts.DefaultContactsRepo

class ContactsActivity : ComponentActivity(), SalesforceActivityInterface {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this, vmFactory)
            .get(DefaultContactsActivityViewModel::class.java)

        salesforceActivityDelegate = SalesforceActivityDelegate(this).also { it.onCreate() }

        setContent {
            /* We use the fact that LocalConfiguration is updated whenever configuration changes are
             * detected to drive the layout restrictions recomposition: */
            val windowSize = remember(LocalConfiguration.current) {
                WindowMetricsCalculator.getOrCreate()
                    .computeCurrentWindowMetrics(this)
                    .bounds
                    .toComposeRect()
                    .size
            }

            val windowSizeRestrictions = with(LocalDensity.current) {
                windowSize.toDpSize().toWindowSizeRestrictions()
            }

            SalesforceMobileSDKAndroidTheme {
                ContactsActivityContent(
                    layoutRestrictions = LayoutRestrictions(windowSizeRestrictions),
                    vm = vm,
                    onInspectDbClick = this::inspectDbClicked,
                    onLogoutClick = this::logoutClicked,
                    onSwitchUserClick = this::switchUserClicked,
                    onSyncClick = this::syncClicked
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
        vm.sync(syncDownOnly = true)
    }

    override fun onLogoutComplete() {
        // TODO is there any cleanup required after logout?
        /* no-op */
    }

    override fun onUserSwitched() {
        salesforceActivityDelegate.onResume(true)
    }

    private fun inspectDbClicked() {
        startActivity(
            SmartStoreInspectorActivity.getIntent(
                this@ContactsActivity,
                false,
                null
            )
        )
    }

    private fun logoutClicked() {
        MobileSyncSDKManager.getInstance().logout(this)
    }

    private fun switchUserClicked() {
        val intent = Intent(this, SalesforceSDKManager.getInstance().accountSwitcherActivityClass)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

        startActivity(intent)
    }

    private fun syncClicked() {
        vm.sync()
    }
}

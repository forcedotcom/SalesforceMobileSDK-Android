/*
 * Copyright (c) 2022-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.samples.mobilesynccompose.contacts

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity
import com.salesforce.androidsdk.ui.SalesforceActivityDelegate
import com.salesforce.androidsdk.ui.SalesforceActivityInterface
import com.salesforce.samples.mobilesynccompose.contacts.state.ContactsActivityMenuHandler
import com.salesforce.samples.mobilesynccompose.contacts.ui.ContactsActivityContent
import com.salesforce.samples.mobilesynccompose.contacts.vm.ContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.contacts.vm.DefaultContactsActivityViewModel
import com.salesforce.samples.mobilesynccompose.core.ui.theme.SalesforceMobileSDKAndroidTheme
import com.salesforce.samples.mobilesynccompose.model.contacts.DefaultContactsRepo

class ContactsActivity
    : ComponentActivity(),
    SalesforceActivityInterface,
    ContactsActivityMenuHandler {

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
            SalesforceMobileSDKAndroidTheme {
                ContactsActivityContent(
                    vm = vm,
                    detailsVm = vm.detailsVm,
                    menuHandler = this
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

    override fun onInspectDbClick() {
        startActivity(
            SmartStoreInspectorActivity.getIntent(
                this@ContactsActivity,
                false,
                null
            )
        )
    }

    override fun onLogoutClick() {
        MobileSyncSDKManager.getInstance().logout(this)
    }

    override fun onSwitchUserClick() {
        val intent = Intent(this, SalesforceSDKManager.getInstance().accountSwitcherActivityClass)
            .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

        startActivity(intent)
    }

    override fun onSyncClick() {
        vm.sync()
    }
}

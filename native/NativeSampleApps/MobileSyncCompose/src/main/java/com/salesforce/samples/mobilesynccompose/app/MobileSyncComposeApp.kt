package com.salesforce.samples.mobilesynccompose.app

import android.app.Application
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.samples.mobilesynccompose.contacts.ContactsActivity

class MobileSyncComposeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileSyncSDKManager.initNative(this, ContactsActivity::class.java)
        MobileSyncSDKManager.getInstance().apply {
            setupUserStoreFromDefaultConfig()
            setupUserSyncsFromDefaultConfig()
        }
    }
}

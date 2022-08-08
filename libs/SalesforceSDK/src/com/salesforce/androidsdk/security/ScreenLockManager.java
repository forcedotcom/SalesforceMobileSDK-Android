/*
 * Copyright (c) 2021-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.security;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.VisibleForTesting;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.ui.ScreenLockActivity;
import com.salesforce.androidsdk.util.EventsObservable;

import java.util.List;

/**
 * Manages if the app should be locked.
 *
 * @author bpage
 */
public class ScreenLockManager {

    public static final String MOBILE_POLICY_PREF = "mobile_policy";
    public static final String SCREEN_LOCK = "screen_lock";
    public static final String SCREEN_LOCK_TIMEOUT = "screen_lock_timeout";

    private long lastLockedTimestamp = 0;

    /**
     * Stores the mobile policy for the org upon user login.
     *
     * @param account the newly add account
     * @param screenLockRequired if the account requires screen lock or not
     *
     * @deprecated Timeout is now required. This method will be removed in 11.0.
     */
    @Deprecated
    public void storeMobilePolicy(UserAccount account, boolean screenLockRequired) { }

    /**
     * Stores the mobile policy for the org upon user login.
     *
     * @param account the newly add account
     * @param screenLockRequired if the account requires screen lock or not
     * @param timeout timeout in milliseconds
     */
    public void storeMobilePolicy(UserAccount account, boolean screenLockRequired, int timeout) {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences accountSharedPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                + account.getUserLevelFilenameSuffix(), Context.MODE_PRIVATE);
        accountSharedPrefs.edit().putBoolean(SCREEN_LOCK, screenLockRequired).putInt(SCREEN_LOCK_TIMEOUT, timeout).apply();

        SharedPreferences globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        if (screenLockRequired) {
            int currentTimeout = globalPrefs.getInt(SCREEN_LOCK_TIMEOUT, 0);
            SharedPreferences.Editor globalPrefsEditor = globalPrefs.edit();

            globalPrefsEditor.putBoolean(SCREEN_LOCK, true);
            if (currentTimeout == 0 || timeout < currentTimeout) {
                globalPrefsEditor.putInt(SCREEN_LOCK_TIMEOUT, timeout);
            }
            globalPrefsEditor.apply();

            lock();
        }
    }

    /**
     * To be called by the protected activity to lock the device when being resumed.
     */
    public void onAppForegrounded() {
        if (shouldLock()) {
            lock();
        }
    }

    /**
     * To be called by the protected activity is paused to denote that the app should lock.
     */
    public void onAppBackgrounded() {
        lastLockedTimestamp = System.currentTimeMillis();
    }

    /**
     * Resets and removes the screen lock.
     */
    public void reset() {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        globalPrefs.edit().remove(SCREEN_LOCK).remove(SCREEN_LOCK_TIMEOUT).apply();
    }

    /**
     * Screen lock specific cleanup for account logout/removal.
     *
     * @param account The account being removed.
     */
    public void cleanUp(UserAccount account) {

        // Clean up and remove lock for account.
        final Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        if (account != null) {
            final SharedPreferences accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                    + account.getUserLevelFilenameSuffix(), Context.MODE_PRIVATE);
            accountPrefs.edit().remove(SCREEN_LOCK).remove(SCREEN_LOCK_TIMEOUT).apply();
        }

        // Determine if any other users still need Screen Lock.
        final List<UserAccount> accounts = SalesforceSDKManager.getInstance()
                .getUserAccountManager().getAuthenticatedUsers();
        int lowestTimeout = Integer.MAX_VALUE;

        if (accounts != null) {
            accounts.remove(account);
            for (final UserAccount mAccount : accounts) {
                if (mAccount != null) {
                    final SharedPreferences accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                            + mAccount.getUserLevelFilenameSuffix(), Context.MODE_PRIVATE);
                    if (accountPrefs.getBoolean(SCREEN_LOCK, false)) {
                        int timeout = accountPrefs.getInt(SCREEN_LOCK_TIMEOUT, Integer.MAX_VALUE);
                        if (timeout < lowestTimeout) {
                            lowestTimeout = timeout;
                        }
                    }
                }
            }

            if (lowestTimeout < Integer.MAX_VALUE) {
                SharedPreferences globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
                globalPrefs.edit().putInt(SCREEN_LOCK_TIMEOUT, lowestTimeout).apply();
                return;
            }
        }

        // If we have returned, no other accounts require Screen Lock.
        reset();
    }

    /**
     * Unlocks the app.
     *
     * @deprecated This method will be removed in 11.0.
     */
    @Deprecated
    public void unlock() { }

    @VisibleForTesting
    protected boolean shouldLock() {
        long elapsedTime = System.currentTimeMillis() - lastLockedTimestamp;
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences sharedPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        boolean hasLock = sharedPrefs.getBoolean(SCREEN_LOCK, false);
        int timeout = sharedPrefs.getInt(SCREEN_LOCK_TIMEOUT, 0);

        return hasLock && (elapsedTime > timeout);
    }

    private void lock() {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        Intent intent = new Intent(ctx, ScreenLockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
        EventsObservable.get().notifyEvent(EventsObservable.EventType.AppLocked);
    }
}

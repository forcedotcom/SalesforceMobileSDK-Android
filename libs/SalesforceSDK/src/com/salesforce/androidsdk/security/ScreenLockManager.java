package com.salesforce.androidsdk.security;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.EventsObservable;

import java.util.List;

public class ScreenLockManager {

    public static final String MOBILE_POLICY_PREF = "mobile_policy";
    public static final String SCREEN_LOCK = "screen_lock";

    private boolean mobilePolicy = readMobilePolicy();
    private boolean shouldLock = true;

    public void storeMobilePolicyForOrg(UserAccount account, boolean screenlockreqiured) {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences accountSharedPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                + account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
        accountSharedPrefs.edit().putBoolean(SCREEN_LOCK, screenlockreqiured).apply();

        SharedPreferences globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        if (screenlockreqiured && !globalPrefs.getBoolean(MOBILE_POLICY_PREF, false)) {
            globalPrefs.edit().putBoolean(SCREEN_LOCK, true).apply();
        }

        // This is necessary to lock the app upon initial login
        mobilePolicy = true;
    }

    public boolean onResume() {
        boolean locked = mobilePolicy && shouldLock;
        if (locked) {
            lock();
        }

        // If locked, do nothing - when the app gets unlocked we will be back here.
        return !locked;
    }

    public void onPause() {
        setShouldLock(true);
    }

    public void setShouldLock(boolean shouldLock) {
        this.shouldLock = shouldLock;
    }

    public boolean shouldLock() {
        return this.shouldLock;
    }

    public void reset() {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences globalPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        globalPrefs.edit().remove(SCREEN_LOCK).apply();
    }

    public void cleanUp(UserAccount account) {
        // CleanUp and remove Lock for account.
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                + account.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
        accountPrefs.edit().remove(SCREEN_LOCK).apply();
        this.mobilePolicy = false;

        // Determine if any other users still need ScreenLock.
        List<UserAccount> accounts = SalesforceSDKManager.getInstance()
                .getUserAccountManager().getAuthenticatedUsers();
        accounts.remove(account);
        for (UserAccount mAccount : accounts) {
            accountPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF
                    + mAccount.getOrgLevelFilenameSuffix(), Context.MODE_PRIVATE);
            if (accountPrefs.getBoolean(SCREEN_LOCK, false)) {
                this.mobilePolicy = true;
                return;
            }
        }

        if (!this.mobilePolicy) {
            reset();
        }
    }

    private boolean readMobilePolicy() {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        SharedPreferences sharedPrefs = ctx.getSharedPreferences(MOBILE_POLICY_PREF, Context.MODE_PRIVATE);
        return sharedPrefs.getBoolean(SCREEN_LOCK, false);
    }

    private void lock() {
        Context ctx = SalesforceSDKManager.getInstance().getAppContext();
        Intent intent = new Intent(ctx, SalesforceSDKManager.getInstance().getScreenLockActivity());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ctx instanceof Activity) {
            ((Activity) ctx).startActivityForResult(intent, PasscodeManager.PASSCODE_REQUEST_CODE);
        } else {
            ctx.startActivity(intent);
        }
        EventsObservable.get().notifyEvent(EventsObservable.EventType.AppLocked);
    }
}

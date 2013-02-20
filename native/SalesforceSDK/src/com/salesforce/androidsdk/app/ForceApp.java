/*
 * Copyright (c) 2012, salesforce.com, inc.
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
package com.salesforce.androidsdk.app;

import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import com.salesforce.androidsdk.auth.AccountWatcher;
import com.salesforce.androidsdk.auth.AccountWatcher.AccountRemoved;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.LoginServerManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.security.Encryptor;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.ui.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.ui.SalesforceR;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;

/**
 * Super class for all force applications.
 * You should extend this class or make sure to initialize HttpAccess in your application's onCreate method.
 */
public abstract class ForceApp extends Application implements AccountRemoved {

    /**
     * Current version of this SDK.
     */
    public static final String SDK_VERSION = "1.5.1";

    /**
     * Last phone version.
     */
    private static final int GINGERBREAD_MR1 = 10;

    /**
     * Default app name.
     */
    private static final String DEFAULT_APP_DISPLAY_NAME = "Salesforce";

    /**
     * Instance of the ForceApp to use for this process.
     */
    public static ForceApp APP;

    private String encryptionKey;
    private AccountWatcher accWatcher;
    private SalesforceR salesforceR = new SalesforceR();

    /**************************************************************************************************
     *
     * Abstract methods: to be implemented by subclass
     *
     **************************************************************************************************/

    /**
     * @return The class for the main activity.
     */
    public abstract Class<? extends Activity> getMainActivityClass();

    /**
     * This function must return the same value for name
     * even when the application is restarted. The value this
     * function returns must be Base64 encoded.
     *
     * {@link Encryptor#isBase64Encoded(String)} can be used to
     * determine whether the generated key is Base64 encoded.
     *
     * {@link Encryptor#hash(String, String)} can be used to
     * generate a Base64 encoded string.
     *
     * For example:
     * <code>
     * Encryptor.hash(name + "12s9adfgret=6235inkasd=012", name + "12kl0dsakj4-cuygsdf625wkjasdol8");
     * </code>
     *
     * @param name The name associated with the key.
     * @return The key used for encrypting salts and keys.
     */
    protected abstract String getKey(String name);

    /**
     * Before 1.3, SalesforceSDK was packaged as a jar, and project had to provide a subclass of SalesforceR.
     * Since 1.3, SalesforceSDK is packaged as a library project and we no longer need to to that.
     * @return SalesforceR object which allows reference to resources living outside the SDK.
     */
    public SalesforceR getSalesforceR() {
        return salesforceR;
    }

    /**
     * @return the class of the activity used to perform the login process and create the account.
     * You can override this if you want to customize the LoginAcitivty
     */
    public Class<? extends Activity> getLoginActivityClass() {
        return LoginActivity.class;
    }

    // passcode manager
    private PasscodeManager passcodeManager;
    
    // login server manager
    private LoginServerManager loginServerManager;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize encryption module
        Encryptor.init(this);

        // Initialize the http client
        HttpAccess.init(this, getUserAgent());

        // Ensure we have a CookieSyncManager
        CookieSyncManager.createInstance(this);

        // Done
        APP = this;
        accWatcher = new AccountWatcher(APP, APP);

        // Upgrade to the latest version.
        UpgradeManager.getInstance().upgradeAccMgr();
        EventsObservable.get().notifyEvent(EventType.AppCreateComplete);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (accWatcher != null) {
            accWatcher.remove();
            accWatcher = null;
        }
    }

    /**
     * Returns whether the SDK should automatically logout when the
     * access token is revoked. This should be overridden to return
     * false, if the app wants to handle cleanup by itself when the
     * access token is revoked.
     *
     * @return True - if the SDK should automatically logout, False - otherwise.
     */
    public boolean shouldLogoutWhenTokenRevoked() {
    	return true;
    }

    @Override
    public void onAccountRemoved() {
        ForceApp.APP.cleanUp(null);
    }

    /**
     * @return The login server manager associated with the app.
     */
    public synchronized LoginServerManager getLoginServerManager() {

        if (loginServerManager == null) {
        	loginServerManager = new LoginServerManager(this);
        }
        return loginServerManager;
    }    

    /**
     * @return The passcode manager associated with the app.
     */
    public synchronized PasscodeManager getPasscodeManager() {

        // Only creating passcode manager if used.
        if (passcodeManager == null) {
            passcodeManager = new PasscodeManager(this);
        }
        return passcodeManager;
    }

    /**
     * Changes the passcode to a new value
     *
     * @param oldPass Old passcode.
     * @param newPass New passcode.
     */
    public synchronized void changePasscode(String oldPass, String newPass) {
        if (!isNewPasscode(oldPass, newPass)) {
            return;
        }
        encryptionKey = null; // Reset cached encryption key, since the passcode has changed
        ClientManager.changePasscode(oldPass, newPass);
    }

    /**
     * @param oldPass
     * @param newPass
     * @return true if newPass is truly different from oldPass
     */
    protected boolean isNewPasscode(String oldPass, String newPass) {
        return ((oldPass == null && newPass == null)
                || (oldPass != null && newPass != null && oldPass.trim().equals(newPass.trim())));
    }

    /**
     * Returns the encryption key being used.
     *
     * @param actualPass Passcode.
     * @return Encryption key for passcode.
     */
    public synchronized String getEncryptionKeyForPasscode(String actualPass) {
        if (actualPass != null && !actualPass.trim().equals("")) {
            return actualPass;
        }
        if (encryptionKey == null) {
            encryptionKey = getPasscodeManager().hashForEncryption("");
        }
        return encryptionKey;
    }

    /**
     * Returns the app display name used by the passcode dialog.
     *
     * @return App display string.
     */
    public String getAppDisplayString() {
    	return DEFAULT_APP_DISPLAY_NAME;
    }

    /**
     * @return The hashed passcode, or null if it's not required.
     */
    public String getPasscodeHash() {
        return passcodeManager == null ? null : passcodeManager.getPasscodeHash();
    }

    /**
     * @return The name of the application (as defined in AndroidManifest.xml).
     */
    public String getApplicationName() {
        return getPackageManager().getApplicationLabel(getApplicationInfo()).toString();
    }

    /**
     * Cleans up cached credentials and data.
     *
     * @param frontActivity Front activity.
     */
    protected void cleanUp(Activity frontActivity) {

        // Finish front activity if specified.
        if (frontActivity != null) {
            frontActivity.finish();
        }

        // Reset passcode and encryption key, if any.
        getPasscodeManager().reset(this);
        passcodeManager = null;
        encryptionKey = null;
        UUIDManager.resetUuids();
    }

    /**
     * Starts login flow if user account has been removed.
     */
    protected void startLoginPage() {

        // Clear cookies.
        CookieSyncManager.createInstance(ForceApp.this);
        CookieManager.getInstance().removeAllCookie();

        // Restart application.
        final Intent i = new Intent(ForceApp.this, getMainActivityClass());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    /**
     * Wipe out the stored authentication credentials (remove account) and restart the app, if specified.
     */
    public void logout(Activity frontActivity) {
        logout(frontActivity, true);
    }

    /**
     * Wipe out the stored authentication credentials (remove account) and restart the app, if specified.
     */
    public void logout(Activity frontActivity, final boolean showLoginPage) {
        if (accWatcher != null) {
            accWatcher.remove();
            accWatcher = null;
        }
        cleanUp(frontActivity);

        // Remove account if any.
        ClientManager clientMgr = new ClientManager(this, getAccountType(), null, shouldLogoutWhenTokenRevoked());
        if (clientMgr.getAccount() == null) {
            EventsObservable.get().notifyEvent(EventType.LogoutComplete);
            if (showLoginPage) {
                startLoginPage();
            }
        } else {
            clientMgr.removeAccountAsync(new AccountManagerCallback<Boolean>() {
                @Override
                public void run(AccountManagerFuture<Boolean> arg0) {
                    EventsObservable.get().notifyEvent(EventType.LogoutComplete);
                    if (showLoginPage) {
                        startLoginPage();
                    }
                }
            });
        }
    }

    /**
     * Get a user agent string based on the mobile SDK version. We are building
     * a user agent of the form: 
     *   SalesforceMobileSDK/<salesforceSDK version> android/<android OS version> appName/appVersion <Native|Hybrid>
     * @return The user agent string to use for all requests.
     */
    public final String getUserAgent() {
        String appName = "";
        String appVersion = "";
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appName = getString(packageInfo.applicationInfo.labelRes);
            appVersion = packageInfo.versionName;
        } catch (NameNotFoundException e) {
            Log.w("ForceApp:getUserAgent", e);
        }
	    String nativeOrHybrid = (isHybrid() ? "Hybrid" : "Native");
	    return String.format("SalesforceMobileSDK/%s android mobile/%s (%s) %s/%s %s",
	            SDK_VERSION, Build.VERSION.RELEASE, Build.MODEL, appName, appVersion, nativeOrHybrid);
	}

	/**
	 * @return true if this is an hybrid application
	 */
	public boolean isHybrid() {
		return SalesforceDroidGapActivity.class.isAssignableFrom(getMainActivityClass());
	}

    /**
     * @return The authentication account type (should match authenticator.xml).
     */
    public String getAccountType() {
        return getString(getSalesforceR().stringAccountType());
    }

    /**
     * Helper function
     * @return true if application is running on a tablet
     */
    public static boolean isTablet() {
        if (Build.VERSION.SDK_INT <= GINGERBREAD_MR1) {
            return false;
        } else if ((APP.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass()).append(": {\n")
          .append("   accountType: ").append(getAccountType()).append("\n")
          .append("   userAgent: ").append(getUserAgent()).append("\n")
          .append("   mainActivityClass: ").append(getMainActivityClass()).append("\n")
          .append("   isFileSystemEncrypted: ").append(Encryptor.isFileSystemEncrypted()).append("\n");
        if (null != passcodeManager) {
            //passcodeManager may be null at startup if the app is running in debug mode
            sb.append("   hasStoredPasscode: ").append(passcodeManager.hasStoredPasscode(this)).append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Encrypts the data using the passcode as the encryption key.
     *
     * @param data Data to be encrypted.
     * @param passcode Encryption key.
     * @return Encrypted data.
     */
    public static String encryptWithPasscode(String data, String passcode) {
        return Encryptor.encrypt(data, ForceApp.APP.getEncryptionKeyForPasscode(passcode));
    }

    /**
     * Decrypts the data using the passcode as the decryption key.
     *
     * @param data Data to be decrypted.
     * @param passcode Decryption key.
     * @return Decrypted data.
     */
    public static String decryptWithPasscode(String data, String passcode) {
        return Encryptor.decrypt(data, ForceApp.APP.getEncryptionKeyForPasscode(passcode));
    }
}

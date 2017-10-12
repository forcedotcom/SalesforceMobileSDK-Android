/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.webkit.CookieManager;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.accounts.UserAccountManager;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.analytics.SalesforceAnalyticsManager;
import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.auth.AuthenticatorService;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.config.AdminPermsManager;
import com.salesforce.androidsdk.config.AdminSettingsManager;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.push.PushMessaging;
import com.salesforce.androidsdk.push.PushNotificationInterface;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.security.PasscodeManager;
import com.salesforce.androidsdk.security.SalesforceKeyGenerator;
import com.salesforce.androidsdk.ui.AccountSwitcherActivity;
import com.salesforce.androidsdk.ui.LoginActivity;
import com.salesforce.androidsdk.ui.PasscodeActivity;
import com.salesforce.androidsdk.ui.SalesforceR;
import com.salesforce.androidsdk.util.EventsObservable;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.net.URI;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

/**
 * This class serves as an interface to the various
 * functions of the Salesforce SDK. In order to use the SDK,
 * your app must first instantiate the singleton SalesforceSDKManager
 * object by calling the static init() method. After calling init(),
 * use the static getInstance() method to access the
 * singleton SalesforceSDKManager object.
 */
public class SalesforceSDKManager {

    /**
     * Current version of this SDK.
     */
    public static final String SDK_VERSION = "6.0.0.dev";

    /**
     * Intent action meant for instances of SalesforceSDKManager residing in other processes
     * to order them to clean up in-memory caches
     */
    private static final String CLEANUP_INTENT_ACTION = "com.salesforce.CLEANUP";

    // Receiver for CLEANUP_INTENT_ACTION broadcast
    private CleanupReceiver cleanupReceiver;

    // Key in broadcast for process id
    private static final String PROCESS_ID_KEY = "processId";

    // Unique per process id added to broadcast to prevent processing broadcast from own process
    private static final String PROCESS_ID = UUID.randomUUID().toString();

    // Key in broadcast for user account
    private static final String USER_ACCOUNT = "userAccount";

    /**
     * Intent action that specifies that logout was completed.
     */
    public static final String LOGOUT_COMPLETE_INTENT_ACTION = "com.salesforce.LOGOUT_COMPLETE";

    /**
     * Default app name.
     */
    private static final String DEFAULT_APP_DISPLAY_NAME = "Salesforce";
    private static final String INTERNAL_ENTROPY = "6cgs4f";
    private static final String TAG = "SalesforceSDKManager";
    protected static String AILTN_APP_NAME;

    /**
     * Instance of the SalesforceSDKManager to use for this process.
     */
    protected static SalesforceSDKManager INSTANCE;

    /**
     * Timeout value for push un-registration.
     */
    private static final int PUSH_UNREGISTER_TIMEOUT_MILLIS = 30000;

    protected Context context;
    protected KeyInterface keyImpl;
    protected LoginOptions loginOptions;
    protected Class<? extends Activity> mainActivityClass;
    protected Class<? extends Activity> loginActivityClass = LoginActivity.class;
    protected Class<? extends PasscodeActivity> passcodeActivityClass = PasscodeActivity.class;
    protected Class<? extends AccountSwitcherActivity> switcherActivityClass = AccountSwitcherActivity.class;
    private SalesforceR salesforceR = new SalesforceR();
    private PasscodeManager passcodeManager;
    private LoginServerManager loginServerManager;
    private boolean isTestRun = false;
	private boolean isLoggingOut = false;
    private AdminSettingsManager adminSettingsManager;
    private AdminPermsManager adminPermsManager;
    private PushNotificationInterface pushNotificationInterface;
    private String uid; // device id
    private volatile boolean loggedOut = false;
    private SortedSet<String> features;
    private List<String> additionalOauthKeys;
    private String loginBrand;
    private boolean browserLoginEnabled;

    /**
     * PasscodeManager object lock.
     */
    private Object passcodeManagerLock = new Object();

    /**
     * Returns a singleton instance of this class.
     *
     * @return Singleton instance of SalesforceSDKManager.
     */
    public static SalesforceSDKManager getInstance() {
    	if (INSTANCE != null) {
    		return INSTANCE;
    	} else {
            throw new RuntimeException("Applications need to call SalesforceSDKManager.init() first.");
    	}
    }

    /**
     *
     * @return true if SalesforceSDKManager has been initialized already
     */
    public static boolean hasInstance() {
        return INSTANCE != null;
    }

    /**
     * Sets the app name to be used by the analytics framework.
     *
     * @param appName App name.
     */
    public static void setAiltnAppName(String appName) {
        if (!TextUtils.isEmpty(appName)) {
            AILTN_APP_NAME = appName;
        }
    }

    /**
     * Returns the app name being used by the analytics framework.
     *
     * @return App name.
     */
    public static String getAiltnAppName() {
        return AILTN_APP_NAME;
    }

    /**
     * Protected constructor.
     * @param context Application context.
     * @param keyImpl Implementation for KeyInterface.
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    protected SalesforceSDKManager(Context context, KeyInterface keyImpl,
                                   Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
        this.uid = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        this.context = context;
    	this.keyImpl = keyImpl;
    	this.mainActivityClass = mainActivity;
    	if (loginActivity != null) {
            this.loginActivityClass = loginActivity;
    	}
        this.features  = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        /*
         * Checks if an analytics app name has already been set by the app.
         * If not, fetches the default app name to be used and sets it.
         */
        final String currentAiltnAppName = getAiltnAppName();
        if (TextUtils.isEmpty(currentAiltnAppName)) {
            String ailtnAppName = null;
            try {
                final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                ailtnAppName = context.getString(packageInfo.applicationInfo.labelRes);
            } catch (NameNotFoundException e) {
                SalesforceSDKLogger.e(TAG, "Package not found", e);
            }
            if (!TextUtils.isEmpty(ailtnAppName)) {
                setAiltnAppName(ailtnAppName);
            }
        }

        // If your app runs in multiple processes, all the SalesforceSDKManager need to run cleanup during a logout
        cleanupReceiver = new CleanupReceiver();
        context.registerReceiver(cleanupReceiver, new IntentFilter(SalesforceSDKManager.CLEANUP_INTENT_ACTION));
    }

    /**
     * Returns the class for the main activity.
     *
     * @return The class for the main activity.
     */
    public Class<? extends Activity> getMainActivityClass() {
    	return mainActivityClass;
    }

	/**
     * Returns the class for the account switcher activity.
     *
     * @return The class for the account switcher activity.
     */
    public Class<? extends AccountSwitcherActivity> getAccountSwitcherActivityClass() {
    	return switcherActivityClass;
    }

    /**
     * Returns the class for the account switcher activity.
     *
     * @return The class for the account switcher activity.
     */
    public void setAccountSwitcherActivityClass(Class<? extends AccountSwitcherActivity> activity) {
    	if (activity != null) {
        	switcherActivityClass = activity;
    	}
    }

    /*
     * TODO: Mark this deprecated and remove it in Mobile SDK 7.0.
     */
    public interface KeyInterface {

        /**
         * Defines a single function for retrieving the key
         * associated with a given name.
         *
         * For the given name, this function must return the same key
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
        public String getKey(String name);
    }

    /**
     * For the given name, this function must return the same key
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
    /*
     * TODO: Mark this deprecated and remove it in Mobile SDK 7.0.
     */
    public String getKey(String name) {
    	String key = null;
    	if (keyImpl != null) {
    		key = keyImpl.getKey(name);
    	}
    	return key;
    }

    /**
     * Before Mobile SDK 1.3, SalesforceSDK was packaged as a jar, and each project had to provide
     * a subclass of SalesforceR.
     *
     * Since 1.3, SalesforceSDK is packaged as a library project, so the SalesforceR subclass is no longer needed.
     * @return SalesforceR object which allows reference to resources living outside the SDK.
     */
    public SalesforceR getSalesforceR() {
        return salesforceR;
    }

    /**
     * Returns the class of the activity used to perform the login process and create the account.
     *
     * @return the class of the activity used to perform the login process and create the account.
     */
    public Class<? extends Activity> getLoginActivityClass() {
    	return loginActivityClass;
    }

    /**
     * Returns unique device ID.
     *
     * @return Device ID.
     */
    public String getDeviceId() {
        return uid;
    }

	/**
     * Returns login options associated with the app.
     *
	 * @return LoginOptions instance.
	 */
	public LoginOptions getLoginOptions() {
		return getLoginOptions(null, null);
	}

    public LoginOptions getLoginOptions(String jwt, String url) {
        if (loginOptions == null) {
            final BootConfig config = BootConfig.getBootConfig(context);
            if (TextUtils.isEmpty(jwt)) {
                loginOptions = new LoginOptions(url, config.getOauthRedirectURI(),
                        config.getRemoteAccessConsumerKey(), config.getOauthScopes());
            } else {
                loginOptions = new LoginOptions(url, config.getOauthRedirectURI(),
                        config.getRemoteAccessConsumerKey(), config.getOauthScopes(), jwt);
            }
        } else {
            loginOptions.setJwt(jwt);
            loginOptions.setUrl(url);
        }
        return loginOptions;
    }

    private static void init(Context context, KeyInterface keyImpl,
                             Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
    	if (INSTANCE == null) {
    		INSTANCE = new SalesforceSDKManager(context, keyImpl, mainActivity, loginActivity);
    	}
    	initInternal(context);
        EventsObservable.get().notifyEvent(EventType.AppCreateComplete);
    }

	/**
	 * For internal use by Salesforce Mobile SDK or by subclasses
	 * of SalesforceSDKManager. Initializes required components.
	 *
	 * @param context Application context.
	 */
    public static void initInternal(Context context) {

        // Upgrades to the latest version.
        SalesforceSDKUpgradeManager.getInstance().upgrade();

        // Initializes the encryption module.
        Encryptor.init(context);

        // Initializes the HTTP client.
        HttpAccess.init(context, INSTANCE.getUserAgent());
    }

    /**
     * Initializes required components. Native apps must call one overload of
     * this method before using the Salesforce Mobile SDK.
     *
     * @param context Application context.
     * @param keyImpl Implementation of KeyInterface.
     * @param mainActivity Activity that should be launched after the login flow.
     */
    public static void initNative(Context context, KeyInterface keyImpl, Class<? extends Activity> mainActivity) {
        SalesforceSDKManager.init(context, keyImpl, mainActivity, LoginActivity.class);
    }

    /**
     * Initializes required components. Native apps must call one overload of
     * this method before using the Salesforce Mobile SDK.
     *
     * @param context Application context.
     * @param keyImpl Implementation of KeyInterface.
     * @param mainActivity Activity that should be launched after the login flow.
     * @param loginActivity Login activity.
     */
    public static void initNative(Context context, KeyInterface keyImpl,
                                  Class<? extends Activity> mainActivity, Class<? extends Activity> loginActivity) {
        SalesforceSDKManager.init(context, keyImpl, mainActivity, loginActivity);
    }

    /**
     * Sets a custom passcode activity class to be used instead of the default class.
     * The custom class must subclass PasscodeActivity.
     *
     * @param activity Subclass of PasscodeActivity.
     */
    public void setPasscodeActivity(Class<? extends PasscodeActivity> activity) {
    	if (activity != null) {
    		passcodeActivityClass = activity;
    	}
    }

    /**
     * Returns the descriptor of the passcode activity class that's currently in use.
     *
     * @return Passcode activity class descriptor.
     */
    public Class<? extends PasscodeActivity> getPasscodeActivity() {
    	return passcodeActivityClass;
    }

    /**
     * Indicates whether the SDK should automatically log out when the
     * access token is revoked. If you override this method to return
     * false, your app is responsible for handling its own cleanup when the
     * access token is revoked.
     *
     * @return True if the SDK should automatically logout.
     */
    public boolean shouldLogoutWhenTokenRevoked() {
    	return true;
    }

    /**
     * Returns the application context.
     *
     * @return Application context.
     */
    public Context getAppContext() {
    	return context;
    }

    /**
     * Returns the login server manager associated with SalesforceSDKManager.
     *
     * @return LoginServerManager instance.
     */
    public synchronized LoginServerManager getLoginServerManager() {
        if (loginServerManager == null) {
        	loginServerManager = new LoginServerManager(context);
        }
        return loginServerManager;
    }
    
    /**
     * Sets a receiver that handles received push notifications.
     *
     * @param pnInterface Implementation of PushNotificationInterface.
     */
    public synchronized void setPushNotificationReceiver(PushNotificationInterface pnInterface) {
    	pushNotificationInterface = pnInterface;
    }

    /**
     * Returns the receiver that's configured to handle incoming push notifications.
     *
     * @return Configured implementation of PushNotificationInterface.
     */
    public synchronized PushNotificationInterface getPushNotificationReceiver() {
    	return pushNotificationInterface;
    }

    /**
     * Returns the passcode manager that's associated with SalesforceSDKManager.
     *
     * @return PasscodeManager instance.
     */
    public PasscodeManager getPasscodeManager() {
    	synchronized (passcodeManagerLock) {
            if (passcodeManager == null) {
                passcodeManager = new PasscodeManager(context);
            }
            return passcodeManager;
		}
    }

	/**
     * Returns the user account manager that's associated with SalesforceSDKManager.
     *
     * @return UserAccountManager instance.
     */
    public UserAccountManager getUserAccountManager() {
    	return UserAccountManager.getInstance();
    }

    /**
     * Returns the administrator settings manager that's associated with SalesforceSDKManager.
     *
     * @return AdminSettingsManager instance.
     */
    public synchronized AdminSettingsManager getAdminSettingsManager() {
    	if (adminSettingsManager == null) {
    		adminSettingsManager = new AdminSettingsManager();
    	}
    	return adminSettingsManager;
    }

    /**
     * Returns the administrator permissions manager that's associated with SalesforceSDKManager.
     *
     * @return AdminPermsManager instance.
     */
    public synchronized AdminPermsManager getAdminPermsManager() {
        if (adminPermsManager == null) {
            adminPermsManager = new AdminPermsManager();
        }
        return adminPermsManager;
    }

    /**
     * Returns the login brand parameter.
     *
     * @return Login brand, if configured.
     */
    public String getLoginBrand() {
    	return loginBrand;
    }

    /**
     * Sets the login brand. In the following example, "<brand>" should be set here.
     * https://community.force.com/services/oauth2/authorize/<brand>?response_type=code&...
     * Note: This API might change in the future.
     *
     * @param loginBrand Login brand param.
     */
    public synchronized void setLoginBrand(String loginBrand) {
        this.loginBrand = loginBrand;
    }

    /**
     * Returns whether browser based login should be used instead of WebView.
     *
     * @return True - if Chrome should be used for login, False - otherwise.
     */
    public boolean isBrowserLoginEnabled() {
        return browserLoginEnabled;
    }

    /**
     * Sets whether browser based login should be used instead of WebView.
     *
     * @param browserLoginEnabled True - if Chrome should be used for login, False - otherwise.
     */
    public synchronized void setBrowserLoginEnabled(boolean browserLoginEnabled) {
        this.browserLoginEnabled = browserLoginEnabled;
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
     * Returns the name of the application (as defined in AndroidManifest.xml).
     *
     * @return The name of the application.
     */
    public String getApplicationName() {
        return context.getPackageManager().getApplicationLabel(context.getApplicationInfo()).toString();
    }

    /**
     * Checks if network connectivity exists.
     *
     * @return True if a network connection is available.
     */
    public boolean hasNetwork() {
    	return HttpAccess.DEFAULT.hasNetwork();
    }

    /**
     * Adds an additional set of OAuth keys to fetch and store from the token endpoint.
     *
     * @param additionalOauthKeys List of additional OAuth keys.
     */
    public void setAdditionalOauthKeys(List<String> additionalOauthKeys) {
        this.additionalOauthKeys = additionalOauthKeys;
    }

    /**
     * Returns the list of additional OAuth keys set for this application.
     *
     * @return List of additional OAuth keys.
     */
    public List<String> getAdditionalOauthKeys() {
        return additionalOauthKeys;
    }

    /**
     * Cleans up cached credentials and data.
     *
     * @param frontActivity Front activity.
     * @param account Account.
     */
    private void cleanUp(Activity frontActivity, Account account) {
        final UserAccount userAccount = UserAccountManager.getInstance().buildUserAccount(account);

        // Clean up in this process
        cleanUp(userAccount);

        // Have SalesforceSDKManager living in separate processes also clean up
        sendCleanupIntent(userAccount);

        final List<UserAccount> users = getUserAccountManager().getAuthenticatedUsers();

        // Finishes front activity if specified, and if this is the last account.
        if (frontActivity != null && (users == null || users.size() <= 1)) {
            frontActivity.finish();
        }

        /*
         * Checks how many accounts are left that are authenticated. If only one
         * account is left, this is the account that is being removed. In this
         * case, we can safely reset passcode manager, admin prefs, and encryption keys.
         * Otherwise, we don't reset passcode manager and admin prefs since
         * there might be other accounts on that same org, and these policies
         * are stored at the org level.
         */
        if (users == null || users.size() <= 1) {
            getAdminSettingsManager().resetAll();
            getAdminPermsManager().resetAll();
            adminSettingsManager = null;
            adminPermsManager = null;
            getPasscodeManager().reset(context);
            passcodeManager = null;
            UUIDManager.resetUuids();
        }
    }

    /**
     * Clean up cached data
     *
     * @param userAccount
     */
    protected void cleanUp(UserAccount userAccount) {
        SalesforceAnalyticsManager.reset(userAccount);
        RestClient.clearCaches(userAccount);
    }

    /**
     * Starts login flow if user account has been removed.
     */
    protected void startLoginPage() {

        // Clears cookies.
    	removeAllCookies();

        // Restarts the application.
        final Intent i = new Intent(context, getMainActivityClass());
        i.setPackage(getAppContext().getPackageName());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

	/**
     * Starts account switcher activity if an account has been removed.
     */
    public void startSwitcherActivityIfRequired() {

        // Clears cookies.
    	removeAllCookies();

        /*
         * If the number of accounts remaining is 0, shows the login page.
         * If the number of accounts remaining is 1, switches to that user
         * automatically. If there is more than 1 account logged in, shows
         * the account switcher screen, so that the user can pick which
         * account to switch to.
         */
        final UserAccountManager userAccMgr = getUserAccountManager();
        final List<UserAccount> accounts = userAccMgr.getAuthenticatedUsers();
        if (accounts == null || accounts.size() == 0) {
        	startLoginPage();
        } else if (accounts.size() == 1) {
        	userAccMgr.switchToUser(accounts.get(0), UserAccountManager.USER_SWITCH_TYPE_LOGOUT, null);
        } else {
        	final Intent i = new Intent(context, switcherActivityClass);
    		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		context.startActivity(i);
        }
	}

    /**
     * Unregisters from push notifications for both GCM (Android) and SFDC, and waits either for
     * unregistration to complete or for the operation to time out. The timeout period is defined
     * in PUSH_UNREGISTER_TIMEOUT_MILLIS. 
     *
     * If timeout occurs while the user is logged in, this method attempts to unregister the push
     * unregistration receiver, and then removes the user's account.
     *
     * @param clientMgr ClientManager instance.
     * @param showLoginPage True - if the login page should be shown, False - otherwise.
     * @param refreshToken Refresh token.
     * @param loginServer Login server.
     * @param account Account instance.
     * @param frontActivity Front activity.
     */
    private void unregisterPush(final ClientManager clientMgr, final boolean showLoginPage,
    		final String refreshToken, final String loginServer,
            final Account account, final Activity frontActivity) {
        final IntentFilter intentFilter = new IntentFilter(PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT);
        final BroadcastReceiver pushUnregisterReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(PushMessaging.UNREGISTERED_ATTEMPT_COMPLETE_EVENT)) {
                    postPushUnregister(this, clientMgr, showLoginPage,
                    		refreshToken, loginServer, account, frontActivity);
                }
            }
        };
        getAppContext().registerReceiver(pushUnregisterReceiver, intentFilter);

        // Unregisters from notifications on logout.
		final UserAccount userAcc = getUserAccountManager().buildUserAccount(account);
        PushMessaging.unregister(context, userAcc);

        /*
         * Starts a background thread to wait up to the timeout period. If
         * another thread has already performed logout, we exit immediately.
         */
        (new Thread() {
            public void run() {
                long startTime = System.currentTimeMillis();
                while ((System.currentTimeMillis() - startTime) < PUSH_UNREGISTER_TIMEOUT_MILLIS && !loggedOut) {

                    // Waits for half a second at a time.
                    SystemClock.sleep(500);
                }
                postPushUnregister(pushUnregisterReceiver, clientMgr, showLoginPage,
                		refreshToken, loginServer, account, frontActivity);
            };
        }).start();
    }

    /**
     * This method is called either when unregistration for push notifications 
     * is complete and the user has logged out, or when a timeout occurs while waiting. 
     * If the user has not logged out, this method attempts to unregister the push 
     * notification unregistration receiver, and then removes the user's account.
     *
     * @param pushReceiver Broadcast receiver.
     * @param clientMgr ClientManager instance.
     * @param showLoginPage True - if the login page should be shown, False - otherwise.
     * @param refreshToken Refresh token.
     * @param loginServer Login server.
     * @param account Account instance.
     * @param frontActivity Front activity.
     */
    private synchronized void postPushUnregister(BroadcastReceiver pushReceiver,
    		final ClientManager clientMgr, final boolean showLoginPage,
    		final String refreshToken, final String loginServer,
            final Account account, Activity frontActivity) {
        if (!loggedOut) {
            try {
                context.unregisterReceiver(pushReceiver);
            } catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Exception occurred while unregistering", e);
            }
    		removeAccount(clientMgr, showLoginPage, refreshToken, loginServer, account, frontActivity);
        }
    }

    /**
     * Destroys the stored authentication credentials (removes the account).
     *
     * @param frontActivity Front activity.
     */
    public void logout(Activity frontActivity) {
        logout(frontActivity, true);
    }

    /**
     * Destroys the stored authentication credentials (removes the account).
     *
     * @param account Account.
     * @param frontActivity Front activity.
     */
    public void logout(Account account, Activity frontActivity) {
        logout(account, frontActivity, true);
    }

    /**
     * Destroys the stored authentication credentials (removes the account)
     * and, if requested, restarts the app.
     *
     * @param frontActivity Front activity.
     * @param showLoginPage If true, displays the login page after removing the account.
     */
    public void logout(Activity frontActivity, final boolean showLoginPage) {
        final ClientManager clientMgr = new ClientManager(context, getAccountType(),
        		null, shouldLogoutWhenTokenRevoked());
		final Account account = clientMgr.getAccount();
		logout(account, frontActivity, showLoginPage);
    }

    /**
     * Destroys the stored authentication credentials (removes the account)
     * and, if requested, restarts the app.
     *
     * @param account Account.
     * @param frontActivity Front activity.
     * @param showLoginPage If true, displays the login page after removing the account.
     */
    public void logout(Account account, Activity frontActivity, final boolean showLoginPage) {
        EventBuilderHelper.createAndStoreEvent("userLogout", null, TAG, null);
        final ClientManager clientMgr = new ClientManager(context, getAccountType(),
        		null, shouldLogoutWhenTokenRevoked());
        isLoggingOut = true;
		final AccountManager mgr = AccountManager.get(context);
		String refreshToken = null;
		String loginServer = null;
		if (account != null) {
			refreshToken = SalesforceSDKManager.decrypt(mgr.getPassword(account));
	        loginServer = SalesforceSDKManager.decrypt(mgr.getUserData(account,
	        		AuthenticatorService.KEY_INSTANCE_URL));
		}

		/*
		 * Makes a call to un-register from push notifications, only
		 * if the refresh token is available.
		 */
		final UserAccount userAcc = getUserAccountManager().buildUserAccount(account);
    	if (PushMessaging.isRegistered(context, userAcc) && refreshToken != null) {
    		loggedOut = false;
    		unregisterPush(clientMgr, showLoginPage, refreshToken,
    				loginServer, account, frontActivity);
    	} else {
    		removeAccount(clientMgr, showLoginPage, refreshToken,
                    loginServer, account, frontActivity);
    	}
    }

    /**
     * Removes the account upon logout.
     *
     * @param clientMgr ClientManager instance.
     * @param showLoginPage If true, displays the login page after removing the account.
     * @param refreshToken Refresh token.
     * @param loginServer Login server.
     * @param account Account instance.
     * @param frontActivity Front activity.
     */
    private void removeAccount(ClientManager clientMgr, final boolean showLoginPage,
    		String refreshToken, String loginServer,
    		Account account, Activity frontActivity) {
    	loggedOut = true;
    	cleanUp(frontActivity, account);

    	/*
    	 * Removes the existing account, if any. 'account == null' does not
    	 * guarantee that there are no accounts to remove. In the 'Forgot Passcode'
    	 * flow there could be accounts to remove, but we don't have them, since
    	 * we don't have the passcode hash to decrypt them. Hence, we query
    	 * AccountManager directly here and remove the accounts for the case
    	 * where 'account == null'. If AccountManager doesn't have accounts
    	 * either, then there's nothing to do.
    	 */
    	if (account == null) {
    		final AccountManager accMgr = AccountManager.get(context);
    		if (accMgr != null) {
    			final Account[] accounts = accMgr.getAccountsByType(getAccountType());
    			if (accounts.length > 0) {
    				for (int i = 0; i < accounts.length - 1; i++) {
    					clientMgr.removeAccounts(accounts);
    				}
    				clientMgr.removeAccountAsync(accounts[accounts.length - 1],
    						new AccountManagerCallback<Boolean>() {

    	    			@Override
    	    			public void run(AccountManagerFuture<Boolean> arg0) {
    	    				notifyLogoutComplete(showLoginPage);
    	    			}
    	    		});
    			} else {
    				notifyLogoutComplete(showLoginPage);
    			}
    		} else {
    			notifyLogoutComplete(showLoginPage);
    		}
    	} else {
    		clientMgr.removeAccountAsync(account, new AccountManagerCallback<Boolean>() {

    			@Override
    			public void run(AccountManagerFuture<Boolean> arg0) {
    				notifyLogoutComplete(showLoginPage);
    			}
    		});
    	}
    	isLoggingOut = false;

    	// Revokes the existing refresh token.
        if (shouldLogoutWhenTokenRevoked() && account != null && refreshToken != null) {
        	new RevokeTokenTask(refreshToken, loginServer).execute();
        }
    }

    private void notifyLogoutComplete(boolean showLoginPage) {
    	EventsObservable.get().notifyEvent(EventType.LogoutComplete);
        sendLogoutCompleteIntent();
		if (showLoginPage) {
			startSwitcherActivityIfRequired();
		}
    }

    /**
     * Returns a user agent string based on the Mobile SDK version. The user agent takes the following form:
     *   SalesforceMobileSDK/{salesforceSDK version} android/{android OS version} appName/appVersion {Native|Hybrid} uid_{device id}
     *
     * @return The user agent string to use for all requests.
     */
    public final String getUserAgent() {
    	return getUserAgent("");
    }
    
    public String getUserAgent(String qualifier) {
        String appName = "";
        String appVersion = "";
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appName = context.getString(packageInfo.applicationInfo.labelRes);
            appVersion = packageInfo.versionName;
        } catch (NameNotFoundException e) {
            SalesforceSDKLogger.w(TAG, "Package info could not be retrieved", e);
        } catch (Resources.NotFoundException nfe) {

    	   	// A test harness such as Gradle does NOT have an application name.
            SalesforceSDKLogger.w(TAG, "Package info could not be retrieved", nfe);
        }
        String appTypeWithQualifier = getAppType() + qualifier;
        return String.format("SalesforceMobileSDK/%s android mobile/%s (%s) %s/%s %s uid_%s ftr_%s",
                SDK_VERSION, Build.VERSION.RELEASE, Build.MODEL, appName, appVersion, appTypeWithQualifier, uid, TextUtils.join(".",features));
    }

    /**
     * Adds AppFeature code to User Agent header for reporting.
     */
    public void registerUsedAppFeature(String appFeatureCode) {
        features.add(appFeatureCode);
    }

    /**
     * Removed AppFeature code to User Agent header for reporting.
     */
    public void unregisterUsedAppFeature(String appFeatureCode) {
        features.remove(appFeatureCode);
    }

    /**
     * @return app type as String
     */
    public String getAppType() {
        return "Native";
    }

	/**
	 * Indicates whether the application is a hybrid application.
	 *
	 * @return True if this is a hybrid application.
	 */
	public boolean isHybrid() {
        return false;
	}

    /**
     * Returns the authentication account type (which should match authenticator.xml).
     *
     * @return Account type string.
     */
    public String getAccountType() {
        return context.getString(getSalesforceR().stringAccountType());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(this.getClass()).append(": {\n")
          .append("   accountType: ").append(getAccountType()).append("\n")
          .append("   userAgent: ").append(getUserAgent()).append("\n")
          .append("   mainActivityClass: ").append(getMainActivityClass()).append("\n")
          .append("   isFileSystemEncrypted: ").append(Encryptor.isFileSystemEncrypted()).append("\n");
        if (passcodeManager != null) {

            // passcodeManager may be null at startup if the app is running in debug mode.
            sb.append("   hasStoredPasscode: ").append(passcodeManager.hasStoredPasscode(context)).append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Encrypts the given data.
     *
     * @param data Data to be encrypted.
     * @return Encrypted data.
     */
    public static String encrypt(String data) {
        return Encryptor.encrypt(data, getEncryptionKey());
    }

    /**
     * Returns the encryption key being used.
     *
     * @return Encryption key.
     */
    public static String getEncryptionKey() {
        return SalesforceKeyGenerator.getEncryptionKey(INTERNAL_ENTROPY);
    }

    /**
     * Decrypts the given data.
     *
     * @param data Data to be decrypted.
     * @return Decrypted data.
     */
    public static String decrypt(String data) {
        return Encryptor.decrypt(data, getEncryptionKey());
    }

    /**
     * Asynchronous task for revoking the refresh token on logout.
     *
     * @author bhariharan
     */
    private class RevokeTokenTask extends AsyncTask<Void, Void, Void> {

    	private String refreshToken;
    	private String loginServer;

    	public RevokeTokenTask(String refreshToken, String loginServer) {
    		this.refreshToken = refreshToken;
    		this.loginServer = loginServer;
    	}

		@Override
		protected Void doInBackground(Void... nothings) {
	        try {
	        	OAuth2.revokeRefreshToken(HttpAccess.DEFAULT, new URI(loginServer), refreshToken);
	        } catch (Exception e) {
                SalesforceSDKLogger.w(TAG, "Revoking token failed", e);
	        }
	        return null;
		}
    }

    /**
     * Retrieves a property value that indicates whether the current run is a test run.
     *
     * @return True if the current run is a test run.
     */
    public boolean getIsTestRun() {
    	return INSTANCE.isTestRun;
    }

    /**
     * Sets a property that indicates whether the current run is a test run.
     *
     * @param isTestRun True if the current run is a test run.
     */
    public void setIsTestRun(boolean isTestRun) {
    	INSTANCE.isTestRun = isTestRun;
    }

    /**
     * Retrieves a property value that indicates whether logout is in progress.
     *
     * @return True if logout is in progress.
     */
    public boolean isLoggingOut() {
    	return isLoggingOut;
    }
    
    /**
     * @return ClientManager
     */
    public ClientManager getClientManager() {
    	return new ClientManager(getAppContext(), getAccountType(), getLoginOptions(), true);
    }

    /**
     * @return ClientManager
     */
    public ClientManager getClientManager(String jwt, String url) {
        return new ClientManager(getAppContext(), getAccountType(), getLoginOptions(jwt, url), true);
    }

	public void removeAllCookies() {
		CookieManager.getInstance().removeAllCookies(null);
    }

	public void removeSessionCookies() {
        CookieManager.getInstance().removeSessionCookies(null);
    }

	public void syncCookies() {
        CookieManager.getInstance().flush();
    }

    private void sendLogoutCompleteIntent() {
        final Intent intent = new Intent(LOGOUT_COMPLETE_INTENT_ACTION);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private void sendCleanupIntent(UserAccount userAccount) {
        final Intent intent = new Intent(CLEANUP_INTENT_ACTION);
        intent.setPackage(context.getPackageName());
        intent.putExtra(PROCESS_ID_KEY, PROCESS_ID);
        if (null != userAccount) {
            intent.putExtra(USER_ACCOUNT, userAccount.toBundle());
        }
        context.sendBroadcast(intent);
    }

    private class CleanupReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null
                    && intent.getAction().equals(SalesforceSDKManager.CLEANUP_INTENT_ACTION)
                    && !intent.getStringExtra(PROCESS_ID_KEY).equals(PROCESS_ID)) {

                UserAccount userAccount = null;
                if (intent.hasExtra(USER_ACCOUNT)) {
                    userAccount = new UserAccount(intent.getBundleExtra(USER_ACCOUNT));
                }
                cleanUp(userAccount);
            }
        }
    }

}

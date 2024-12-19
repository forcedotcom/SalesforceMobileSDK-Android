package com.salesforce.androidsdk.ui

import android.os.Bundle
import android.security.KeyChain.getCertificateChain
import android.security.KeyChain.getPrivateKey
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.makeText
import com.salesforce.androidsdk.R.string.sf__biometric_signout_user
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error
import com.salesforce.androidsdk.R.string.sf__generic_authentication_error_title
import com.salesforce.androidsdk.R.string.sf__managed_app_error
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.analytics.EventBuilderHelper.createAndStoreEventSync
import com.salesforce.androidsdk.app.Features.FEATURE_BIOMETRIC_AUTH
import com.salesforce.androidsdk.app.Features.FEATURE_SCREEN_LOCK
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess.DEFAULT
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.auth.OAuth2.IdServiceResponse
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse
import com.salesforce.androidsdk.auth.OAuth2.revokeRefreshToken
import com.salesforce.androidsdk.config.RuntimeConfig.getRuntimeConfig
import com.salesforce.androidsdk.push.PushMessaging.register
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient.clearCaches
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.security.BiometricAuthenticationManager.Companion.isBiometricAuthenticationEnabled
import com.salesforce.androidsdk.security.ScreenLockManager
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.Companion
import com.salesforce.androidsdk.ui.OAuthWebviewHelper.Companion.MUST_BE_MANAGED_APP_PERM
import com.salesforce.androidsdk.util.SalesforceSDKLogger.d
import com.salesforce.androidsdk.util.SalesforceSDKLogger.e
import com.salesforce.androidsdk.util.SalesforceSDKLogger.w
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.function.Consumer

/**
 * A abstract class for objects that, provided an authentication response,
 * can finish the authentication flow, publish progress and handle errors.
 *
 * By overriding the background work and post-work methods, it is possible
 * to use this class to model other types of background work.
 *
 * The parameter type generic parameter models the type provided by the
 * authentication flow, such as a token endpoint response.  It can also
 * model generic types for implementations that handle tasks other than
 * authentication. The actual type is provided by the implementation.
 *
 * This was once Android's now deprecated async task.  It has been severed
 * from inheriting from deprecated classes.  The implementation now uses
 * Kotlin Coroutines for concurrency needs.  The overall class and method
 * structure remains very similar to that provided by earlier versions, for
 * compatibility and ease of adoption.
 */
internal abstract class TestBaseFinishAuthFlowTask<Parameter> {

    private val TAG = "TestBaseFinishAuthFlowTask"

    /**
     * Finishes the authentication flow.
     * @param request The authentication response
     */
    internal suspend fun execute(request: Parameter?, nativeLogin: Boolean = false) = withContext(IO) {
        onPostExecute(doInBackground(request), nativeLogin)
    }

    /** The exception that occurred during background work, if applicable */
    protected var backgroundException: Throwable? = null

    protected var id: IdServiceResponse? = null

    /**
     * Indicates if authentication is blocked for the current user due to
     * the block Salesforce integration user option.
     */
    protected var shouldBlockSalesforceIntegrationUser = false

    abstract fun finish(userAccount: UserAccount?)

    abstract fun onAuthFlowError(
        error: String,
        errorDesc: String?,
        e: Throwable?,
    )

    abstract fun onAccountAuthenticatorResult(authResult: Bundle)

    open fun doInBackground(request: Parameter?) =
        runCatching {
            publishProgress(true)
            performRequest(request)
        }.onFailure { throwable ->
            handleException(throwable)
        }.getOrNull()

    @Suppress("unused")
    @Throws(Exception::class)
    protected abstract fun performRequest(
        param: Parameter?
    ): TokenEndpointResponse?

    open fun onPostExecute(tr: TokenEndpointResponse?, nativeLogin: Boolean) {
        val context = SalesforceSDKManager.getInstance().appContext
        var accountOptions: Bundle? = null

        // Failure cases
        if (shouldBlockSalesforceIntegrationUser) {
            /*
             * Salesforce integration users are prohibited from successfully
             * completing authentication. This alleviates the Restricted
             * Product Approval requirement on Salesforce Integration add-on
             * SKUs and conforms to Legal and Product Strategy requirements
             */
            w(TAG, "Salesforce integration users are prohibited from successfully authenticating.")
            onAuthFlowError( // Issue the generic authentication error
                context.getString(sf__generic_authentication_error_title),
                context.getString(sf__generic_authentication_error), backgroundException
            )

            finish(null)
            return
        }

        if (backgroundException != null) {
            w(TAG, "Exception thrown while retrieving token response", backgroundException)
            onAuthFlowError(
                context.getString(sf__generic_authentication_error_title),
                context.getString(sf__generic_authentication_error),
                backgroundException
            )
            finish(null)
            return
        }

        id?.let { id ->
            val mustBeManagedApp = id.customPermissions?.optBoolean(MUST_BE_MANAGED_APP_PERM)
            if (mustBeManagedApp == true && !getRuntimeConfig(context).isManagedApp) {
                onAuthFlowError(
                    context.getString(sf__generic_authentication_error_title),
                    context.getString(sf__managed_app_error), backgroundException
                )
                finish(null)
                return
            }
        }

        // TODO:  how do I get login options??? (for login server and client id)  ViewModel?
        val account = UserAccountBuilder.getInstance()
            .populateFromTokenEndpointResponse(tr)
            .populateFromIdServiceResponse(id)
            .nativeLogin(nativeLogin)
            .accountName(buildAccountName(id?.username, tr?.instanceUrl))
            .loginServer("login.salesforce.com")
            .clientId("")
            .build()

        accountOptions = account.toBundle()

        account.downloadProfilePhoto()

        // Set additional administrator prefs if they exist
        id?.customAttributes?.let { customAttributes ->
            SalesforceSDKManager.getInstance().adminSettingsManager?.setPrefs(customAttributes, account)
        }

        id?.customPermissions?.let { customPermissions ->
            SalesforceSDKManager.getInstance().adminPermsManager?.setPrefs(customPermissions, account)
        }

        SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers?.let { existingUsers ->
            // Check if the user already exists
            if (existingUsers.contains(account)) {
                val duplicateUserAccount = existingUsers.removeAt(existingUsers.indexOf(account))
                clearCaches()
                UserAccountManager.getInstance().clearCachedCurrentUser()

                // Revoke existing refresh token
                if (account.refreshToken != duplicateUserAccount.refreshToken) {
                    runCatching {
                        URI(duplicateUserAccount.instanceServer)
                    }.onFailure { throwable ->
                        w(TAG, "Revoking token failed", throwable)
                    }.onSuccess { uri ->
                        // The user authenticated via webview again, unlock the app.
                        if (isBiometricAuthenticationEnabled(duplicateUserAccount)) {
                            (SalesforceSDKManager.getInstance().biometricAuthenticationManager
                                    as? BiometricAuthenticationManager)?.onUnlock()
                        }
                        CoroutineScope(IO).launch {
                            revokeRefreshToken(
                                DEFAULT,
                                uri,
                                duplicateUserAccount.refreshToken,
                                OAuth2.LogoutReason.REFRESH_TOKEN_ROTATED,
                            )
                        }
                    }
                }
            }

            // If this account has biometric authentication enabled remove any others that also have it
            if (id?.biometricAuth == true) {
                existingUsers.forEach(Consumer { existingUser ->
                    if (isBiometricAuthenticationEnabled(existingUser)) {

                        // TODO: figure this out
//                        activity?.runOnUiThread {
//                            makeText(
//                                activity,
//                                activity.getString(
//                                    sf__biometric_signout_user,
//                                    existingUser.username
//                                ),
//                                LENGTH_LONG
//                            ).show()
//                        }
//                        // This is an unexpected logout(s) because we only support one Bio Auth user.
//                        SalesforceSDKManager.getInstance().userAccountManager.signoutUser(
//                            existingUser, activity, false, OAuth2.LogoutReason.UNEXPECTED
//                        )
                    }
                })
            }
        }

        // Save the user account
        addAccount(account)

        // Screen lock required by mobile policy
        if (id?.screenLockTimeout?.compareTo(0) == 1) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_SCREEN_LOCK)
            val timeoutInMills = (id?.screenLockTimeout ?: 0) * 1000 * 60
            (SalesforceSDKManager.getInstance().screenLockManager as ScreenLockManager?)?.storeMobilePolicy(
                account,
                id?.screenLock ?: false,
                timeoutInMills
            )
        }

        // Biometric authorization required by mobile policy
        if (id?.biometricAuth == true) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(FEATURE_BIOMETRIC_AUTH)
            val timeoutInMills = (id?.biometricAuthTimeout ?: 0) * 60 * 1000
            (SalesforceSDKManager.getInstance().biometricAuthenticationManager as BiometricAuthenticationManager?)?.storeMobilePolicy(
                account,
                id?.biometricAuth ?: false,
                timeoutInMills
            )
        }

        // All done
        finish(account)
    }

    private fun publishProgress(
        @Suppress("SameParameterValue") value: Boolean?
    ) = onProgressUpdate(value)

    @Suppress("MemberVisibilityCanBePrivate")
    fun onProgressUpdate(@Suppress("UNUSED_PARAMETER") value: Boolean?) {
    }

    @Suppress("MemberVisibilityCanBePrivate", "unused")
    protected fun handleException(throwable: Throwable) {
        if (throwable.message != null) {
            w(TAG, "Exception thrown", throwable)
        }
        backgroundException = throwable
    }

    private fun addAccount(account: UserAccount?) {
        val clientManager = with(SalesforceSDKManager.getInstance()) {
            ClientManager(appContext, accountType, loginOptions, shouldLogoutWhenTokenRevoked())
        }

        // New account
        val extras = clientManager.createNewAccount(account)

        /*
         * Registers for push notifications if setup by the app. This step needs
         * to happen after the account has been added by client manager, so that
         * the push service has all the account info it needs.
         */
        register(SalesforceSDKManager.getInstance().appContext, account)

        onAccountAuthenticatorResult(extras)

        when {
            SalesforceSDKManager.getInstance().isTestRun -> logAddAccount(account)
            else -> CoroutineScope(IO).launch {
                logAddAccount(account)
            }
        }
    }

    /**
     * Log the addition of a new account.
     *
     * @param account The user account
     */
    private fun logAddAccount(account: UserAccount?) {
        val attributes = JSONObject()
        runCatching {
            val users = UserAccountManager.getInstance().authenticatedUsers
            attributes.put("numUsers", users?.size ?: 0)
            val servers = SalesforceSDKManager.getInstance().loginServerManager.loginServers
            attributes.put("numLoginServers", servers?.size ?: 0)
            servers?.let { serversUnwrapped ->
                val serversJson = JSONArray()
                for (server in serversUnwrapped) {
                    server?.let { serverUnwrapped ->
                        serversJson.put(serverUnwrapped.url)
                    }
                }
                attributes.put("loginServers", serversJson)
            }
            createAndStoreEventSync("addUser", account, TAG, attributes)
        }.onFailure { throwable ->
            e(TAG, "Exception thrown while creating JSON", throwable)
        }
    }

    /**
     * The name to be shown for account in Settings -> Accounts & Sync
     * @return name to be shown for account in Settings -> Accounts & Sync
     */
    protected open fun buildAccountName(
        username: String?,
        instanceServer: String?
    ) = String.format(
        "%s (%s) (%s)", username, instanceServer,
        SalesforceSDKManager.getInstance().applicationName
    )

//    override fun alias(alias: String?) {
//        runCatching {
//            activity?.let { activity ->
//                d(OAuthWebviewHelper.TAG, "Keychain alias callback received")
//                alias?.let { alias ->
//                    certChain = getCertificateChain(activity, alias)
//                    key = getPrivateKey(activity, alias)
//                }
//                activity.runOnUiThread { loadLoginPage() }
//            }
//        }.onFailure { throwable ->
//            e(OAuthWebviewHelper.TAG, "Exception thrown while retrieving X.509 certificate", throwable)
//        }
//    }
}
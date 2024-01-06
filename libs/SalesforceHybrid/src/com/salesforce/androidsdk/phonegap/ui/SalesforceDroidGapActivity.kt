/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap.ui

import android.net.Uri.parse
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.URLUtil.isHttpsUrl
import androidx.lifecycle.lifecycleScope
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess.NoNetworkException
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.config.BootConfig.getBootConfig
import com.salesforce.androidsdk.config.BootConfig.isAbsoluteUrl
import com.salesforce.androidsdk.config.BootConfig.validateBootConfig
import com.salesforce.androidsdk.config.LoginServerManager.PRODUCTION_LOGIN_URL
import com.salesforce.androidsdk.config.LoginServerManager.SANDBOX_LOGIN_URL
import com.salesforce.androidsdk.phonegap.app.SalesforceHybridSDKManager
import com.salesforce.androidsdk.phonegap.ui.SalesforceWebViewClientHelper.getAppHomeUrl
import com.salesforce.androidsdk.phonegap.ui.SalesforceWebViewClientHelper.hasCachedAppHome
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger.i
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger.w
import com.salesforce.androidsdk.rest.ApiVersionStrings.VERSION_NUMBER
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestRequest.getCheapRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.ui.SalesforceActivityDelegate
import com.salesforce.androidsdk.ui.SalesforceActivityInterface
import com.salesforce.androidsdk.util.AuthConfigUtil.MyDomainAuthConfig
import com.salesforce.androidsdk.util.AuthConfigUtil.getMyDomainAuthConfig
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType.GapWebViewCreateComplete
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaActivity
import org.apache.cordova.CordovaWebView
import org.apache.cordova.CordovaWebViewEngine
import org.apache.cordova.CordovaWebViewImpl.createEngine

/**
 * Class that defines the main activity for a PhoneGap-based application.
 */
open class SalesforceDroidGapActivity : CordovaActivity(), SalesforceActivityInterface {

    // Delegate
    private val delegate: SalesforceActivityDelegate? by lazy {
        SalesforceActivityDelegate(this)
    }

    /** The REST client */
    var restClient: RestClient? = null
        private set

    /** The client manager */
    private var clientManager: ClientManager? = null

    /** The boot configuration */
    var bootConfig: BootConfig? = null
        private set

    /**
     * The authentication configuration associated with the current login
     * server, if it exists.
     */
    var authConfig: MyDomainAuthConfig? = null
        private set

    /** Indicates if the web app is loaded */
    private var webAppLoaded = false

    /**
     * Called when the activity is first created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        init()

        // Get the boot configuration
        bootConfig = getBootConfig(this)

        // Get the client manager
        clientManager = buildClientManager()

        // Set up global stores and syncs defined in static configs
        SalesforceHybridSDKManager.getInstance().run {
            setupGlobalStoreFromDefaultConfig()
            setupGlobalSyncsFromDefaultConfig()
        }

        // Create the delegate
        delegate?.onCreate()
    }

    open fun buildClientManager() =
        SalesforceHybridSDKManager.getInstance().clientManager

    public override fun init() {
        super.init()

        EventsObservable.get().notifyEvent(
            GapWebViewCreateComplete,
            appView
        )
    }

    override fun makeWebViewEngine(): CordovaWebViewEngine {

        preferences["webview"] = SalesforceWebViewEngine::class.java.canonicalName

        return createEngine(this, preferences)
    }

    public override fun onResume() {
        super.onResume()

        // Fetch authentication configuration if required
        lifecycleScope.launch {
            doAuthConfig()
        }

        delegate?.onResume(false)

        // Will call this.onResume(RestClient client) with a null client
    }

    /* Called from delegate with null */
    override fun onResume(restClient: RestClient?) {

        // Get the REST client when logged in
        this.restClient = runCatching {
            clientManager?.peekRestClient()
        }.getOrNull()

        when (this.restClient) {
            // When not logged in
            null -> when {
                !webAppLoaded -> onResumeNotLoggedIn()
                else -> i(TAG, "onResume - unauthenticated web app already loaded")
            }

            // Logged in
            else ->
                when {
                    // Web app never loaded
                    !webAppLoaded -> onResumeLoggedInNotLoaded()

                    // Web app already loaded
                    else -> i(TAG, "onResume - already logged in/web app already loaded")
                }
        }
    }

    /**
     * Called when resuming activity and user is not authenticated
     */
    private fun onResumeNotLoggedIn() {
        val bootConfig = bootConfig
        val unauthenticatedStartPage = unauthenticatedStartPage

        runCatching {
            validateBootConfig(bootConfig)

            when {
                // Need to be authenticated
                bootConfig?.shouldAuthenticate() == true ->

                    when {
                        // Online
                        SalesforceSDKManager.getInstance().hasNetwork() -> {
                            i(TAG, "onResumeNotLoggedIn - should authenticate/online - authenticating")
                            authenticate(null)
                        }
                        // Offline
                        else -> {
                            w(TAG, "onResumeNotLoggedIn - should authenticate/offline - can not proceed")
                            loadErrorPage()
                        }
                    }

                // Does not need to be authenticated
                else ->
                    when (bootConfig?.isLocal) {
                        // Local
                        true -> {
                            i(TAG, "onResumeNotLoggedIn - should not authenticate/local start page - loading web app")
                            loadLocalStartPage()
                        }

                        // Remote
                        else -> {
                            w(TAG, "onResumeNotLoggedIn - should not authenticate/remote start page - loading web app")
                            unauthenticatedStartPage?.let { unauthenticatedStartPage ->
                                loadRemoteStartPage(
                                    unauthenticatedStartPage,
                                    false
                                )
                            }
                        }
                    }
            }
        }.onFailure { e ->
            w(TAG, "onResumeNotLoggedIn - Boot config did not pass validation: ${e.message} - cannot proceed")
            loadErrorPage()
        }
    }

    /**
     * Called when resuming activity and user is authenticated but webview has
     * not been loaded yet.
     */
    private fun onResumeLoggedInNotLoaded() {
        val bootConfig = bootConfig

        // Set up user stores and syncs defined in static configs
        SalesforceHybridSDKManager.getInstance().run {
            setupUserStoreFromDefaultConfig()
            setupUserSyncsFromDefaultConfig()
        }

        when (bootConfig?.isLocal) {
            // Local
            true -> {
                i(TAG, "onResumeLoggedInNotLoaded - local start page - loading web app")
                loadLocalStartPage()
            }

            // Remote
            else -> when {
                // Online
                SalesforceSDKManager.getInstance().hasNetwork() -> {
                    i(TAG, "onResumeLoggedInNotLoaded - remote start page/online - loading web app")
                    bootConfig?.startPage?.let { startPage ->
                        loadRemoteStartPage(startPage, true)
                    }
                }

                // Offline
                else -> when {
                    // Has cached version
                    hasCachedAppHome(this) -> {
                        i(TAG, "onResumeLoggedInNotLoaded - remote start page/offline/cached - loading cached web app")
                        loadCachedStartPage()
                    }
                    // No cached version
                    else -> {
                        i(TAG, "onResumeLoggedInNotLoaded - remote start page/offline/not cached - can not proceed")
                        loadErrorPage()
                    }
                }
            }
        }
    }

    public override fun onPause() {
        super.onPause()
        delegate?.onPause()
    }

    override fun onDestroy() {
        delegate?.onDestroy()
        super.onDestroy()
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent
    ) = (delegate?.onKeyUp(
        keyCode,
        event
    ) ?: false) || super.onKeyUp(
        keyCode,
        event
    )

    /** The unauthenticated start page from the boot configuration */
    @Suppress("MemberVisibilityCanBePrivate")
    protected val unauthenticatedStartPage
        get() = bootConfig?.unauthenticatedStartPage

    fun logout(callbackContext: CallbackContext?) {
        i(TAG, "logout called")

        SalesforceSDKManager.getInstance().logout(
            account = null,
            frontActivity = this
        )

        callbackContext?.success()
    }

    /**
     * Get a REST client and refresh the authentication token
     *
     * @param callbackContext When not null credentials/errors are sent through
     * to callbackContext.success()/error()
     */
    fun authenticate(
        callbackContext: CallbackContext?
    ) {
        i(TAG, "authenticate called")

        clientManager?.getRestClient(this) { client ->
            when (client) {

                null -> {
                    i(TAG, "authenticate callback triggered with null client")
                    logout(null)
                }

                else -> {
                    i(TAG, "authenticate callback triggered with actual client")
                    restClient = client

                    /*
                     * Do a cheap REST call to refresh the access token if
                     * needed. If the login took place a while back (e.g. the
                     * already logged in application was restarted), then the
                     * returned session ID (access token) might be stale. This
                     * is not an issue if one uses exclusively REST client for
                     * calling the server because it takes care of refreshing
                     * the access token when needed, but a stale session ID will
                     * cause the web view to redirect to the web login
                     */
                    restClient?.sendAsync(
                        getCheapRequest(VERSION_NUMBER), object : AsyncRequestCallback {
                            override fun onSuccess(
                                request: RestRequest,
                                response: RestResponse
                            ) =
                                runOnUiThread {
                                    /*
                                     * The client instance being used here needs
                                     * to be refreshed, to ensure we use the new
                                     * access token
                                     */
                                    restClient = clientManager?.peekRestClient()
                                    getAuthCredentials(callbackContext)
                                }

                            override fun onError(exception: Exception) {
                                callbackContext?.error(exception.message)
                            }
                        })
                }
            }
        }
    }

    /**
     * Get JSON for credentials.
     */
    fun getAuthCredentials(callbackContext: CallbackContext?) {
        i(TAG, "getAuthCredentials called")

        when {
            restClient != null -> {
                val credentials = restClient?.jsonCredentials
                callbackContext?.success(credentials)
            }

            else ->
                callbackContext?.error("Never authenticated")
        }
    }

    /**
     * If an action causes a redirect to the login page, this method will be
     * called. It causes the session to be refreshed and reloads the URL through
     * the front door.
     *
     * @param url The page to load once the session has been refreshed
     */
    fun refresh(url: String?) {
        i(TAG, "refresh called")

        /*
         * If client is null at this point, authentication hasn't been performed
         * yet. We need to trigger authentication and recreate the webview in
         * the callback to load the page correctly. This handles some corner
         * cases involving hitting the back button when authentication is in
         * progress
         */
        if (restClient == null) {
            clientManager?.getRestClient(this) { recreate() }
            return
        }

        restClient?.sendAsync(
            getCheapRequest(VERSION_NUMBER), object : AsyncRequestCallback {

                override fun onSuccess(
                    request: RestRequest,
                    response: RestResponse
                ) {
                    i(TAG, "refresh callback - refresh succeeded")

                    runOnUiThread {
                        /*
                         * The client instance being used here needs to be
                         * refreshed, to ensure we use the new access token.
                         * However, if the refresh token was revoked when the
                         * app was in the background we need to catch that
                         * exception and trigger a proper logout to reset the
                         * state of this class
                        */
                        runCatching {
                            restClient = clientManager?.peekRestClient()
                            val frontDoorUrl = getFrontDoorUrl(url, isAbsoluteUrl(url))
                            loadUrl(frontDoorUrl)
                        }.onFailure {
                            i(TAG, "User has been logged out.")
                            logout(null)
                        }
                    }
                }

                override fun onError(exception: Exception) {
                    w(TAG, "refresh callback - refresh failed", exception)

                    // Only logout if we are NOT offline
                    if (exception !is NoNetworkException) {
                        logout(null)
                    }
                }
            })
    }

    /**
     * Load the local start page
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun loadLocalStartPage() {
        assert(bootConfig?.isLocal == true)
        val startPage = bootConfig?.startPage ?: return

        i(TAG, "loadLocalStartPage called - loading!")

        loadUrl("file:///android_asset/www/$startPage")
        webAppLoaded = true
    }

    /**
     * Load the remote start page (front-doored)
     */
    @Suppress("unused")
    fun loadRemoteStartPage() =
        bootConfig?.startPage?.let { startPage ->
            loadRemoteStartPage(startPage, true)
        }

    /**
     * Load the remote start page.
     * @param startPageUrl The start page to load
     * @param loadThroughFrontDoor Whether or not to load through front-door
     */
    private fun loadRemoteStartPage(
        startPageUrl: String,
        loadThroughFrontDoor: Boolean
    ) {
        assert(bootConfig?.isLocal != true)

        var url = startPageUrl
        if (loadThroughFrontDoor) {
            url = getFrontDoorUrl(url, isAbsoluteUrl(url)) ?: return
        }

        i(TAG, "loadRemoteStartPage called - loading!")

        loadUrl(url)
        webAppLoaded = true
    }

    /**
     * Returns the front-doored URL of a URL passed in.
     *
     * @param providedUrl URL to be front-doored
     * @param isAbsoluteUrl True if the URL should be used as is; false
     * otherwise
     * @return The front-doored URL
     */
    fun getFrontDoorUrl(
        providedUrl: String?,
        isAbsoluteUrl: Boolean
    ): String? {

        /*
         * Use the absolute URL in some cases and the relative URL in some other
         * cases because of differences between instance URL and community URL.
         * Community URL can be custom and the logic of determining which URL to
         * use is in the 'resolveUrl' method in 'ClientInfo'
         */
        val restClient = restClient ?: return null
        return "${restClient.clientInfo.instanceUrlAsString}/secur/frontdoor.jsp?".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter(
                name = "sid",
                value = restClient.authToken
            )
            ?.addQueryParameter(
                name = "retURL",
                value = when {
                    isAbsoluteUrl -> providedUrl
                    else -> restClient.clientInfo.resolveUrl(providedUrl).toString()
                }
            )
            ?.addQueryParameter(
                name = "display",
                value = "touch"
            )
            ?.build().toString()
    }

    /**
     * Load cached start page
     */
    private fun loadCachedStartPage() {
        loadUrl(getAppHomeUrl(this))
        webAppLoaded = true
    }

    /**
     * Load error page
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun loadErrorPage() {
        val errorPage = bootConfig?.errorPage ?: return
        i(TAG, "getErrorPageUrl called - local error page: $errorPage")
        loadUrl("file:///android_asset/www/$errorPage")
    }

    /** The web view being used */
    var cordovaWebView: CordovaWebView
        get() = super.appView
        set(appView) {
            super.appView = appView
        }


    override fun onLogoutComplete() {}

    override fun onUserSwitched() {
        restClient?.let { restClient ->
            runCatching {
                val currentClient = clientManager?.peekRestClient() ?: return
                if (currentClient.clientInfo.userId != restClient.clientInfo?.userId) {
                    recreate()
                }
            }.onFailure {
                i(TAG, "restartIfUserSwitched - no user account found")
            }
        }
    }

    private fun doAuthConfig() {
        val loginServer = SalesforceHybridSDKManager
            .getInstance()
            .loginServerManager
            .selectedLoginServer
            ?.url
            ?.trim { it <= ' ' } ?: return

        if (loginServer == PRODUCTION_LOGIN_URL || loginServer == SANDBOX_LOGIN_URL || !isHttpsUrl(loginServer) || parse(loginServer) == null
        ) {
            return
        }

        authConfig = getMyDomainAuthConfig(loginServer)

        return
    }

    companion object {
        private const val TAG = "SfDroidGapActivity"
    }
}

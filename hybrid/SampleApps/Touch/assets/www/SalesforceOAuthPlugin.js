/*
 * Copyright (c) 2012, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the
 * following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 * the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

cordova.define("salesforce/plugin/oauth", function(require, exports, module) {
    /**
     * OAuthProperties data structure, for plugin arguments.
     *   remoteAccessConsumerKey - String containing the remote access object ID (client ID).
     *   oauthRedirectURI        - String containing the redirect URI configured for the remote access object.
     *   oauthScopes             - Array of strings specifying the authorization scope of the app (e.g ["api", "visualforce"]).
     *   autoRefreshOnForeground - Boolean, determines whether the container automatically refreshes OAuth session when app is foregrounded
     *   autoRefreshPeriodically - Boolean, determines whether the container automatically refreshes OAuth session periodically
     */
    var OAuthProperties = function (remoteAccessConsumerKey, oauthRedirectURI, oauthScopes, autoRefreshOnForeground, autoRefreshPeriodically) {
        this.remoteAccessConsumerKey = remoteAccessConsumerKey;
        this.oauthRedirectURI = oauthRedirectURI;
        this.oauthScopes = oauthScopes;
        this.autoRefreshOnForeground = autoRefreshOnForeground;
        this.autoRefreshPeriodically = autoRefreshPeriodically;
    };

	/**
	 * Obtain authentication credentials, calling 'authenticate' only if necessary.
	 * Most index.html authors can simply use this method to obtain auth credentials
	 * after onDeviceReady.
     *   success - The success callback function to use.
     *   fail    - The failure/error callback function to use.
	 * cordova returns a dictionary with:
	 * 	accessToken
	 * 	refreshToken
     *  clientId
	 * 	userId
	 * 	orgId
     *  loginUrl
	 * 	instanceUrl
	 * 	userAgent
	 */
    var getAuthCredentials = function(success, fail) {
        cordova.exec(success, fail, "com.salesforce.oauth","getAuthCredentials",[]);
    };
    
    /**
     * Initiates the authentication process, with the given app configuration.
     *   success         - The success callback function to use.
     *   fail            - The failure/error callback function to use.
     *   oauthProperties - The configuration properties for the authentication process.
     *                     See OAuthProperties() below.
     * cordova returns a dictionary with:
     *   accessToken
     *   refreshToken
     *   clientId
     *   userId
     *   orgId
     *   loginUrl
     *   instanceUrl
     *   userAgent
     */
    var authenticate = function(success, fail, oauthProperties) {
        cordova.exec(success, fail, "com.salesforce.oauth", "authenticate", [JSON.stringify(oauthProperties)]);
    };


    /**
     * Logout the current authenticated user. This removes any current valid session token
     * as well as any OAuth refresh token.  The user is forced to login again.
     * This method does not call back with a success or failure callback, as 
     * (1) this method must not fail and (2) in the success case, the current user
     * will be logged out and asked to re-authenticate.
     */
    var logout = function() {
        cordova.exec(null, null, "com.salesforce.oauth", "logoutCurrentUser", []);
    };
    
    /**
     * Gets the app's homepage as an absolute URL.  Used for attempting to load any cached
     * content that the developer may have built into the app (via HTML5 caching).
     *
     * This method will either return the URL as a string, or an empty string if the URL has not been
     * initialized.
     */
    var getAppHomeUrl = function(success) {
        cordova.exec(success, null, "com.salesforce.oauth", "getAppHomeUrl", []);
    };


    /**
     * Part of the module that is public
     */
    module.exports = {
        getAuthCredentials: getAuthCredentials,
        authenticate: authenticate,
        logout: logout,
        getAppHomeUrl: getAppHomeUrl,

        // Constructor
        OAuthProperties: OAuthProperties,
    };
});

// For backward compatibility
var SalesforceOAuthPlugin = cordova.require("salesforce/plugin/oauth");

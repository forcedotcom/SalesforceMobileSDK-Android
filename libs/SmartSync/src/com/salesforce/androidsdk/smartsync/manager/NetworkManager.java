/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartsync.manager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.http.entity.StringEntity;

import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.AccMgrAuthTokenProvider;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;

/**
 * This class has APIs to perform HTTP requests against a Salesforce endpoint.
 *
 * @author bhariharan
 */
public class NetworkManager {

    private static final String TAG = "SmartSync: NetworkManager";

    private static Map<String, NetworkManager> INSTANCES;

    private RestClient client;

    /**
     * Returns the instance of this class associated with this user account.
     *
     * @param account User account.
     * @return Instance of this class.
     */
    public static synchronized NetworkManager getInstance(UserAccount account) {
        return getInstance(account, null);
    }

    /**
     * Returns the instance of this class associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     * @return Instance of this class.
     */
    public static synchronized NetworkManager getInstance(UserAccount account,
            String communityId) {
        if (account == null) {
            account = SalesforceSDKManagerWithSmartStore.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (account == null) {
            return null;
        }
        String uniqueId = account.getUserId();
        if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
            communityId = null;
        }
        if (!TextUtils.isEmpty(communityId)) {
            uniqueId = uniqueId + communityId;
        }
        NetworkManager instance = null;
        if (INSTANCES == null) {
            INSTANCES = new HashMap<String, NetworkManager>();
            instance = new NetworkManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        } else {
            instance = INSTANCES.get(uniqueId);
        }
        if (instance == null) {
            instance = new NetworkManager(account, communityId);
            INSTANCES.put(uniqueId, instance);
        }
        return instance;
    }

    /**
     * Resets the network manager associated with this user account.
     *
     * @param account User account.
     */
    public static synchronized void reset(UserAccount account) {
        reset(account, null);
    }

    /**
     * Resets the network manager associated with this user and community.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    public static synchronized void reset(UserAccount account, String communityId) {
        if (account == null) {
            account = SalesforceSDKManagerWithSmartStore.getInstance().getUserAccountManager().getCurrentUser();
        }
        if (account != null) {
            String uniqueId = account.getUserId();
            if (UserAccount.INTERNAL_COMMUNITY_ID.equals(communityId)) {
                communityId = null;
            }
            if (!TextUtils.isEmpty(communityId)) {
                uniqueId = uniqueId + communityId;
            }
            if (INSTANCES != null) {
                INSTANCES.remove(uniqueId);
            }
        }
    }

    /**
     * Private parameterized constructor.
     *
     * @param account User account.
     * @param communityId Community ID.
     */
    private NetworkManager(UserAccount account, String communityId) {
        try {
            final ClientManager clientManager = new ClientManager(SalesforceSDKManagerWithSmartStore.getInstance().getAppContext(),
                    SalesforceSDKManagerWithSmartStore.getInstance().getAccountType(),
                    SalesforceSDKManagerWithSmartStore.getInstance().getLoginOptions(),
                    true);
            final AccMgrAuthTokenProvider authTokenProvider = new AccMgrAuthTokenProvider(clientManager,
                    account.getAuthToken(), account.getRefreshToken());
            final ClientInfo clientInfo = new ClientInfo(account.getClientId(),
                    new URI(account.getInstanceServer()), new URI(account.getLoginServer()),
                    new URI(account.getIdUrl()), account.getAccountName(),
                    account.getUsername(), account.getUserId(), account.getOrgId(),
                    account.getCommunityId(), account.getCommunityUrl());
            client = new RestClient(clientInfo, account.getAuthToken(),
                    HttpAccess.DEFAULT, authTokenProvider);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Exception occurred while instantiating NetworkServiceManager", e);
            client = null;
        }
    }

    /**
     * Makes a remote GET request and returns the response received.
     *
     * @param path Request path.
     * @param params Additional params.
     * @return Response received.
     */
    public RestResponse makeRemoteGETRequest(String path, Map<String, Object> params) {
        return makeRemoteRequest(true, path, params, null, null, null);
    }

    /**
     * Makes a remote GET request and returns the response received.
     *
     * @param path Request path.
     * @param params Additional params.
     * @param requestHeaders Additional headers for the request.
     * @return Response received.
     */
    public RestResponse makeRemoteGETRequest(String path, Map<String, Object> params,
            Map<String, String> requestHeaders) {
        return makeRemoteRequest(true, path, params, null, null, requestHeaders);
    }

    /**
     * Makes a remote request and returns the response received.
     *
     * @param isGetMethod True - if this is a GET request, False - otherwise.
     * @param path Request path.
     * @param params Additional params.
     * @param postData POST data.
     * @param postDataContentType Content type for POST data.
     * @param requestHeaders Additional headers for the request.
     * @return Response received.
     */
    public RestResponse makeRemoteRequest(boolean isGetMethod, String path,
            Map<String, Object> params, String postData,
            String postDataContentType, Map<String, String> requestHeaders) {
        if (path == null) {
            return null;
        }
        if (params != null && params.size() > 0) {
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            final StringBuilder sb = new StringBuilder(path);
            sb.append("?");
            final Set<String> keys = params.keySet();
            for (final String key : keys) {
                if (key != null) {
                    final String value = (String) params.get(key);
                    if (value != null) {
                        sb.append(key).append("=").append(value);
                        sb.append("&");
                    }
                }
            }
            final String pathWithParams = sb.toString();
            if (pathWithParams.endsWith("?") || pathWithParams.endsWith("&")) {
                path = pathWithParams.substring(0, pathWithParams.length() - 1);
            }
        }
        final RestMethod requestMethod = isGetMethod ? RestMethod.GET : RestMethod.POST;
        RestResponse response = null;
        StringEntity postEntity = null;
        try {
            if (postData != null) {
                postEntity = new StringEntity(postData);
            }
            final RestRequest request = new RestRequest(requestMethod, path,
                    postEntity, requestHeaders);
            response = client.sendSync(request);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Exception occurred while encoding POST data", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while making network request", e);
        } finally {
            if (response != null) {
                try {
                    response.consume();
                } catch (IOException e) {
                    Log.e(TAG, "IOException occurred while making network request", e);
                }
            }
        }
        return response;
    }

    /**
     * Sets the RestClient instance associated with this user account.
     * This is primarily used only by tests.
     *
     * @param account User account.
     * @param client RestClient instance.
     */
    public void setRestClient(UserAccount account, RestClient client) {
        getInstance(account).client = client;
    }
    
    /**
     * @return RestClient instance used by this NetworkManager
     */
    RestClient getRestClient() {
    	return client;
    }
}

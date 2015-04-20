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

import java.net.URI;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.TestCredentials;
import com.salesforce.androidsdk.smartsync.TestForceApp;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

/**
 * Abstract super class for manager test classes
 */
abstract public class ManagerTestCase extends InstrumentationTestCase {

	private static final String[] TEST_SCOPES = new String[] {"web"};
	private static final String TEST_CALLBACK_URL = "test://callback";
	private static final String TEST_AUTH_TOKEN = "test_auth_token";

    Context targetContext;
    EventsListenerQueue eq;
    MetadataManager metadataManager;
    CacheManager cacheManager;
    SyncManager syncManager;
    RestClient restClient;
    HttpAccess httpAccess;
    SmartStore smartStore;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        targetContext = getInstrumentation().getTargetContext();
        final Application app = Instrumentation.newApplication(TestForceApp.class,
        		targetContext);
        getInstrumentation().callApplicationOnCreate(app);
        TestCredentials.init(getInstrumentation().getContext());
        eq = new EventsListenerQueue();
        if (SmartSyncSDKManager.getInstance() == null) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
        final LoginOptions loginOptions = new LoginOptions(TestCredentials.LOGIN_URL,
        		null, TEST_CALLBACK_URL, TestCredentials.CLIENT_ID, TEST_SCOPES);
        final ClientManager clientManager = new ClientManager(targetContext,
        		TestCredentials.ACCOUNT_TYPE, loginOptions, true);
        clientManager.createNewAccount(TestCredentials.ACCOUNT_NAME,
        		TestCredentials.USERNAME, TestCredentials.REFRESH_TOKEN,
        		TEST_AUTH_TOKEN, TestCredentials.INSTANCE_URL,
        		TestCredentials.LOGIN_URL, TestCredentials.IDENTITY_URL,
        		TestCredentials.CLIENT_ID, TestCredentials.ORG_ID,
        		TestCredentials.USER_ID, null);
    	MetadataManager.reset(null);
    	CacheManager.hardReset(null);
    	SyncManager.reset();
        metadataManager = MetadataManager.getInstance(null);
        cacheManager = CacheManager.getInstance(null);
        syncManager = SyncManager.getInstance();
        restClient = initRestClient();
        metadataManager.setRestClient(restClient);
        syncManager.setRestClient(restClient);
        smartStore = cacheManager.getSmartStore();
    }

    @Override
    public void tearDown() throws Exception {
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
    	MetadataManager.reset(null);
    	CacheManager.hardReset(null);
        super.tearDown();
    }
    
    /**
     * Initializes and returns a RestClient instance used for live calls by tests.
     *
     * @return RestClient instance.
     */
    private RestClient initRestClient() throws Exception {
        httpAccess = new HttpAccess(null, null);
        final TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess,
        		new URI(TestCredentials.INSTANCE_URL), TestCredentials.CLIENT_ID,
        		TestCredentials.REFRESH_TOKEN);
        final String authToken = refreshResponse.authToken;
        final ClientInfo clientInfo = new ClientInfo(TestCredentials.CLIENT_ID,
        		new URI(TestCredentials.INSTANCE_URL),
        		new URI(TestCredentials.LOGIN_URL),
        		new URI(TestCredentials.IDENTITY_URL),
        		TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
        		TestCredentials.USER_ID, TestCredentials.ORG_ID, null, null);
        return new RestClient(clientInfo, authToken, httpAccess, null);
    }
    
}
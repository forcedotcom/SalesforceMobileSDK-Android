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
package com.salesforce.androidsdk.smartsync.manager;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.analytics.logger.SalesforceLogger;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.TestCredentials;
import com.salesforce.androidsdk.smartsync.TestForceApp;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SmartSyncLogger;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;

import java.net.HttpURLConnection;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Abstract super class for manager test classes.
 */
abstract public class ManagerTestCase {

	private static final String[] TEST_SCOPES = new String[] {"web"};
	private static final String TEST_CALLBACK_URL = "test://callback";
	private static final String TEST_AUTH_TOKEN = "test_auth_token";
    private static final String LID = "id"; // lower case id in create response

    protected Context targetContext;
    protected EventsListenerQueue eq;
    protected SmartSyncSDKManager sdkManager;
    protected SyncManager syncManager;
    protected SyncManager globalSyncManager;
    protected RestClient restClient;
    protected HttpAccess httpAccess;
    protected SmartStore smartStore;
    protected SmartStore globalSmartStore;
    protected String apiVersion;

    public void setUp() throws Exception {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        apiVersion = ApiVersionStrings.getVersionNumber(targetContext);
        final Application app = Instrumentation.newApplication(TestForceApp.class,
        		targetContext);
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app);
        TestCredentials.init(InstrumentationRegistry.getInstrumentation().getContext());
        eq = new EventsListenerQueue();
        if (SmartSyncSDKManager.getInstance() == null) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
        final LoginOptions loginOptions = new LoginOptions(TestCredentials.LOGIN_URL,
        		TEST_CALLBACK_URL, TestCredentials.CLIENT_ID, TEST_SCOPES);
        final ClientManager clientManager = new ClientManager(targetContext,
        		TestCredentials.ACCOUNT_TYPE, loginOptions, true);
        clientManager.createNewAccount(TestCredentials.ACCOUNT_NAME,
        		TestCredentials.USERNAME, TestCredentials.REFRESH_TOKEN,
        		TEST_AUTH_TOKEN, TestCredentials.INSTANCE_URL,
        		TestCredentials.LOGIN_URL, TestCredentials.IDENTITY_URL,
        		TestCredentials.CLIENT_ID, TestCredentials.ORG_ID,
        		TestCredentials.USER_ID, null, null, null,
                null, null, null, TestCredentials.PHOTO_URL, null, null);
    	SyncManager.reset();
    	sdkManager = SmartSyncSDKManager.getInstance();
        smartStore = sdkManager.getSmartStore();
        globalSmartStore = sdkManager.getGlobalSmartStore();
        syncManager = SyncManager.getInstance();
        globalSyncManager = SyncManager.getInstance(null, null, globalSmartStore);
        restClient = initRestClient();
        syncManager.setRestClient(restClient);
        SmartSyncLogger.setLogLevel(SalesforceLogger.Level.DEBUG);
    }

    public void tearDown() throws Exception {
        if (eq != null) {
            eq.tearDown();
            eq = null;
        }
    }

    private RestClient initRestClient() throws Exception {
        httpAccess = new HttpAccess(null, "dummy-agent");
        final TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess,
        		new URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID,
        		TestCredentials.REFRESH_TOKEN, null);
        final String authToken = refreshResponse.authToken;
        final ClientInfo clientInfo = new ClientInfo(new URI(TestCredentials.INSTANCE_URL),
        		new URI(TestCredentials.LOGIN_URL),
        		new URI(TestCredentials.IDENTITY_URL),
        		TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
        		TestCredentials.USER_ID, TestCredentials.ORG_ID, null, null,
                null, null, null, null, TestCredentials.PHOTO_URL, null, null);
        return new RestClient(clientInfo, authToken, httpAccess, null);
    }

    /**
     * Helper methods to create "count" of test records
     * @param count
     * @return map of id to name for the created records
     * @throws Exception
     */
    protected Map<String, String> createRecordsOnServer(int count, String objectType) throws Exception {
        Map<String, Map <String, Object>> idToFields = createRecordsOnServerReturnFields(count, objectType, null);
        Map<String, String> idToNames = new HashMap<>();
        for (String id : idToFields.keySet()) {
            idToNames.put(id, (String) idToFields.get(id).get(objectType == Constants.CONTACT ? Constants.LAST_NAME : Constants.NAME));
        }
        return idToNames;
    }

    /**
     * Helper methods to create "count" of test records
     * @param count
     * @param additionalFields
     * @return map of id to map of field name to field value for the created records
     * @throws Exception
     */
    protected Map<String, Map<String, Object>> createRecordsOnServerReturnFields(int count, String objectType, Map<String, Object> additionalFields) throws Exception {
        List<Map<String, Object>> listFields = buildFieldsMapForRecords(count, objectType, additionalFields);

        // Prepare request
        List<RestRequest> requests = new ArrayList<>();
        for (Map<String, Object> fields : listFields) {
            requests.add(RestRequest.getRequestForCreate(apiVersion, objectType, fields));
        }
        final RestRequest batchRequest = RestRequest.getBatchRequest(apiVersion, false, requests);

        // Go to server
        RestResponse response = restClient.sendSync(batchRequest);
        Assert.assertTrue("Creates failed", response.isSuccess() && !response.asJSONObject().getBoolean("hasErrors"));
        Map<String, Map <String, Object>> idToFields = new HashMap<>();
        JSONArray results = response.asJSONObject().getJSONArray("results");
        for (int i = 0; i< results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            Assert.assertEquals("Status should be HTTP_CREATED", HttpURLConnection.HTTP_CREATED, result.getInt("statusCode"));
            String id = result.getJSONObject("result").getString(LID);
            Map<String, Object> fields = listFields.get(i);

            idToFields.put(id, fields);
        }
        return idToFields;
    }

    /**
     * Helper method to build field name to field value maps
     *
     * @param count
     * @param objectType
     * @param additionalFields
     * @return
     */
    protected List<Map<String, Object>> buildFieldsMapForRecords(int count, String objectType, Map<String, Object> additionalFields) {
        List<Map <String, Object>> listFields = new ArrayList<>();
        for (int i = 0; i < count; i++) {

            // Request.
            String name = createRecordName(objectType);
            Map<String, Object> fields = new HashMap<>();

            // Add additional fields if any
            if (additionalFields != null) {
                fields.putAll(additionalFields);
            }

            //add more object type if need to support to use this API
            //to create a new record on server
            switch (objectType) {
                case Constants.ACCOUNT:
                    fields.put(Constants.NAME, name);
                    fields.put(Constants.DESCRIPTION, "Description_" + name);
                    break;
                case Constants.CONTACT:
                    fields.put(Constants.LAST_NAME, name);
                    break;
                case Constants.OPPORTUNITY:
                    fields.put(Constants.NAME, name);
                    fields.put("StageName", "Prospecting");
                    DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                    fields.put("CloseDate", formatter.format(new Date()));
                    break;
                default:
                    break;
            }
            listFields.add(fields);
        }
        return listFields;
    }

    /**
     * Delete records with given ids from server
     * @param ids
     * @throws Exception
     */
    protected void deleteRecordsOnServer(Collection<String> ids, String objectType) throws Exception {
        List<RestRequest> requests = new ArrayList<>();
        for (String id : ids) {
            requests.add(RestRequest.getRequestForDelete(apiVersion, objectType, id));
        }
        restClient.sendSync(RestRequest.getBatchRequest(apiVersion, false, requests));
    }

    /**
     * @return record name of the form SyncManagerTest<random number left-padded to be 8 digits long>
     */
    protected String createRecordName(String objectType) {
        return String.format(Locale.US, "ManagerTest_%s_%d", objectType, System.nanoTime());
    }

    /**
     * Update records on server
     * @param idToFieldsUpdated
     * @param sObjectType
     * @throws Exception
     */
    protected void updateRecordsOnServer(Map<String, Map<String, Object>> idToFieldsUpdated, String sObjectType) throws Exception {
        List<RestRequest> requests = new ArrayList<>();
        for (String id : idToFieldsUpdated.keySet()) {
            requests.add(RestRequest.getRequestForUpdate(apiVersion, sObjectType, id, idToFieldsUpdated.get(id)));
        }
        RestResponse response = restClient.sendSync(RestRequest.getBatchRequest(apiVersion, false, requests));
        Assert.assertTrue("Updates failed", response.isSuccess() && !response.asJSONObject().getBoolean("hasErrors"));
    }
}

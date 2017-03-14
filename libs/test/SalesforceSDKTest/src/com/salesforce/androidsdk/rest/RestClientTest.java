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
package com.salesforce.androidsdk.rest;

import android.test.InstrumentationTestCase;
import android.util.Log;

import com.salesforce.androidsdk.TestCredentials;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.RestClient.AuthTokenProvider;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
/**
 * Tests for RestClient
 *
 * Does live calls to a test org
 *
 */
public class RestClientTest extends InstrumentationTestCase {

    private static final String ENTITY_NAME_PREFIX = "RestClientTest";
    private static final String BAD_TOKEN = "bad-token";

    public static final String ACCOUNT = "account";
    public static final String LAST_MODIFIED_DATE = "LastModifiedDate";
    public static final String NAME = "Name";
    public static final String LNAME = "name";

    private ClientInfo clientInfo;
    private HttpAccess httpAccess;
    private RestClient restClient;
    private String authToken;
    private String instanceUrl;
    private List<String> testOauthKeys;
    private Map<String, String> testOauthValues;

    public static final String TEST_FIRST_NAME = "firstName";
    public static final String TEST_LAST_NAME = "lastName";
    public static final String TEST_DISPLAY_NAME = "displayName";
    public static final String TEST_EMAIL = "test@email.com";
    public static final String TEST_PHOTO_URL = "http://some.photo.url";
    public static final String TEST_THUMBNAIL_URL = "http://some.thumbnail.url";
    public static final String TEST_CUSTOM_KEY = "test_custom_key";
    public static final String TEST_CUSTOM_VALUE = "test_custom_value";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestCredentials.init(getInstrumentation().getContext());
        httpAccess = new HttpAccess(null, "dummy-agent");
        TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess, new URI(TestCredentials.INSTANCE_URL), TestCredentials.CLIENT_ID, TestCredentials.REFRESH_TOKEN);
        authToken = refreshResponse.authToken;
        instanceUrl = refreshResponse.instanceUrl;
        testOauthKeys = new ArrayList<>();
        testOauthKeys.add(TEST_CUSTOM_KEY);
        testOauthValues = new HashMap<>();
        testOauthValues.put(TEST_CUSTOM_KEY, TEST_CUSTOM_VALUE);
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(testOauthKeys);
        clientInfo = new ClientInfo(TestCredentials.CLIENT_ID,
        		new URI(TestCredentials.INSTANCE_URL),
        		new URI(TestCredentials.LOGIN_URL),
        		new URI(TestCredentials.IDENTITY_URL),
        		TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
        		TestCredentials.USER_ID, TestCredentials.ORG_ID, null, null,
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_DISPLAY_NAME, TEST_EMAIL, TEST_PHOTO_URL,
                TEST_THUMBNAIL_URL, testOauthValues);
        restClient = new RestClient(clientInfo, authToken, httpAccess, null);
    }

    @Override
    public void tearDown() throws Exception {
        cleanup();
        testOauthKeys = null;
        testOauthValues = null;
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(testOauthKeys);
    }

    /**
     * Testing getClientInfo
     * @throws URISyntaxException
     */
    public void testGetClientInfo() throws URISyntaxException {
        assertEquals("Wrong client id", TestCredentials.CLIENT_ID, restClient.getClientInfo().clientId);
        assertEquals("Wrong instance url", new URI(TestCredentials.INSTANCE_URL), restClient.getClientInfo().instanceUrl);
        assertEquals("Wrong login url", new URI(TestCredentials.LOGIN_URL), restClient.getClientInfo().loginUrl);
        assertEquals("Wrong account name", TestCredentials.ACCOUNT_NAME, restClient.getClientInfo().accountName);
        assertEquals("Wrong username", TestCredentials.USERNAME, restClient.getClientInfo().username);
        assertEquals("Wrong userId", TestCredentials.USER_ID, restClient.getClientInfo().userId);
        assertEquals("Wrong orgId", TestCredentials.ORG_ID, restClient.getClientInfo().orgId);
        assertEquals("Wrong firstName", TEST_FIRST_NAME, restClient.getClientInfo().firstName);
        assertEquals("Wrong lastName", TEST_LAST_NAME, restClient.getClientInfo().lastName);
        assertEquals("Wrong displayName", TEST_DISPLAY_NAME, restClient.getClientInfo().displayName);
        assertEquals("Wrong email", TEST_EMAIL, restClient.getClientInfo().email);
        assertEquals("Wrong photoUrl", TEST_PHOTO_URL, restClient.getClientInfo().photoUrl);
        assertEquals("Wrong thumbnailUrl", TEST_THUMBNAIL_URL, restClient.getClientInfo().thumbnailUrl);
        assertEquals("Wrong additional OAuth value", testOauthValues, restClient.getClientInfo().additionalOauthValues);
    }

    public void testClientInfoResolveUrl() {
    	assertEquals("Wrong url", TestCredentials.INSTANCE_URL + "/a/b/", clientInfo.resolveUrl("a/b/").toString());
    	assertEquals("Wrong url", TestCredentials.INSTANCE_URL + "/a/b/", clientInfo.resolveUrl("/a/b/").toString());
    }

    public void testClientInfoResolveUrlForHttpsUrl() {
        assertEquals("Wrong url", "https://testurl", clientInfo.resolveUrl("https://testurl").toString());
        assertEquals("Wrong url", "http://testurl", clientInfo.resolveUrl("http://testurl").toString());
        assertEquals("Wrong url", "HTTPS://testurl", clientInfo.resolveUrl("HTTPS://testurl").toString());
        assertEquals("Wrong url", "HTTP://testurl", clientInfo.resolveUrl("HTTP://testurl").toString());
    }

    public void testClientInfoResolveUrlForCommunityUrl() throws Exception {
        final ClientInfo info = new ClientInfo(TestCredentials.CLIENT_ID,
        		new URI(TestCredentials.INSTANCE_URL),
        		new URI(TestCredentials.LOGIN_URL),
        		new URI(TestCredentials.IDENTITY_URL),
        		TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
        		TestCredentials.USER_ID, TestCredentials.ORG_ID, null,
        		TestCredentials.COMMUNITY_URL, null, null, null, null, null, null, testOauthValues);
    	assertEquals("Wrong url", TestCredentials.COMMUNITY_URL + "/a/b/", info.resolveUrl("a/b/").toString());
    	assertEquals("Wrong url", TestCredentials.COMMUNITY_URL + "/a/b/", info.resolveUrl("/a/b/").toString());
    }

    public void testGetInstanceUrlForCommunity() throws Exception {
        final ClientInfo info = new ClientInfo(TestCredentials.CLIENT_ID,
        		new URI(TestCredentials.INSTANCE_URL),
        		new URI(TestCredentials.LOGIN_URL),
        		new URI(TestCredentials.IDENTITY_URL),
        		TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
        		TestCredentials.USER_ID, TestCredentials.ORG_ID, null,
        		TestCredentials.COMMUNITY_URL, null, null, null, null, null, null, testOauthValues);
        assertEquals("Wrong url", TestCredentials.COMMUNITY_URL, info.getInstanceUrlAsString());
    }

    public void testGetInstanceUrl() {
        assertEquals("Wrong url", TestCredentials.INSTANCE_URL, clientInfo.getInstanceUrlAsString());
    }

    /**
     * Testing getAuthToken
     */
    public void testGetAuthToken() {
        assertEquals("Wrong auth token", authToken, restClient.getAuthToken());
    }

    /**
     * Testing a call with a bad auth token when restClient has no token provider
     * Expect a 401.
     * @throws URISyntaxException
     * @throws IOException
     */
    public void testCallWithBadAuthToken() throws URISyntaxException, IOException {
        RestClient.clearOkClientsCache();
        RestClient unauthenticatedRestClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, null);
        RestResponse response = unauthenticatedRestClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        assertFalse("Expected error", response.isSuccess());
        checkResponse(response, HttpURLConnection.HTTP_UNAUTHORIZED, true);
    }

    /**
     * Testing a call with a bad auth token when restClient has a token provider
     * Expect token provider to be invoked and new token to be used.
     * @throws URISyntaxException
     * @throws IOException
     */
    public void testCallWithBadTokenAndTokenProvider() throws URISyntaxException, IOException {
        RestClient.clearOkClientsCache();
        AuthTokenProvider authTokenProvider = new AuthTokenProvider() {
            @Override
            public String getNewAuthToken() {
                return authToken;
            }

            @Override
            public String getRefreshToken() {
                return null;
            }

            @Override
            public long getLastRefreshTime() {
                return -1;
            }

            @Override
            public String getInstanceUrl() { return instanceUrl; }
        };
        RestClient unauthenticatedRestClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, authTokenProvider);
        assertEquals("RestClient should be using the bad token initially", BAD_TOKEN, unauthenticatedRestClient.getAuthToken());
        RestResponse response = unauthenticatedRestClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        assertEquals("RestClient should now be using the good token", authToken, unauthenticatedRestClient.getAuthToken());
        assertTrue("Expected success", response.isSuccess());
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
    }

    /**
     * Testing a call with a bad auth token when restClient has a token provider
     * Expect token provider to be invoked and new token to be used and a new instance url to be returned.
     * @throws URISyntaxException
     * @throws IOException
     */
    public void testCallWithBadInstanceUrl() throws URISyntaxException, IOException {
        RestClient.clearOkClientsCache();
        AuthTokenProvider authTokenProvider = new AuthTokenProvider() {
            @Override
            public String getNewAuthToken() {
                return authToken;
            }

            @Override
            public String getRefreshToken() {
                return null;
            }

            @Override
            public long getLastRefreshTime() {
                return -1;
            }

            @Override
            public String getInstanceUrl() { return instanceUrl; }
        };
        RestClient unauthenticatedRestClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, authTokenProvider);
        assertEquals("RestClient has bad instance url", new URI(TestCredentials.INSTANCE_URL), unauthenticatedRestClient.getClientInfo().instanceUrl);
        RestResponse response = unauthenticatedRestClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        assertEquals("RestClient should now have the correct instance url", new URI(instanceUrl), unauthenticatedRestClient.getClientInfo().instanceUrl);
        assertTrue("Expected success", response.isSuccess());
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
    }


    /**
     * Testing a get versions call to the server - check response
     * @throws Exception
     */
    public void testGetVersions() throws Exception {
        // We don't need to be authenticated
        RestClient unauthenticatedRestClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, null);
        RestResponse response = unauthenticatedRestClient.sendSync(RestRequest.getRequestForVersions());
        checkResponse(response, HttpURLConnection.HTTP_OK, true);
        checkKeys(response.asJSONArray().getJSONObject(0), "label", "url", "version");
    }

    /**
     * Testing a get resources call to the server - check response
     * @throws Exception
     */
    public void testGetResources() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        checkKeys(response.asJSONObject(), "sobjects", "search", "recent");
    }

    /**
     * Testing a get resources async call to the server - check response
     * @throws Exception
     */
    public void testGetResourcesAsync() throws Exception {
        RestResponse response = sendAsync(restClient, RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        checkKeys(response.asJSONObject(), "sobjects", "search", "recent");
    }

    /**
     * Testing a describe global call to the server - check response
     * @throws Exception
     */
    public void testDescribeGlobal() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForDescribeGlobal(TestCredentials.API_VERSION));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "encoding", "maxBatchSize", "sobjects");
        checkKeys(jsonResponse.getJSONArray("sobjects").getJSONObject(0), LNAME, "label", "custom", "keyPrefix");
    }

    /**
     * Testing a describe global async call to the server - check response
     * @throws Exception
     */
    public void testDescribeGlobalAsync() throws Exception {
        RestResponse response = sendAsync(restClient, RestRequest.getRequestForDescribeGlobal(TestCredentials.API_VERSION));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "encoding", "maxBatchSize", "sobjects");
        checkKeys(jsonResponse.getJSONArray("sobjects").getJSONObject(0), LNAME, "label", "custom", "keyPrefix");
    }

    /**
     * Testing a metadata call to the server - check response
     * @throws Exception
     */
    public void testMetadata() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForMetadata(TestCredentials.API_VERSION, ACCOUNT));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "objectDescribe", "recentItems");
        checkKeys(jsonResponse.getJSONObject("objectDescribe"), LNAME, "label", "keyPrefix");
        assertEquals("Wrong object name", "Account", jsonResponse.getJSONObject("objectDescribe").getString(LNAME));
    }

    /**
     * Testing a describe call to the server - check response
     * @throws Exception
     */
    public void testDescribe() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForDescribe(TestCredentials.API_VERSION, ACCOUNT));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, LNAME, "fields", "urls", "label");
        assertEquals("Wrong object name", "Account", jsonResponse.getString(LNAME));
    }

    /**
     * Testing a create call to the server - check response
     * @throws Exception
     */
    public void testCreate() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        String newAccountName = ENTITY_NAME_PREFIX + System.nanoTime();
        fields.put(NAME, newAccountName);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForCreate(TestCredentials.API_VERSION, ACCOUNT, fields));
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "id", "errors", "success");
        assertTrue("Create failed", jsonResponse.getBoolean("success"));
    }

    /**
     * Testing a retrieve call to the server.
     * Create new account then retrieve it.
     * @throws Exception
     */
    public void testRetrieve() throws Exception {
        List<String> fields = Arrays.asList(new String[] {NAME, "ownerId"});
        IdName newAccountIdName = createAccount();
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "attributes", NAME, "OwnerId", "Id");
        assertEquals("Wrong row returned", newAccountIdName.name, jsonResponse.getString(NAME));
    }

    /**
     * Testing an update call to the server.
     * Create new account then update it then get it back
     * @throws Exception
     */
    public void testUpdate() throws Exception {

        // Create
        IdName newAccountIdName = createAccount();

        // Update
        Map<String, Object> fields = new HashMap<String, Object>();
        String updatedAccountName = ENTITY_NAME_PREFIX + "-" + System.nanoTime();
        fields.put(NAME, updatedAccountName);
        RestResponse updateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields));
        assertTrue("Update failed", updateResponse.isSuccess());

        // Retrieve - expect updated name
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{NAME})));
        assertEquals("Wrong row returned", updatedAccountName, response.asJSONObject().getString(NAME));
    }

    /**
     * Testing update calls to the server with if-unmodified-since.
     * Create new account,
     * then update it with created date for unmodified since date (should update)
     * then update it again with created date for unmodified since date (should not update)
     * @throws Exception
     */
    public void testUpdateWithIfUnmodifiedSince() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        Date pastDate = new Date(new Date().getTime() - 3600*1000); // an hour ago

        // Create
        IdName newAccountIdName = createAccount();
        String originalName = newAccountIdName.name;

        // Retrieve to get created date
        RestResponse retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{LAST_MODIFIED_DATE})));
        Date createdDate = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")).parse(retrieveResponse.asJSONObject().getString(LAST_MODIFIED_DATE));

        // Wait a bit
        Thread.sleep(1000);

        // Update with if-unmodified-since with createdDate - should update
        String updatedName = originalName + "_upd";
        fields.put(NAME, updatedName);
        RestResponse updateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields, createdDate));
        assertTrue("Update failed", updateResponse.isSuccess());

        // Retrieve - expect updated name
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{NAME})));
        assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME));

        // Second update with if-unmodified-since with created date - should not update
        String blockedUpdatedName = originalName + "_blocked_upd";
        fields.put(NAME, blockedUpdatedName);
        RestResponse blockedUpdateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields, createdDate));
        assertEquals("Expected 412", HttpURLConnection.HTTP_PRECON_FAILED, blockedUpdateResponse.getStatusCode());

        // Retrieve - expect name from first update
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{NAME})));
        assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME));

    }

    /**
     * Testing a delete call to the server.
     * Create new account then delete it then try to retrieve it again (expect 404).
     * @throws Exception
     */
    public void testDelete() throws Exception {

        // Create
        IdName newAccountIdName = createAccount();

        // Delete
        RestResponse deleteResponse = restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id));
        assertTrue("Delete failed", deleteResponse.isSuccess());

        // Retrieve - expect 404
        List<String> fields = Arrays.asList(new String[] {NAME});
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields));
        assertEquals("404 was expected", HttpURLConnection.HTTP_NOT_FOUND, response.getStatusCode());
    }

    /**
     * Testing delete calls to the server with if-unmodified-since.
     * Create new account
     * Update it
     * then delete it with created date for unmodified since date (should not delete)
     * then delete it with last modified date unmodified since date (should delete)
     * @throws Exception
     */
    public void testDeleteWithIfUnmodifiedSince() throws Exception {
        Map<String, Object> fields = new HashMap<>();

        // Create
        IdName newAccountIdName = createAccount();
        String originalName = newAccountIdName.name;

        // Retrieve to get created date
        RestResponse retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{LAST_MODIFIED_DATE})));
        Date createdDate = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")).parse(retrieveResponse.asJSONObject().getString(LAST_MODIFIED_DATE));

        // Wait a bit
        Thread.sleep(1000);

        // Update
        String updatedName = originalName + "_upd";
        fields.put(NAME, updatedName);
        RestResponse updateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields));
        assertTrue("Update failed", updateResponse.isSuccess());

        // Retrieve - expect updated name
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{NAME, LAST_MODIFIED_DATE})));
        assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME));
        Date lastModifiedDate = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")).parse(retrieveResponse.asJSONObject().getString(LAST_MODIFIED_DATE));

        // Delete with if-unmodified-since with created date - should not delete
        RestResponse blockedDeleteResponse = restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, createdDate));
        assertFalse("Delete should have failed", blockedDeleteResponse.isSuccess());

        Log.i("--response-->", blockedDeleteResponse.asJSONArray().toString(2));

        assertEquals("Expected 412", HttpURLConnection.HTTP_PRECON_FAILED, blockedDeleteResponse.getStatusCode());

        // Retrieve - expect success
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{NAME})));
        assertTrue("Retrieve should have succeeded", retrieveResponse.isSuccess());
        assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME));

        // Delete with if-unmodified-since with created date - should delete
        RestResponse deleteResponse = restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, lastModifiedDate));
        assertTrue("Delete should have succeeded", deleteResponse.isSuccess());

        // Retrieve - expect 404
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{NAME})));
        assertFalse("Retrieve should have failed", retrieveResponse.isSuccess());
        assertEquals("Expected 404", HttpURLConnection.HTTP_NOT_FOUND, retrieveResponse.getStatusCode());
    }
    /**
     * Testing a query call to the server.
     * Create new account then look for it using soql.
     * @throws Exception
     */
    public void testQuery() throws Exception {
        IdName newAccountIdName = createAccount();
        RestResponse response = restClient.sendSync(RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select name from account where id = '" + newAccountIdName.id + "'"));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "done", "totalSize", "records");
        assertEquals("Expected one row", 1, jsonResponse.getInt("totalSize"));
        assertEquals("Wrong row returned", newAccountIdName.name, jsonResponse.getJSONArray("records").getJSONObject(0).get(NAME));
    }

    /**
     * Testing a search call to the server.
     * Create new account then look for it using sosl.
     * @throws Exception
     */
    public void testSearch() throws Exception {
        IdName idNameFirstAccount = createAccount();
        IdName idNameSecondAccount = createAccount();
        RestResponse response = restClient.sendSync(RestRequest.getRequestForSearch(TestCredentials.API_VERSION, "find {" + ENTITY_NAME_PREFIX + "}"));

        JSONArray jsonResults = response.asJSONObject().getJSONArray("searchRecords");
        assertEquals("Two results expected", 2, jsonResults.length());
        HashSet<Object> idsFromSearch = new HashSet<>(JSONObjectHelper.pluck(jsonResults, "Id"));
        assertEquals("wrong number of results for search request", 2, idsFromSearch.size());
        assertTrue("Account id not returned by search", idsFromSearch.contains(idNameFirstAccount.id));
        assertTrue("Contact id not returned by search", idsFromSearch.contains(idNameSecondAccount.id));
    }

    /**
     * Testing that calling resume more than once on a RestResponse doesn't throw an exception
     * @throws Exception 
     */
    public void testDoubleConsume() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForMetadata(TestCredentials.API_VERSION, ACCOUNT));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        try {
        	response.consume();
        	response.consume();
        }
        catch (IllegalStateException e) {
        	fail("Calling consume should not have thrown an exception");
        }
    }
    
    /**
     * Testing doing a sync request against a non salesforce public api with a RestClient that uses an UnauthenticatedClientInfo
     * @return
     * @throws Exception
     */
    public void testRestClientUnauthenticatedlientInfo() throws Exception {
        RestClient unauthenticatedRestClient = new RestClient(new RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT, null);
        RestRequest request = new RestRequest(RestMethod.GET, "https://api.spotify.com/v1/search?q=James%20Brown&type=artist");
        RestResponse response = unauthenticatedRestClient.sendSync(request);
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "artists");
        checkKeys(jsonResponse.getJSONObject("artists"), "href", "items", "limit", "next", "offset", "previous", "total");
    }

    /**
     * Testing doing an async request against a non salesforce public api with a RestClient that uses an UnauthenticatedClientInfo
     * @return
     * @throws Exception
     */
    public void testRestClientUnauthenticatedlientInfoAsync() throws Exception {
        RestClient unauthenticatedRestClient = new RestClient(new RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT, null);
        RestRequest request = new RestRequest(RestMethod.GET, "https://api.spotify.com/v1/search?q=James%20Brown&type=artist");
        RestResponse response = sendAsync(unauthenticatedRestClient, request);
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "artists");
        checkKeys(jsonResponse.getJSONObject("artists"), "href", "items", "limit", "next", "offset", "previous", "total");
    }

    /**
     * Tests if a stream from {@link RestResponse#asInputStream()} is readable.
     *
     * @throws Exception
     */
    public void testResponseStreamIsReadable() throws Exception {
        final RestResponse response = getStreamTestResponse();

        try {
            InputStream in = response.asInputStream();
            assertStreamTestResponseStreamIsValid(in);
        } catch (IOException e) {
            fail("The InputStream should be readable and an IOException should not have been thrown");
        } catch (JSONException e) {
            fail("Valid JSON data should have been returned");
        } finally {
            response.consumeQuietly();
        }
    }

    /**
     * Tests if a stream from {@link RestResponse#asInputStream()} is consumed (according to the REST client) by fully reading the stream.
     *
     * @throws Exception
     */
    public void testResponseStreamConsumedByReadingStream() throws Exception {
        final RestResponse response = getStreamTestResponse();

        try {
            InputStream in = response.asInputStream();
            inputStreamToString(in);
        } catch (IOException e) {
            fail("The InputStream should be readable and an IOException should not have been thrown");
        }

        // We read the entire stream but forgot to call consume() or consumeQuietly() - can another REST call be made?
        final RestResponse anotherResponse = getStreamTestResponse();
        assertNotNull(anotherResponse);
    }

    /**
     * Tests that a stream from {@link RestResponse#asInputStream()} cannot be read from twice.
     *
     * @throws Exception
     */
    public void testResponseStreamCannotBeReadTwice() throws Exception {
        final RestResponse response = getStreamTestResponse();

        try {
            final InputStream in = response.asInputStream();
            inputStreamToString(in);
        } catch (IOException e) {
            fail("The InputStream should be readable and an IOException should not have been thrown");
        }

        try {
            response.asInputStream();
            fail("An IOException should have been thrown while trying to read the InputStream a second time");
        } catch (IOException e) {
            // Expected
        } finally {
            response.consumeQuietly();
        }
    }

    /**
     * Tests that {@link RestResponse}'s accessor methods (like {@link RestResponse#asBytes()} do not return valid data if the response is streamed first.
     *
     * @throws Exception
     */
    public void testOtherAccessorsNotAvailableAfterResponseStreaming() throws Exception {
        final RestResponse response = getStreamTestResponse();

        final Runnable testAccessorsNotAccessible = new Runnable() {
            @Override
            public void run() {
                try {
                    // The other accessors should not return valid data as soon as the stream is opened
                    assertNotNull(response.asBytes());
                    assertEquals("asBytes() array should be empty", 0, response.asBytes().length);
                    assertEquals("asString() should return the empty string", "", response.asString());

                    try {
                        assertNull(response.asJSONObject());
                        fail("asJSONObject() should fail");
                    } catch (JSONException e) {
                        // Expected
                    }

                    try {
                        assertNull(response.asJSONArray());
                        fail("asJSONArray() should fail");
                    } catch (JSONException e) {
                        // Expected
                    }
                } catch (IOException e) {
                    fail("IOException not expected");
                }
            }
        };

        try {
            response.asInputStream();
            testAccessorsNotAccessible.run();
        } catch (IOException e) {
            fail("The InputStream should be readable and an IOException should not have been thrown");
        } finally {
            response.consumeQuietly();
        }

        // Ensure that consuming the stream doesn't make the accessors accessible again
        testAccessorsNotAccessible.run();
    }

    /**
     * Tests that any call to {@link RestResponse}'s accessor methods prevent the response data from being streamed via {@link RestResponse#asInputStream()}.
     *
     * @throws Exception
     */
    public void testAccessorMethodsPreventResponseStreaming() throws Exception {
        final RestResponse response = getStreamTestResponse();
        response.asBytes();

        try {
            response.asInputStream();
            fail("The InputStream should not be readable after an accessor method is called");
        } catch (IOException e) {
            // Expected
        } finally {
            response.consumeQuietly();
        }
    }

    /**
     * Test for batch request
     *
     * Run a batch request that:
     * - creates an account,
     * - creates a contact,
     * - run a query that should return newly created account
     * - run a query that should return newly created contact
     *
     * @throws IOException
     * @throws JSONException
     */
    public void testBatchRequest() throws IOException, JSONException {
        Map<String, Object> accountFields = new HashMap<String, Object>();
        String accountName = ENTITY_NAME_PREFIX + System.nanoTime();
        accountFields.put(NAME, accountName);
        RestRequest firstRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION, ACCOUNT, accountFields);

        Map<String, Object> contactFields = new HashMap<String, Object>();
        String contactName = ENTITY_NAME_PREFIX + System.nanoTime();
        contactFields.put("LastName", contactName);
        RestRequest secondRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION, "contact", contactFields);

        RestRequest thirdRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select Id from Account where Name = '" + accountName + "'");
        RestRequest fourthRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select Id from Contact where Name = '" + contactName + "'");


        // Build batch request
        RestRequest batchRequest = RestRequest.getBatchRequest(TestCredentials.API_VERSION, false, Arrays.asList(new RestRequest[]{firstRequest, secondRequest, thirdRequest, fourthRequest}));

        // Send batch request
        RestResponse response = restClient.sendSync(batchRequest);

        // Checking response
        JSONObject jsonResponse = response.asJSONObject();

        checkKeys(jsonResponse, "hasErrors", "results");
        assertFalse("Batch had errors", jsonResponse.getBoolean("hasErrors"));
        JSONArray jsonResults = jsonResponse.getJSONArray("results");
        assertEquals("Wrong number of results", 4, jsonResults.length());
        assertEquals("Wrong status for first request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(0).getInt("statusCode"));
        assertEquals("Wrong status for second request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(1).getInt("statusCode"));
        assertEquals("Wrong status for third request", HttpURLConnection.HTTP_OK, jsonResults.getJSONObject(2).getInt("statusCode"));
        assertEquals("Wrong status for fourth request", HttpURLConnection.HTTP_OK, jsonResults.getJSONObject(3).getInt("statusCode"));

        // Queries should have returned ids of newly created account and contact
        String accountId =  jsonResults.getJSONObject(0).getJSONObject("result").getString("id");
        String contactId =  jsonResults.getJSONObject(1).getJSONObject("result").getString("id");
        String idFromFirstQuery = jsonResults.getJSONObject(2).getJSONObject("result").getJSONArray("records").getJSONObject(0).getString("Id");
        String idFromSecondQuery = jsonResults.getJSONObject(3).getJSONObject("result").getJSONArray("records").getJSONObject(0).getString("Id");
        assertEquals("Account id not returned by query", accountId, idFromFirstQuery);
        assertEquals("Contact id not returned by query", contactId, idFromSecondQuery);
    }

    /**
     * Test for composite request
     *
     * Run a composite request that:
     * - creates an account,
     * - creates a contact (with newly created account as parent),
     * - run a query that should return newly created account and contact
     *
     * @throws IOException
     * @throws JSONException
     */
    public void testCompositeRequest() throws IOException, JSONException {
        Map<String, Object> accountFields = new HashMap<String, Object>();
        String accountName = ENTITY_NAME_PREFIX + System.nanoTime();
        accountFields.put(NAME, accountName);
        RestRequest firstRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION, ACCOUNT, accountFields);

        Map<String, Object> contactFields = new HashMap<String, Object>();
        String contactName = ENTITY_NAME_PREFIX + System.nanoTime();
        contactFields.put("LastName", contactName);
        contactFields.put("AccountId", "@{refAccount.id}");
        RestRequest secondRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION, "contact", contactFields);

        RestRequest thirdRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select Id, AccountId from Contact where LastName = '" + contactName + "'");

        LinkedHashMap<String, RestRequest> refIdToRequests = new LinkedHashMap<>();
        refIdToRequests.put("refAccount", firstRequest);
        refIdToRequests.put("refContact", secondRequest);
        refIdToRequests.put("refSearch", thirdRequest);

        // Build composite request
        RestRequest compositeRequest = RestRequest.getCompositeRequest(TestCredentials.API_VERSION, false, refIdToRequests);

        // Send composite request
        RestResponse response = restClient.sendSync(compositeRequest);

        // Checking response
        JSONObject jsonResponse = response.asJSONObject();

        JSONArray jsonResults = jsonResponse.getJSONArray("compositeResponse");
        assertEquals("Wrong number of results", 3, jsonResults.length());
        assertEquals("Wrong status for first request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(0).getInt("httpStatusCode"));
        assertEquals("Wrong status for second request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(1).getInt("httpStatusCode"));
        assertEquals("Wrong status for third request", HttpURLConnection.HTTP_OK, jsonResults.getJSONObject(2).getInt("httpStatusCode"));

        // Query should have returned ids of newly created account and contact
        String accountId =  jsonResults.getJSONObject(0).getJSONObject("body").getString("id");
        String contactId =  jsonResults.getJSONObject(1).getJSONObject("body").getString("id");
        JSONArray queryRecords = jsonResults.getJSONObject(2).getJSONObject("body").getJSONArray("records");
        assertEquals("wrong number of results for query request", 1, queryRecords.length());
        assertEquals("Account id not returned by query", accountId, queryRecords.getJSONObject(0).getString("AccountId"));
        assertEquals("Contact id not returned by query", contactId, queryRecords.getJSONObject(0).getString("Id"));
    }

    /**
     * Test for sobject tree request
     *
     * Run a sobject tree request that:
     * - creates an account,
     * - creates two children contacts
     *
     * Then queries that should return newly created account and contacts
     *
     * @throws IOException
     * @throws JSONException
     */
    public void testSObjectTreeRequest() throws IOException, JSONException {
        Map<String, Object> accountFields = new HashMap<String, Object>();
        String accountName = ENTITY_NAME_PREFIX + System.nanoTime();
        accountFields.put(NAME, accountName);

        Map<String, Object> contactFields = new HashMap<String, Object>();
        String contactName = ENTITY_NAME_PREFIX + System.nanoTime();
        contactFields.put("LastName", contactName);

        Map<String, Object> otherContactFields = new HashMap<String, Object>();
        String otherContactName = ENTITY_NAME_PREFIX + System.nanoTime();
        otherContactFields.put("LastName", otherContactName);

        List<RestRequest.SObjectTree> childrenTrees = new ArrayList<>();
        childrenTrees.add(new RestRequest.SObjectTree("contact", "Contacts", "refContact", contactFields, null));
        childrenTrees.add(new RestRequest.SObjectTree("contact", "Contacts", "refOtherContact", otherContactFields, null));

        List<RestRequest.SObjectTree> recordTrees = new ArrayList<>();
        recordTrees.add(new RestRequest.SObjectTree(ACCOUNT, null, "refAccount", accountFields, childrenTrees));

        // Build sobject tree request
        RestRequest sobjectTreeRequest = RestRequest.getRequestForSObjectTree(TestCredentials.API_VERSION, ACCOUNT, recordTrees);

        // Send sobject tree request
        RestResponse response = restClient.sendSync(sobjectTreeRequest);

        // Checking response
        JSONObject jsonResponse = response.asJSONObject();

        checkKeys(jsonResponse, "hasErrors", "results");
        assertFalse("SObject tree request had errors", jsonResponse.getBoolean("hasErrors"));

        JSONArray jsonResults = jsonResponse.getJSONArray("results");
        assertEquals("Wrong number of results", 3, jsonResults.length());
        String accountId =  jsonResults.getJSONObject(0).getString("id");
        String contactId =  jsonResults.getJSONObject(1).getString("id");
        String otherContactId =  jsonResults.getJSONObject(2).getString("id");

        // Running query that should match first contact and its parent
        RestRequest queryRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select Id, AccountId from Contact where LastName = '" + contactName + "'");
        RestResponse queryResponse = restClient.sendSync(queryRequest);
        JSONArray queryRecords = queryResponse.asJSONObject().getJSONArray("records");
        assertEquals("wrong number of results for query request", 1, queryRecords.length());
        assertEquals("Account id not returned by query", accountId, queryRecords.getJSONObject(0).getString("AccountId"));
        assertEquals("Contact id not returned by query", contactId, queryRecords.getJSONObject(0).getString("Id"));

        // Running other query that should match first contact and its parent
        RestRequest otherQueryRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select Id, AccountId from Contact where LastName = '" + otherContactName + "'");
        RestResponse otherQueryResponse = restClient.sendSync(otherQueryRequest);
        JSONArray otherQueryRecords = otherQueryResponse.asJSONObject().getJSONArray("records");
        assertEquals("wrong number of results for query request", 1, otherQueryRecords.length());
        assertEquals("Account id not returned by query", accountId, otherQueryRecords.getJSONObject(0).getString("AccountId"));
        assertEquals("Contact id not returned by query", otherContactId, otherQueryRecords.getJSONObject(0).getString("Id"));
    }

    //
    // Helper methods
    //

    /**
     * @return a {@link RestResponse} for testing streaming. It should contain some JSON data.
     * @throws IOException if the response could not be made
     */
    private RestResponse getStreamTestResponse() throws IOException {
        final RestResponse response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        assertEquals("Response code should be HTTP OK", response.getStatusCode(), HttpURLConnection.HTTP_OK);
        return response;
    }

    /**
     * Assert that the {@link RestResponse} returned from {@link #getStreamTestResponse()} is valid.
     * @param in the {@link InputStream} of response data
     * @throws IOException if the stream could not be read
     * @throws JSONException if the response could not be decoded to a valid JSON object
     */
    private void assertStreamTestResponseStreamIsValid(InputStream in) throws IOException, JSONException {
        final String responseData = inputStreamToString(in);
        assertNotNull("The response should contain data", responseData);

        final JSONObject responseJson = new JSONObject(responseData);
        checkKeys(responseJson, "sobjects", "search", "recent");
    }

    private String inputStreamToString(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;

        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }

        return builder.toString();
    }

    /**
     * Send request using sendAsync method
     * @param client
     * @param request
     * @return
     * @throws InterruptedException
     */
    private RestResponse sendAsync(RestClient client, RestRequest request) throws InterruptedException {
        final BlockingQueue<RestResponse> responseBlockingQueue = new ArrayBlockingQueue<>(1);
        client.sendAsync(request, new RestClient.AsyncRequestCallback() {
            @Override
            public void onSuccess(RestRequest request, RestResponse response) {
                responseBlockingQueue.add(response);
            }

            @Override
            public void onError(Exception exception) {
                responseBlockingQueue.add(null);
            }
        });
        return responseBlockingQueue.poll(30, TimeUnit.SECONDS);
    }

    /**
     * Helper method to create a account with a unique name and returns its name and id
     */
    private IdName createAccount() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        String newAccountName = ENTITY_NAME_PREFIX + "-" + System.nanoTime();
        fields.put(NAME, newAccountName);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForCreate(TestCredentials.API_VERSION, ACCOUNT, fields));
        String newAccountId = response.asJSONObject().getString("id");
        return new IdName(newAccountId, newAccountName);
    }

    /**
     * Helper method to delete any entities created by one of the test
     */
    private void cleanup() {
        try {
            RestResponse response = restClient.sendSync(RestRequest.getRequestForSearch(TestCredentials.API_VERSION, "find {" + ENTITY_NAME_PREFIX + "}"));
            JSONArray jsonResults = response.asJSONObject().getJSONArray("searchRecords");
            for (int i = 0; i < jsonResults.length(); i++) {
                JSONObject jsonResult = jsonResults.getJSONObject(i);
                String objectType = jsonResult.getJSONObject("attributes").getString("type");
                String id = jsonResult.getString("Id");
                restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, objectType, id));
            }
        }
        catch(Exception e) {
            // We tried our best :-(
        }
    }

    /**
     * Helper method to validate responses
     * @param response
     * @param expectedStatusCode
     */
    private void checkResponse(RestResponse response, int expectedStatusCode, boolean isJsonArray) {
        // Check status code
        assertEquals(expectedStatusCode  + " response expected", expectedStatusCode, response.getStatusCode());

        // Try to parse as json
        try {
            if (isJsonArray) {
                response.asJSONArray();
            }
            else {
                response.asJSONObject();
            }
        }
        catch (Exception e) {
            fail("Failed to parse response body");
            e.printStackTrace();
        }
    }

    /**
     * Helper method to check if a jsonObject has all the expected keys
     * @param jsonObject
     * @param expectedKeys
     */
    private void checkKeys(JSONObject jsonObject, String... expectedKeys) {
        for (String expectedKey : expectedKeys) {
            assertTrue("Object should have key: " + expectedKey, jsonObject.has(expectedKey));
        }
    }

    /**
     * Helper class to hold name and id
     */
    private static class IdName {
        public final String id;
        public final String name;

        public IdName(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}

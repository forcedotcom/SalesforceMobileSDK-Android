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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.RestClient.AuthTokenProvider;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.util.test.TestCredentials;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Tests for RestClient
 *
 * Does live calls to a test org
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class RestClientTest {

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
    public static final String TEST_THUMBNAIL_URL = "http://some.thumbnail.url";
    public static final String TEST_CUSTOM_KEY = "test_custom_key";
    public static final String TEST_CUSTOM_VALUE = "test_custom_value";

    @Before
    public void setUp() throws Exception {
        TestCredentials.init(InstrumentationRegistry.getInstrumentation().getContext());
        httpAccess = new HttpAccess(null, "dummy-agent");
        TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess,
                new URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID,
                TestCredentials.REFRESH_TOKEN, null);
        authToken = refreshResponse.authToken;
        instanceUrl = refreshResponse.instanceUrl;
        testOauthKeys = new ArrayList<>();
        testOauthKeys.add(TEST_CUSTOM_KEY);
        testOauthValues = new HashMap<>();
        testOauthValues.put(TEST_CUSTOM_KEY, TEST_CUSTOM_VALUE);
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(testOauthKeys);
        clientInfo = new ClientInfo(new URI(TestCredentials.INSTANCE_URL),
        		new URI(TestCredentials.LOGIN_URL),
        		new URI(TestCredentials.IDENTITY_URL),
        		TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
        		TestCredentials.USER_ID, TestCredentials.ORG_ID, null, null,
                TEST_FIRST_NAME, TEST_LAST_NAME, TEST_DISPLAY_NAME, TEST_EMAIL, TestCredentials.PHOTO_URL,
                TEST_THUMBNAIL_URL, testOauthValues, null, null, null,
                null, null, null, null);
        restClient = new RestClient(clientInfo, authToken, httpAccess, null);
    }

    @After
    public void tearDown() throws Exception {
        cleanup();
        testOauthKeys = null;
        testOauthValues = null;
        SalesforceSDKManager.getInstance().setAdditionalOauthKeys(testOauthKeys);
    }

    @Test
    public void testNewWebSocket() {
        final RestRequest restRequest = new RestRequest(
                RestMethod.GET,
                RestRequest.RestEndpoint.LOGIN,
                "/a",
                (JSONObject) null,
                null);

        final WebSocket webSocket = restClient.newWebSocket(
                restClient.buildRequest(restRequest),
                new WebSocketListener() {
                });

        Assert.assertNotNull(webSocket);
    }

    /**
     * Testing getClientInfo
     * @throws URISyntaxException
     */
    @Test
    public void testGetClientInfo() throws URISyntaxException {
        Assert.assertEquals("Wrong instance url", new URI(TestCredentials.INSTANCE_URL), restClient.getClientInfo().instanceUrl);
        Assert.assertEquals("Wrong login url", new URI(TestCredentials.LOGIN_URL), restClient.getClientInfo().loginUrl);
        Assert.assertEquals("Wrong account name", TestCredentials.ACCOUNT_NAME, restClient.getClientInfo().accountName);
        Assert.assertEquals("Wrong username", TestCredentials.USERNAME, restClient.getClientInfo().username);
        Assert.assertEquals("Wrong userId", TestCredentials.USER_ID, restClient.getClientInfo().userId);
        Assert.assertEquals("Wrong orgId", TestCredentials.ORG_ID, restClient.getClientInfo().orgId);
        Assert.assertEquals("Wrong firstName", TEST_FIRST_NAME, restClient.getClientInfo().firstName);
        Assert.assertEquals("Wrong lastName", TEST_LAST_NAME, restClient.getClientInfo().lastName);
        Assert.assertEquals("Wrong displayName", TEST_DISPLAY_NAME, restClient.getClientInfo().displayName);
        Assert.assertEquals("Wrong email", TEST_EMAIL, restClient.getClientInfo().email);
        Assert.assertEquals("Wrong photoUrl", TestCredentials.PHOTO_URL, restClient.getClientInfo().photoUrl);
        Assert.assertEquals("Wrong thumbnailUrl", TEST_THUMBNAIL_URL, restClient.getClientInfo().thumbnailUrl);
        Assert.assertEquals("Wrong additional OAuth value", testOauthValues, restClient.getClientInfo().additionalOauthValues);
    }

    @Test
    public void testClientInfoResolveUrl() {
        Assert.assertEquals("Wrong url", TestCredentials.INSTANCE_URL + "/a/b/", clientInfo.resolveUrl("a/b/").toString());
        Assert.assertEquals("Wrong url", TestCredentials.INSTANCE_URL + "/a/b/", clientInfo.resolveUrl("/a/b/").toString());
    }

    @Test
    public void testClientInfoResolveUrlForHttpsUrl() {
        Assert.assertEquals("Wrong url", "https://testurl", clientInfo.resolveUrl("https://testurl").toString());
        Assert.assertEquals("Wrong url", "http://testurl", clientInfo.resolveUrl("http://testurl").toString());
        Assert.assertEquals("Wrong url", "HTTPS://testurl", clientInfo.resolveUrl("HTTPS://testurl").toString());
        Assert.assertEquals("Wrong url", "HTTP://testurl", clientInfo.resolveUrl("HTTP://testurl").toString());
    }

    @Test
    public void testClientInfoResolveUrlForCommunityUrl() throws Exception {
        final ClientInfo info = new ClientInfo(new URI(TestCredentials.INSTANCE_URL),
        		new URI(TestCredentials.LOGIN_URL),
        		new URI(TestCredentials.IDENTITY_URL),
        		TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
        		TestCredentials.USER_ID, TestCredentials.ORG_ID, null,
        		TestCredentials.COMMUNITY_URL, null, null, null,
                null, null, null, testOauthValues, null,
                null, null, null, null, null, null);
        Assert.assertEquals("Wrong url", TestCredentials.COMMUNITY_URL + "/a/b/", info.resolveUrl("a/b/").toString());
        Assert.assertEquals("Wrong url", TestCredentials.COMMUNITY_URL + "/a/b/", info.resolveUrl("/a/b/").toString());
    }

    @Test
    public void testClientInfoResolveRequestWithLoginEndpoint() {
        RestRequest r = new RestRequest(RestMethod.GET, RestRequest.RestEndpoint.LOGIN, "/a", (JSONObject) null, null);
        Assert.assertEquals("URL should have login host endpoint", TestCredentials.LOGIN_URL + "/a", clientInfo.resolveUrl(r).toString());
    }

    @Test
    public void testClientInfoResolveRequestWithInstanceEndpoint() {
        RestRequest r = new RestRequest(RestMethod.GET, RestRequest.RestEndpoint.INSTANCE, "/a", (JSONObject) null, null);
        Assert.assertEquals("URL should have instance host endpoint", TestCredentials.INSTANCE_URL + "/a", clientInfo.resolveUrl(r).toString());
    }

    @Test
    public void testClientInfoResolveRequestWithCommunity() throws Exception {
        final ClientInfo info = new ClientInfo(new URI(TestCredentials.INSTANCE_URL),
                new URI(TestCredentials.LOGIN_URL),
                new URI(TestCredentials.IDENTITY_URL),
                TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
                TestCredentials.USER_ID, TestCredentials.ORG_ID, null,
                TestCredentials.COMMUNITY_URL, null, null, null,
                null, null, null, testOauthValues, null,
                null, null, null, null, null, null);
        RestRequest r = new RestRequest(RestMethod.GET, RestRequest.RestEndpoint.LOGIN, "/a", (JSONObject) null, null);
        Assert.assertEquals("Community URL should take precedence over login or instance endpoint",
                TestCredentials.COMMUNITY_URL + "/a", info.resolveUrl(r).toString());
    }

    @Test
    public void testGetInstanceUrlForCommunity() throws Exception {
        final ClientInfo info = new ClientInfo(new URI(TestCredentials.INSTANCE_URL),
        		new URI(TestCredentials.LOGIN_URL),
        		new URI(TestCredentials.IDENTITY_URL),
        		TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
        		TestCredentials.USER_ID, TestCredentials.ORG_ID, null,
        		TestCredentials.COMMUNITY_URL, null, null, null,
                null, null, null, testOauthValues, null,
                null, null, null, null, null, null);
        Assert.assertEquals("Wrong url", TestCredentials.COMMUNITY_URL, info.getInstanceUrlAsString());
    }

    @Test
    public void testGetInstanceUrl() {
        Assert.assertEquals("Wrong url", TestCredentials.INSTANCE_URL, clientInfo.getInstanceUrlAsString());
    }

    /**
     * Testing getAuthToken
     */
    @Test
    public void testGetAuthToken() {
        Assert.assertEquals("Wrong auth token", authToken, restClient.getAuthToken());
    }

    /**
     * Testing a call with a bad auth token when restClient has no token provider
     * Expect a 401.
     * @throws URISyntaxException
     * @throws IOException
     */
    @Test
    public void testCallWithBadAuthToken() throws IOException {
        RestClient.clearCaches();
        RestClient restClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, null);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        Assert.assertFalse("Expected error", response.isSuccess());
        checkResponse(response, HttpURLConnection.HTTP_UNAUTHORIZED, true);
    }

    /**
     * Testing a call with a bad auth token when restClient has a token provider
     * Expect token provider to be invoked and new token to be used.
     * @throws URISyntaxException
     * @throws IOException
     */
    @Test
    public void testCallWithBadTokenAndTokenProvider() throws IOException {
        RestClient.clearCaches();
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
        RestClient restClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, authTokenProvider);
        Assert.assertEquals("RestClient should be using the bad token initially", BAD_TOKEN, restClient.getAuthToken());
        RestResponse response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        Assert.assertEquals("RestClient should now be using the good token", authToken, restClient.getAuthToken());
        Assert.assertTrue("Expected success", response.isSuccess());
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
    }

    /**
     * Testing a call with a bad auth token when restClient has a token provider
     * Expect token provider to be invoked and new token to be used and a new instance url to be returned.
     * @throws URISyntaxException
     * @throws IOException
     */
    @Test
    public void testCallWithBadInstanceUrl() throws URISyntaxException, IOException {
        RestClient.clearCaches();
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
        RestClient restClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, authTokenProvider);
        Assert.assertEquals("RestClient has bad instance url", new URI(TestCredentials.INSTANCE_URL), restClient.getClientInfo().instanceUrl);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        Assert.assertEquals("RestClient should now have the correct instance url", new URI(instanceUrl), restClient.getClientInfo().instanceUrl);
        Assert.assertTrue("Expected success", response.isSuccess());
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
    }


    /**
     * Testing RestResponse:getRawResponse
     */
    @Test
    public void testGetRawResponse() throws Exception {
        RestClient restClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, null);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForVersions());
        Response rawResponse = response.getRawResponse();
        Assert.assertEquals(200, rawResponse.code());
        Assert.assertEquals("application/json;charset=UTF-8", rawResponse.header("Content-Type"));
        checkKeys(new JSONArray(rawResponse.body().string()).getJSONObject(0), "label", "url", "version");
        rawResponse.close();
    }

    /**
     * Testing a get single access call to the server - check response
     * @throws Exception
     */
    @Test
    public void testGetSingleAccess() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForSingleAccess("abc/def"));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        checkKeys(response.asJSONObject(), "frontdoor_uri");
    }

    /**
     * Testing a get versions call to the server - check response
     * @throws Exception
     */
    @Test
    public void testGetVersions() throws Exception {
        // We don't need to be authenticated
        RestClient restClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, null);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForVersions());
        checkResponse(response, HttpURLConnection.HTTP_OK, true);
        checkKeys(response.asJSONArray().getJSONObject(0), "label", "url", "version");
    }

    /**
     * Testing a get resources call to the server - check response
     * @throws Exception
     */
    @Test
    public void testGetResources() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        checkKeys(response.asJSONObject(), "sobjects", "search", "recent");
    }

    /**
     * Testing a get resources async call to the server - check response
     * @throws Exception
     */
    @Test
    public void testGetResourcesAsync() throws Exception {
        RestResponse response = sendAsync(restClient, RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        checkKeys(response.asJSONObject(), "sobjects", "search", "recent");
    }

    /**
     * Testing a describe global call to the server - check response
     * @throws Exception
     */
    @Test
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
    @Test
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
    @Test
    public void testMetadata() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForMetadata(TestCredentials.API_VERSION, ACCOUNT));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "objectDescribe", "recentItems");
        checkKeys(jsonResponse.getJSONObject("objectDescribe"), LNAME, "label", "keyPrefix");
        Assert.assertEquals("Wrong object name", "Account", jsonResponse.getJSONObject("objectDescribe").getString(LNAME));
    }

    /**
     * Testing a describe call to the server - check response
     * @throws Exception
     */
    @Test
    public void testDescribe() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForDescribe(TestCredentials.API_VERSION, ACCOUNT));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, LNAME, "fields", "urls", "label");
        Assert.assertEquals("Wrong object name", "Account", jsonResponse.getString(LNAME));
    }

    /**
     * Testing a create call to the server - check response
     * @throws Exception
     */
    @Test
    public void testCreate() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        String newAccountName = ENTITY_NAME_PREFIX + System.nanoTime();
        fields.put(NAME, newAccountName);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForCreate(TestCredentials.API_VERSION, ACCOUNT, fields));
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "id", "errors", "success");
        Assert.assertTrue("Create failed", jsonResponse.getBoolean("success"));
    }

    /**
     * Testing a retrieve call to the server.
     * Create new account then retrieve it.
     * @throws Exception
     */
    @Test
    public void testRetrieve() throws Exception {
        List<String> fields = Arrays.asList(NAME, "ownerId");
        IdName newAccountIdName = createAccount();
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "attributes", NAME, "OwnerId", "Id");
        Assert.assertEquals("Wrong row returned", newAccountIdName.name, jsonResponse.getString(NAME));
    }

    /**
     * Testing an update call to the server.
     * Create new account then update it then get it back
     * @throws Exception
     */
    @Test
    public void testUpdate() throws Exception {

        // Create
        IdName newAccountIdName = createAccount();

        // Update
        Map<String, Object> fields = new HashMap<String, Object>();
        String updatedAccountName = ENTITY_NAME_PREFIX + "-" + System.nanoTime();
        fields.put(NAME, updatedAccountName);
        RestResponse updateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields));
        Assert.assertTrue("Update failed", updateResponse.isSuccess());

        // Retrieve - expect updated name
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(NAME)));
        Assert.assertEquals("Wrong row returned", updatedAccountName, response.asJSONObject().getString(NAME));
    }

    /**
     * Testing upsert calls to the server.
     * Create new account using a first upsert call then update it with a second upsert call then get it back
     * @throws Exception
     */
    @Test
    public void testUpsert() throws Exception {

        // Create with upsert call
        Map<String, Object> fields = new HashMap<>();
        String accountName = ENTITY_NAME_PREFIX + "-" + System.nanoTime();
        fields.put(NAME, accountName);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForUpsert(TestCredentials.API_VERSION, ACCOUNT, "Id", null, fields));
        Assert.assertTrue("Create with upsert failed", response.isSuccess());
        String accountId = response.asJSONObject().getString("id");

        // Update with upsert call
        fields = new HashMap<String, Object>();
        String updatedAccountName = ENTITY_NAME_PREFIX + "-" + System.nanoTime();
        fields.put(NAME, updatedAccountName);
        response = restClient.sendSync(RestRequest.getRequestForUpsert(TestCredentials.API_VERSION, ACCOUNT, "Id", accountId, fields));
        Assert.assertTrue("Update with upsert failed", response.isSuccess());

        // Retrieve - expect updated name
        response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, accountId, Arrays.asList(NAME)));
        Assert.assertEquals("Wrong row returned", updatedAccountName, response.asJSONObject().getString(NAME));
    }


    /**
     * Testing update calls to the server with if-unmodified-since.
     * Create new account,
     * then update it with created date for unmodified since date (should update)
     * then update it again with created date for unmodified since date (should not update)
     * @throws Exception
     */
    @Test
    public void testUpdateWithIfUnmodifiedSince() throws Exception {
        Map<String, Object> fields = new HashMap<>();
        Date pastDate = new Date(new Date().getTime() - 3600*1000); // an hour ago

        // Create
        IdName newAccountIdName = createAccount();
        String originalName = newAccountIdName.name;

        // Retrieve to get created date
        RestResponse retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(LAST_MODIFIED_DATE)));
        Date createdDate = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")).parse(retrieveResponse.asJSONObject().getString(LAST_MODIFIED_DATE));

        // Wait a bit
        Thread.sleep(1000);

        // Update with if-unmodified-since with createdDate - should update
        String updatedName = originalName + "_upd";
        fields.put(NAME, updatedName);
        RestResponse updateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields, createdDate));
        Assert.assertTrue("Update failed", updateResponse.isSuccess());

        // Retrieve - expect updated name
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(NAME)));
        Assert.assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME));

        // Second update with if-unmodified-since with created date - should not update
        String blockedUpdatedName = originalName + "_blocked_upd";
        fields.put(NAME, blockedUpdatedName);
        RestResponse blockedUpdateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields, createdDate));
        Assert.assertEquals("Expected 412", HttpURLConnection.HTTP_PRECON_FAILED, blockedUpdateResponse.getStatusCode());

        // Retrieve - expect name from first update
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(NAME)));
        Assert.assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME));

    }

    /**
     * Testing a delete call to the server.
     * Create new account then delete it then try to retrieve it again (expect 404).
     * @throws Exception
     */
    @Test
    public void testDelete() throws Exception {

        // Create
        IdName newAccountIdName = createAccount();

        // Delete
        RestResponse deleteResponse = restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id));
        Assert.assertTrue("Delete failed", deleteResponse.isSuccess());

        // Retrieve - expect 404
        List<String> fields = Arrays.asList(NAME);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, fields));
        Assert.assertEquals("404 was expected", HttpURLConnection.HTTP_NOT_FOUND, response.getStatusCode());
    }

    /**
     *
     * TODO: if-unmodified-since not supported for delete
     * Bring this test back once it is
     *
     * Testing delete calls to the server with if-unmodified-since.
     * Create new account
     * Update it
     * then delete it with created date for unmodified since date (should not delete)
     * then delete it with last modified date unmodified since date (should delete)
     * @throws Exception
     */
    /*
    @Test
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
        Assert.assertTrue("Update failed", updateResponse.isSuccess());

        // Retrieve - expect updated name
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{NAME, LAST_MODIFIED_DATE})));
        Assert.assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME));
        Date lastModifiedDate = (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")).parse(retrieveResponse.asJSONObject().getString(LAST_MODIFIED_DATE));

        // Delete with if-unmodified-since with created date - should not delete
        RestResponse blockedDeleteResponse = restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, createdDate));
        Assert.assertFalse("Delete should have failed", blockedDeleteResponse.isSuccess());
        Assert.assertEquals("Expected 412", HttpURLConnection.HTTP_PRECON_FAILED, blockedDeleteResponse.getStatusCode());

        // Retrieve - expect success
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{NAME})));
        Assert.assertTrue("Retrieve should have succeeded", retrieveResponse.isSuccess());
        Assert.assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME));

        // Delete with if-unmodified-since with created date - should delete
        RestResponse deleteResponse = restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, lastModifiedDate));
        Assert.assertTrue("Delete should have succeeded", deleteResponse.isSuccess());

        // Retrieve - expect 404
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, ACCOUNT, newAccountIdName.id, Arrays.asList(new String[]{NAME})));
        Assert.assertFalse("Retrieve should have failed", retrieveResponse.isSuccess());
        Assert.assertEquals("Expected 404", HttpURLConnection.HTTP_NOT_FOUND, retrieveResponse.getStatusCode());
    }
    */

    /**
     * Testing a query call to the server.
     * Create new account then look for it using soql.
     * @throws Exception
     */
    @Test
    public void testQuery() throws Exception {
        IdName newAccountIdName = createAccount();
        RestResponse response = restClient.sendSync(RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select name from account where id = '" + newAccountIdName.id + "'"));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "done", "totalSize", "records");
        Assert.assertEquals("Expected one row", 1, jsonResponse.getInt("totalSize"));
        Assert.assertEquals("Wrong row returned", newAccountIdName.name, jsonResponse.getJSONArray("records").getJSONObject(0).get(NAME));
    }

    /**
     * Testing a query all call to the server.
     * Create new account then look for it using soql.
     * @throws Exception
     */
    @Test
    public void testQueryAll() throws Exception {
        // Create 3 accounts
        List<IdName> idNames = createAccounts(3, "-testQueryAll-");
        // Delete 1 account
        restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, ACCOUNT, idNames.get(0).id));

        String soql = "select name from account where Name like '" + ENTITY_NAME_PREFIX + "-testQueryAll-%' order by Name";

        // Query - expect 2 records
        RestResponse responseQuery = restClient.sendSync(RestRequest.getRequestForQuery(TestCredentials.API_VERSION, soql));
        checkResponse(responseQuery, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponseQuery = responseQuery.asJSONObject();
        checkKeys(jsonResponseQuery, "done", "totalSize", "records");
        Assert.assertEquals("Expected three rows", 2, jsonResponseQuery.getInt("totalSize"));
        Assert.assertEquals("Wrong row returned", idNames.get(1).name, jsonResponseQuery.getJSONArray("records").getJSONObject(0).get(NAME));
        Assert.assertEquals("Wrong row returned", idNames.get(2).name, jsonResponseQuery.getJSONArray("records").getJSONObject(1).get(NAME));


        // Query all - expect 3 records
        RestResponse responseQueryAll = restClient.sendSync(RestRequest.getRequestForQueryAll(TestCredentials.API_VERSION, soql));
        checkResponse(responseQueryAll, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponseQueryAll = responseQueryAll.asJSONObject();
        checkKeys(jsonResponseQueryAll, "done", "totalSize", "records");
        Assert.assertTrue("Expected three rows (or more)", jsonResponseQueryAll.getInt("totalSize") >= 3);
        JSONArray records = jsonResponseQueryAll.getJSONArray("records");
        ArrayList<String> names = new ArrayList<>();
        for (int i=0; i<records.length(); i++) {
            names.add(records.getJSONObject(i).getString(NAME));
        }

        Assert.assertTrue("Row not foumd", names.contains(idNames.get(0).name));
        Assert.assertTrue("Row not foumd", names.contains(idNames.get(1).name));
        Assert.assertTrue("Row not foumd", names.contains(idNames.get(2).name));
    }

    /**
     * Testing a query call to the server which specifies a batch size.
     * Create new account then look for it using soql.
     * @throws Exception
     */
    @Test
    public void testQueryWithBatchSize() throws Exception {
        cleanup();
        List<IdName> idNames = createAccounts(201, "-testWithBatchSize-");
        String soql = "select name from account where Name like '" + ENTITY_NAME_PREFIX + "-testWithBatchSize-%'";

        // SOQL without batch size
        RestRequest requestNoBatchSizeSpecified = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, soql);
        Assert.assertNull(requestNoBatchSizeSpecified.getAdditionalHttpHeaders());
        RestResponse responseNoBatchSizeSpecified = restClient.sendSync(requestNoBatchSizeSpecified);
        checkResponse(responseNoBatchSizeSpecified, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponseNoBatchSizeSpecified = responseNoBatchSizeSpecified.asJSONObject();
        checkKeys(jsonResponseNoBatchSizeSpecified, "done", "totalSize", "records");
        Assert.assertEquals("201 rows should match", 201, jsonResponseNoBatchSizeSpecified.getInt("totalSize"));
        Assert.assertEquals("201 rows should have been returned", 201, jsonResponseNoBatchSizeSpecified.getJSONArray("records").length());

        // SOQL with batch size
        RestRequest requestWithBatchSizeSpecified = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, soql, 200);
        Assert.assertEquals("batchSize=200", requestWithBatchSizeSpecified.getAdditionalHttpHeaders().get(RestRequest.SFORCE_QUERY_OPTIONS));
        RestResponse responseWithBatchSizeSpecified = restClient.sendSync(requestWithBatchSizeSpecified);
        checkResponse(responseWithBatchSizeSpecified, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponseWithBatchSizeSpecified = responseWithBatchSizeSpecified.asJSONObject();
        checkKeys(jsonResponseWithBatchSizeSpecified, "done", "totalSize", "records");
        Assert.assertEquals("201 rows should match", 201, jsonResponseWithBatchSizeSpecified.getInt("totalSize"));
        Assert.assertEquals("200 rows should have been returned", 200, jsonResponseWithBatchSizeSpecified.getJSONArray("records").length());
    }

    /**
     * Testing that calling resume more than once on a RestResponse doesn't throw an exception
     * @throws Exception 
     */
    @Test
    public void testDoubleConsume() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForMetadata(TestCredentials.API_VERSION, ACCOUNT));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        try {
        	response.consume();
        	response.consume();
        }
        catch (IllegalStateException e) {
            Assert.fail("Calling consume should not have thrown an exception");
        }
    }

    /**
     * Testing a search call to the server.
     * Create new account then ensure the results of SOSL don't have an error.
     * @throws Exception
     */
    @Test
    public void testSearch() throws Exception {
        createAccount();
        createAccount();
        RestResponse response = restClient.sendSync(RestRequest.getRequestForSearch(TestCredentials.API_VERSION, "find {" + ENTITY_NAME_PREFIX + "}"));
        JSONArray jsonResults = response.asJSONObject().getJSONArray("searchRecords");
        Assert.assertNotNull("Results expected", jsonResults);
    }

    /**
     * Testing doing a sync request with a RestClient that uses an UnauthenticatedClientInfo
     * @return
     * @throws Exception
     */
    @Test
    public void testRestClientUnauthenticatedClientInfo() throws Exception {
        RestClient unauthenticatedRestClient = new RestClient(new RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT, null);
        RestRequest request = new RestRequest(RestMethod.GET, "https://na1.salesforce.com/services/data");
        RestResponse response = unauthenticatedRestClient.sendSync(request);
        Assert.assertNull("Unauthenticated requests should not have auth header.",
                response.getRawResponse().request().headers().get("Authentication"));
        checkResponse(response, HttpURLConnection.HTTP_OK, true);
        JSONArray jsonResponse = response.asJSONArray();
        checkKeys(jsonResponse.getJSONObject(0), "label", "url", "version");
    }

    /**
     * Testing doing an async request with a RestClient that uses an UnauthenticatedClientInfo
     * @return
     * @throws Exception
     */
    @Test
    public void testRestClientUnauthenticatedClientInfoAsync() throws Exception {
        RestClient unauthenticatedRestClient = new RestClient(new RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT, null);
        RestRequest request = new RestRequest(RestMethod.GET, "https://na1.salesforce.com/services/data");
        RestResponse response = sendAsync(unauthenticatedRestClient, request);
        Assert.assertNull("Unauthenticated requests should not have auth header.",
                response.getRawResponse().request().headers().get("Authentication"));
        checkResponse(response, HttpURLConnection.HTTP_OK, true);
        JSONArray jsonResponse = response.asJSONArray();
        checkKeys(jsonResponse.getJSONObject(0), "label", "url", "version");
    }

    /**
     * Tests if a stream from {@link RestResponse#asInputStream()} is readable.
     *
     * @throws Exception
     */
    @Test
    public void testResponseStreamIsReadable() throws Exception {
        final RestResponse response = getStreamTestResponse();
        try {
            InputStream in = response.asInputStream();
            assertStreamTestResponseStreamIsValid(in);
        } catch (IOException e) {
            Assert.fail("The InputStream should be readable and an IOException should not have been thrown");
        } catch (JSONException e) {
            Assert.fail("Valid JSON data should have been returned");
        } finally {
            response.consumeQuietly();
        }
    }

    /**
     * Tests if a stream from {@link RestResponse#asInputStream()} is consumed (according to the REST client) by fully reading the stream.
     *
     * @throws Exception
     */
    @Test
    public void testResponseStreamConsumedByReadingStream() throws Exception {
        final RestResponse response = getStreamTestResponse();
        try {
            InputStream in = response.asInputStream();
            Encryptor.getStringFromStream(in);
        } catch (IOException e) {
            Assert.fail("The InputStream should be readable and an IOException should not have been thrown");
        }

        // We read the entire stream but forgot to call consume() or consumeQuietly() - can another REST call be made?
        final RestResponse anotherResponse = getStreamTestResponse();
        Assert.assertNotNull(anotherResponse);
    }

    /**
     * Tests that a stream from {@link RestResponse#asInputStream()} cannot be read from twice.
     *
     * @throws Exception
     */
    @Test
    public void testResponseStreamCannotBeReadTwice() throws Exception {
        final RestResponse response = getStreamTestResponse();
        try {
            final InputStream in = response.asInputStream();
            Encryptor.getStringFromStream(in);
        } catch (IOException e) {
            Assert.fail("The InputStream should be readable and an IOException should not have been thrown");
        }
        try {
            response.asInputStream();
            Assert.fail("An IOException should have been thrown while trying to read the InputStream a second time");
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
    @Test
    public void testOtherAccessorsNotAvailableAfterResponseStreaming() throws Exception {
        final RestResponse response = getStreamTestResponse();
        final Runnable testAccessorsNotAccessible = new Runnable() {
            @Override
            public void run() {
                try {
                    // The other accessors should not return valid data as soon as the stream is opened
                    Assert.assertNotNull(response.asBytes());
                    Assert.assertEquals("asBytes() array should be empty", 0, response.asBytes().length);
                    Assert.assertEquals("asString() should return the empty string", "", response.asString());

                    try {
                        Assert.assertNull(response.asJSONObject());
                        Assert.fail("asJSONObject() should fail");
                    } catch (JSONException e) {
                        // Expected
                    }
                    try {
                        Assert.assertNull(response.asJSONArray());
                        Assert.fail("asJSONArray() should fail");
                    } catch (JSONException e) {
                        // Expected
                    }
                } catch (IOException e) {
                    Assert.fail("IOException not expected");
                }
            }
        };
        try {
            response.asInputStream();
            testAccessorsNotAccessible.run();
        } catch (IOException e) {
            Assert.fail("The InputStream should be readable and an IOException should not have been thrown");
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
    @Test
    public void testAccessorMethodsPreventResponseStreaming() throws Exception {
        final RestResponse response = getStreamTestResponse();
        response.asBytes();
        try {
            response.asInputStream();
            Assert.fail("The InputStream should not be readable after an accessor method is called");
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
    @Test
    public void testBatchRequest() throws IOException, JSONException {
        Map<String, Object> accountFields = new HashMap<>();
        String accountName = ENTITY_NAME_PREFIX + System.nanoTime();
        accountFields.put(NAME, accountName);
        RestRequest firstRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION, ACCOUNT, accountFields);
        Map<String, Object> contactFields = new HashMap<>();
        String contactName = ENTITY_NAME_PREFIX + System.nanoTime();
        contactFields.put("LastName", contactName);
        RestRequest secondRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION, "contact", contactFields);
        RestRequest thirdRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select Id from Account where Name = '" + accountName + "'");
        RestRequest fourthRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select Id from Contact where Name = '" + contactName + "'");

        // Build batch request
        RestRequest batchRequest = RestRequest.getBatchRequest(TestCredentials.API_VERSION, false, Arrays.asList(firstRequest, secondRequest, thirdRequest, fourthRequest));

        // Send batch request
        RestResponse response = restClient.sendSync(batchRequest);

        // Checking response
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "hasErrors", "results");
        Assert.assertFalse("Batch had errors", jsonResponse.getBoolean("hasErrors"));
        JSONArray jsonResults = jsonResponse.getJSONArray("results");
        Assert.assertEquals("Wrong number of results", 4, jsonResults.length());
        Assert.assertEquals("Wrong status for first request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(0).getInt("statusCode"));
        Assert.assertEquals("Wrong status for second request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(1).getInt("statusCode"));
        Assert.assertEquals("Wrong status for third request", HttpURLConnection.HTTP_OK, jsonResults.getJSONObject(2).getInt("statusCode"));
        Assert.assertEquals("Wrong status for fourth request", HttpURLConnection.HTTP_OK, jsonResults.getJSONObject(3).getInt("statusCode"));

        // Queries should have returned ids of newly created account and contact
        String accountId =  jsonResults.getJSONObject(0).getJSONObject("result").getString("id");
        String contactId =  jsonResults.getJSONObject(1).getJSONObject("result").getString("id");
        String idFromFirstQuery = jsonResults.getJSONObject(2).getJSONObject("result").getJSONArray("records").getJSONObject(0).getString("Id");
        String idFromSecondQuery = jsonResults.getJSONObject(3).getJSONObject("result").getJSONArray("records").getJSONObject(0).getString("Id");
        Assert.assertEquals("Account id not returned by query", accountId, idFromFirstQuery);
        Assert.assertEquals("Contact id not returned by query", contactId, idFromSecondQuery);
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
    @Test
    public void testCompositeRequest() throws IOException, JSONException {
        Map<String, Object> accountFields = new HashMap<>();
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
        Assert.assertEquals("Wrong number of results", 3, jsonResults.length());
        Assert.assertEquals("Wrong status for first request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(0).getInt("httpStatusCode"));
        Assert.assertEquals("Wrong status for second request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(1).getInt("httpStatusCode"));
        Assert.assertEquals("Wrong status for third request", HttpURLConnection.HTTP_OK, jsonResults.getJSONObject(2).getInt("httpStatusCode"));

        // Query should have returned ids of newly created account and contact
        String accountId =  jsonResults.getJSONObject(0).getJSONObject("body").getString("id");
        String contactId =  jsonResults.getJSONObject(1).getJSONObject("body").getString("id");
        JSONArray queryRecords = jsonResults.getJSONObject(2).getJSONObject("body").getJSONArray("records");
        Assert.assertEquals("wrong number of results for query request", 1, queryRecords.length());
        Assert.assertEquals("Account id not returned by query", accountId, queryRecords.getJSONObject(0).getString("AccountId"));
        Assert.assertEquals("Contact id not returned by query", contactId, queryRecords.getJSONObject(0).getString("Id"));
    }

    /**
     * Test for sobject tree request
     *
     * Run a sobject tree request that:
     * - creates an account,
     * - creates two children contacts
     *
     * Then run queries that should return newly created account and contacts
     *
     * @throws IOException
     * @throws JSONException
     */
    @Test
    public void testSObjectTreeRequest() throws IOException, JSONException {
        Map<String, Object> accountFields = new HashMap<>();
        String accountName = ENTITY_NAME_PREFIX + System.nanoTime();
        accountFields.put(NAME, accountName);
        Map<String, Object> contactFields = new HashMap<>();
        String contactName = ENTITY_NAME_PREFIX + System.nanoTime();
        contactFields.put("LastName", contactName);
        Map<String, Object> otherContactFields = new HashMap<>();
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
        Assert.assertFalse("SObject tree request had errors", jsonResponse.getBoolean("hasErrors"));
        JSONArray jsonResults = jsonResponse.getJSONArray("results");
        Assert.assertEquals("Wrong number of results", 3, jsonResults.length());
        String accountId =  jsonResults.getJSONObject(0).getString("id");
        String contactId =  jsonResults.getJSONObject(1).getString("id");
        String otherContactId =  jsonResults.getJSONObject(2).getString("id");

        // Running query that should match first contact and its parent
        RestRequest queryRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select Id, AccountId from Contact where LastName = '" + contactName + "'");
        RestResponse queryResponse = restClient.sendSync(queryRequest);
        JSONArray queryRecords = queryResponse.asJSONObject().getJSONArray("records");
        Assert.assertEquals("wrong number of results for query request", 1, queryRecords.length());
        Assert.assertEquals("Account id not returned by query", accountId, queryRecords.getJSONObject(0).getString("AccountId"));
        Assert.assertEquals("Contact id not returned by query", contactId, queryRecords.getJSONObject(0).getString("Id"));

        // Running other query that should match other contact and its parent
        RestRequest otherQueryRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select Id, AccountId from Contact where LastName = '" + otherContactName + "'");
        RestResponse otherQueryResponse = restClient.sendSync(otherQueryRequest);
        JSONArray otherQueryRecords = otherQueryResponse.asJSONObject().getJSONArray("records");
        Assert.assertEquals("wrong number of results for query request", 1, otherQueryRecords.length());
        Assert.assertEquals("Account id not returned by query", accountId, otherQueryRecords.getJSONObject(0).getString("AccountId"));
        Assert.assertEquals("Contact id not returned by query", otherContactId, otherQueryRecords.getJSONObject(0).getString("Id"));
    }

    @Test
    public void testGetNotificationsStatus() throws Exception {
        RestRequest request = RestRequest.getRequestForNotificationsStatus(TestCredentials.API_VERSION);
        RestResponse response = restClient.sendSync(request);
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        checkKeys(response.asJSONObject(), "lastActivity", "oldestUnread", "oldestUnseen", "unreadCount", "unseenCount");
    }

    @Test
    public void testGetNotifications() throws Exception {
        Date yesterday =  new Date(new Date().getTime() - 24*60*60*1000);
        RestRequest request = RestRequest.getRequestForNotifications(TestCredentials.API_VERSION, 10, null, yesterday);
        RestResponse response = restClient.sendSync(request);
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        checkKeys(response.asJSONObject(), "notifications");
    }

    @Test
    public void testUpdateReadNotifications() throws Exception {
        RestRequest request = RestRequest.getRequestForNotificationsUpdate(TestCredentials.API_VERSION, null, new Date(), true, null);
        RestResponse response = restClient.sendSync(request);
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
    }

    @Test
    public void testUpdateSeenNotifications() throws Exception {
        RestRequest request = RestRequest.getRequestForNotificationsUpdate(TestCredentials.API_VERSION, null, new Date(), null, true);
        RestResponse response = restClient.sendSync(request);
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
    }

    @Test
    public void testGetPrimingRecords() throws Exception {
        RestRequest request = RestRequest.getRequestForPrimingRecords(TestCredentials.API_VERSION, null, null);
        RestResponse response = restClient.sendSync(request);
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        checkKeys(response.asJSONObject(), "primingRecords", "relayToken", "ruleErrors", "stats");
    }

    @Test
    public void testGetPrimingRecordsWithChangedAfterTimestampParameter() throws Exception {
        RestRequest request = RestRequest.getRequestForPrimingRecords(TestCredentials.API_VERSION, null, new Date().getTime());
        RestResponse response = restClient.sendSync(request);
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        checkKeys(response.asJSONObject(), "primingRecords", "relayToken", "ruleErrors", "stats");
    }

    @Test
    public void testParsePrimingRecordsResponse() throws Exception {
        RestRequest request = RestRequest.getRequestForPrimingRecords(TestCredentials.API_VERSION, null, null);
        RestResponse response = restClient.sendSync(request);
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        try {
            PrimingRecordsResponse primingRecordsResponse = new PrimingRecordsResponse(
                response.asJSONObject());
        } catch (Exception e) {
            Assert.fail("Fail to parse priming records response: " + e.getMessage());
        }
    }

    @Test
    public void testCollectionCreate() throws JSONException, IOException {
        String firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime();
        String secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime();
        String contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime();

        JSONArray records = makeRecords(
            Arrays.asList("Account", "Name", firstAccountName),
            Arrays.asList("Contact", "LastName", contactName),
            Arrays.asList("Account", "Name", secondAccountName)
        );

        // Doing a collection create
        RestResponse createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION, true , records));

        // Parsing response
        CollectionResponse parsedCreateResponse = new CollectionResponse(createResponse.asJSONArray());
        String firstAccountId = parsedCreateResponse.subResponses.get(0).id;
        String contactId = parsedCreateResponse.subResponses.get(1).id;
        String secondAccountId = parsedCreateResponse.subResponses.get(1).id;

        // Checking response
        Assert.assertEquals(3, parsedCreateResponse.subResponses.size());
        Assert.assertTrue(parsedCreateResponse.subResponses.get(0).id.startsWith("001"));
        Assert.assertTrue(parsedCreateResponse.subResponses.get(0).success);
        Assert.assertTrue(parsedCreateResponse.subResponses.get(0).errors.isEmpty());
        Assert.assertTrue(parsedCreateResponse.subResponses.get(1).id.startsWith("003"));
        Assert.assertTrue(parsedCreateResponse.subResponses.get(1).success);
        Assert.assertTrue(parsedCreateResponse.subResponses.get(1).errors.isEmpty());
        Assert.assertTrue(parsedCreateResponse.subResponses.get(2).id.startsWith("001"));
        Assert.assertTrue(parsedCreateResponse.subResponses.get(2).success);
        Assert.assertTrue(parsedCreateResponse.subResponses.get(2).errors.isEmpty());
    }

    @Test
    public void testCollectionRetrieve() throws JSONException, IOException {
        String firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime();
        String secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime();
        String contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime();

        JSONArray records = makeRecords(
            Arrays.asList("Account", "Name", firstAccountName),
            Arrays.asList("Contact", "LastName", contactName),
            Arrays.asList("Account", "Name", secondAccountName)
        );

        // Doing a collection create
        RestResponse createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION, true , records));

        // Parsing response
        CollectionResponse parsedCreateResponse = new CollectionResponse(createResponse.asJSONArray());
        String firstAccountId = parsedCreateResponse.subResponses.get(0).id;
        String contactId = parsedCreateResponse.subResponses.get(1).id;
        String secondAccountId = parsedCreateResponse.subResponses.get(2).id;

        // Doing a collection retrieve for the accounts
        RestRequest accountsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION, "Account", Arrays.asList(firstAccountId, secondAccountId), Arrays.asList("Id", "Name"));
        JSONArray accountsRetrieved = restClient.sendSync(accountsRetrieveRequest).asJSONArray();

        // Checking response
        Assert.assertEquals(2, accountsRetrieved.length());
        Assert.assertEquals(firstAccountName, accountsRetrieved.getJSONObject(0).getString("Name"));
        Assert.assertEquals(secondAccountName, accountsRetrieved.getJSONObject(1).getString("Name"));

        // Doing a collection retrieve for the contact
        RestRequest contactsRetrievedRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION, "Contact", Arrays.asList(contactId), Arrays.asList("Id", "LastName"));
        JSONArray contactsRetrieved = restClient.sendSync(contactsRetrievedRequest).asJSONArray();

        // Checking response
        Assert.assertEquals(1, contactsRetrieved.length());
        Assert.assertEquals(contactName, contactsRetrieved.getJSONObject(0).getString("LastName"));
    }

    @Test
    public void testCollectionUpsertNewRecords() throws JSONException, IOException {
        String firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime();
        String secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime();

        JSONArray records = makeRecords(
            Arrays.asList("Account", "Name", firstAccountName),
            Arrays.asList("Account", "Name", secondAccountName)
        );

        // Doing a collection upsert
        RestResponse upsertResponse = restClient.sendSync(RestRequest.getRequestForCollectionUpsert(TestCredentials.API_VERSION, true, "Account" , "Id", records));

        // Parsing response
        CollectionResponse parsedUpsertResponse = new CollectionResponse(upsertResponse.asJSONArray());
        String firstAccountId = parsedUpsertResponse.subResponses.get(0).id;
        String secondAccountId = parsedUpsertResponse.subResponses.get(1).id;

        // Checking response
        Assert.assertEquals(2, parsedUpsertResponse.subResponses.size());
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(0).id.startsWith("001"));
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(0).success);
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(0).errors.isEmpty());
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(1).id.startsWith("001"));
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(1).success);
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(1).errors.isEmpty());
    }

    @Test
    public void testCollectionUpsertExistingRecords() throws JSONException, IOException {
        String firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime();
        String secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime();

        JSONArray records = makeRecords(
            Arrays.asList("Account", "Name", firstAccountName),
            Arrays.asList("Account", "Name", secondAccountName)
        );

        // Doing a collection create
        RestResponse createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION, true, records));

        // Parsing response
        CollectionResponse parsedCreateResponse = new CollectionResponse(createResponse.asJSONArray());
        String firstAccountId = parsedCreateResponse.subResponses.get(0).id;
        String secondAccountId = parsedCreateResponse.subResponses.get(1).id;

        // Doing a collection upsert to update the accounts
        String firstAccountNameUpdated = firstAccountName + "_updated";
        String secondAccountNameUpdated = secondAccountName + "_updated";
        JSONArray updatedAccounts = makeRecords(
            Arrays.asList("Account", "Name", firstAccountNameUpdated, "Id", firstAccountId),
            Arrays.asList("Account", "Name", secondAccountNameUpdated, "Id", secondAccountId)
        );

        RestResponse upsertResponse = restClient.sendSync(RestRequest.getRequestForCollectionUpsert(TestCredentials.API_VERSION, true, "Account" , "Id", updatedAccounts));

        // Parsing response
        CollectionResponse parsedUpsertResponse = new CollectionResponse(upsertResponse.asJSONArray());

        // Checking response
        Assert.assertEquals(2, parsedUpsertResponse.subResponses.size());
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(0).id.startsWith("001"));
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(0).success);
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(0).errors.isEmpty());
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(1).id.startsWith("001"));
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(1).success);
        Assert.assertTrue(parsedUpsertResponse.subResponses.get(1).errors.isEmpty());

        // Checking account on server to make sure they were updated
        RestRequest accountsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION, "Account", Arrays.asList(firstAccountId, secondAccountId), Arrays.asList("Id", "Name"));
        JSONArray accountsRetrieved = restClient.sendSync(accountsRetrieveRequest).asJSONArray();
        Assert.assertEquals(2, accountsRetrieved.length());
        Assert.assertEquals(firstAccountNameUpdated, accountsRetrieved.getJSONObject(0).getString("Name"));
        Assert.assertEquals(secondAccountNameUpdated, accountsRetrieved.getJSONObject(1).getString("Name"));
    }

    @Test
    public void testCollectionUpdate() throws JSONException, IOException {
        String firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime();
        String secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime();
        String contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime();

        JSONArray records = makeRecords(
            Arrays.asList("Account", "Name", firstAccountName),
            Arrays.asList("Contact", "LastName", contactName),
            Arrays.asList("Account", "Name", secondAccountName)
        );

        // Doing a collection create
        RestResponse createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION, true , records));

        // Parsing response
        CollectionResponse parsedCreateResponse = new CollectionResponse(createResponse.asJSONArray());
        String firstAccountId = parsedCreateResponse.subResponses.get(0).id;
        String contactId = parsedCreateResponse.subResponses.get(1).id;
        String secondAccountId = parsedCreateResponse.subResponses.get(2).id;

        // Doing a collection update for one account and the contact
        String firstAccountNameUpdated = firstAccountName + "_updated";
        String contactNameUpdated = contactName + "_updated";
        JSONArray updatedRecords = makeRecords(
            Arrays.asList("Account", "Name", firstAccountNameUpdated, "Id", firstAccountId),
            Arrays.asList("Contact", "LastName", contactNameUpdated, "Id", contactId)
        );

        RestResponse updateResponse = restClient.sendSync(RestRequest.getRequestForCollectionUpdate(TestCredentials.API_VERSION, true, updatedRecords));

        // Parsing response
        CollectionResponse parsedUpdateResponse = new CollectionResponse(updateResponse.asJSONArray());

        // Checking response
        Assert.assertEquals(2, parsedUpdateResponse.subResponses.size());
        Assert.assertTrue(parsedUpdateResponse.subResponses.get(0).id.startsWith("001"));
        Assert.assertTrue(parsedUpdateResponse.subResponses.get(0).success);
        Assert.assertTrue(parsedUpdateResponse.subResponses.get(0).errors.isEmpty());
        Assert.assertTrue(parsedUpdateResponse.subResponses.get(1).id.startsWith("003"));
        Assert.assertTrue(parsedUpdateResponse.subResponses.get(1).success);
        Assert.assertTrue(parsedUpdateResponse.subResponses.get(1).errors.isEmpty());

        // Checking accounts on server
        RestRequest accountsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION, "Account", Arrays.asList(firstAccountId, secondAccountId), Arrays.asList("Id", "Name"));
        JSONArray accountsRetrieved = restClient.sendSync(accountsRetrieveRequest).asJSONArray();
        Assert.assertEquals(2, accountsRetrieved.length());
        Assert.assertEquals(firstAccountNameUpdated, accountsRetrieved.getJSONObject(0).getString("Name"));
        Assert.assertEquals(secondAccountName, accountsRetrieved.getJSONObject(1).getString("Name"));

        // Checking contact on server
        RestRequest contactsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION, "Contact", Arrays.asList(contactId), Arrays.asList("Id", "LastName"));
        JSONArray contactsRetrieved = restClient.sendSync(contactsRetrieveRequest).asJSONArray();
        Assert.assertEquals(1, contactsRetrieved.length());
        Assert.assertEquals(contactNameUpdated, contactsRetrieved.getJSONObject(0).getString("LastName"));
    }

    @Test
    public void testCollectionDelete() throws JSONException, IOException {
        String firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime();
        String secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime();
        String contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime();

        JSONArray records = makeRecords(
            Arrays.asList("Account", "Name", firstAccountName),
            Arrays.asList("Contact", "LastName", contactName),
            Arrays.asList("Account", "Name", secondAccountName)
        );

        // Doing a collection create
        RestResponse createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION, true , records));

        // Parsing response
        CollectionResponse parsedCreateResponse = new CollectionResponse(createResponse.asJSONArray());
        String firstAccountId = parsedCreateResponse.subResponses.get(0).id;
        String contactId = parsedCreateResponse.subResponses.get(1).id;
        String secondAccountId = parsedCreateResponse.subResponses.get(2).id;

        // Doing a collection delete for one account and the contact
        RestResponse deleteResponse = restClient.sendSync(RestRequest.getRequestForCollectionDelete(TestCredentials.API_VERSION, false, Arrays.asList(firstAccountId, contactId)));
        CollectionResponse parsedDeleteResponse = new CollectionResponse(deleteResponse.asJSONArray());

        // Checking response
        Assert.assertEquals(2, parsedDeleteResponse.subResponses.size());
        Assert.assertEquals(firstAccountId, parsedDeleteResponse.subResponses.get(0).id);
        Assert.assertTrue(parsedDeleteResponse.subResponses.get(0).success);
        Assert.assertTrue(parsedDeleteResponse.subResponses.get(0).errors.isEmpty());
        Assert.assertEquals(contactId, parsedDeleteResponse.subResponses.get(1).id);
        Assert.assertTrue(parsedDeleteResponse.subResponses.get(1).success);
        Assert.assertTrue(parsedDeleteResponse.subResponses.get(1).errors.isEmpty());

        // Making sure deleted account is gone using retrieve
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, "Account", firstAccountId, Arrays.asList("Id")));
        Assert.assertEquals("404 was expected", HttpURLConnection.HTTP_NOT_FOUND, response.getStatusCode());

        // Making sure only deleted account is gone using collection retrieve
        RestRequest accountsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION, "Account", Arrays.asList(firstAccountId, secondAccountId), Arrays.asList("Id", "Name"));
        JSONArray accountsRetrieved = restClient.sendSync(accountsRetrieveRequest).asJSONArray();
        Assert.assertEquals(2, accountsRetrieved.length());
        Assert.assertTrue(accountsRetrieved.isNull(0));
        Assert.assertEquals(secondAccountName, accountsRetrieved.getJSONObject(1).getString("Name"));

        // Making sure deleted contact is gone using retrieve
        response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, "Contact", contactId, Arrays.asList("Id")));
        Assert.assertEquals("404 was expected", HttpURLConnection.HTTP_NOT_FOUND, response.getStatusCode());

        // Making sure contact is gone using collection retrieve
        RestRequest contactsRetrievedRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION, "Contact", Arrays.asList(contactId), Arrays.asList("Id", "LastName"));
        JSONArray contactsRetrieved = restClient.sendSync(contactsRetrievedRequest).asJSONArray();
        Assert.assertEquals(1, contactsRetrieved.length());
        contactsRetrieved = restClient.sendSync(contactsRetrievedRequest).asJSONArray();
        Assert.assertTrue(contactsRetrieved.isNull(0));
    }

    @Test
    public void testCollectionCreateWithBadRecordAndAllOrNoneFalse() throws JSONException, IOException {
        String accountName = ENTITY_NAME_PREFIX + "_account_" + System.nanoTime();
        String contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime();

        JSONArray records = makeRecords(
            Arrays.asList("Account", "BadField", accountName),
            Arrays.asList("Contact", "LastName", contactName)
        );

        RestResponse response = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION, false , records));
        JSONArray jsonResponse = response.asJSONArray();
        CollectionResponse parsedResponse = new CollectionResponse(jsonResponse);
        Assert.assertEquals(2, parsedResponse.subResponses.size());
        Assert.assertNull(parsedResponse.subResponses.get(0).id);
        Assert.assertFalse(parsedResponse.subResponses.get(0).success);
        Assert.assertFalse(parsedResponse.subResponses.get(0).errors.isEmpty());
        Assert.assertEquals("INVALID_FIELD", parsedResponse.subResponses.get(0).errors.get(0).statusCode);
        Assert.assertTrue(parsedResponse.subResponses.get(1).id.startsWith("003"));
        Assert.assertTrue(parsedResponse.subResponses.get(1).success);
        Assert.assertTrue(parsedResponse.subResponses.get(1).errors.isEmpty());
    }

    @Test
    public void testCollectionCreateWithBadRecordAndAllOrNoneTrue() throws JSONException, IOException {
        String accountName = ENTITY_NAME_PREFIX + "_account_" + System.nanoTime();
        String contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime();

        JSONArray records = makeRecords(
            Arrays.asList("Account", "BadField", accountName),
            Arrays.asList("Contact", "LastName", contactName)
        );

        RestResponse response = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION, true , records));
        JSONArray jsonResponse = response.asJSONArray();
        CollectionResponse parsedResponse = new CollectionResponse(jsonResponse);
        Assert.assertEquals(2, parsedResponse.subResponses.size());
        Assert.assertNull(parsedResponse.subResponses.get(0).id);
        Assert.assertFalse(parsedResponse.subResponses.get(0).success);
        Assert.assertFalse(parsedResponse.subResponses.get(0).errors.isEmpty());
        Assert.assertEquals("INVALID_FIELD", parsedResponse.subResponses.get(0).errors.get(0).statusCode);
        Assert.assertNull(parsedResponse.subResponses.get(1).id);
        Assert.assertFalse(parsedResponse.subResponses.get(1).success);
        Assert.assertFalse(parsedResponse.subResponses.get(1).errors.isEmpty());
        Assert.assertEquals("ALL_OR_NONE_OPERATION_ROLLED_BACK", parsedResponse.subResponses.get(1).errors.get(0).statusCode);
    }

    /**
     * Testing a limits call to the server - check response
     * @throws Exception
     */
    @Test
    public void testLimits() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForLimits(TestCredentials.API_VERSION));
        checkResponse(response, HttpURLConnection.HTTP_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "DailyApiRequests");
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
        Assert.assertEquals("Response code should be HTTP OK", HttpURLConnection.HTTP_OK, response.getStatusCode());
        return response;
    }

    /**
     * Assert that the {@link RestResponse} returned from {@link #getStreamTestResponse()} is valid.
     * @param in the {@link InputStream} of response data
     * @throws IOException if the stream could not be read
     * @throws JSONException if the response could not be decoded to a valid JSON object
     */
    private void assertStreamTestResponseStreamIsValid(InputStream in) throws IOException, JSONException {
        final String responseData = Encryptor.getStringFromStream(in);
        Assert.assertNotNull("The response should contain data", responseData);
        final JSONObject responseJson = new JSONObject(responseData);
        checkKeys(responseJson, "sobjects", "search", "recent");
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
        Map<String, Object> fields = new HashMap<>();
        String newAccountName = ENTITY_NAME_PREFIX + System.nanoTime();
        fields.put(NAME, newAccountName);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForCreate(TestCredentials.API_VERSION, ACCOUNT, fields));
        String newAccountId = response.asJSONObject().getString("id");
        return new IdName(newAccountId, newAccountName);
    }

    /**
     * Helper method to create multiple accounts with a unique name and returns their name and id
     */
    private List<IdName> createAccounts(int count, String additionalPrefix) throws Exception {
        Map<String, Object> fields = new HashMap<>();
        List<String> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();

        // Creating names
        for (int i = 0; i < count; i++) {
            names.add(ENTITY_NAME_PREFIX + additionalPrefix + System.nanoTime());
        }

        // Creating accounts collecting ids
        List<RestRequest> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fields.put(NAME, names.get(i));
            requests.add(RestRequest.getRequestForCreate(TestCredentials.API_VERSION, ACCOUNT, fields));
            if (requests.size() == 25) {
                ids.addAll(getCreatedIds(requests));
                requests.clear();
            }
        }
        if (requests.size() > 0) {
            ids.addAll(getCreatedIds(requests));
        }

        // Build IdName's
        List<IdName> idNames = new ArrayList<>();
        for (int i=0;i<ids.size(); i++) {
            idNames.add(new IdName(ids.get(i), names.get(i)));
        }
        return idNames;
    }

    private List<String> getCreatedIds(List<RestRequest> createRequests) throws Exception {
        List<String> ids = new ArrayList<>();
        BatchResponse batchResponse = new BatchResponse(restClient.sendSync(RestRequest.getBatchRequest(TestCredentials.API_VERSION, false, createRequests)).asJSONObject());
        for (JSONObject response : batchResponse.results) {
            ids.add(response.getJSONObject("result").getString("id"));
        }
        return ids;
    }

    /**
     * Helper method to delete any entities created by one of the test
     */
    private void cleanup() {
        try {
            RestResponse response = restClient.sendSync(RestRequest.getRequestForSearch(TestCredentials.API_VERSION, "find {" + ENTITY_NAME_PREFIX + "}"));
            JSONArray jsonResults = response.asJSONObject().getJSONArray("searchRecords");
            List<RestRequest> requests = new ArrayList<>();
            for (int i = 0; i < jsonResults.length(); i++) {
                JSONObject jsonResult = jsonResults.getJSONObject(i);
                String objectType = jsonResult.getJSONObject("attributes").getString("type");
                String id = jsonResult.getString("Id");
                RestRequest deleteRequest = RestRequest.getRequestForDelete(TestCredentials.API_VERSION, objectType, id);
                requests.add(deleteRequest);
                if (requests.size() == 25) {
                    restClient.sendSync(RestRequest.getBatchRequest(TestCredentials.API_VERSION, false, requests));
                    requests.clear();
                }
            }
            if (requests.size() > 0) {
                restClient.sendSync(RestRequest.getBatchRequest(TestCredentials.API_VERSION, false, requests));
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
        Assert.assertEquals(expectedStatusCode  + " response expected", expectedStatusCode, response.getStatusCode());

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
            Assert.fail("Failed to parse response body");
        }
    }

    /**
     * Helper method to check if a jsonObject has all the expected keys
     * @param jsonObject
     * @param expectedKeys
     */
    private void checkKeys(JSONObject jsonObject, String... expectedKeys) {
        for (String expectedKey : expectedKeys) {
            Assert.assertTrue("Object should have key: " + expectedKey, jsonObject.has(expectedKey));
        }
    }

    /**
     * Make JSONArray of JSONObject's representing records with provided type an field
     * @param typeFieldNameValues a list of list containing objectType, fieldName, otherFieldName, otherFieldValue etc
     * @return
     */
    private JSONArray makeRecords(List<String>... typeFieldNameValues)
        throws JSONException {
        JSONArray records = new JSONArray();
        for (List<String> entry : typeFieldNameValues) {
            JSONObject record = new JSONObject();
            JSONObject recordAttributes = new JSONObject();
            recordAttributes.put("type", entry.get(0));
            record.put("attributes", recordAttributes);
            for (int i=1; i<entry.size(); i +=2 ) {
                record.put(entry.get(i), entry.get(i+1));
            }
            records.put(record);
        }

        return records;
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

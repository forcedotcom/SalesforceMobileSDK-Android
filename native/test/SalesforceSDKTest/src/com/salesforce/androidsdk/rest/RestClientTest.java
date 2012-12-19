/*
 * Copyright (c) 2011, salesforce.com, inc.
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONObject;

import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.TestCredentials;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.auth.OAuth2;
import com.salesforce.androidsdk.auth.OAuth2.TokenEndpointResponse;
import com.salesforce.androidsdk.rest.RestClient.AuthTokenProvider;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;

/**
 * Tests for RestClient
 *
 * Does live calls to a test org
 *
 */
public class RestClientTest extends InstrumentationTestCase {

    private static final String ENTITY_NAME_PREFIX = "RestClientTest";
    private static final String BAD_TOKEN = "bad-token";
    private ClientInfo clientInfo;
    private HttpAccess httpAccess;
    private RestClient restClient;
    private String authToken;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestCredentials.init(getInstrumentation().getContext());
        httpAccess = new HttpAccess(null, null);
        TokenEndpointResponse refreshResponse = OAuth2.refreshAuthToken(httpAccess, new URI(TestCredentials.INSTANCE_URL), TestCredentials.CLIENT_ID, TestCredentials.REFRESH_TOKEN);
        authToken = refreshResponse.authToken;
        clientInfo = new ClientInfo(TestCredentials.CLIENT_ID, new URI(TestCredentials.INSTANCE_URL), new URI(TestCredentials.LOGIN_URL), new URI(TestCredentials.IDENTITY_URL), TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME, TestCredentials.USER_ID, TestCredentials.ORG_ID);
        restClient = new RestClient(clientInfo, authToken, httpAccess, null);
    }

    @Override
    public void tearDown() throws Exception {
        cleanup(); // try to remove any entities the test created
        httpAccess.resetNetwork();
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
        RestClient unauthenticatedRestClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, null);
        RestResponse response = unauthenticatedRestClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        assertFalse("Expected error", response.isSuccess());
        checkResponse(response, HttpStatus.SC_UNAUTHORIZED, true);
    }

    /**
     * Testing a call with a bad auth token when restClient has a token provider
     * Expect token provider to be invoked and new token to be used.
     * @throws URISyntaxException
     * @throws IOException
     */
    public void testCallWithBadTokenAndTokenProvider() throws URISyntaxException, IOException {
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
        };
        RestClient unauthenticatedRestClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, authTokenProvider);

        assertEquals("RestClient should be using the bad token initially", BAD_TOKEN, unauthenticatedRestClient.getAuthToken());
        RestResponse response = unauthenticatedRestClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        assertEquals("RestClient should now be using the good token", authToken, unauthenticatedRestClient.getAuthToken());

        assertTrue("Expected success", response.isSuccess());
        checkResponse(response, HttpStatus.SC_OK, false);
    }


    /**
     * Testing a get versions call to the server - check response
     * @throws Exception
     */
    public void testGetVersions() throws Exception {
        // We don't need to be authenticated
        RestClient unauthenticatedRestClient = new RestClient(clientInfo, BAD_TOKEN, httpAccess, null);
        RestResponse response = unauthenticatedRestClient.sendSync(RestRequest.getRequestForVersions());
        checkResponse(response, HttpStatus.SC_OK, true);
        checkKeys(response.asJSONArray().getJSONObject(0), "label", "url", "version");
    }

    /**
     * Testing a get resources call to the server - check response
     * @throws Exception
     */
    public void testGetResources() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION));
        checkResponse(response, HttpStatus.SC_OK, false);
        checkKeys(response.asJSONObject(), "sobjects", "search", "recent");
    }


    /**
     * Testing a describe global call to the server - check response
     * @throws Exception
     */
    public void testDescribeGlobal() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForDescribeGlobal(TestCredentials.API_VERSION));
        checkResponse(response, HttpStatus.SC_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "encoding", "maxBatchSize", "sobjects");
        checkKeys(jsonResponse.getJSONArray("sobjects").getJSONObject(0), "name", "label", "custom", "keyPrefix");
    }

    /**
     * Testing a metadata call to the server - check response
     * @throws Exception
     */
    public void testMetadata() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForMetadata(TestCredentials.API_VERSION, "account"));
        checkResponse(response, HttpStatus.SC_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "objectDescribe", "recentItems");
        checkKeys(jsonResponse.getJSONObject("objectDescribe"), "name", "label", "keyPrefix");
        assertEquals("Wrong object name", "Account", jsonResponse.getJSONObject("objectDescribe").getString("name"));
    }

    /**
     * Testing a describe call to the server - check response
     * @throws Exception
     */
    public void testDescribe() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForDescribe(TestCredentials.API_VERSION, "account"));
        checkResponse(response, HttpStatus.SC_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "name", "fields", "urls", "label");
        assertEquals("Wrong object name", "Account", jsonResponse.getString("name"));
    }

    /**
     * Testing a create call to the server - check response
     * @throws Exception
     */
    public void testCreate() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        String newAccountName = ENTITY_NAME_PREFIX + System.nanoTime();
        fields.put("name", newAccountName);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForCreate(TestCredentials.API_VERSION, "account", fields));
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
        List<String> fields = Arrays.asList(new String[] {"name", "ownerId"});
        IdName newAccountIdName = createAccount();
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, "account", newAccountIdName.id, fields));
        checkResponse(response, HttpStatus.SC_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "attributes", "Name", "OwnerId", "Id");
        assertEquals("Wrong row returned", newAccountIdName.name, jsonResponse.getString("Name"));
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
        fields.put("name", updatedAccountName);
        RestResponse updateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION, "account", newAccountIdName.id, fields));
        assertTrue("Update failed", updateResponse.isSuccess());

        // Retrieve - expect updated name
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, "account", newAccountIdName.id, Arrays.asList(new String[] {"name"})));
        assertEquals("Wrong row returned", updatedAccountName, response.asJSONObject().getString("Name"));
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
        RestResponse deleteResponse = restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, "account", newAccountIdName.id));
        assertTrue("Delete failed", deleteResponse.isSuccess());

        // Retrieve - expect 404
        List<String> fields = Arrays.asList(new String[] {"name"});
        RestResponse response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION, "account", newAccountIdName.id, fields));
        assertEquals("404 was expected", HttpStatus.SC_NOT_FOUND, response.getStatusCode());
    }


    /**
     * Testing a query call to the server.
     * Create new account then look for it using soql.
     * @throws Exception
     */
    public void testQuery() throws Exception {
        IdName newAccountIdName = createAccount();
        RestResponse response = restClient.sendSync(RestRequest.getRequestForQuery(TestCredentials.API_VERSION, "select name from account where id = '" + newAccountIdName.id + "'"));
        checkResponse(response, HttpStatus.SC_OK, false);
        JSONObject jsonResponse = response.asJSONObject();
        checkKeys(jsonResponse, "done", "totalSize", "records");
        assertEquals("Expected one row", 1, jsonResponse.getInt("totalSize"));
        assertEquals("Wrong row returned", newAccountIdName.name, jsonResponse.getJSONArray("records").getJSONObject(0).get("Name"));
    }

    /**
     * Testing a search call to the server.
     * Create new account then look for it using sosl.
     * @throws Exception
     */
    public void testSearch() throws Exception {
        IdName newAccountIdName = createAccount();
        RestResponse response = restClient.sendSync(RestRequest.getRequestForSearch(TestCredentials.API_VERSION, "find {" + ENTITY_NAME_PREFIX + "}"));
        checkResponse(response, HttpStatus.SC_OK, true);
        JSONArray matchingRows = response.asJSONArray();
        assertEquals("Expected one row", 1, matchingRows.length());
        JSONObject matchingRow = matchingRows.getJSONObject(0);
        checkKeys(matchingRow, "attributes", "Id");
        assertEquals("Wrong row returned", newAccountIdName.id, matchingRow.get("Id"));
    }
    
    /**
     * Testing that calling resume more than once on a RestResponse doesn't throw an exception
     * @throws Exception 
     */
    public void testDoubleConsume() throws Exception {
        RestResponse response = restClient.sendSync(RestRequest.getRequestForMetadata(TestCredentials.API_VERSION, "account"));
        checkResponse(response, HttpStatus.SC_OK, false);
        try {
        	response.consume();
        	response.consume();
        }
        catch (IllegalStateException e) {
        	fail("Calling consume should not have thrown an exception");
        }
    }


    /**
     * Helper method to create a account with a unique name and returns its name and id
     */
    private IdName createAccount() throws Exception {
        Map<String, Object> fields = new HashMap<String, Object>();
        String newAccountName = ENTITY_NAME_PREFIX + "-" + System.nanoTime();
        fields.put("name", newAccountName);
        RestResponse response = restClient.sendSync(RestRequest.getRequestForCreate(TestCredentials.API_VERSION, "account", fields));
        String newAccountId = response.asJSONObject().getString("id");
        return new IdName(newAccountId, newAccountName);
    }

    /**
     * Helper method to delete any entities created by one of the test
     */
    private void cleanup() {
        try {
            RestResponse searchResponse = restClient.sendSync(RestRequest.getRequestForSearch(TestCredentials.API_VERSION, "find {" + ENTITY_NAME_PREFIX + "}"));
            JSONArray matchingRows = searchResponse.asJSONArray();
            for (int i=0; i<matchingRows.length(); i++) {
                JSONObject matchingRow = matchingRows.getJSONObject(i);
                String matchingRowType = matchingRow.getJSONObject("attributes").getString("type");
                String matchingRowId = matchingRow.getString("Id");
                restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION, matchingRowType, matchingRowId));
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

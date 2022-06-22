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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.util.JSONTestHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okio.Buffer;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class RestRequestTest {
	
	private static final String TEST_API_VERSION = "v99.0";
	private static final String TEST_OBJECT_TYPE = "testObjectType";
	private static final String TEST_LAYOUT_TYPE_COMPACT = "Compact";
	private static final String TEST_FORM_FACTOR_MEDIUM = "Medium";
	private static final String TEST_MODE_EDIT = "Edit";
	private static final String TEST_RECORD_TYPE_ID = "test_record_type_id";
    private static final String TEST_OTHER_OBJECT_TYPE = "testOtherObjectType";
	private static final String TEST_OBJECT_ID = "testObjectId";
	private static final String TEST_EXTERNAL_ID_FIELD = "testExternalIdField";
	private static final String TEST_EXTERNAL_ID = "testExternalId";
	private static final String TEST_QUERY = "testQuery";
	private static final String TEST_SEARCH = "testSearch";
	private static final String TEST_FIELDS_STRING = "{\"fieldX\":\"value with spaces\",\"name\":\"testAccount\"}";
	private static final List<String> TEST_FIELDS_LIST = Collections.unmodifiableList(Arrays.asList("name", "fieldX"));
	private static final String TEST_FIELDS_LIST_STRING = "name%2CfieldX";
	private static final List<String> TEST_OBJECTS_LIST = Collections.unmodifiableList(Arrays.asList("Account", "Contact"));
	private static final String TEST_OBJECTS_LIST_STRING = "Account%2CContact";
    private static final String TEST_OTHER_OBJECT_TYPE_PLURAL = "testOtherObjectTypes";
	public static final String TEST_REF_PARENT = "testRefParent";
	public static final String TEST_REF_CHILD = "testRefChild";

	private static Map<String, Object> TEST_FIELDS;
	static {
		Map<String, Object> fields = new HashMap<String, Object>();
		fields.put("name", "testAccount");
		fields.put("fieldX", "value with spaces");
		TEST_FIELDS = Collections.unmodifiableMap(fields);
	}

    private static Map<String, Object> TEST_OTHER_FIELDS;
    static {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("name", "testContact");
        fields.put("fieldY", "value with spaces");
        TEST_OTHER_FIELDS = Collections.unmodifiableMap(fields);
    }

	/**
	 * Test for getRequestForUserInfo
	 */
	@Test
	public void testGetRequestForUserInfo() {
		RestRequest request = RestRequest.getRequestForUserInfo();
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/oauth2/userinfo", request.getPath());
		Assert.assertEquals("Wrong endpoint", RestRequest.RestEndpoint.LOGIN, request.getEndpoint());
		Assert.assertNull("Wrong request entity", request.getRequestBody());
		Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForVersions
	 */
    @Test
	public void testGetRequestForVersions() {
		RestRequest request = RestRequest.getRequestForVersions();
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/", request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForResources
	 */
    @Test
	public void testGetRequestForResources() {
		RestRequest request = RestRequest.getRequestForResources(TEST_API_VERSION);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/", request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForDescribeGlobal
	 */
    @Test
	public void testGetRequestForDescribeGlobal() {
		RestRequest request = RestRequest.getRequestForDescribeGlobal(TEST_API_VERSION);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/", request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForMetadata
	 */
    @Test
	public void testGetRequestForMetadata() {
		RestRequest request = RestRequest.getRequestForMetadata(TEST_API_VERSION, TEST_OBJECT_TYPE);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/", request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForDescribe
	 */
    @Test
	public void testGetRequestForDescribe() {
		RestRequest request = RestRequest.getRequestForDescribe(TEST_API_VERSION, TEST_OBJECT_TYPE);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/describe/", request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForObjectLayout without formFactor.
	 */
	@Test
	public void testGetRequestForObjectLayoutWithoutFormFactor() {
		RestRequest request = RestRequest.getRequestForObjectLayout(TEST_API_VERSION,
				TEST_OBJECT_TYPE, null, null, null, null);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION +
				"/ui-api/layout/" + TEST_OBJECT_TYPE, request.getPath());
		Assert.assertNull("Wrong request entity", request.getRequestBody());
		Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForObjectLayout with formFactor.
	 */
	@Test
	public void testGetRequestForObjectLayoutWithFormFactor() {
		RestRequest request = RestRequest.getRequestForObjectLayout(TEST_API_VERSION,
				TEST_OBJECT_TYPE, TEST_FORM_FACTOR_MEDIUM, null, null, null);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION +
				"/ui-api/layout/" + TEST_OBJECT_TYPE + "?formFactor=" + TEST_FORM_FACTOR_MEDIUM, request.getPath());
		Assert.assertNull("Wrong request entity", request.getRequestBody());
		Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForObjectLayout without layoutType.
	 */
	@Test
	public void testGetRequestForObjectLayoutWithoutLayoutType() {
		RestRequest request = RestRequest.getRequestForObjectLayout(TEST_API_VERSION,
				TEST_OBJECT_TYPE, null, null, null, null);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION +
                "/ui-api/layout/" + TEST_OBJECT_TYPE, request.getPath());
		Assert.assertNull("Wrong request entity", request.getRequestBody());
		Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

    /**
     * Test for getRequestForObjectLayout with layoutType.
     */
    @Test
    public void testGetRequestForObjectLayoutWithLayoutType() {
		RestRequest request = RestRequest.getRequestForObjectLayout(TEST_API_VERSION,
				TEST_OBJECT_TYPE, null, TEST_LAYOUT_TYPE_COMPACT, null, null);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION +
                "/ui-api/layout/" + TEST_OBJECT_TYPE + "?layoutType=" + TEST_LAYOUT_TYPE_COMPACT, request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
    }

	/**
	 * Test for getRequestForObjectLayout without mode.
	 */
	@Test
	public void testGetRequestForObjectLayoutWithoutMode() {
		RestRequest request = RestRequest.getRequestForObjectLayout(TEST_API_VERSION,
				TEST_OBJECT_TYPE, null, null, null, null);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION +
				"/ui-api/layout/" + TEST_OBJECT_TYPE, request.getPath());
		Assert.assertNull("Wrong request entity", request.getRequestBody());
		Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForObjectLayout with mode.
	 */
	@Test
	public void testGetRequestForObjectLayoutWithMode() {
		RestRequest request = RestRequest.getRequestForObjectLayout(TEST_API_VERSION,
				TEST_OBJECT_TYPE, null, null, TEST_MODE_EDIT, null);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION +
				"/ui-api/layout/" + TEST_OBJECT_TYPE + "?mode=" + TEST_MODE_EDIT, request.getPath());
		Assert.assertNull("Wrong request entity", request.getRequestBody());
		Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForObjectLayout without recordTypeId.
	 */
	@Test
	public void testGetRequestForObjectLayoutWithoutRecordTypeId() {
		RestRequest request = RestRequest.getRequestForObjectLayout(TEST_API_VERSION,
				TEST_OBJECT_TYPE, null, null, null, null);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION +
				"/ui-api/layout/" + TEST_OBJECT_TYPE, request.getPath());
		Assert.assertNull("Wrong request entity", request.getRequestBody());
		Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForObjectLayout with recordTypeId.
	 */
	@Test
	public void testGetRequestForObjectLayoutWithRecordTypeId() {
		RestRequest request = RestRequest.getRequestForObjectLayout(TEST_API_VERSION,
				TEST_OBJECT_TYPE, null, null, null, TEST_RECORD_TYPE_ID);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION +
				"/ui-api/layout/" + TEST_OBJECT_TYPE + "?recordTypeId=" + TEST_RECORD_TYPE_ID, request.getPath());
		Assert.assertNull("Wrong request entity", request.getRequestBody());
		Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForCreate
	 * @throws IOException 
	 * @throws UnsupportedEncodingException 
	 * @throws JSONException 
	 */
    @Test
	public void testGetRequestForCreate() throws UnsupportedEncodingException, IOException, JSONException {
		RestRequest request = RestRequest.getRequestForCreate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_FIELDS);
        Assert.assertEquals("Wrong method", RestMethod.POST, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE, request.getPath());
		JSONTestHelper.assertSameJSON("Wrong request entity", new JSONObject(TEST_FIELDS_STRING), new JSONObject(bodyToString(request)));
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForRetrieve
	 * @throws UnsupportedEncodingException 
	 */
    @Test
	public void testGetRequestForRetrieve() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForRetrieve(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS_LIST);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/" + TEST_OBJECT_ID + "?fields=" + TEST_FIELDS_LIST_STRING, request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForUpdate
	 * @throws IOException 
	 * @throws UnsupportedEncodingException 
	 * @throws JSONException 
	 */
    @Test
	public void testGetRequestForUpdate() throws IOException, JSONException {
		RestRequest request = RestRequest.getRequestForUpdate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS);
        Assert.assertEquals("Wrong method", RestMethod.PATCH, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/" + TEST_OBJECT_ID, request.getPath());
		JSONTestHelper.assertSameJSON("Wrong request entity", new JSONObject(TEST_FIELDS_STRING), new JSONObject(bodyToString(request)));
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForUpsert
	 * @throws IOException 
	 * @throws UnsupportedEncodingException 
	 * @throws JSONException 
	 */
    @Test
	public void testGetRequestForUpsert() throws IOException, JSONException {
		RestRequest request = RestRequest.getRequestForUpsert(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_EXTERNAL_ID_FIELD, TEST_EXTERNAL_ID, TEST_FIELDS);
        Assert.assertEquals("Wrong method", RestMethod.PATCH, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/" + TEST_EXTERNAL_ID_FIELD + "/" + TEST_EXTERNAL_ID, request.getPath());
		JSONTestHelper.assertSameJSON("Wrong request entity", new JSONObject(TEST_FIELDS_STRING), new JSONObject(bodyToString(request)));
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForDelete
	 */
    @Test
	public void testGetRequestForDelete() {
		RestRequest request = RestRequest.getRequestForDelete(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID);
        Assert.assertEquals("Wrong method", RestMethod.DELETE, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/" + TEST_OBJECT_ID, request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForQuery
	 * @throws UnsupportedEncodingException 
	 */
    @Test
	public void testGetRequestForQuery() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForQuery(TEST_API_VERSION, TEST_QUERY);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/query?q=" + TEST_QUERY, request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForQuery specifying a batch size
	 * @throws UnsupportedEncodingException
	 */
	@Test
	public void testGetRequestForQueryWithBatchSize() throws UnsupportedEncodingException {
		RestRequest request500 = RestRequest.getRequestForQuery(TEST_API_VERSION, TEST_QUERY, 500);
		RestRequest request199 = RestRequest.getRequestForQuery(TEST_API_VERSION, TEST_QUERY, 199);
		RestRequest request2001 = RestRequest.getRequestForQuery(TEST_API_VERSION, TEST_QUERY, 2001);
		Assert.assertEquals("Wrong method", RestMethod.GET, request500.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/query?q=" + TEST_QUERY, request500.getPath());
		Assert.assertNull("Wrong request entity", request500.getRequestBody());
		Assert.assertEquals("batchSize=500", request500.getAdditionalHttpHeaders().get(RestRequest.SFORCE_QUERY_OPTIONS));
		Assert.assertEquals("batchSize=200", request199.getAdditionalHttpHeaders().get(RestRequest.SFORCE_QUERY_OPTIONS));
		Assert.assertNull("Wrong additional headers", request2001.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForSearch
	 * @throws UnsupportedEncodingException 
	 */
    @Test
	public void testGetRequestForSeach() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForSearch(TEST_API_VERSION, TEST_SEARCH);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/search?q=" + TEST_SEARCH, request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForSearchScopeAndOrder
	 * @throws UnsupportedEncodingException 
	 */
    @Test
	public void testGetRequestForSeachScopeAndOrder() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForSearchScopeAndOrder(TEST_API_VERSION);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/search/scopeOrder", request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForSearchResultLayout
	 * @throws UnsupportedEncodingException 
	 */
    @Test
	public void testGetRequestForSearchResultLayout() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForSearchResultLayout(TEST_API_VERSION, TEST_OBJECTS_LIST);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/search/layout?q=" + TEST_OBJECTS_LIST_STRING, request.getPath());
        Assert.assertNull("Wrong request entity", request.getRequestBody());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

    @Test
	public void testAdditionalHeaders() {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("X-Foo", "x-foo-header");
		RestRequest req = new RestRequest(RestMethod.GET, "/my/foo/", headers);
        Assert.assertEquals("Wrong method", RestMethod.GET, req.getMethod());
        Assert.assertEquals("Wrong path", "/my/foo/", req.getPath());
        Assert.assertNull("Wrong entity", req.getRequestBody());
        Assert.assertEquals("Wrong headers", headers, req.getAdditionalHttpHeaders());
	}

    /**
     * Test for getCompositeRequest
     * @throws JSONException
     */
    @Test
    public void testGetCompositeRequest() throws JSONException, IOException {
        LinkedHashMap<String, RestRequest> requests = new LinkedHashMap<>();
        requests.put("ref1", RestRequest.getRequestForUpdate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS));
        requests.put("ref2", RestRequest.getRequestForDelete(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID));
        RestRequest request = RestRequest.getCompositeRequest(TEST_API_VERSION, true, requests);
        Assert.assertEquals("Wrong method", RestMethod.POST, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/composite", request.getPath());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
        JSONObject expectedBodyJson = new JSONObject();
        expectedBodyJson.put("allOrNone", true);
        expectedBodyJson.put("compositeRequest",
                new JSONArray(String.format(""
                                + "["
                                + "  {"
                                + "    \"method\": \"PATCH\","
                                + "    \"url\": \"/services/data/v99.0/sobjects/%s/%s\","
                                + "    \"body\": %s,"
                                + "    \"referenceId\": \"ref1\""
                                + "  },"
                                + "  {"
                                + "    \"method\": \"DELETE\","
                                + "    \"url\": \"/services/data/v99.0/sobjects/%s/%s\","
                                + "    \"referenceId\": \"ref2\""
                                + "  }"
                                + "]",
                        TEST_OBJECT_TYPE, TEST_OBJECT_ID, new JSONObject(TEST_FIELDS),
                        TEST_OBJECT_TYPE, TEST_OBJECT_ID)));
        JSONObject actualBodyJson = new JSONObject(bodyToString(request));
        JSONTestHelper.assertSameJSON("Wrong request entity", expectedBodyJson, actualBodyJson);
    }

    /**
     * Test for getBatchRequest
     * @throws JSONException
     */
    @Test
    public void testGetBatchRequest() throws JSONException, IOException {
        RestRequest[] requests = new RestRequest[]{
                RestRequest.getRequestForUpdate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS),
                RestRequest.getRequestForDelete(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID)
        };
        RestRequest request = RestRequest.getBatchRequest(TEST_API_VERSION, true, Arrays.asList(requests));
        Assert.assertEquals("Wrong method", RestMethod.POST, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/composite/batch", request.getPath());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
        JSONObject expectedBodyJson = new JSONObject();
        expectedBodyJson.put("haltOnError", true);
        expectedBodyJson.put("batchRequests",
                new JSONArray(String.format(""
                        + "["
                        + "  {"
                        + "    \"method\": \"PATCH\","
                        + "    \"url\": \"v99.0/sobjects/%s/%s\","
                        + "    \"richInput\": %s"
                        + "  },"
                        + "  {"
                        + "    \"method\": \"DELETE\","
                        + "    \"url\": \"v99.0/sobjects/%s/%s\""
                        + "  }"
                        + "]",
                TEST_OBJECT_TYPE, TEST_OBJECT_ID, new JSONObject(TEST_FIELDS),
                TEST_OBJECT_TYPE, TEST_OBJECT_ID)));
        JSONObject actualBodyJson = new JSONObject(bodyToString(request));
        JSONTestHelper.assertSameJSON("Wrong request entity", expectedBodyJson, actualBodyJson);
    }

    /**
     * Test for getRequestForSObjectTree
     * @throws JSONException
     */
    @Test
    public void testGetRequestForSObjectTree() throws JSONException, IOException {
        List<RestRequest.SObjectTree> childrenTrees = new ArrayList<>();
        childrenTrees.add(new RestRequest.SObjectTree(TEST_OTHER_OBJECT_TYPE, TEST_OTHER_OBJECT_TYPE_PLURAL, TEST_REF_CHILD, TEST_OTHER_FIELDS, null));
        List<RestRequest.SObjectTree> recordTrees = new ArrayList<>();
        recordTrees.add(new RestRequest.SObjectTree(TEST_OBJECT_TYPE, null, TEST_REF_PARENT, TEST_FIELDS, childrenTrees));
        RestRequest request = RestRequest.getRequestForSObjectTree(TEST_API_VERSION, TEST_OBJECT_TYPE, recordTrees);
        Assert.assertEquals("Wrong method", RestMethod.POST, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/composite/tree/" + TEST_OBJECT_TYPE, request.getPath());
        Assert.assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
        JSONObject expectedBodyJson = new JSONObject();
        expectedBodyJson.put("records",
                new JSONArray(String.format(""
                        + "["
                        + "  {"
                        + "    \"name\": \"testAccount\","
                        + "    \"fieldX\": \"value with spaces\","
                        + "    \"attributes\": {"
                        + "      \"type\": \"%s\","
						+ "      \"referenceId\": \"%s\""
                        + "    },"
                        + "    \"%s\": {"
                        + "      \"records\": ["
                        + "        {"
                        + "          \"name\": \"testContact\","
                        + "          \"fieldY\": \"value with spaces\","
                        + "          \"attributes\": {"
                        + "            \"type\": \"%s\","
						+ "            \"referenceId\": \"%s\""
                        + "          }"
                        + "        }"
                        + "      ]"
                        + "    }"
                        + "  }"
                        + "]",
						TEST_OBJECT_TYPE,
						TEST_REF_PARENT,
						TEST_OTHER_OBJECT_TYPE_PLURAL,
						TEST_OTHER_OBJECT_TYPE,
						TEST_REF_CHILD)));

        JSONObject actualBodyJson = new JSONObject(bodyToString(request));

        JSONTestHelper.assertSameJSON("Wrong request entity", expectedBodyJson, actualBodyJson);
    }

    @Test
    public void testGetRequestForNotification() throws Exception {
        String notificationId = "testID";
        RestRequest request = RestRequest.getRequestForNotification(TEST_API_VERSION, notificationId);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/connect/notifications/" + notificationId, request.getPath());
    }

    @Test
    public void testGetRequestForNotificationUpdate() throws Exception {
        String notificationId = "testID";
        RestRequest request = RestRequest.getRequestForNotificationUpdate(TEST_API_VERSION, notificationId, true, null);
        Assert.assertEquals("Wrong method", RestMethod.PATCH, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/connect/notifications/" + notificationId, request.getPath());
    }

    @Test
    public void testGetRequestForNotificationsUpdate() throws Exception {
        List<String> notificationIds = Arrays.asList("testID1", "testID2");
        RestRequest request =  RestRequest.getRequestForNotificationsUpdate(TEST_API_VERSION, notificationIds, null, true, null);
        Assert.assertEquals("Wrong method", RestMethod.PATCH, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/connect/notifications/", request.getPath());
        JSONObject expectedBodyJson = new JSONObject();
        expectedBodyJson.put("notificationIds", new JSONArray(notificationIds));
        expectedBodyJson.put("read", true);
        JSONObject actualBodyJson = new JSONObject(bodyToString(request));
        JSONTestHelper.assertSameJSON("Wrong request entity", expectedBodyJson, actualBodyJson);
    }

    @Test
	public void testGetRequestForPrimingRecords() throws Exception {
    	RestRequest request = RestRequest.getRequestForPrimingRecords(TEST_API_VERSION, null, null);
    	Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/connect/briefcase/priming-records", request.getPath());
	}

	@Test
	public void testGetRequestForPrimingRecordsWithRelayToken() throws Exception {
		RestRequest request = RestRequest.getRequestForPrimingRecords(TEST_API_VERSION, "my-relay-token", null);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/connect/briefcase/priming-records?relayToken=my-relay-token", request.getPath());
	}

	@Test
	public void testGetRequestForPrimingRecordsWithChangedAfterTimestamp() throws Exception {
    	long timestamp = PrimingRecordsResponse.TIMESTAMP_FORMAT.parse("2022-01-31T03:50:10.000Z").getTime();
		RestRequest request = RestRequest.getRequestForPrimingRecords(TEST_API_VERSION, null, timestamp);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/connect/briefcase/priming-records?changedAfterTimestamp=2022-01-31T03%3A50%3A10.000Z", request.getPath());
	}

	@Test
	public void testGetRequestForPrimingRecordsWithRelayTokenAndChangedAfterTimestamp() throws Exception {
		long timestamp = PrimingRecordsResponse.TIMESTAMP_FORMAT.parse("2022-01-31T03:50:10.000Z").getTime();
		RestRequest request = RestRequest.getRequestForPrimingRecords(TEST_API_VERSION, "my-relay-token", timestamp);
		Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/connect/briefcase/priming-records?relayToken=my-relay-token&changedAfterTimestamp=2022-01-31T03%3A50%3A10.000Z", request.getPath());
	}


	@Test
	public void testParsePrimingRecordsResponse() throws Exception {
    	JSONObject json = new JSONObject("{\n"
			+ "  \"primingRecords\": {\n"
			+ "    \"Account\": {\n"
			+ "      \"012S00000009B8HIAU\": [\n"
			+ "        {\n"
			+ "          \"id\": \"001S000001QEDnzIAH\",\n"
			+ "          \"systemModstamp\": \"2021-08-23T18:42:32.000Z\"\n"
			+ "        },\n"
			+ "        {\n"
			+ "          \"id\": \"001S000000va6rGIAQ\",\n"
			+ "          \"systemModstamp\": \"2019-02-09T02:19:38.000Z\"\n"
			+ "        }\n"
			+ "      ]\n"
			+ "    },\n"
			+ "    \"Contact\": {\n"
			+ "      \"012000000000000AAA\": [\n"
			+ "        {\n"
			+ "          \"id\": \"003S00000129813IAA\",\n"
			+ "          \"systemModstamp\": \"2018-12-22T06:13:59.000Z\"\n"
			+ "        },\n"
			+ "        {\n"
			+ "          \"id\": \"003S0000012LUhRIAW\",\n"
			+ "          \"systemModstamp\": \"2019-01-12T06:13:11.000Z\"\n"
			+ "        },\n"
			+ "        {\n"
			+ "          \"id\": \"003S0000012hWwRIAU\",\n"
			+ "          \"systemModstamp\": \"2019-01-30T00:59:06.000Z\"\n"
			+ "        }\n"
			+ "      ]\n"
			+ "    }\n"
			+ "  },\n"
			+ "  \"relayToken\": \"fake-token\",\n"
			+ "  \"ruleErrors\": ["
			+ "     {\n"
			+ "       \"ruleId\": \"rule-1\"\n"
			+ "     },\n"
			+ "     {\n"
			+ "       \"ruleId\": \"rule-2\"\n"
			+ "     }\n"
			+ "  ],\n"
			+ "  \"stats\": {\n"
			+ "    \"recordCountServed\": 100,\n"
			+ "    \"recordCountTotal\": 200,\n"
			+ "    \"ruleCountServed\": 2,\n"
			+ "    \"ruleCountTotal\": 3\n"
			+ "  }\n"
			+ "}");
		PrimingRecordsResponse primingRecordsResponse = new PrimingRecordsResponse(json);

		// Checking priming records
		// We have accounts and contacts
		Assert.assertEquals(2, primingRecordsResponse.primingRecords.size());
		// We have one record type for accounts and two accounts
		Assert.assertEquals(1, primingRecordsResponse.primingRecords.get("Account").size());
		Assert.assertEquals(2, primingRecordsResponse.primingRecords.get("Account").get("012S00000009B8HIAU").size());
		Assert.assertEquals("001S000001QEDnzIAH", primingRecordsResponse.primingRecords.get("Account").get("012S00000009B8HIAU").get(0).id);
		Assert.assertEquals("001S000000va6rGIAQ", primingRecordsResponse.primingRecords.get("Account").get("012S00000009B8HIAU").get(1).id);
		Assert.assertEquals(1629744152000L, primingRecordsResponse.primingRecords.get("Account").get("012S00000009B8HIAU").get(0).systemModstamp
			.getTime());
		// We have one record type for contacts and three contacts
		Assert.assertEquals(1, primingRecordsResponse.primingRecords.get("Contact").size());
		Assert.assertEquals(3, primingRecordsResponse.primingRecords.get("Contact").get("012000000000000AAA").size());
		Assert.assertEquals("003S00000129813IAA", primingRecordsResponse.primingRecords.get("Contact").get("012000000000000AAA").get(0).id);
		Assert.assertEquals("003S0000012LUhRIAW", primingRecordsResponse.primingRecords.get("Contact").get("012000000000000AAA").get(1).id);
		Assert.assertEquals("003S0000012hWwRIAU", primingRecordsResponse.primingRecords.get("Contact").get("012000000000000AAA").get(2).id);
		Assert.assertEquals(1545459239000L, primingRecordsResponse.primingRecords.get("Contact").get("012000000000000AAA").get(0).systemModstamp
			.getTime());

		// Checking relay token
		Assert.assertEquals("fake-token", primingRecordsResponse.relayToken);
		// Checking rule errors
		Assert.assertEquals(2, primingRecordsResponse.ruleErrors.size());
		Assert.assertEquals("rule-1", primingRecordsResponse.ruleErrors.get(0).ruleId);
		Assert.assertEquals("rule-2", primingRecordsResponse.ruleErrors.get(1).ruleId);
		// Checking stats
		Assert.assertEquals(100, primingRecordsResponse.stats.recordCountServed);
		Assert.assertEquals(200, primingRecordsResponse.stats.recordCountTotal);
		Assert.assertEquals(2, primingRecordsResponse.stats.ruleCountServed);
		Assert.assertEquals(3, primingRecordsResponse.stats.ruleCountTotal);
	}

    private static String bodyToString(final RestRequest request) throws IOException {
		final Buffer buffer = new Buffer();
		request.getRequestBody().writeTo(buffer);
		return buffer.readUtf8();
	}	
}
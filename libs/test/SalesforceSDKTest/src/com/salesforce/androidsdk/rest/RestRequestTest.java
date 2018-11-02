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

import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

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
	private static final String LAYOUT_TYPE_COMPACT = "Compact";
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
	 * Test for getRequestForObjectLayout without layoutType.
	 */
	@Test
	public void testGetRequestForObjectLayoutWithoutLayoutType() {
		RestRequest request = RestRequest.getRequestForObjectLayout(TEST_API_VERSION,
                TEST_OBJECT_TYPE, null);
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
                TEST_OBJECT_TYPE, LAYOUT_TYPE_COMPACT);
        Assert.assertEquals("Wrong method", RestMethod.GET, request.getMethod());
        Assert.assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION +
                "/ui-api/layout/" + TEST_OBJECT_TYPE + "?layoutType=" + LAYOUT_TYPE_COMPACT, request.getPath());
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

    private static String bodyToString(final RestRequest request) throws IOException {
		final Buffer buffer = new Buffer();
		request.getRequestBody().writeTo(buffer);
		return buffer.readUtf8();
	}	
}
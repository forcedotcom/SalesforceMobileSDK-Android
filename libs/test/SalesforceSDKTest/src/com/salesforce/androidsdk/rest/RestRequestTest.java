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
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.util.test.JSONTestHelper;

public class RestRequestTest extends TestCase {
	
	private static final String TEST_API_VERSION = "v99.0";
	private static final String TEST_OBJECT_TYPE = "testObjectType";
	private static final String TEST_OBJECT_ID = "testObjectId";
	private static final String TEST_EXTERNAL_ID_FIELD = "testExternalIdField";
	private static final String TEST_EXTERNAL_ID = "testExternalId";
	private static final String TEST_QUERY = "testQuery";
	private static final String TEST_SEARCH = "testSearch";
	private static final String TEST_FIELDS_STRING = "{\"fieldX\":\"value with spaces\",\"name\":\"testAccount\"}";
	private static final List<String> TEST_FIELDS_LIST = Collections.unmodifiableList(Arrays.asList(new String[]{"name", "fieldX"}));
	private static final String TEST_FIELDS_LIST_STRING = "name%2CfieldX";
	private static final List<String> TEST_OBJECTS_LIST = Collections.unmodifiableList(Arrays.asList(new String[]{"Account", "Contact"}));
	private static final String TEST_OBJECTS_LIST_STRING = "Account%2CContact";
	
	private static Map<String, Object> TEST_FIELDS;
	static {
		Map<String, Object> fields = new HashMap<String, Object>();
		fields.put("name", "testAccount");
		fields.put("fieldX", "value with spaces");
		TEST_FIELDS = Collections.unmodifiableMap(fields);
	}
	
	/**
	 * Test for getRequestForVersions
	 */
	public void testGetRequestForVersions() {
		RestRequest request = RestRequest.getRequestForVersions();
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/", request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForResources
	 */
	public void testGetRequestForResources() {
		RestRequest request = RestRequest.getRequestForResources(TEST_API_VERSION);
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/", request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	
	/**
	 * Test for getRequestForDescribeGlobal
	 */
	public void testGetRequestForDescribeGlobal() {
		RestRequest request = RestRequest.getRequestForDescribeGlobal(TEST_API_VERSION);
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/", request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	
	/**
	 * Test for getRequestForMetadata
	 */
	public void testGetRequestForMetadata() {
		RestRequest request = RestRequest.getRequestForMetadata(TEST_API_VERSION, TEST_OBJECT_TYPE);
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/", request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForDescribe
	 */
	public void testGetRequestForDescribe() {
		RestRequest request = RestRequest.getRequestForDescribe(TEST_API_VERSION, TEST_OBJECT_TYPE);
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/describe/", request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	
	/**
	 * Test for getRequestForCreate
	 * @throws IOException 
	 * @throws UnsupportedEncodingException 
	 * @throws JSONException 
	 * @throws ParseException 
	 */
	public void testGetRequestForCreate() throws UnsupportedEncodingException, IOException, ParseException, JSONException {
		RestRequest request = RestRequest.getRequestForCreate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_FIELDS);
		assertEquals("Wrong method", RestMethod.POST, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE, request.getPath());
		JSONTestHelper.assertSameJSON("Wrong request entity", new JSONObject(TEST_FIELDS_STRING), new JSONObject(EntityUtils.toString(request.getRequestEntity())));
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForRetrieve
	 * @throws UnsupportedEncodingException 
	 */
	public void testGetRequestForRetrieve() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForRetrieve(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS_LIST);
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/" + TEST_OBJECT_ID + "?fields=" + TEST_FIELDS_LIST_STRING, request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForUpdate
	 * @throws IOException 
	 * @throws UnsupportedEncodingException 
	 * @throws JSONException 
	 * @throws ParseException 
	 */
	public void testGetRequestForUpdate() throws UnsupportedEncodingException, IOException, ParseException, JSONException {
		RestRequest request = RestRequest.getRequestForUpdate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS);
		assertEquals("Wrong method", RestMethod.PATCH, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/" + TEST_OBJECT_ID, request.getPath());
		JSONTestHelper.assertSameJSON("Wrong request entity", new JSONObject(TEST_FIELDS_STRING), new JSONObject(EntityUtils.toString(request.getRequestEntity())));
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForUpsert
	 * @throws IOException 
	 * @throws UnsupportedEncodingException 
	 * @throws JSONException 
	 * @throws ParseException 
	 */
	public void testGetRequestForUpsert() throws UnsupportedEncodingException, IOException, ParseException, JSONException {
		RestRequest request = RestRequest.getRequestForUpsert(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_EXTERNAL_ID_FIELD, TEST_EXTERNAL_ID, TEST_FIELDS);
		assertEquals("Wrong method", RestMethod.PATCH, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/" + TEST_EXTERNAL_ID_FIELD + "/" + TEST_EXTERNAL_ID, request.getPath());
		JSONTestHelper.assertSameJSON("Wrong request entity", new JSONObject(TEST_FIELDS_STRING), new JSONObject(EntityUtils.toString(request.getRequestEntity())));
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForDelete
	 */
	public void testGetRequestForDelete() {
		RestRequest request = RestRequest.getRequestForDelete(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID);
		assertEquals("Wrong method", RestMethod.DELETE, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/sobjects/" + TEST_OBJECT_TYPE + "/" + TEST_OBJECT_ID, request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForQuery
	 * @throws UnsupportedEncodingException 
	 */
	public void testGetRequestForQuery() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForQuery(TEST_API_VERSION, TEST_QUERY);
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/query?q=" + TEST_QUERY, request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForSearch
	 * @throws UnsupportedEncodingException 
	 */
	public void testGetRequestForSeach() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForSearch(TEST_API_VERSION, TEST_SEARCH);
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/search?q=" + TEST_SEARCH, request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	/**
	 * Test for getRequestForSearchScopeAndOrder
	 * @throws UnsupportedEncodingException 
	 */
	public void testGetRequestForSeachScopeAndOrder() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForSearchScopeAndOrder(TEST_API_VERSION);
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/search/scopeOrder", request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}
	
	/**
	 * Test for getRequestForSearchResultLayout
	 * @throws UnsupportedEncodingException 
	 */
	public void testGetRequestForSearchResultLayout() throws UnsupportedEncodingException {
		RestRequest request = RestRequest.getRequestForSearchResultLayout(TEST_API_VERSION, TEST_OBJECTS_LIST);
		assertEquals("Wrong method", RestMethod.GET, request.getMethod());
		assertEquals("Wrong path", "/services/data/" + TEST_API_VERSION + "/search/layout?q=" + TEST_OBJECTS_LIST_STRING, request.getPath());
		assertNull("Wrong request entity", request.getRequestEntity());
		assertNull("Wrong additional headers", request.getAdditionalHttpHeaders());
	}

	public void testAdditionalHeaders() {
		Map<String, String> headers = new HashMap<String, String>();
		headers.put("X-Foo", getName());
		RestRequest req = new RestRequest(RestMethod.GET, "/my/foo/", null, headers);
		assertEquals("Wrong method", RestMethod.GET, req.getMethod());
		assertEquals("Wrong path", "/my/foo/", req.getPath());
		assertNull("Wrong entity", req.getRequestEntity());
		assertEquals("Wrong headers", headers, req.getAdditionalHttpHeaders());
	}
}
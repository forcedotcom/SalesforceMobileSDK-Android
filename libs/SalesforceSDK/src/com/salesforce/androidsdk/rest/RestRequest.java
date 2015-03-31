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
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import com.android.volley.Request;


/**
 * RestRequest: Class to represent any REST request.
 * 
 * The class offers factory methods to build RestRequest objects for all REST API actions:
 * <ul>
 * <li> versions</li>
 * <li> resources</li>
 * <li> describeGlobal</li>
 * <li> metadata</li>
 * <li> describe</li>
 * <li> create</li>
 * <li> retrieve</li>
 * <li> update</li>
 * <li> upsert</li>
 * <li> delete</li>
 * <li> searchScopeAndOrder</li>
 * <li> searchResultLayout</li>
 * </ul>
 * 
 * It also has constructors to build any arbitrary request.
 * 
 */
public class RestRequest {

	/**
	 * Enumeration for all HTTP methods.
	 *
	 */
	public enum RestMethod {
		GET, POST, PUT, DELETE, HEAD, PATCH;
		
		// Methods missing from Request.Method
		public static final int MethodPATCH = 4;
		public static final int MethodHEAD = 5;
		
		public int asVolleyMethod() {
			switch (this) {
			case DELETE: return Request.Method.DELETE;
			case GET:    return Request.Method.GET;
			case POST:   return Request.Method.POST;
			case PUT:    return Request.Method.PUT;
			case HEAD:   return MethodHEAD; // not in Request.Method
			case PATCH:  return MethodPATCH; // not in Request.Method 
			default: return -2; // should never happen
			}
		}
	}
	
	/**
	 * Enumeration for all REST API actions.
	 */
	private enum RestAction {
		VERSIONS("/services/data/"), 
		RESOURCES("/services/data/%s/"), 
		DESCRIBE_GLOBAL("/services/data/%s/sobjects/"), 
		METADATA("/services/data/%s/sobjects/%s/"), 
		DESCRIBE("/services/data/%s/sobjects/%s/describe/"), 
		CREATE("/services/data/%s/sobjects/%s"), 
		RETRIEVE("/services/data/%s/sobjects/%s/%s"), 
		UPSERT("/services/data/%s/sobjects/%s/%s/%s"), 
		UPDATE("/services/data/%s/sobjects/%s/%s"), 
		DELETE("/services/data/%s/sobjects/%s/%s"), 
		QUERY("/services/data/%s/query"), 
		SEARCH("/services/data/%s/search"),
		SEARCH_SCOPE_AND_ORDER("/services/data/%s/search/scopeOrder"),
		SEARCH_RESULT_LAYOUT("/services/data/%s/search/layout");

		private final String pathTemplate;

		private RestAction(String uriTemplate) {
			this.pathTemplate = uriTemplate;
		}
		
		public String getPath(Object... args) {
			return String.format(pathTemplate, args);
		}
	}

	private final RestMethod method;
	private final String path;
	private final HttpEntity requestEntity;
	private final Map<String, String> additionalHttpHeaders;
	
	/**
	 * Generic constructor for arbitrary requests.
	 * 
	 * @param method				the HTTP method for the request (GET/POST/DELETE etc)
	 * @param path					the URI path, this will automatically be resolved against the users current instance host.
	 * @param requestEntity			the request body if there is one, can be null.
	 */
	public RestRequest(RestMethod method, String path, HttpEntity requestEntity) {
		this(method, path, requestEntity, null);
	}

	public RestRequest(RestMethod method, String path, HttpEntity requestEntity, Map<String, String> additionalHttpHeaders) {
		this.method = method;
		this.path = path;
		this.requestEntity = requestEntity;
		this.additionalHttpHeaders = additionalHttpHeaders;
	}
	
	/**
	 * @return HTTP method of the request.
	 */
	public RestMethod getMethod() {
		return method;
	}

	/**
	 * @return Path of the request.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return request HttpEntity 
	 */
	public HttpEntity getRequestEntity() {
		return requestEntity;
	}
	
	public Map<String, String> getAdditionalHttpHeaders() {
		return additionalHttpHeaders;
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(method).append(" ").append(path);
		return sb.toString();
	}
	
	/**
	 * Request to get summary information about each Salesforce.com version currently available.
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_versions.htm
	 * 
	 * @return a JsonNode
     */
    public static RestRequest getRequestForVersions() {
        return new RestRequest(RestMethod.GET, RestAction.VERSIONS.getPath(), null);
    }
	
	/**
	 * Request to list available resources for the specified API version, including resource name and URI.
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_discoveryresource.htm
	 *
	 * @param apiVersion
	 * @return a RestRequest
	 */
	public static RestRequest getRequestForResources(String apiVersion) {
		return new RestRequest(RestMethod.GET, RestAction.RESOURCES.getPath(apiVersion), null);
	}

	/**
	 * Request to list the available objects and their metadata for your organization's data.
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_describeGlobal.htm
	 *
	 * @param apiVersion
	 * @return a RestRequest
	 */
	public static RestRequest getRequestForDescribeGlobal(String apiVersion) {
		return new RestRequest(RestMethod.GET, RestAction.DESCRIBE_GLOBAL.getPath(apiVersion), null);
	}

	/**
	 * Request to describe the individual metadata for the specified object.
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_basic_info.htm
	 * 
	 * @param apiVersion
	 * @param objectType
	 * @return a RestRequest
	 * @throws IOException
	 */
	public static RestRequest getRequestForMetadata(String apiVersion, String objectType) {
        return new RestRequest(RestMethod.GET, RestAction.METADATA.getPath(apiVersion, objectType), null);
	}

	/**
	 * Request to completely describe the individual metadata at all levels for the specified object. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_describe.htm
	 * 
	 * @param apiVersion
	 * @param objectType
	 * @return a RestRequest
	 */
	public static RestRequest getRequestForDescribe(String apiVersion, String objectType)  {
        return new RestRequest(RestMethod.GET, RestAction.DESCRIBE.getPath(apiVersion, objectType), null);
	}
	
	/**
	 * Request to create a record. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm
	 * 
	 * @param apiVersion
	 * @param objectType
	 * @param fields
	 * @return a RestRequest
	 * @throws IOException 
	 * @throws UnsupportedEncodingException 
	 */
	public static RestRequest getRequestForCreate(String apiVersion, String objectType, Map<String, Object> fields) throws IOException  {
		HttpEntity fieldsData = prepareFieldsData(fields); 
		return new RestRequest(RestMethod.POST, RestAction.CREATE.getPath(apiVersion, objectType), fieldsData);	
	}

	/**
	 * Request to retrieve a record by object id. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm
	 * 
	 * @param apiVersion
	 * @param objectType
	 * @param objectId
	 * @param fieldList
	 * @return a RestRequest
	 * @throws UnsupportedEncodingException 
	 */
	public static RestRequest getRequestForRetrieve(String apiVersion, String objectType, String objectId, List<String> fieldList) throws UnsupportedEncodingException  {
		StringBuilder path = new StringBuilder(RestAction.RETRIEVE.getPath(apiVersion, objectType, objectId));
		if (fieldList != null && fieldList.size() > 0) { 
			path.append("?fields=");
			path.append(URLEncoder.encode(toCsv(fieldList).toString(), HTTP.UTF_8));
		}

		return new RestRequest(RestMethod.GET, path.toString(), null);
	}

	private static StringBuilder toCsv(List<String> fieldList) {
		StringBuilder fieldsCsv = new StringBuilder();
		for (int i=0; i<fieldList.size(); i++) {
			fieldsCsv.append(fieldList.get(i));
			if (i<fieldList.size() - 1) {
				fieldsCsv.append(",");
			}
		}
		return fieldsCsv;
	}

	/**
	 * Request to update a record. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm
	 *
	 * @param apiVersion 
	 * @param objectType
	 * @param objectId
	 * @param fields
	 * @return a RestRequest
	 * @throws IOException 
	 */
	public static RestRequest getRequestForUpdate(String apiVersion, String objectType, String objectId, Map<String, Object> fields) throws IOException  {
		HttpEntity fieldsData = prepareFieldsData(fields);
		return new RestRequest(RestMethod.PATCH, RestAction.UPDATE.getPath(apiVersion, objectType, objectId), fieldsData);	
	}
	
	
	/**
	 * Request to upsert (update or insert) a record. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_upsert.htm
	 *
	 * @param apiVersion
	 * @param objectType
	 * @param externalIdField
	 * @param externalId
	 * @param fields
	 * @return a RestRequest
	 * @throws IOException 
	 */
	public static RestRequest getRequestForUpsert(String apiVersion, String objectType, String externalIdField, String externalId, Map<String, Object> fields) throws IOException  {
		HttpEntity fieldsData = prepareFieldsData(fields); 
		return new RestRequest(RestMethod.PATCH, RestAction.UPSERT.getPath(apiVersion, objectType, externalIdField, externalId), fieldsData);	
	}
	
	/**
	 * Request to delete a record. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm
	 * 
	 * @param apiVersion
	 * @param objectType
	 * @param objectId
	 * @return a RestRequest
	 */
	public static RestRequest getRequestForDelete(String apiVersion, String objectType, String objectId)  {
		return new RestRequest(RestMethod.DELETE, RestAction.DELETE.getPath(apiVersion, objectType, objectId), null);	
	}

	/**
	 * Request to execute the specified SOSL search. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search.htm
	 * 
	 * @param apiVersion
	 * @param q
	 * @return a RestRequest
	 * @throws UnsupportedEncodingException 
	 */
	public static RestRequest getRequestForSearch(String apiVersion, String q) throws UnsupportedEncodingException  {
		StringBuilder path = new StringBuilder(RestAction.SEARCH.getPath(apiVersion));
		path.append("?q=");
		path.append(URLEncoder.encode(q, HTTP.UTF_8));
		return new RestRequest(RestMethod.GET, path.toString(), null);	
	}

	/**
	 * Request to execute the specified SOQL search. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_query.htm
	 * 
	 * @param apiVersion
	 * @param q
	 * @return a RestRequest
	 * @throws UnsupportedEncodingException 
	 */
	public static RestRequest getRequestForQuery(String apiVersion, String q) throws UnsupportedEncodingException  {
		StringBuilder path = new StringBuilder(RestAction.QUERY.getPath(apiVersion));
		path.append("?q=");
		path.append(URLEncoder.encode(q, HTTP.UTF_8));
		return new RestRequest(RestMethod.GET, path.toString(), null);	
	}

	/**
	 * Request to execute the specified SOQL search. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_scope_order.htm
	 * 
	 * @param apiVersion
	 * @param q
	 * @return a RestRequest
	 * @throws UnsupportedEncodingException 
	 */
	public static RestRequest getRequestForSearchScopeAndOrder(String apiVersion) throws UnsupportedEncodingException  {
		StringBuilder path = new StringBuilder(RestAction.SEARCH_SCOPE_AND_ORDER.getPath(apiVersion));
		return new RestRequest(RestMethod.GET, path.toString(), null);	
	}	
	
	/**
	 * Request to execute the specified SOQL search. 
	 * See http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_layouts.htm
	 * 
	 * @param apiVersion
	 * @param objectList
	 * @return a RestRequest
	 * @throws UnsupportedEncodingException 
	 */
	public static RestRequest getRequestForSearchResultLayout(String apiVersion, List<String> objectList) throws UnsupportedEncodingException  {
		StringBuilder path = new StringBuilder(RestAction.SEARCH_RESULT_LAYOUT.getPath(apiVersion));
		path.append("?q=");
		path.append(URLEncoder.encode(toCsv(objectList).toString(), HTTP.UTF_8));
		return new RestRequest(RestMethod.GET, path.toString(), null);	
	}	
	
	/**
	 * Jsonize map and create a StringEntity out of it 
	 * @param fields
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	private static StringEntity prepareFieldsData(Map<String, Object> fields)
			throws UnsupportedEncodingException {
		if (fields == null) {
			return null;
		}
		else {
			StringEntity entity = new StringEntity(new JSONObject(fields).toString(), HTTP.UTF_8);
			entity.setContentType("application/json"); 
			return entity;
		}
	}
	
}

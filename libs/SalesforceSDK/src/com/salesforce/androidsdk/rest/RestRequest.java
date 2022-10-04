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

import android.net.Uri;
import android.text.TextUtils;

import com.salesforce.androidsdk.rest.BatchRequest.BatchRequestBuilder;
import com.salesforce.androidsdk.rest.CompositeRequest.CompositeRequestBuilder;
import com.salesforce.androidsdk.rest.files.ConnectUriBuilder;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * RestRequest: Class to represent any REST request.
 * 
 * The class offers factory methods to build RestRequest objects for all REST API actions:
 * <ul>
 * <li> userinfo</li>
 * <li> versions</li>
 * <li> resources</li>
 * <li> describeGlobal</li>
 * <li> metadata</li>
 * <li> describe</li>
 * <li> create</li>
 * <li> retrieve</li>
 * <li> upsert</li>
 * <li> update</li>
 * <li> delete</li>
 * <li> query</li>
 * <li> search</li>
 * <li> searchScopeAndOrder</li>
 * <li> searchResultLayout</li>
 * <li> objectLayout</li>
 * <li> composite</li>
 * <li> batch</li>
 * <li> tree</li>
 * <li> notifications</li>
 * <li> priming records</li>
 * <li> sobject collection create</li>
 * <li> sobject collection retrieve</li>
 * <li> sobject collection update</li>
 * <li> sobject collection upsert</li>
 * <li> sobject collection delete</li>
 * </ul>
 * 
 * It also has constructors to build any arbitrary request.
 * 
 */
public class RestRequest {

	/**
	 * application/json media type
	 */
	public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

	/**
	 * utf_8 charset
	 */
	public static final String UTF_8 = StandardCharsets.UTF_8.name();

    /**
     * Misc keys appearing in requests
     */
    public static final String RECORDS = "records";
    public static final String METHOD = "method";
    public static final String URL = "url";
    public static final String BODY = "body";
    public static final String HTTP_HEADERS = "httpHeaders";
    public static final String COMPOSITE_REQUEST = "compositeRequest";
    public static final String BATCH_REQUESTS = "batchRequests";
    public static final String ALL_OR_NONE = "allOrNone";
    public static final String HALT_ON_ERROR = "haltOnError";
    public static final String RICH_INPUT = "richInput";
    public static final String SERVICES_DATA = "/services/data/";
    public static final String REFERENCE_ID = "referenceId";
    public static final String TYPE = "type";
    public static final String ATTRIBUTES = "attributes";
    public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
    public static final String SFORCE_QUERY_OPTIONS = "Sforce-Query-Options";
    public static final String BATCH_SIZE_OPTION = "batchSize";
	public static final int MIN_BATCH_SIZE = 200;
    public static final int MAX_BATCH_SIZE = 2000;
    public static final int DEFAULT_BATCH_SIZE = 2000;
    public static final int MAX_COLLECTION_RETRIEVE_SIZE = 2000;

    /**
     * HTTP date format
     */
    public static final DateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);

	static {
        HTTP_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

	/**
	 * Salesforce timestamp format.
	 */
	public static final DateFormat ISO8601_DATE_FORMAT = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

    /**
	 * Enumeration for all HTTP methods.
	 */
	public enum RestMethod {
		GET, POST, PUT, DELETE, HEAD, PATCH
	}

    /**
     * Enumeration for all REST API endpoints.
     */
	public enum RestEndpoint {
		LOGIN, INSTANCE
	}

	enum RestAction {

		USERINFO("/services/oauth2/userinfo"),
		VERSIONS(SERVICES_DATA),
		RESOURCES(SERVICES_DATA + "%s/"),
		DESCRIBE_GLOBAL(SERVICES_DATA + "%s/sobjects/"),
		METADATA(SERVICES_DATA + "%s/sobjects/%s/"),
		DESCRIBE(SERVICES_DATA + "%s/sobjects/%s/describe/"),
		CREATE(SERVICES_DATA + "%s/sobjects/%s"),
		RETRIEVE(SERVICES_DATA + "%s/sobjects/%s/%s"),
		UPSERT(SERVICES_DATA + "%s/sobjects/%s/%s/%s"),
		UPDATE(SERVICES_DATA + "%s/sobjects/%s/%s"),
		DELETE(SERVICES_DATA + "%s/sobjects/%s/%s"),
		QUERY(SERVICES_DATA + "%s/query"),
		QUERY_ALL(SERVICES_DATA + "%s/queryAll"),
		SEARCH(SERVICES_DATA + "%s/search"),
		SEARCH_SCOPE_AND_ORDER(SERVICES_DATA + "%s/search/scopeOrder"),
		SEARCH_RESULT_LAYOUT(SERVICES_DATA + "%s/search/layout"),
        OBJECT_LAYOUT(SERVICES_DATA + "%s/ui-api/layout/%s"),
		COMPOSITE(SERVICES_DATA + "%s/composite"),
        BATCH(SERVICES_DATA + "%s/composite/batch"),
        SOBJECT_TREE(SERVICES_DATA + "%s/composite/tree/%s"),
		SOBJECT_COLLECTION(SERVICES_DATA + "%s/composite/sobjects"),
		SOBJECT_COLLECTION_RETRIEVE(SERVICES_DATA + "%s/composite/sobjects/%s"),
		SOBJECT_COLLECTION_UPSERT(SERVICES_DATA + "%s/composite/sobjects/%s/%s"),
        NOTIFICATIONS_STATUS(SERVICES_DATA + "%s/connect/notifications/status"),
		NOTIFICATIONS(SERVICES_DATA + "%s/connect/notifications/%s"),
		PRIMING_RECORDS(SERVICES_DATA + "%s/connect/briefcase/priming-records");

		private final String pathTemplate;

		RestAction(String uriTemplate) {
			this.pathTemplate = uriTemplate;
		}
		
		public String getPath(Object... args) {
			return String.format(pathTemplate, args);
		}
	}

	private final RestMethod method;
	private final RestEndpoint endpoint;
	private final String path;
	private final RequestBody requestBody;
	private final Map<String, String> additionalHttpHeaders;
	private final JSONObject requestBodyAsJson; // needed for composite and batch requests

    /**
     * Generic constructor for arbitrary requests without a body.
     *
     * @param method				HTTP method used in the request (GET/POST/DELETE etc).
     * @param path					URI path. This is automatically resolved against the user's current instance host.
     */
    public RestRequest(RestMethod method, String path) {
        this(method, path, (RequestBody) null, null);
    }

    /**
     * Generic constructor for arbitrary requests without a body.
     *
     * @param method				HTTP method used for the request (GET/POST/DELETE etc).
     * @param path					URI path. This will automatically be resolved against the user's current instance host.
     * @param additionalHttpHeaders Additional headers.
     *
     */
    public RestRequest(RestMethod method, String path, Map<String, String> additionalHttpHeaders) {
        this(method, path, (RequestBody) null, additionalHttpHeaders);
    }

	/**
	 * Generic constructor for arbitrary requests.
	 * 
	 * @param method				HTTP method used for the request (GET/POST/DELETE etc).
	 * @param path					URI path. This will automatically be resolved against the user's current instance host.
	 * @param requestBody			Request body, if one exists. Can be null.
     *
     * Note: Do not use this constructor if requestBody is not null and you want to build a batch or composite request.
	 */
	public RestRequest(RestMethod method, String path, RequestBody requestBody) {
        this(method, path, requestBody, null);
	}

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method				 HTTP method used for the request (GET/POST/DELETE etc).
     * @param path					 URI path. This will automatically be resolved against the user's current instance host.
     * @param requestBodyAsJson		 Request body as JSON, if one exists. Can be null.
     *
     * Note: Use this constructor if requestBody is not null and you want to build a batch or composite request.
     */
    public RestRequest(RestMethod method, String path, JSONObject requestBodyAsJson) {
        this(method, path, requestBodyAsJson, null);
    }

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method				 HTTP method used for the request (GET/POST/DELETE etc).
     * @param path					 URI path. This will automatically be resolved against the user's current instance host.
     * @param requestBody			 Request body, if one exists. Can be null.
     * @param additionalHttpHeaders  Additional headers.
     *
     * Note: Do not use this constructor if requestBody is not null and you want to build a batch or composite request.
     */
    public RestRequest(RestMethod method, String path, RequestBody requestBody, Map<String, String> additionalHttpHeaders) {
    	this(method, RestEndpoint.INSTANCE, path, requestBody, additionalHttpHeaders);
    }

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method				HTTP method used for the request (GET/POST/DELETE etc).
     * @param path				    URI path. This will automatically be resolved against the user's current instance host.
     * @param requestBodyAsJson     Request body as JSON, if one exists. Can be null.
     * @param additionalHttpHeaders Additional headers.
     *
     * Note: Use this constructor if requestBody is not null and you want to build a batch or composite request.
     */
    public RestRequest(RestMethod method, String path, JSONObject requestBodyAsJson, Map<String, String> additionalHttpHeaders) {
        this(method, RestEndpoint.INSTANCE, path, requestBodyAsJson, additionalHttpHeaders);
    }

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method				HTTP method used for the request (GET/POST/DELETE etc).
     * @param endpoint				The endpoint associated with the request.
     * @param path					URI path. This will be resolved against the user's current
     * 								Rest endpoint, as specified by the endpoint parameter.
     * @param requestBody			Request body, if one exists. Can be null.
     * @param additionalHttpHeaders	Additional headers.
     */
    public RestRequest(RestMethod method, RestEndpoint endpoint, String path, RequestBody requestBody, Map<String, String> additionalHttpHeaders) {
        this.method = method;
        this.endpoint = endpoint;
        this.path = path;
        this.requestBody = requestBody;
        this.additionalHttpHeaders = additionalHttpHeaders;
        this.requestBodyAsJson = null;
    }

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method				HTTP method used for the request (GET/POST/DELETE etc).
     * @param endpoint				The endpoint associated with the request.
     * @param path					URI path. This will be resolved against the user's current
     * 								Rest endpoint, as specified by the endpoint parameter.
     * @param requestBodyAsJson		Request body as JSON, if one exists. Can be null.
     * @param additionalHttpHeaders	Additional headers.
     */
    public RestRequest(RestMethod method, RestEndpoint endpoint, String path, JSONObject requestBodyAsJson, Map<String, String> additionalHttpHeaders) {
        this.method = method;
        this.endpoint = endpoint;
        this.path = path;
        this.requestBody = requestBodyAsJson == null ? null : RequestBody.create(MEDIA_TYPE_JSON, requestBodyAsJson.toString());
        this.additionalHttpHeaders = additionalHttpHeaders;
        this.requestBodyAsJson = requestBodyAsJson;
    }

    /**
	 * @return  HTTP method of the request.
	 */
	public RestMethod getMethod() {
		return method;
	}

	/**
	 * @return The endpoint of the request.
	 */
	public RestEndpoint getEndpoint() { return endpoint; }

	/**
	 * @return  Path of the request.
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return  Request body.
	 */
	public RequestBody getRequestBody() {
		return requestBody;
	}

    /**
     * @return  Request body as JSON.
     */
    public JSONObject getRequestBodyAsJson() {
        return requestBodyAsJson;
    }

    /**
	 * @return  Additional HTTP headers.
	 */
	public Map<String, String> getAdditionalHttpHeaders() {
		return additionalHttpHeaders;
	}

	/**
	 * Request to get information about the user making the request.
	 * @return RestRequest object that requests user info.
	 * @see <a href="https://help.salesforce.com/articleView?id=remoteaccess_using_userinfo_endpoint.htm">https://help.salesforce.com/articleView?id=remoteaccess_using_userinfo_endpoint.htm</a></a>
	 */
	public static RestRequest getRequestForUserInfo() {
		return new RestRequest(RestMethod.GET, RestEndpoint.LOGIN, RestAction.USERINFO.getPath(), (RequestBody) null, null);
	}

	/**
	 * Request to get summary information about each Salesforce.com version currently available.
	 * 
	 * @return  RestRequest object that requests the list of versions.
     * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_versions.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_versions.htm</a>
     */
    public static RestRequest getRequestForVersions() {
        return new RestRequest(RestMethod.GET, RestAction.VERSIONS.getPath());
    }
	
	/**
	 * Request to list available resources for the specified API version, including resource name and URI.
	 *
	 * @param apiVersion    Salesforce API version.
     * @return              RestRequest object that requests resources for the given API version.
     * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_discoveryresource.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_discoveryresource.htm</a>
	 */
	public static RestRequest getRequestForResources(String apiVersion) {
		return new RestRequest(RestMethod.GET, RestAction.RESOURCES.getPath(apiVersion));
	}

	/**
	 * Request to list the available objects and their metadata for your organization's data.
	 *
     * @param apiVersion    Salesforce API version.
     * @return              RestRequest object that requests objects and metadata for the given API version.
     * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_describeGlobal.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_describeGlobal.htm</a>
	 */
	public static RestRequest getRequestForDescribeGlobal(String apiVersion) {
		return new RestRequest(RestMethod.GET, RestAction.DESCRIBE_GLOBAL.getPath(apiVersion));
	}

	/**
	 * Request to describe the individual metadata for the specified object.
	 *
     * @param apiVersion    Salesforce API version.
     * @param objectType    Type of object for which the caller is requesting object metadata.
     * @return              RestRequest object that requests an object's metadata for the given API version.
	 * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_basic_info.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_basic_info.htm</a>
	 */
	public static RestRequest getRequestForMetadata(String apiVersion, String objectType) {
        return new RestRequest(RestMethod.GET, RestAction.METADATA.getPath(apiVersion, objectType));
	}

	/**
	 * Request to completely describe the individual metadata at all levels for the specified object.
	 *
     * @param apiVersion Salesforce API version.
     * @param objectType Type of object for which the caller is requesting the metadata description.
     * @return RestRequest object that requests an object's metadata description for the given API version.
     * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_describe.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_describe.htm</a>
	 */
	public static RestRequest getRequestForDescribe(String apiVersion, String objectType) {
        return new RestRequest(RestMethod.GET, RestAction.DESCRIBE.getPath(apiVersion, objectType));
	}
	
	/**
	 * Request to create a record. 
	 *
     * @param apiVersion    Salesforce API version.
     * @param objectType    Type of record to be created.
     * @param fields        Map of the new record's fields and their values. Can be null.
     * @return              RestRequest object that requests creation of a record.
	 * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm</a>
	 */
	public static RestRequest getRequestForCreate(String apiVersion, String objectType, Map<String, Object> fields) {
		return new RestRequest(RestMethod.POST, RestAction.CREATE.getPath(apiVersion, objectType), fields == null ? null : new JSONObject(fields));
	}

	/**
	 * Request to retrieve a record by object ID.
	 *
     * @param apiVersion    Salesforce API version.
     * @param objectType    Type of the requested record.
     * @param objectId      Salesforce ID of the requested record.
     * @param fieldList     List of requested field names.
     * @return              RestRequest object that requests a record.
	 * @throws UnsupportedEncodingException
     * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm</a>
	 */
	public static RestRequest getRequestForRetrieve(String apiVersion, String objectType, String objectId, List<String> fieldList) throws UnsupportedEncodingException {
		StringBuilder path = new StringBuilder(RestAction.RETRIEVE.getPath(apiVersion, objectType, objectId));
		if (fieldList != null && fieldList.size() > 0) { 
			path.append("?fields=");
			path.append(URLEncoder.encode(TextUtils.join(",", fieldList), UTF_8));
		}
		return new RestRequest(RestMethod.GET, path.toString());
	}

	/**
	 * Request to update a record. 
	 *
     * @param apiVersion    Salesforce API version.
     * @param objectType    Type of the record.
     * @param objectId      Salesforce ID of the record.
     * @param fields        Map of the fields to be updated and their new values.
     * @return              RestRequest object that requests a record update.
	 * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm</a>
	 */
	public static RestRequest getRequestForUpdate(String apiVersion, String objectType, String objectId, Map<String, Object> fields) {
        return getRequestForUpdate(apiVersion, objectType, objectId, fields, null);
	}

    /**
     * Request to update a record.
     *
     * @param apiVersion            Salesforce API version.
     * @param objectType            Type of the record.
     * @param objectId              Salesforce ID of the record.
     * @param fields                Map of the fields to be updated and their new values. Can be null.
     * @param ifUnmodifiedSinceDate Fulfill the request only if the record has not been modified since the given date.
     * @return                      RestRequest object that requests a record update.
     * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm</a>
     */
    public static RestRequest getRequestForUpdate(String apiVersion, String objectType, String objectId, Map<String, Object> fields, Date ifUnmodifiedSinceDate) {
        Map<String, String> additionalHttpHeaders = prepareConditionalHeader(IF_UNMODIFIED_SINCE, ifUnmodifiedSinceDate);
        return new RestRequest(RestMethod.PATCH, RestAction.UPDATE.getPath(apiVersion, objectType, objectId), fields == null ? null : new JSONObject(fields), additionalHttpHeaders);
    }
	
	/**
	 * Request to upsert (update or insert) a record. 
	 *
     * @param apiVersion        Salesforce API version.
     * @param objectType        Type of the record.
     * @param externalIdField   Name of ID field in source data.
     * @param externalId        ID of source data record. Can be an empty string.
     * @param fields            Map of the fields to be upserted and their new values. Can be null.
     * @return                  RestRequest object that requests a record upsert.
	 * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_upsert.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_upsert.htm</a>
	 */
	public static RestRequest getRequestForUpsert(String apiVersion, String objectType, String externalIdField, String externalId, Map<String, Object> fields) {
        return new RestRequest(
                externalId == null ? RestMethod.POST : RestMethod.PATCH,
                RestAction.UPSERT.getPath(
                        apiVersion,
                        objectType,
                        externalIdField,
                        externalId == null ? "" : externalId),
                fields == null ? null : new JSONObject(fields));
    }
	
	/**
	 * Request to delete a record. 
	 *
     * @param apiVersion    Salesforce API version.
     * @param objectType    Type of the record.
	 * @param objectId      Salesforce ID of the record.
     * @return              RestRequest object that requests a record deletion.
     * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm</a>
	 */
	public static RestRequest getRequestForDelete(String apiVersion, String objectType, String objectId) {
        return new RestRequest(RestMethod.DELETE, RestAction.DELETE.getPath(apiVersion, objectType, objectId));
	}

    /**
	 * Request to execute the specified SOSL search. 
	 *
	 * @param apiVersion    Salesforce API version.
	 * @param q             SOSL search string.
     * @return              RestRequest object that requests a SOSL search.
	 * @throws UnsupportedEncodingException
     * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search.htm</a>
	 */
	public static RestRequest getRequestForSearch(String apiVersion, String q) throws UnsupportedEncodingException {
		StringBuilder path = new StringBuilder(RestAction.SEARCH.getPath(apiVersion));
		path.append("?q=");
		path.append(URLEncoder.encode(q, UTF_8));
		return new RestRequest(RestMethod.GET, path.toString());
	}

	/**
	 * Request to execute the specified SOQL query.
	 *
	 * @param apiVersion    Salesforce API version.
	 * @param q             SOQL query string.
	 * @return              RestRequest object that requests a SOQL query.
	 * @throws UnsupportedEncodingException
	 * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_query.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_query.htm</a>
	 */
	public static RestRequest getRequestForQuery(String apiVersion, String q) throws UnsupportedEncodingException {
		return getRequestForQuery(apiVersion, q, DEFAULT_BATCH_SIZE);
	}

	/**
	 * Request to execute the specified SOQL query.
	 *
	 * @param apiVersion    Salesforce API version.
	 * @param q             SOQL query string.
	 * @param batchSize     Batch size: number between 200 and 2000 (default).
	 * @return              RestRequest object that requests a SOQL query.
	 * @throws UnsupportedEncodingException
	 * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_query.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_query.htm</a>
	 */
	public static RestRequest getRequestForQuery(String apiVersion, String q, int batchSize) throws UnsupportedEncodingException {
		StringBuilder path = new StringBuilder(RestAction.QUERY.getPath(apiVersion));
		path.append("?q=");
		path.append(URLEncoder.encode(q, UTF_8));
		batchSize = Math.max(Math.min(batchSize, MAX_BATCH_SIZE), MIN_BATCH_SIZE);
		Map<String, String> headers = null;
		if (batchSize != DEFAULT_BATCH_SIZE) {
			headers = new HashMap<>();
			headers.put(SFORCE_QUERY_OPTIONS, BATCH_SIZE_OPTION + "=" + batchSize);
		}
		return new RestRequest(RestMethod.GET, path.toString(), headers);
	}

	/**
	 * Request to execute the specified SOQL query which includes deleted records because of a merge or delete in the result set.
	 *
	 * @param apiVersion    Salesforce API version.
	 * @param q             SOQL query string.
	 * @return              RestRequest object that requests a SOQL query that includes deleted records.
	 * @throws UnsupportedEncodingException
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_queryall.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_queryall.htm</a>
	 */
	public static RestRequest getRequestForQueryAll(String apiVersion, String q) throws UnsupportedEncodingException {
		StringBuilder path = new StringBuilder(RestAction.QUERY_ALL.getPath(apiVersion));
		path.append("?q=");
		path.append(URLEncoder.encode(q, UTF_8));
		return new RestRequest(RestMethod.GET, path.toString());
	}

	/**
	 * Request to get search scope and order.
	 *
	 * @param apiVersion    Salesforce API version.
     * @return              RestRequest object that requests the search scope and order for the given API version.
	 * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_scope_order.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_scope_order.htm</a>
	 */
	public static RestRequest getRequestForSearchScopeAndOrder(String apiVersion) {
        return new RestRequest(RestMethod.GET, new StringBuilder(RestAction.SEARCH_SCOPE_AND_ORDER.getPath(apiVersion)).toString());
	}	
	
	/**
	 * Request to get search result layouts
	 *
	 * @param apiVersion    Salesforce API version.
	 * @param objectList    List of objects whose search result layouts are being requested.
     * @return              RestRequest object that requests the search result layout for the given list of objects.
	 * @throws UnsupportedEncodingException
     * @see <a href="http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_layouts.htm">http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_layouts.htm</a>
	 */
	public static RestRequest getRequestForSearchResultLayout(String apiVersion, List<String> objectList) throws UnsupportedEncodingException {
		StringBuilder path = new StringBuilder(RestAction.SEARCH_RESULT_LAYOUT.getPath(apiVersion));
		path.append("?q=");
		path.append(URLEncoder.encode(TextUtils.join(",", objectList).toString(), UTF_8));
		return new RestRequest(RestMethod.GET, path.toString());
	}

	/**
	 * Request to get object layout data.
	 *
	 * @param apiVersion Salesforce API version.
	 * @param objectAPIName Object API name.
	 * @param formFactor Form factor. Could be "Large", "Medium" or "Small". Default value is "Large".
	 * @param layoutType Layout type. Could be "Compact" or "Full". Default value is "Full".
	 * @param mode Mode. Could be "Create", "Edit" or "View". Default value is "View".
	 * @param recordTypeId Record type ID. Default will be used if not supplied.
	 * @return RestRequest object that requests the object layout for the given parameters.
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_resources_record_layout.htm">https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_resources_record_layout.htm</a>
	 */
	public static RestRequest getRequestForObjectLayout(String apiVersion, String objectAPIName,
														String formFactor, String layoutType,
														String mode, String recordTypeId) {
		final StringBuilder path = new StringBuilder(RestAction.OBJECT_LAYOUT.getPath(apiVersion, objectAPIName));
		path.append("?");
		if (!TextUtils.isEmpty(formFactor)) {
			path.append("formFactor=");
			path.append(formFactor);
			path.append("&");
		}
		if (!TextUtils.isEmpty(layoutType)) {
			path.append("layoutType=");
			path.append(layoutType);
			path.append("&");
		}
		if (!TextUtils.isEmpty(mode)) {
			path.append("mode=");
			path.append(mode);
			path.append("&");
		}
		if (!TextUtils.isEmpty(recordTypeId)) {
			path.append("recordTypeId=");
			path.append(recordTypeId);
		}
		if (path.charAt(path.length() - 1) == '?' || path.charAt(path.length() - 1) == '&') {
			path.deleteCharAt(path.length() - 1);
		}
		return new RestRequest(RestMethod.GET, path.toString());
	}

    /**
	 * Composite request
	 *
     * @param apiVersion        Salesforce API version.
     * @param allOrNone         Indicates whether the request will accept partially complete results.
	 * @param refIdToRequests   Linked map of reference IDs to RestRequest objects. The requests will be played in order in which they were added.
     * @return RestRequest object that requests execution of the given composite request.
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_composite.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_composite.htm</a>
	 */
	public static CompositeRequest getCompositeRequest(String apiVersion, boolean allOrNone, LinkedHashMap<String, RestRequest> refIdToRequests) throws JSONException {
		CompositeRequestBuilder builder = new CompositeRequestBuilder();
        for (Map.Entry<String,RestRequest> entry : refIdToRequests.entrySet()) {
			builder.addRequest(entry.getKey(), entry.getValue());
		}
		builder.setAllOrNone(allOrNone);
        return builder.build(apiVersion);
	}

    @Override
    public String toString() {
        try {
            return asJSON().toString(2);
        } catch (JSONException e) {
            return super.toString();
        }
    }

    protected JSONObject asJSON() throws JSONException {
        JSONObject requestJson = new JSONObject();
        requestJson.put(METHOD, getMethod().toString());
        requestJson.put(URL, getPath());
        requestJson.put(BODY, getRequestBodyAsJson());
        if (getAdditionalHttpHeaders() != null) requestJson.put(HTTP_HEADERS, new JSONObject(getAdditionalHttpHeaders()));
        return requestJson;
    }

    /**
     * Batch request
     * @param apiVersion    Salesforce API version.
     * @param haltOnError   Indicates whether to stop processing the batch if an error occurs.
     * @param requests      List of RestRequest objects.
     * @return              RestRequest object that requests execution of the given batch of requests.
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_batch.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_batch.htm</a>
     */
    public static BatchRequest getBatchRequest(String apiVersion, boolean haltOnError, List<RestRequest> requests) throws JSONException {
    	BatchRequestBuilder builder = new BatchRequestBuilder();
        for (RestRequest request : requests) {
			builder.addRequest(request);
		}
        builder.setHaltOnError(haltOnError);
        return builder.build(apiVersion);
    }

    /**
     * Request to create one or more sObject trees with root records of the specified type.
     *
     * @param apiVersion    Salesforce API version.
     * @param objectType    Type of object requested.
     * @param objectTrees   List of {link #SObjectTree} objects.
     * @return              RestRequest object that requests creation of one or more sObject trees.
     * @throws JSONException
     * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobject_tree.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobject_tree.htm</a>
     */
    public static RestRequest getRequestForSObjectTree(String apiVersion, String objectType, List<SObjectTree> objectTrees) throws JSONException {
        JSONArray jsonTrees = new JSONArray();
        for (SObjectTree objectTree : objectTrees) {
            jsonTrees.put(objectTree.asJSON());
        }
        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, JSONObjectHelper.makeJSONObject(RECORDS, jsonTrees).toString());
        return new RestRequest(RestMethod.POST, RestAction.SOBJECT_TREE.getPath(apiVersion, objectType), body);
    }

    /**
     * Request to get status of notifications for the user.
     *
     * @param apiVersion   Salesforce API version.
	 *
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_notifications_status.htm">https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_notifications_status.htm</a>
     */
    public static RestRequest getRequestForNotificationsStatus(String apiVersion) {
        return new RestRequest(RestMethod.GET, RestAction.NOTIFICATIONS_STATUS.getPath(apiVersion));
    }

    /**
     * Request to get a notification.
     *
     * @param apiVersion      Salesforce API version.
     * @param notificationId  ID of notification.
	 *
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resource_notifications_specific.htm">https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resource_notifications_specific.htm</a>
     */
    public static RestRequest getRequestForNotification(String apiVersion, String notificationId) {
        return new RestRequest(RestMethod.GET, RestAction.NOTIFICATIONS.getPath(apiVersion, notificationId));
    }

    /**
     * Request for updating a notification.
     *
     * @param apiVersion      Salesforce API version.
     * @param notificationId  ID of notification.
     * @param read            Marks notification as read (true) or unread (false). If null, field won't be updated.
     *                        Required if `seen` not provided.
     * @param seen            Marks notification as seen (true) or unseen (false). If null, field won't be updated.
     *                        Required if `read` not provided.
	 *
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resource_notifications_specific.htm">https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resource_notifications_specific.htm</a>
     */
    public static RestRequest getRequestForNotificationUpdate(String apiVersion, String notificationId, Boolean read, Boolean seen) {
        final Map<String, Object> parameters = new HashMap<>();
        if (read != null) {
            parameters.put("read", read);
        }
        if (seen != null) {
            parameters.put("seen", seen);
        }
        final String path = RestAction.NOTIFICATIONS.getPath(apiVersion, notificationId);
        return new RestRequest(RestMethod.PATCH, path, new JSONObject(parameters));
    }

    /**
     * Request for getting notifications.
     *
     * @param apiVersion   Salesforce API version.
     * @param size         Number of notifications to get.
     * @param before       Get notifications occurring before the provided date. Shouldn't be used with `after`.
     * @param after        Get notifications occurring after the provided date. Shouldn't be used with `before`.
	 *
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_notifications_list.htm>https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_notifications_list.htm</a>
     */
    public static RestRequest getRequestForNotifications(String apiVersion, Integer size, Date before, Date after) {
        final Map<String, String> parameters = new HashMap<>();
        if (size != null) {
            parameters.put("size", size.toString());
        }
        if (before != null) {
            parameters.put("before", ISO8601_DATE_FORMAT.format(before));
        }
        if (after != null) {
            parameters.put("after", ISO8601_DATE_FORMAT.format(after));
        }

        final ConnectUriBuilder builder = new ConnectUriBuilder(Uri.parse(RestAction.NOTIFICATIONS.getPath(apiVersion, "")).buildUpon());
        for (final Map.Entry<String, String> parameter : parameters.entrySet()) {
            builder.appendQueryParam(parameter.getKey(), parameter.getValue());
        }
        return new RestRequest(RestMethod.GET, builder.toString());
    }

    /**
     * Request for updating notifications.
     *
     * @param apiVersion       Salesforce API version.
     * @param notificationIds  IDs of notifications to get. Shouldn't be used with `before`.
     * @param before           Get notifications before the provided date. Shouldn't be used with `notificationIds`.
     * @param read             Marks notifications as read (true) or unread (false). If null, field won't be updated.
     *                         Required if `seen` not provided.
     * @param seen             Marks notifications as seen (true) or unseen (false). If null, field won't be updated.
     *                         Required if `read` not provided.
     */
    public static RestRequest getRequestForNotificationsUpdate(String apiVersion, List<String> notificationIds, Date before, Boolean read, Boolean seen) {
        final Map<String, Object> parameters = new HashMap<>();
        if (notificationIds != null) {
            parameters.put("notificationIds", notificationIds);
        }
        if (before != null) {
            parameters.put("before", ISO8601_DATE_FORMAT.format(before));
        }
        if (read != null) {
            parameters.put("read", read);
        }
        if (seen != null) {
            parameters.put("seen", seen);
        }
        final String path = RestAction.NOTIFICATIONS.getPath(apiVersion, "");
        return new RestRequest(RestMethod.PATCH, path, new JSONObject(parameters));
    }

	/**
	 * Request for getting list of record related to offline briefcase
	 *
	 * @param apiVersion       Salesforce API version.
	 * @param relayToken       Relay token (to get next page of results) - or null
	 * @param changedAfterTime To only get ids of records that changed after given time - or null
	 *
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_briefcase_priming_records.htm">https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_briefcase_priming_records.htm</a>
	 */
    public static RestRequest getRequestForPrimingRecords(String apiVersion, String relayToken, Long changedAfterTime) throws UnsupportedEncodingException {
    	StringBuilder path = new StringBuilder(RestAction.PRIMING_RECORDS.getPath(apiVersion));
    	if (relayToken != null) {
    		path.append("?relayToken=");
    		path.append(URLEncoder.encode(relayToken, UTF_8));
		}
    	if (changedAfterTime != null) {
    		path.append(relayToken != null ? "&" : "?");
    		path.append("changedAfterTimestamp=");
    		path.append(URLEncoder.encode(PrimingRecordsResponse.TIMESTAMP_FORMAT.format(new Date(changedAfterTime)), UTF_8));
		}
		return new RestRequest(RestMethod.GET, path.toString());
	}

	/**
	 * Request for creating multiple records with fewer round trips
	 *
	 * @param apiVersion Salesforce API version.
	 * @param allOrNone  Indicates whether to roll back the entire request when the creation of any object fails (true) or to continue with the independent creation of other objects in the request.
	 * @param records    A list of sObjects.
	 *
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_create.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_create.htm</a>
	 */
	public static RestRequest getRequestForCollectionCreate(String apiVersion, boolean allOrNone, JSONArray records) throws JSONException {
		JSONObject requestBodyAsJson = new JSONObject();
		requestBodyAsJson.put(ALL_OR_NONE, allOrNone);
		requestBodyAsJson.put(RECORDS, records);
		return new RestRequest(RestMethod.POST, RestAction.SOBJECT_COLLECTION.getPath(apiVersion), requestBodyAsJson);
	}

	/**
	 * Request for retrieving multiple records with fewer round trips
	 *
	 * @param apiVersion    Salesforce API version.
	 * @param objectType    Type of the requested record.
	 * @param objectIds     List of Salesforce IDs of the requested records.
	 * @param fieldList     List of requested field names.
	 *
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_retrieve.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_retrieve.htm</a>
	 */
	public static RestRequest getRequestForCollectionRetrieve(String apiVersion, String objectType, List<String> objectIds, List<String> fieldList)
		throws UnsupportedEncodingException, JSONException {
		StringBuilder path = new StringBuilder(RestAction.SOBJECT_COLLECTION_RETRIEVE.getPath(apiVersion, objectType));
		// Using a post body which is allowed by the end point and allows more ids to be sent up (2000 instead of ~800)
		JSONObject body = new JSONObject();
		body.put("ids", new JSONArray(objectIds));
		body.put("fields", new JSONArray(fieldList));
		return new RestRequest(RestMethod.POST, path.toString(), body);
	}

	/**
	 * Request for updating multiple records with fewer round trips
	 *
	 * @param apiVersion    Salesforce API version.
	 * @param allOrNone     Indicates whether to roll back the entire request when the update of any object fails (true) or to continue with the independent update of other objects in the request.
	 * @param records       A list of sObjects.
	 *
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_update.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_update.htm</a>
	 */
	public static RestRequest getRequestForCollectionUpdate(String apiVersion, boolean allOrNone, JSONArray records) throws JSONException {
		JSONObject requestBodyAsJson = new JSONObject();
		requestBodyAsJson.put(ALL_OR_NONE, allOrNone);
		requestBodyAsJson.put(RECORDS, records);
		return new RestRequest(RestMethod.PATCH, RestAction.SOBJECT_COLLECTION.getPath(apiVersion), requestBodyAsJson);
	}

	/**
	 * Request for upserting multiple records with fewer round trips
	 *
	 * @param apiVersion        Salesforce API version.
	 * @param allOrNone         Indicates whether to roll back the entire request when the upsert of any object fails (true) or to continue with the independent upsert of other objects in the request.
	 * @param objectType        Type of the requested record.
	 * @param externalIdField   Name of ID field in source data.
	 * @param records           A list of sObjects.
	 *
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_upsert.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_upsert.htm</a>
	 */
	public static RestRequest getRequestForCollectionUpsert(String apiVersion, boolean allOrNone, String objectType, String externalIdField, JSONArray records) throws JSONException {
		JSONObject requestBodyAsJson = new JSONObject();
		requestBodyAsJson.put(ALL_OR_NONE, allOrNone);
		requestBodyAsJson.put(RECORDS, records);
		return new RestRequest(RestMethod.PATCH, RestAction.SOBJECT_COLLECTION_UPSERT.getPath(apiVersion, objectType, externalIdField), requestBodyAsJson);
	}

	/**
	 * Request for deleting multiple records with fewer round trips
	 *
	 * @param apiVersion    Salesforce API version.
	 * @param allOrNone     Indicates whether to roll back the entire request when the delete of any object fails (true) or to continue with the independent delete of other objects in the request.
	 * @param objectIds     List of Salesforce IDs of the records to delete.
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_delete.htm">https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_delete.htm</a>
	 */
	public static RestRequest getRequestForCollectionDelete(String apiVersion, boolean allOrNone, List<String> objectIds) throws UnsupportedEncodingException {
		StringBuilder path = new StringBuilder(RestAction.SOBJECT_COLLECTION.getPath(apiVersion));
		path.append("?allOrNone=" + allOrNone + "&ids=");
		path.append(URLEncoder.encode(TextUtils.join(",", objectIds), UTF_8));
		return new RestRequest(RestMethod.DELETE, path.toString());
	}

    /**
     * Helper method for creating conditional HTTP header.
     *
     * @param headerName Name of header.
     * @param date Date of header. If null, this method returns null.
     * @return Map of header name and date, or null if no date is provided.
     */
    private static Map<String, String> prepareConditionalHeader(String headerName, Date date) {
        if (date != null) {
            Map<String, String> additionalHttpHeaders = new HashMap<>();
            additionalHttpHeaders.put(headerName, HTTP_DATE_FORMAT.format(date));
            return additionalHttpHeaders;
        } else {
            return null;
        }
    }

	/**
     * Helper class for getRequestForSObjectTree.
     */
    public static class SObjectTree {

        final String objectType;
        final String objectTypePlural;
        final String referenceId;
        final Map<String, Object> fields;
        final List<SObjectTree> childrenTrees;

        public SObjectTree(String objectType, String objectTypePlural, String referenceId, Map<String, Object> fields, List<SObjectTree> childrenTrees) {
            this.objectType = objectType;
            this.objectTypePlural = objectTypePlural;
            this.referenceId = referenceId;
            this.fields = fields;
            this.childrenTrees = childrenTrees;
        }

        public JSONObject asJSON() throws JSONException {
            JSONObject parentJson = buildJsonForRecord(objectType, referenceId, fields);
            if (childrenTrees != null) {

                // Grouping children trees by type and figuring out object type to object type plural mapping
                Map<String, String> objectTypeToObjectTypePlural = new HashMap<>();
                Map<String, List<SObjectTree>> objectTypeToChildrenTrees = new HashMap<>();
                for (SObjectTree childTree : childrenTrees) {
                    String childObjectType = childTree.objectType;
                    if (!objectTypeToObjectTypePlural.containsKey(childObjectType)) {
                        objectTypeToObjectTypePlural.put(childObjectType, childTree.objectTypePlural);
                    }
                    if (!objectTypeToChildrenTrees.containsKey(childObjectType)) {
                        objectTypeToChildrenTrees.put(childObjectType, new ArrayList<SObjectTree>());
                    }
                    objectTypeToChildrenTrees.get(childObjectType).add(childTree);
                }

                // Iterating through children
                for (Map.Entry<String, List<SObjectTree>> entry : objectTypeToChildrenTrees.entrySet()) {
                    String childrenObjectType = entry.getKey();
                    List<SObjectTree> childrenTreesForType = entry.getValue();
                    JSONArray childrenJsonArray = new JSONArray();
                    for (SObjectTree childTree : childrenTreesForType) {
                        JSONObject childJson = buildJsonForRecord(childrenObjectType, childTree.referenceId, childTree.fields);
                        childrenJsonArray.put(childJson);
                    }
                    parentJson.put(objectTypeToObjectTypePlural.get(childrenObjectType), JSONObjectHelper.makeJSONObject(RECORDS, childrenJsonArray));
                }
            }

            // Done
            return parentJson;
        }

        private JSONObject buildJsonForRecord(String objectType, String referenceId, Map<String, Object> fields) throws JSONException {
            JSONObject jsonForAttributes = new JSONObject();
            jsonForAttributes.put(REFERENCE_ID, referenceId);
            jsonForAttributes.put(TYPE, objectType);
            JSONObject jsonForRecord = new JSONObject(fields);
            jsonForRecord.put(ATTRIBUTES, jsonForAttributes);
            return jsonForRecord;
        }
    }
}

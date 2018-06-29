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

import android.text.TextUtils;

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

    /**
     * HTTP date format
     */
    public static final DateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    static {
        HTTP_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

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

	private enum RestAction {

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
		SEARCH(SERVICES_DATA + "%s/search"),
		SEARCH_SCOPE_AND_ORDER(SERVICES_DATA + "%s/search/scopeOrder"),
		SEARCH_RESULT_LAYOUT(SERVICES_DATA + "%s/search/layout"),
        OBJECT_LAYOUT(SERVICES_DATA + "%s/ui-api/layout/%s"),
		COMPOSITE(SERVICES_DATA + "%s/composite"),
        BATCH(SERVICES_DATA + "%s/composite/batch"),
        SOBJECT_TREE(SERVICES_DATA + "%s/composite/tree/%s");

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
    private boolean shouldRefreshOn403 = true;

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
    public RestRequest(RestMethod method, String path,  Map<String, String> additionalHttpHeaders) {
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
    public RestRequest(RestMethod method, String path, JSONObject requestBodyAsJson,  Map<String, String> additionalHttpHeaders) {
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
     * Returns whether the SDK should attempt to refresh tokens if the service returns HTTP 403.
     *
     * @return True - if the SDK should refresh on HTTP 403, False - otherwise.
     */
	public boolean getShouldRefreshOn403() {
	    return shouldRefreshOn403;
    }

    /**
     * Sets whether the SDK should attempt to refresh tokens if the service returns HTTP 403.
     *
     * @param shouldRefreshOn403 True - if the SDK should refresh on HTTP 403, False - otherwise.
     */
	public synchronized void setShouldRefreshOn403(boolean shouldRefreshOn403) {
        this.shouldRefreshOn403 = shouldRefreshOn403;
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
	public static RestRequest getRequestForRetrieve(String apiVersion, String objectType, String objectId, List<String> fieldList) throws UnsupportedEncodingException  {
		StringBuilder path = new StringBuilder(RestAction.RETRIEVE.getPath(apiVersion, objectType, objectId));
		if (fieldList != null && fieldList.size() > 0) { 
			path.append("?fields=");
			path.append(URLEncoder.encode(toCsv(fieldList).toString(), UTF_8));
		}
		return new RestRequest(RestMethod.GET, path.toString());
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
	public static RestRequest getRequestForDelete(String apiVersion, String objectType, String objectId)  {
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
	public static RestRequest getRequestForSearch(String apiVersion, String q) throws UnsupportedEncodingException  {
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
	public static RestRequest getRequestForQuery(String apiVersion, String q) throws UnsupportedEncodingException  {
		StringBuilder path = new StringBuilder(RestAction.QUERY.getPath(apiVersion));
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
	public static RestRequest getRequestForSearchScopeAndOrder(String apiVersion)  {
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
	public static RestRequest getRequestForSearchResultLayout(String apiVersion, List<String> objectList) throws UnsupportedEncodingException  {
		StringBuilder path = new StringBuilder(RestAction.SEARCH_RESULT_LAYOUT.getPath(apiVersion));
		path.append("?q=");
		path.append(URLEncoder.encode(toCsv(objectList).toString(), UTF_8));
		return new RestRequest(RestMethod.GET, path.toString());
	}

	/**
	 * Request to get object layout data.
	 *
	 * @param apiVersion Salesforce API version.
	 * @param objectType Object type.
     * @param layoutType Layout type. Could be "Compact" or "Full".
	 * @return RestRequest object that requests the object layout for the given object and layout types.
	 * @see <a href="https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_resources_record_layout.htm">https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_resources_record_layout.htm</a>
	 */
	public static RestRequest getRequestForObjectLayout(String apiVersion, String objectType, String layoutType)  {
		final StringBuilder path = new StringBuilder(RestAction.OBJECT_LAYOUT.getPath(apiVersion, objectType));
		if (!TextUtils.isEmpty(layoutType)) {
            path.append("?layoutType=");
            path.append(layoutType);
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
	public static RestRequest getCompositeRequest(String apiVersion, boolean allOrNone, LinkedHashMap<String, RestRequest> refIdToRequests) throws JSONException {
		JSONArray requestsArrayJson = new JSONArray();
        for (Map.Entry<String,RestRequest> entry : refIdToRequests.entrySet()) {
            String referenceId = entry.getKey();
            RestRequest request = entry.getValue();
            JSONObject requestJson = request.asJSON();
            requestJson.put(REFERENCE_ID, referenceId);
			requestsArrayJson.put(requestJson);
		}
		JSONObject compositeRequestJson =  new JSONObject();
		compositeRequestJson.put(COMPOSITE_REQUEST, requestsArrayJson);
        compositeRequestJson.put(ALL_OR_NONE, allOrNone);
		return new RestRequest(RestMethod.POST, RestAction.COMPOSITE.getPath(apiVersion), compositeRequestJson);
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
    public static RestRequest getBatchRequest(String apiVersion, boolean haltOnError, List<RestRequest> requests) throws JSONException {
        JSONArray requestsArrayJson = new JSONArray();
        for (RestRequest request : requests) {
            // Note: unfortunately batch sub request and composite sub request differ
            if (!request.getPath().startsWith(SERVICES_DATA)) {
                throw new RuntimeException("Request not supported in batch: " + request.toString());
            }
            JSONObject requestJson = new JSONObject();
            requestJson.put(METHOD, request.getMethod().toString());
            requestJson.put(URL, request.getPath().substring(SERVICES_DATA.length()));
            requestJson.put(RICH_INPUT, request.getRequestBodyAsJson());
            requestsArrayJson.put(requestJson);
        }
        JSONObject batchRequestJson =  new JSONObject();
        batchRequestJson.put(BATCH_REQUESTS, requestsArrayJson);
        batchRequestJson.put(HALT_ON_ERROR, haltOnError);
        return new RestRequest(RestMethod.POST, RestAction.BATCH.getPath(apiVersion), batchRequestJson);
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

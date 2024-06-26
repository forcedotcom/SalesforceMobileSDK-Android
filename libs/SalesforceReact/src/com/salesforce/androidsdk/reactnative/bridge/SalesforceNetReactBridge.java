/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.reactnative.bridge;

import android.util.Base64;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.salesforce.androidsdk.reactnative.ui.SalesforceReactActivity;
import com.salesforce.androidsdk.reactnative.util.SalesforceReactLogger;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

public class SalesforceNetReactBridge extends ReactContextBaseJavaModule {

    private static final String METHOD_KEY = "method";
    private static final String END_POINT_KEY = "endPoint";
    private static final String PATH_KEY = "path";
    private static final String QUERY_PARAMS_KEY = "queryParams";
    private static final String HEADER_PARAMS_KEY = "headerParams";
    private static final String FILE_PARAMS_KEY = "fileParams";
    private static final String FILE_MIME_TYPE_KEY = "fileMimeType";
    private static final String FILE_URL_KEY = "fileUrl";
    private static final String FILE_NAME_KEY = "fileName";
    private static final String RETURN_BINARY = "returnBinary";
    private static final String ENCODED_BODY = "encodedBody";
    private static final String CONTENT_TYPE = "contentType";
    private static final String DOES_NOT_REQUIRE_AUTHENTICATION = "doesNotRequireAuthentication";
    private static final String TAG = "SalesforceNetReactBridge";

    public SalesforceNetReactBridge(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return TAG;
    }

    @ReactMethod
    public void sendRequest(ReadableMap args, final Callback successCallback, final Callback errorCallback) {
        try {
            // Prepare request
            final RestRequest request = prepareRestRequest(args);
            final boolean returnBinary = args.hasKey(RETURN_BINARY) && args.getBoolean(RETURN_BINARY);
            final boolean doesNotRequireAuth = args.hasKey(DOES_NOT_REQUIRE_AUTHENTICATION)
                    && args.getBoolean(DOES_NOT_REQUIRE_AUTHENTICATION);

            // Sending request
            final RestClient restClient = getRestClient(doesNotRequireAuth);
            if (restClient == null) {
                return; // we are detached - do nothing
            }
            restClient.sendAsync(request, new RestClient.AsyncRequestCallback() {

                @Override
                public void onSuccess(RestRequest request, RestResponse response) {
                    try {

                        // Sending a string over and letting javascript do a JSON.parse(result)
                        // It would be better to use NativeMap/NativeArray
                        // Although the absence of a common super class would force us to
                        // introduce two sendRequest methods:
                        // - one that expects map back from the server
                        // - one that expects array back from the server

                        // Not a 2xx status
                        if (!response.isSuccess()) {
                            final JSONObject responseObject = new JSONObject();
                            responseObject.put("headers", new JSONObject(response.getAllHeaders()));
                            responseObject.put("statusCode", response.getStatusCode());
                            responseObject.put("body", parsedResponse(response));
                            final JSONObject errorObject = new JSONObject();
                            errorObject.put("response", responseObject);
                            errorCallback.invoke(errorObject.toString());
                        }

                        // Binary response
                        else if (returnBinary) {
                            JSONObject result = new JSONObject();
                            result.put(CONTENT_TYPE, response.getContentType());
                            result.put(ENCODED_BODY, Base64.encodeToString(response.asBytes(), Base64.DEFAULT));
                            successCallback.invoke(result.toString());
                        }

                        // Other cases
                        else {
                            successCallback.invoke(response.asString());
                        }
                    } catch (Exception e) {
                        SalesforceReactLogger.e(TAG, "sendRequest failed", e);
                        onError(e);
                    }
                }

                @Override
                public void onError(Exception exception) {
                    final JSONObject errorObject = new JSONObject();
                    try {
                        errorObject.put("error", exception.getMessage());
                    } catch (JSONException jsonException) {
                        SalesforceReactLogger.e(TAG, "Error creating error object", jsonException);
                    }
                    errorCallback.invoke(errorObject.toString());
                }
            });
        } catch (Exception exception) {
            final JSONObject errorObject = new JSONObject();
            try {
                errorObject.put("error", exception.getMessage());
            } catch (JSONException jsonException) {
                SalesforceReactLogger.e(TAG, "Error creating error object", jsonException);
            }
            errorCallback.invoke(errorObject.toString());
        }
    }

    private Object parsedResponse(RestResponse response) throws IOException {
        // Is it a JSONObject?
        final JSONObject responseAsJSONObject = parseResponseAsJSONObject(response);
        if (responseAsJSONObject != null) {
            return responseAsJSONObject;
        }

        // Is it a JSONArray?
        final JSONArray responseAsJSONArray = parseResponseAsJSONArray(response);
        if (responseAsJSONArray != null) {
            return responseAsJSONArray;
        }

        // Otherwise return as string
        return response.asString();
    }

    private JSONObject parseResponseAsJSONObject(RestResponse response) throws IOException {
        try {
            return response.asJSONObject();
        }
        catch (JSONException e) {
            // Not a JSON object
            return null;
        }
    }

    private JSONArray parseResponseAsJSONArray(RestResponse response) throws IOException {
        try {
            return response.asJSONArray();
        }
        catch (JSONException e) {
            // Not a JSON array
            return null;
        }
    }

    @NonNull
    private RestRequest prepareRestRequest(ReadableMap args) throws UnsupportedEncodingException, URISyntaxException {

        // Parse args
        RestRequest.RestMethod method = RestRequest.RestMethod.valueOf(args.getString(METHOD_KEY));
        String endPoint = !args.hasKey(END_POINT_KEY) || args.isNull(END_POINT_KEY) ? "" : args.getString(END_POINT_KEY);
        String path = args.getString(PATH_KEY);
        ReadableMap queryParams = args.getMap(QUERY_PARAMS_KEY);
        ReadableMap headerParams = args.getMap(HEADER_PARAMS_KEY);
        ReadableMap fileParams = args.getMap(FILE_PARAMS_KEY);

        // Preparing request
        Map<String, String> additionalHeaders = ReactBridgeHelper.toJavaStringStringMap(headerParams);
        Map<String, Object> queryParamsMap = ReactBridgeHelper.toJavaMap(queryParams);
        Map<String, Map<String, String>> fileParamsMap = ReactBridgeHelper.toJavaStringMapMap(fileParams);
        String urlParams = "";
        RequestBody requestBody = null;
        if (method == RestRequest.RestMethod.DELETE || method == RestRequest.RestMethod.GET
                || method == RestRequest.RestMethod.HEAD) {
            urlParams = buildQueryString(queryParamsMap);
        } else {
            requestBody = buildRequestBody(queryParamsMap, fileParamsMap);
        }
        String separator = urlParams.isEmpty()
                ? ""
                : path.contains("?")
                    ? (path.endsWith("&") ? "" : "&")
                    : "?";
        return new RestRequest(method, endPoint + path + separator + urlParams, requestBody, additionalHeaders);
    }

    /**
     * Returns the RestClient instance being used by this bridge.
     *
     * @param doesNotRequireAuth True - if an unauthenticated client should be used, False - otherwise.
     * @return RestClient instance.
     */
    protected RestClient getRestClient(boolean doesNotRequireAuth) {
        final SalesforceReactActivity currentActivity = (SalesforceReactActivity) getCurrentActivity();
        if (currentActivity == null) {
            return null;
        }
        if (doesNotRequireAuth) {
            return currentActivity.buildClientManager().peekUnauthenticatedRestClient();
        }
        return currentActivity.getRestClient();
    }

    private static String buildQueryString(Map<String, Object> params) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, Object> entry : params.entrySet()) {
            sb.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue().toString(), RestRequest.UTF_8)).append("&");
        }
        return sb.toString();
    }

    private static RequestBody buildRequestBody(Map<String, Object> params, Map<String, Map<String, String>> fileParams) throws URISyntaxException {
        final RequestBody paramsRequestBody = RequestBody.create(RestRequest.MEDIA_TYPE_JSON, new JSONObject(params).toString());
        if (fileParams.isEmpty()) {
            return paramsRequestBody;
        } else {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            builder.addFormDataPart("", null, paramsRequestBody);
            
            // File params expected to be of the form:
            // {<fileParamNameInPost>: {fileMimeType:<someMimeType>, fileUrl:<fileUrl>, fileName:<fileNameForPost>}}
            for(Map.Entry<String, Map<String, String>> fileParamEntry : fileParams.entrySet()) {
                Map<String, String> fileParam = fileParamEntry.getValue();
                String fileParamName = fileParamEntry.getKey();
                String mimeType = fileParam.get(FILE_MIME_TYPE_KEY);
                String name = fileParam.get(FILE_NAME_KEY);
                URI url = new URI(fileParam.get(FILE_URL_KEY));
                File file = new File(url);
                MediaType mediaType = MediaType.parse(mimeType);
                builder.addFormDataPart(fileParamName, name, RequestBody.create(mediaType, file));
            }
            return builder.build();
        }
    }
}

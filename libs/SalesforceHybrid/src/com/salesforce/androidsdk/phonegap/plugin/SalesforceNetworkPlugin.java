/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap.plugin;

import android.text.TextUtils;

import com.salesforce.androidsdk.phonegap.ui.SalesforceDroidGapActivity;
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * PhoneGap plugin for native networking.
 *
 * @author bhariharan
 */
public class SalesforceNetworkPlugin extends ForcePlugin {

    private static final String TAG = "SalesforceNetworkPlugin";
    private static final String METHOD_KEY = "method";
    private static final String END_POINT_KEY = "endPoint";
    private static final String PATH_KEY = "path";
    private static final String QUERY_PARAMS_KEY = "queryParams";
    private static final String HEADER_PARAMS_KEY = "headerParams";
    private static final String FILE_PARAMS_KEY = "fileParams";
    private static final String FILE_MIME_TYPE_KEY = "fileMimeType";
    private static final String FILE_URL_KEY = "fileUrl";
    private static final String FILE_NAME_KEY = "fileName";

    private RestClient restClient;

    /**
     * Supported plugin actions that the client can take.
     */
    enum Action {
        pgSendRequest
    }

    @Override
    public boolean execute(String actionStr, JavaScriptPluginVersion jsVersion, JSONArray args,
                           CallbackContext callbackContext) throws JSONException {
        Action action = null;
        try {
            action = Action.valueOf(actionStr);
            switch(action) {
                case pgSendRequest:
                    sendRequest(args, callbackContext);
                    return true;
                default:
                    return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Native implementation for "sendRequest" action.
     *
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    protected void sendRequest(JSONArray args, final CallbackContext callbackContext) {
        try {
            final RestRequest request = prepareRestRequest(args);

            // Sends the request.
            final RestClient restClient = getRestClient();
            if (restClient == null) {
                return;
            }
            restClient.sendAsync(request, new RestClient.AsyncRequestCallback() {

                @Override
                public void onSuccess(RestRequest request, RestResponse response) {
                    try {

                        /*
                         * Response body could be either JSONObject or JSONArray, and there's no
                         * good way to determine this from the response headers. Hence, we try both.
                         */
                        if (response.hasResponseBody()) {
                            try {
                                final JSONObject responseAsJSONObject = response.asJSONObject();
                                callbackContext.success(responseAsJSONObject);
                            } catch (Exception ex) {
                                SalesforceHybridLogger.e(TAG, "Error while parsing response", ex);
                                final JSONArray responseAsJSONArray = response.asJSONArray();
                                callbackContext.success(responseAsJSONArray);
                            }
                        } else {
                            callbackContext.success();
                        }
                    } catch (Exception e) {
                        SalesforceHybridLogger.e(TAG, "Error while parsing response", e);
                        if (response.isSuccess()) {
                            callbackContext.success();
                        } else {
                            onError(e);
                        }
                    }
                }

                @Override
                public void onError(Exception exception) {
                    callbackContext.error(exception.getMessage());
                }
            });
        } catch (Exception exception) {
            callbackContext.error(exception.getMessage());
        }
    }

    private RestRequest prepareRestRequest(JSONArray args) throws UnsupportedEncodingException,
            URISyntaxException, JSONException {
        final JSONObject arg0 = args.optJSONObject(0);
        if (arg0 != null) {
            final RestRequest.RestMethod method = RestRequest.RestMethod.valueOf(arg0.optString(METHOD_KEY));
            final String endPoint = arg0.optString(END_POINT_KEY);
            final String path = arg0.optString(PATH_KEY);
            final String queryParamString = arg0.optString(QUERY_PARAMS_KEY);
            JSONObject queryParams = new JSONObject();
            if (!TextUtils.isEmpty(queryParamString)) {
                queryParams = new JSONObject(queryParamString);
            }
            final JSONObject headerParams = arg0.optJSONObject(HEADER_PARAMS_KEY);
            final Iterator<String> headerKeys = headerParams.keys();
            final Map<String, String> additionalHeaders = new HashMap<String, String>();
            if (headerKeys != null) {
                while (headerKeys.hasNext()) {
                    final String headerKeyStr = headerKeys.next();
                    if (!TextUtils.isEmpty(headerKeyStr)) {
                        additionalHeaders.put(headerKeyStr, headerParams.optString(headerKeyStr));
                    }
                }
            }
            final JSONObject fileParams = arg0.optJSONObject(FILE_PARAMS_KEY);

            // Prepares the request.
            String urlParams = "";
            RequestBody requestBody = null;
            if (method == RestRequest.RestMethod.DELETE || method == RestRequest.RestMethod.GET
                    || method == RestRequest.RestMethod.HEAD) {
                urlParams = buildQueryString(queryParams);
            } else {
                requestBody = buildRequestBody(queryParams, fileParams);
            }
            final String separator = urlParams.isEmpty()
                    ? ""
                    : path.contains("?")
                    ? (path.endsWith("&") ? "" : "&")
                    : "?";
            return new RestRequest(method, endPoint + path + separator + urlParams,
                    requestBody, additionalHeaders);
        }
        return null;
    }

    private RestClient getRestClient() {
        final SalesforceDroidGapActivity currentActivity = (SalesforceDroidGapActivity) cordova.getActivity();
        return currentActivity != null ? currentActivity.getRestClient() : null;
    }

    private static String buildQueryString(JSONObject params) throws UnsupportedEncodingException {
        if (params == null || params.length() == 0) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        final Iterator<String> keys = params.keys();
        if (keys != null) {
            while (keys.hasNext()) {
                final String keyStr = keys.next();
                if (!TextUtils.isEmpty(keyStr)) {
                    sb.append(keyStr).append("=").append(URLEncoder.encode(params.optString(keyStr),
                            RestRequest.UTF_8)).append("&");
                }
            }
        }
        return sb.toString();
    }

    private static RequestBody buildRequestBody(JSONObject params, JSONObject fileParams) throws URISyntaxException {
        if (fileParams == null || fileParams.length() == 0) {
            return RequestBody.create(RestRequest.MEDIA_TYPE_JSON, params.toString());
        } else {
            final MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            final Iterator<String> keys = params.keys();
            if (keys != null) {
                while (keys.hasNext()) {
                    final String keyStr = keys.next();
                    if (!TextUtils.isEmpty(keyStr)) {
                        builder.addFormDataPart(keyStr, params.optString(keyStr));
                    }
                }
            }

            /*
             * File params expected to be of the form:
             * {<fileParamNameInPost>: {fileMimeType:<someMimeType>, fileUrl:<fileUrl>, fileName:<fileNameForPost>}}.
             */
            final Iterator<String> fileKeys = fileParams.keys();
            if (fileKeys != null) {
                while (fileKeys.hasNext()) {
                    final String fileKeyStr = fileKeys.next();
                    if (!TextUtils.isEmpty(fileKeyStr)) {
                        final JSONObject fileParam = fileParams.optJSONObject(fileKeyStr);
                        if (fileParam != null) {
                            final String mimeType = fileParam.optString(FILE_MIME_TYPE_KEY);
                            final String name = fileParam.optString(FILE_NAME_KEY);
                            final URI url = new URI(fileParam.optString(FILE_URL_KEY));
                            final File file = new File(url);
                            final MediaType mediaType = MediaType.parse(mimeType);
                            builder.addFormDataPart(fileKeyStr, name, RequestBody.create(mediaType, file));
                        }
                    }
                }
            }
            return builder.build();
        }
    }
}

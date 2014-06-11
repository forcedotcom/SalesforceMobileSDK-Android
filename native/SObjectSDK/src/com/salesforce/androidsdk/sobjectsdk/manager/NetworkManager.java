/*
 * Copyright (c) 2014, salesforce.com, inc.
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
package com.salesforce.androidsdk.sobjectsdk.manager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Set;

import org.apache.http.entity.StringEntity;

import android.util.Log;

import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestRequest.RestMethod;
import com.salesforce.androidsdk.rest.RestResponse;

/**
 * This class has APIs to perform HTTP requests against a Salesforce endpoint.
 *
 * @author bhariharan
 */
public class NetworkManager {

    private static final String TAG = "SObjectSDK: NetworkManager";

    private static NetworkManager INSTANCE;

    private RestClient client;

    /**
     * Returns a singleton instance of this class.
     *
     * @param client Instance of RestClient.
     * @return Singleton instance of this class.
     */
    public static NetworkManager getInstance(RestClient client) {
        if (INSTANCE == null) {
            INSTANCE = new NetworkManager(client);
        }
        return INSTANCE;
    }

    /**
     * Resets the network manager.
     */
    public static void reset() {
        INSTANCE = null;
    }

    /**
     * Private constructor.
     *
     * @param client RestClient instance.
     */
    private NetworkManager(RestClient client) {
        this.client = client;
    }

    /**
     * Makes a remote GET request and returns the response received.
     *
     * @param path Request path.
     * @param params Additional params.
     * @return Response received.
     */
    public RestResponse makeRemoteGETRequest(String path, Map<String, Object> params) {
        return makeRemoteRequest(true, path, params, null, null, null);
    }

    /**
     * Makes a remote GET request and returns the response received.
     *
     * @param path Request path.
     * @param params Additional params.
     * @param requestHeaders Additional headers for the request.
     * @return Response received.
     */
    public RestResponse makeRemoteGETRequest(String path, Map<String, Object> params,
            Map<String, String> requestHeaders) {
        return makeRemoteRequest(true, path, params, null, null, requestHeaders);
    }

    /**
     * Makes a remote request and returns the response received.
     *
     * @param isGetMethod True - if this is a GET request, False - otherwise.
     * @param path Request path.
     * @param params Additional params.
     * @param postData POST data.
     * @param postDataContentType Content type for POST data.
     * @param requestHeaders Additional headers for the request.
     * @return Response received.
     */
    public RestResponse makeRemoteRequest(boolean isGetMethod, String path,
            Map<String, Object> params, String postData,
            String postDataContentType, Map<String, String> requestHeaders) {
        if (path == null) {
            return null;
        }
        if (params != null && params.size() > 0) {
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            final StringBuilder sb = new StringBuilder(path);
            sb.append("?");
            final Set<String> keys = params.keySet();
            for (final String key : keys) {
                if (key != null) {
                    final String value = (String) params.get(key);
                    if (value != null) {
                        sb.append(key).append("=").append(value);
                        sb.append("&");
                    }
                }
            }
            final String pathWithParams = sb.toString();
            if (pathWithParams.endsWith("?") || pathWithParams.endsWith("&")) {
                path = pathWithParams.substring(0, pathWithParams.length() - 1);
            }
        }
        final RestMethod requestMethod = isGetMethod ? RestMethod.GET : RestMethod.POST;
        RestResponse response = null;
        StringEntity postEntity = null;
        try {
            if (postData != null) {
                postEntity = new StringEntity(postData);
            }
            final RestRequest request = new RestRequest(requestMethod, path,
                    postEntity, requestHeaders);
            response = client.sendSync(request);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Exception occurred while encoding POST data", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException occurred while making network request", e);
        } finally {
            if (response != null) {
                try {
                    response.consume();
                } catch (IOException e) {
                    Log.e(TAG, "IOException occurred while making network request", e);
                }
            }
        }
        return response;
    }

    /**
     * Sets the RestClient instance associated with this network interface.
     *
     * @param client RestClient instance.
     */
    public void setRestClient(RestClient client) {
        INSTANCE.client = client;
    }
}

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
package com.salesforce.androidsdk.analytics;

import com.salesforce.androidsdk.analytics.model.InstrumentationEvent;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.GzipSink;
import okio.Okio;

/**
 * Network publisher for the AILTN endpoint.
 *
 * @author bhariharan
 */
public class AILTNPublisher implements AnalyticsPublisher {

    private static final String TAG = "AILTNPublisher";
    private static final String CODE = "code";
    private static final String AILTN = "ailtn";
    private static final String DATA = "data";
    private static final String LOG_LINES = "logLines";
    private static final String PAYLOAD = "payload";
    private static final String API_PATH = "/services/data/%s/connect/proxy/app-analytics-logging";
    private static final String CONTENT_ENCODING = "Content-Encoding";
    private static final String CONTENT_LENGTH = "Content-Length";
    private static final String GZIP = "gzip";

    @Override
    public boolean publish(JSONArray events) {
        if (events == null || events.length() == 0) {
            return true;
        }

        // Builds the POST body of the request.
        final JSONArray logLines = new JSONArray();
        try {
            for (int i = 0; i < events.length(); i++) {
                final JSONObject event = events.optJSONObject(i);
                if (event != null) {
                    final JSONObject trackingInfo = new JSONObject();
                    trackingInfo.put(CODE, AILTN);
                    final JSONObject data = new JSONObject();
                    final String schemaType = event.optString(InstrumentationEvent.SCHEMA_TYPE_KEY);
                    data.put(InstrumentationEvent.SCHEMA_TYPE_KEY, schemaType);
                    event.remove(InstrumentationEvent.SCHEMA_TYPE_KEY);
                    data.put(PAYLOAD, event.toString());
                    trackingInfo.put(DATA, data);
                    logLines.put(trackingInfo);
                }
            }
        } catch (JSONException e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while constructing event payload", e);
            return false;
        }
        return publishLogLines(logLines);
    }

    public boolean publishLogLines(JSONArray logLines) {
        final JSONObject body = new JSONObject();
        try {
            body.put(LOG_LINES, logLines);
        } catch (JSONException e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while constructing event payload", e);
            return false;
        }
        RestResponse restResponse = null;
        try {
            final String apiPath = String.format(API_PATH,
                    ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().getAppContext()));
            final RestClient restClient = SalesforceSDKManager.getInstance().getClientManager().peekRestClient();

            /*
             * Since the publisher is invoked from a Service, it could use an instance
             * of RestClient from memory that has no backing OkHttpClient that's ready.
             */
            if (restClient.getOkHttpClient() == null) {
                return false;
            }

            /*
             * There's no easy way to get content length using GZIP interceptors. Some trickery is
             * required to achieve this by adding an additional interceptor to determine content length.
             * See this post for more details: https://github.com/square/okhttp/issues/350#issuecomment-123105641.
             */
            final RequestBody requestBody = setContentLength(gzipCompressedBody(RequestBody.create(RestRequest.MEDIA_TYPE_JSON,
                    body.toString())));
            final Map<String, String> requestHeaders = new HashMap<>();
            requestHeaders.put(CONTENT_ENCODING, GZIP);
            requestHeaders.put(CONTENT_LENGTH, Long.toString(requestBody.contentLength()));
            final RestRequest restRequest = new RestRequest(RestRequest.RestMethod.POST, apiPath,
                    requestBody, requestHeaders);
            restResponse = restClient.sendSync(restRequest);
        } catch (ClientManager.AccountInfoNotFoundException e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while constructing rest client", e);
        } catch (IOException e) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while making network request", e);
        }
        if (restResponse != null && restResponse.isSuccess()) {
            return true;
        }
        return false;
    }

    private RequestBody setContentLength(final RequestBody requestBody) throws IOException {
        final Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);
        return new RequestBody() {

            @Override
            public MediaType contentType() {
                return requestBody.contentType();
            }

            @Override
            public long contentLength() {
                return buffer.size();
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                sink.write(buffer.snapshot());
            }
        };
    }

    private RequestBody gzipCompressedBody(final RequestBody body) {
        return new RequestBody() {

            @Override
            public MediaType contentType() {
                return body.contentType();
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                final BufferedSink gzipSink = Okio.buffer(new GzipSink(sink));
                body.writeTo(gzipSink);
                gzipSink.close();
            }
        };
    }
}

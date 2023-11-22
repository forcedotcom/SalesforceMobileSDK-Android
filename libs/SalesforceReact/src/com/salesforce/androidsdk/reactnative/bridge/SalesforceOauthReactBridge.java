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

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.salesforce.androidsdk.reactnative.ui.SalesforceReactActivity;
import com.salesforce.androidsdk.rest.RestClient;


import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class SalesforceOauthReactBridge extends ReactContextBaseJavaModule {

    private static final String TAG = "SalesforceOauthReactBridge";

    public SalesforceOauthReactBridge(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return TAG;
    }

    @ReactMethod
    public void authenticate(ReadableMap args,
                             Callback successCallback, Callback errorCallback) {
        final SalesforceReactActivity currentActivity = (SalesforceReactActivity) getCurrentActivity();
        if (currentActivity != null) {
            currentActivity.authenticate(successCallback, errorCallback);
        }
        else {
            if (errorCallback != null) {
                errorCallback.invoke("SalesforceReactActivity not found");
            }
        }
    }


    @ReactMethod
    public void getAuthCredentials(ReadableMap args,
                                   Callback successCallback, Callback errorCallback) {
        final SalesforceReactActivity currentActivity = (SalesforceReactActivity) getCurrentActivity();
        if (currentActivity != null) {
            currentActivity.getAuthCredentials(successCallback, errorCallback);
        }
        else {
            if (errorCallback != null) {
                errorCallback.invoke("SalesforceReactActivity not found");
            }
        }
    }
    // Reference the function in your React Native module
    @ReactMethod
    public String updateAccessToken(ReadableMap args, Callback successCallback, Callback errorCallback) {
        final SalesforceReactActivity currentActivity = (SalesforceReactActivity) getCurrentActivity();
        if (currentActivity != null) {
            try {
                String salesforceEndpoint = currentActivity.getRestClient().getClientInfo().loginUrl.toString();
RestClient client = currentActivity.getRestClient();
                String clientId = "3MVG9ux34Ig8G5eqGtsZfUwvvvqAVW2Ud3PsVW_pN.4cd79XOOlxVeK315e2_F6fQyj0DMbKGtlXzPaIdKRtO";
                String clientSecret = "81C0282B921F3361FC78D4A765332C670AC04CEF04D79EE25B51225DEC768284";
                // Get the refresh token from secure storage
                String refreshToken =currentActivity.getRestClient().getRefreshToken().toString();


                // Construct the URL for token refresh
                String tokenRefreshURL = salesforceEndpoint + "/services/oauth2/token";
                // Construct the request parameters
                String postParameters = "grant_type=refresh_token&client_id=" + clientId + "&client_secret=" + clientSecret + "&refresh_token=" + refreshToken;
                // Create URL object
                URL url = new URL(tokenRefreshURL);
                // Create connection
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);
                // Send request
                DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
                outputStream.writeBytes(postParameters);
                outputStream.flush();
                outputStream.close();
                // Get the response
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                  String response = null;
                    while ((inputLine = in.readLine()) != null) {
                        response=inputLine.toString();
                    }
                    in.close();

                    response = response.substring(1, response.length() - 1); // remove curly brackets
                    String[] keyValuePairs = response.split(","); // split the string to create key-value pairs
                    Map<String, String> map = new HashMap<>();

                    for (String pair : keyValuePairs) {
                        String[] entry = pair.split(":");
                        String key = entry[0].trim().replace("\"", ""); // remove quotes from the key
                        String value = entry[1].trim().replace("\"", ""); // remove quotes from the value
                        map.put(key, value);
                    }

                    String newAccessToken = map.get("access_token");
          
                    currentActivity.getRestClient().setAccessToken(newAccessToken);
successCallback.invoke(response.toString());
                } else {
                    Log.e("TokenRefreshTask", "HTTP error code: " + responseCode);
                }
            } catch (Exception e) {

                errorCallback.invoke(e.getMessage());
            }
        } else {
            if (errorCallback != null) {
                errorCallback.invoke("SalesforceReactActivity not found");
            }
        }
        return null;
    }

    private static String extractAccessToken(String responseBody) {
        // This is a simplified example, you should use a JSON library to properly parse the response
        // In a real application, handle errors and extract the access token securely
        return responseBody.split("\"access_token\" : \"")[1].split("\"")[0];
    }

    @ReactMethod
    public void logoutCurrentUser(ReadableMap args,
                                  Callback successCallback, Callback errorCallback) {
        final SalesforceReactActivity currentActivity = (SalesforceReactActivity) getCurrentActivity();
        if (currentActivity != null) {
            currentActivity.logout(successCallback);
        }
        else {
            if (errorCallback != null) {
                errorCallback.invoke("SalesforceReactActivity not found");
            }
        }
    }
}

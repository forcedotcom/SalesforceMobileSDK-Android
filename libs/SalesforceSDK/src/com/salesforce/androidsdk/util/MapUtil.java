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
package com.salesforce.androidsdk.util;

import android.os.Bundle;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple util class that has methods to serialize and deserialize Map and Bundle.
 *
 * @author bhariharan
 */
public class MapUtil {

    private static final String TAG = "MapUtil";

    /**
     * Adds a map of key-value pairs extracted from a bundle.
     *
     * @param bundle Bundle.
     * @param keys Keys.
     * @param map Map to be added to.
     * @return Map with the additions.
     */
    public static Map<String, String> addBundleToMap(Bundle bundle, List<String> keys,
                                                     Map<String, String> map) {
        if (bundle == null || keys == null || bundle.isEmpty() || keys.isEmpty()) {
            return map;
        }
        if (map == null) {
            map = new HashMap<>();
        }
        for (final String key : keys) {
            if (!TextUtils.isEmpty(key)) {
                map.put(key, bundle.getString(key));
            }
        }
        return map;
    }

    /**
     * Adds a bundle of key-value pairs extracted from a map.
     *
     * @param map Map.
     * @param keys Keys.
     * @param bundle Bundle to be added to.
     * @return Bundle with the additions.
     */
    public static Bundle addMapToBundle(Map<String, String> map, List<String> keys, Bundle bundle) {
        if (map == null || keys == null || map.isEmpty() || keys.isEmpty()) {
            return bundle;
        }
        if (bundle == null) {
            bundle = new Bundle();
        }
        for (final String key : keys) {
            if (!TextUtils.isEmpty(key)) {
                bundle.putString(key, map.get(key));
            }
        }
        return bundle;
    }

    /**
     * Adds a map of key-value pairs extracted from a JSONObject.
     *
     * @param jsonObject JSONObject.
     * @param keys Keys.
     * @param map Map to be added to.
     * @return Map with the additions.
     */
    public static Map<String, String> addJSONObjectToMap(JSONObject jsonObject, List<String> keys,
                                                         Map<String, String> map) {
        if (jsonObject == null || keys == null || jsonObject.length() == 0 || keys.isEmpty()) {
            return map;
        }
        if (map == null) {
            map = new HashMap<>();
        }
        for (final String key : keys) {
            if (!TextUtils.isEmpty(key)) {
                map.put(key, jsonObject.optString(key));
            }
        }
        return map;
    }

    /**
     * Adds a JSONObject of key-value pairs extracted from a map.
     *
     * @param map Map.
     * @param keys Keys.
     * @param jsonObject JSONObject to be added to.
     * @return JSONObject with the additions.
     */
    public static JSONObject addMapToJSONObject(Map<String, String> map, List<String> keys,
                                                JSONObject jsonObject) {
        if (map == null || keys == null || map.isEmpty() || keys.isEmpty()) {
            return jsonObject;
        }
        if (jsonObject == null) {
            jsonObject = new JSONObject();
        }
        for (final String key : keys) {
            if (!TextUtils.isEmpty(key)) {
                try {
                    jsonObject.put(key, map.get(key));
                } catch (JSONException e) {
                    SalesforceSDKLogger.e(TAG, "Exception thrown while creating JSON object", e);
                }
            }
        }
        return jsonObject;
    }
}

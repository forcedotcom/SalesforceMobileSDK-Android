/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.config;

import android.content.Context;

import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;

/**
 * Class encapsulating a SmartStore schema (soups).
 *
 * Config expected JSON in a resource file in JSON with the following:
 * {
 *     soups: [
 *          {
 *              soupName: xxx
 *              indexes: [
 *                  {
 *                      path: xxx
 *                      type: xxx
 *                  }
 *              ]
 *          }
 *     ]
 * }
 */

public class StoreConfig {

    private static final String TAG = "StoreConfig";

    public static final String SOUPS = "soups";
    public static final String SOUP_NAME = "soupName";
    public static final String INDEXES = "indexes";

    private JSONArray soupsConfig;

    /**
     * Constructor
     * @param ctx
     * @param resourceId
     */
    public StoreConfig(Context ctx, int resourceId) {
        try {
            String str = getRawResourceAsString(ctx, resourceId);
            JSONObject config = new JSONObject(str);
            soupsConfig = config.getJSONArray(SOUPS);
        } catch (JSONException e) {
            SmartStoreLogger.e(TAG, "Unhandled exception parsing json", e);
        }
    }

    /**
     * Register the soup from the config in the given store
     * NB: only feedback is through the logs - the config is static so getting it right is something the developer should do while writing the app
     * @param store
     */
    public void registerSoups(SmartStore store) {
        if (soupsConfig == null)
            return;

        for (int i=0; i<soupsConfig.length(); i++) {
            try {
                JSONObject soupConfig = soupsConfig.getJSONObject(i);
                String soupName = soupConfig.getString(SOUP_NAME);
                IndexSpec[] indexSpecs = IndexSpec.fromJSON(soupConfig.getJSONArray(INDEXES));
                SmartStoreLogger.d(TAG, "Registering soup:" + soupName);
                store.registerSoup(soupName, indexSpecs);
            } catch (JSONException e) {
                SmartStoreLogger.e(TAG, "Unhandled exception parsing json", e);
            }
        }
    }

    private String getRawResourceAsString(Context ctx, int resourceId) {
        InputStream resourceReader = ctx.getResources().openRawResource(resourceId);
        Writer writer = new StringWriter();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(resourceReader, "UTF-8"));
            String line = reader.readLine();
            while (line != null) {
                writer.write(line);
                line = reader.readLine();
            }
        } catch (Exception e) {
            SmartStoreLogger.e(TAG, "Unhandled exception reading resource", e);
        } finally {
            try {
                resourceReader.close();
            } catch (Exception e) {
                SmartStoreLogger.e(TAG, "Unhandled exception closing reader", e);
            }
        }

        return writer.toString();
    }

}

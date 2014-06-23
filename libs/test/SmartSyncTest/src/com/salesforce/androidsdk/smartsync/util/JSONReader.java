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
package com.salesforce.androidsdk.smartsync.util;

import java.io.IOException;
import java.io.InputStream;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

import com.salesforce.androidsdk.smartsync.util.Constants;

/**
 * A simple utility class to read data from a JSON file.
 *
 * @author bhariharan
 */
public class JSONReader {

	private static final String TAG = "SmartSyncTest: JSONReader";

	/**
	 * Returns a JSON string from the specified JSON file.
	 *
	 * @param context Context.
	 * @param filename Filename.
	 * @return JSON string.
	 */
	public static String readJSONString(Context context, String filename) {
		if (filename == null || Constants.EMPTY_STRING.equals(filename)) {
			return null;
		}
		String json = null;
        try {
        	final InputStream is = context.getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException e) {
        	Log.e(TAG, "IOException occurred while parsing JSON file", e);
        }
        return json;
	}

	/**
	 * Returns a JSON object from the specified JSON file.
	 *
	 * @param context Context.
	 * @param filename Filename.
	 * @return JSON object.
	 */
	public static JSONObject readJSONObject(Context context, String filename) {
		JSONObject json = null;
		final String jsonStr = readJSONString(context, filename);
		if (jsonStr != null) {
			try {
				json = new JSONObject(jsonStr);
			} catch (JSONException e) {
	        	Log.e(TAG, "JSONException occurred while parsing JSON file", e);
			}
		}
		return json;
	}
}

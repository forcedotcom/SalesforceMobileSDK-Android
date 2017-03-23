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
package com.salesforce.androidsdk.smartsync.util;

import android.util.Log;

import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Simple logger for sync manager
 */

public class SyncManagerLogger {

    private static final String TAG = "SyncManagerLogger";

    private int minPriority;

    public SyncManagerLogger(int minPriority) {
        setLogLevel(minPriority);
    }

    public void setLogLevel(int minPriority) {
        this.minPriority = minPriority;
    }

    public void d(Object origin, String msg, Object obj) {
        println(Log.DEBUG, origin, msg, obj);
    }

    public void i(Object origin, String msg, Object obj) {
        println(Log.INFO, origin, msg, obj);
    }

    public void w(Object origin, String msg, Object obj) {
        println(Log.WARN, origin, msg, obj);
    }

    public void e(Object origin, String msg, Object obj) {
        println(Log.ERROR, origin, msg, obj);
    }

    private void println(int priority, Object origin, String msg, Object obj) {
        if (priority >= minPriority) {
            String originStr = origin.getClass().getSimpleName();
            String objStr = toString(obj);
            Log.println(priority, TAG, originStr + ":" + msg + ":" + objStr);
        }
    }

    private String toString(Object obj) {
        if (obj == null) {
            return "null";
        }
        else if (obj instanceof Throwable) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            final Throwable err = (Throwable) obj;
            pw.print(err.getMessage());
            err.printStackTrace(pw);
            return sw.toString();
        }
        else if (obj instanceof RestResponse) {
            final RestResponse response = (RestResponse) obj;
            try {
                return toString(response.asJSONObject());
            } catch (Exception e) {
                try {
                    return toString(response.asJSONArray());
                } catch (Exception e1) {
                    try {
                        return response.asString();
                    } catch (IOException e2) {
                        return obj.toString();
                    }
                }
            }
        }
        else if (obj instanceof JSONObject) {
            try {
                return ((JSONObject) obj).toString(2);
            } catch (JSONException e) {
                return obj.toString();
            }

        }
        else if (obj instanceof JSONArray) {
            try {
                return ((JSONArray) obj).toString(2);
            } catch (JSONException e) {
                return obj.toString();
            }
        } else {
            return obj.toString();
        }
    }
}
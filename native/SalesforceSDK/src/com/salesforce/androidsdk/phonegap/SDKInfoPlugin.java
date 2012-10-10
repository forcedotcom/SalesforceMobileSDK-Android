/*
 * Copyright (c) 2012, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.XmlResourceParser;
import android.util.Log;

import com.phonegap.api.PluginResult;
import com.salesforce.androidsdk.app.ForceApp;

/**
 * PhoneGap plugin for SDK info.
 */
public class SDKInfoPlugin extends ForcePlugin {
    // Keys in sdk info map
    private static final String SDK_VERSION = "sdkVersion";
    private static final String APP_NAME = "appName";
    private static final String APP_VERSION = "appVersion";
	private static final String FORCE_PLUGINS_AVAILABLE = "forcePluginsAvailable";
    	
	// Cached 
	private static List<String> forcePlugins;
    
    /**
     * Supported plugin actions that the client can take.
     */
    enum Action {
        getInfo
    }

    @Override
    public PluginResult execute(String actionStr, JavaScriptPluginVersion jsVersion, JSONArray args, String callbackId) throws JSONException {
        // Figure out action
        Action action = null;
        try {
            action = Action.valueOf(actionStr);
            switch(action) {
                case getInfo:  return getInfo(args, callbackId);
                default: return new PluginResult(PluginResult.Status.INVALID_ACTION, actionStr); // should never happen
            }
        }
        catch (IllegalArgumentException e) {
            return new PluginResult(PluginResult.Status.INVALID_ACTION, e.getMessage());
        }
    }

    /**
     * Native implementation for "getInfo" action.
     * @param callbackId The callback ID used when calling back into Javascript.
     * @return The plugin result (ok if authenticated, error otherwise).
     * @throws JSONException
     */
    protected PluginResult getInfo(JSONArray args, final String callbackId) throws JSONException {
        Log.i("SalesforceOAuthPlugin.authenticate", "authenticate called");
        try {
            return new PluginResult(PluginResult.Status.OK, getSDKInfo((Context) ctx));
        }
        catch (NameNotFoundException e) {
            return new PluginResult(PluginResult.Status.ERROR, e.getMessage());
        }
    }

    /**************************************************************************************************
    *
    * Helper methods for building js credentials
    *
    **************************************************************************************************/

   /**
    * @return sdk info as JSONObject
    * @throws NameNotFoundException 
    * @throws JSONException 
    */
   public static JSONObject getSDKInfo(Context ctx) throws NameNotFoundException, JSONException {
       PackageInfo packageInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
       String appName = ctx.getString(packageInfo.applicationInfo.labelRes);
       String appVersion = packageInfo.versionName;
        
       JSONObject data = new JSONObject();
       data.put(SDK_VERSION, ForceApp.SDK_VERSION);
       data.put(APP_NAME, appName);
       data.put(APP_VERSION, appVersion);
       data.put(FORCE_PLUGINS_AVAILABLE, new JSONArray(getForcePlugins(ctx)));
       return data;
   }

   
	/**
	 * @param ctx
	 * @return list of force plugins (read from XML the first time, and stored in field afterwards)
	 */
	public static List<String> getForcePlugins(Context ctx) {
		if (forcePlugins == null) {
			forcePlugins = getForcePluginsFromXML(ctx);
		}
		return forcePlugins;
	}

	/**
	 * @param ctx
	 * @return list of force plugins (read from XML)
	 */
	public static List<String> getForcePluginsFromXML(Context ctx) {
		List<String> services = new ArrayList<String>();
		
		int id = ctx.getResources().getIdentifier("plugins", "xml", ctx.getPackageName());
		if (id != 0) {
			XmlResourceParser xml = ctx.getResources().getXml(id);
			int eventType = -1;
			while (eventType != XmlResourceParser.END_DOCUMENT) {
				if (eventType == XmlResourceParser.START_TAG) {
					if (xml.getName().equals("plugin")) {
						String service = xml.getAttributeValue(null, "name");
						if (service.startsWith("com.salesforce.")) {
							services.add(service);
						}
					}
				}
				try {
					eventType = xml.next();
				} catch (XmlPullParserException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return services;
	}

}

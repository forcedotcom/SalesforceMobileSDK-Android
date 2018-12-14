/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PhoneGap plugin for SDK info.
 */
public class SDKInfoPlugin extends ForcePlugin {

    // Keys in sdk info map
    private static final String SDK_VERSION = "sdkVersion";
    private static final String APP_NAME = "appName";
    private static final String APP_VERSION = "appVersion";
	private static final String FORCE_PLUGINS_AVAILABLE = "forcePluginsAvailable";
	private static final String BOOT_CONFIG = "bootConfig";
    private static final String TAG = "SDKInfoPlugin";

	// Cached 
	private static List<String> forcePlugins;
    
    /**
     * Supported plugin actions that the client can take.
     */
    enum Action {
        getInfo,
        registerAppFeature,
        unregisterAppFeature
    }

    @Override
    public boolean execute(String actionStr, JavaScriptPluginVersion jsVersion, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Action action;
        try {
            action = Action.valueOf(actionStr);
            switch(action) {
                case getInfo:  getInfo(args, callbackContext); return true;
                case registerAppFeature: registerAppFeature(args, callbackContext); return true;
                case unregisterAppFeature: unregisterAppFeature(args, callbackContext); return true;
                default: return false;
            }
        }
        catch (IllegalArgumentException e) {
        	return false;
        }
    }

    /**
     * Native implementation for "getInfo" action.
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    protected void getInfo(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        SalesforceHybridLogger.i(TAG, "getInfo called");
        try {
            callbackContext.success(getSDKInfo(cordova.getActivity()));
        } catch (NameNotFoundException e) {
            callbackContext.error(e.getMessage());
        }
    }

    /**
     * Native implementation for "registerAppFeature" action.
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    protected void registerAppFeature(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        SalesforceHybridLogger.i(TAG, "registerAppFeature called");

        // Parse args.
        JSONObject arg0 = args.getJSONObject(0);
        if (arg0 != null){
            String appFeatureCode = arg0.getString("feature");
            if (!TextUtils.isEmpty(appFeatureCode)) {
                SalesforceSDKManager.getInstance().registerUsedAppFeature(appFeatureCode);
            }
        }
        callbackContext.success();
    }

    /**
     * Native implementation for "unregisterAppFeature" action.
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    protected void unregisterAppFeature(JSONArray args, final CallbackContext callbackContext) throws JSONException {
        SalesforceHybridLogger.i(TAG, "unregisterAppFeature called");

        // Parse args.
        JSONObject arg0 = args.getJSONObject(0);
        if (arg0 != null){
            String appFeatureCode = arg0.getString("feature");
            if (!TextUtils.isEmpty(appFeatureCode)) {
                SalesforceSDKManager.getInstance().unregisterUsedAppFeature(appFeatureCode);
            }
        }
        callbackContext.success();
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
	   String appName = "";
       try {
           final PackageInfo packageInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
           appName = ctx.getString(packageInfo.applicationInfo.labelRes);
       } catch (Resources.NotFoundException nfe) {

    	   // A test harness such as Gradle does NOT have an application name.
           SalesforceHybridLogger.w(TAG, "getSDKInfo failed", nfe);
       }
       JSONObject data = new JSONObject();
       data.put(SDK_VERSION, SalesforceSDKManager.SDK_VERSION);
       data.put(APP_NAME, appName);
       data.put(APP_VERSION, SalesforceSDKManager.getInstance().getAppVersion());
       data.put(FORCE_PLUGINS_AVAILABLE, new JSONArray(getForcePlugins(ctx)));
       data.put(BOOT_CONFIG, BootConfig.getBootConfig(ctx).asJSON());
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
		List<String> services = new ArrayList<>();
        int id = ctx.getResources().getIdentifier("config", "xml", ctx.getPackageName());
        if (id == 0) {
            id = ctx.getResources().getIdentifier("plugins", "xml", ctx.getPackageName());
        }		
		if (id != 0) {
			XmlResourceParser xml = ctx.getResources().getXml(id);
			int eventType = -1;
			while (eventType != XmlResourceParser.END_DOCUMENT) {
				if (eventType == XmlResourceParser.START_TAG && xml.getName().equals("feature")) {
					String service = xml.getAttributeValue(null, "name");
					if (service.startsWith("com.salesforce.")) {
						services.add(service);
					}
				}
				try {
					eventType = xml.next();
				} catch (XmlPullParserException | IOException e) {
                    SalesforceHybridLogger.w(TAG, "getForcePluginsFromXML failed", e);
				}
			}
		}
		return services;
	}
}

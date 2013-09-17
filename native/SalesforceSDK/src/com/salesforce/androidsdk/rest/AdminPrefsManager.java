/*
 * Copyright (c) 2013, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.salesforce.androidsdk.app.SalesforceSDKManager;

/**
 * This class represents custom settings for a connected app
 * set by the org admin.
 *
 * @author meghneel.gore
 */
public class AdminPrefsManager {

	private static final String ADMIN_PREFS = "admin_prefs";
	private Map<String, String> customAttributes;

	/**
	 * Sets the admin prefs.
	 *
	 * @param attribs Admin prefs.
	 */
    @SuppressWarnings("unchecked")
	public void setPrefs(JSONObject attribs) {
		if (attribs != null) {
			if (customAttributes == null) {
				customAttributes = new HashMap<String, String>();
			}
			final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext()
	                    .getSharedPreferences(ADMIN_PREFS, Context.MODE_PRIVATE);
	        final Editor e = sp.edit();
	        final Iterator<String> keys = attribs.keys();
	        while (keys.hasNext()) {
	        	final String currentKey = keys.next();
	            final String currentValue = attribs.optString(currentKey);
	            customAttributes.put(currentKey, currentValue);
		        e.putString(currentKey, currentValue);
	        }
	        e.commit();
	    }
	}

	/**
	 * Returs the admin pref value for the specified key.
	 *
	 * @param key Key.
	 * @return Corresponding value.
	 */
    @SuppressWarnings("unchecked")
	public String getPref(String key) {
    	if (customAttributes == null || customAttributes.isEmpty()) {
    		final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext()
    				.getSharedPreferences(ADMIN_PREFS, Context.MODE_PRIVATE);
	        customAttributes = (Map<String, String>) sp.getAll();
	    }
    	if (customAttributes != null) {
    	    return customAttributes.get(key);
    	}
    	return null;
    }

    /**
     * Clears the stored admin prefs from memory and shared prefs.
     */
    public void reset() {
    	customAttributes = null;
    	final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext()
    			.getSharedPreferences(ADMIN_PREFS, Context.MODE_PRIVATE);
        final Editor editor = sp.edit();
        editor.clear();
        editor.commit();
    }
}

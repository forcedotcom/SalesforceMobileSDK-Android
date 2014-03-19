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
package com.salesforce.androidsdk.rest;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

/**
 * This class represents custom settings for a connected app
 * set by the org admin.
 *
 * @author meghneel.gore
 */
public class AdminPrefsManager {

	public static final String ADMIN_PREFS = "admin_prefs";

	/**
	 * Sets the admin prefs for the specified user account.
	 *
	 * @param attribs Admin prefs.
	 * @param account UserAccount instance.
	 */
    @SuppressWarnings("unchecked")
	public void setPrefs(JSONObject attribs, UserAccount account) {
		if (attribs != null) {
			String sharedPrefPath = ADMIN_PREFS;
			if (account != null) {
				sharedPrefPath = ADMIN_PREFS + account.getOrgLevelFilenameSuffix();
			}
			final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext()
	                    .getSharedPreferences(sharedPrefPath, Context.MODE_PRIVATE);
	        final Editor e = sp.edit();
	        final Iterator<String> keys = attribs.keys();
	        while (keys.hasNext()) {
	        	final String currentKey = keys.next();
	            final String currentValue = attribs.optString(currentKey);
		        e.putString(currentKey, currentValue);
	        }
	        e.commit();
	    }
	}

	/**
	 * Sets the admin prefs for the specified user account.
	 *
	 * @param attribs Admin prefs.
	 * @param account UserAccount instance.
	 */
	public void setPrefs(Map<String, String> attribs, UserAccount account) {
		if (attribs != null) {
			String sharedPrefPath = ADMIN_PREFS;
			if (account != null) {
				sharedPrefPath = ADMIN_PREFS + account.getOrgLevelFilenameSuffix();
			}
			final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext()
	                    .getSharedPreferences(sharedPrefPath, Context.MODE_PRIVATE);
	        final Editor e = sp.edit();
	        if (attribs != null) {
	        	final Set<String> keys = attribs.keySet();
	        	if (keys != null) {
	        		for (final String key : keys) {
	    		        e.putString(key, attribs.get(key));
	        		}
	        	}
	        }
	        e.commit();
	    }
	}

	/**
	 * Returns the admin pref value for the specified key, for a user account.
	 *
	 * @param key Key.
	 * @param account UserAccount instance.
	 * @return Corresponding value.
	 */
    @SuppressWarnings("unchecked")
	public String getPref(String key, UserAccount account) {
    	String sharedPrefPath = ADMIN_PREFS;
		if (account != null) {
			sharedPrefPath = ADMIN_PREFS + account.getOrgLevelFilenameSuffix();
		}
    	final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext()
    			.getSharedPreferences(sharedPrefPath, Context.MODE_PRIVATE);
	    final Map<String, String> customAttributes = (Map<String, String>) sp.getAll();
    	if (customAttributes != null) {
    	    return customAttributes.get(key);
    	}
    	return null;
    }

	/**
	 * Returns all the admin prefs for a user account.
	 *
	 * @param account UserAccount instance.
	 * @return Corresponding value.
	 */
    @SuppressWarnings("unchecked")
	public Map<String, String> getPrefs(UserAccount account) {
    	String sharedPrefPath = ADMIN_PREFS;
		if (account != null) {
			sharedPrefPath = ADMIN_PREFS + account.getOrgLevelFilenameSuffix();
		}
    	final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext()
    			.getSharedPreferences(sharedPrefPath, Context.MODE_PRIVATE);
	    return (Map<String, String>) sp.getAll();
    }

    /**
     * Clears the stored admin prefs for the specified user.
     *
     * @param account UserAccount instance.
     */
    public void reset(UserAccount account) {
    	String sharedPrefPath = ADMIN_PREFS;
		if (account != null) {
			sharedPrefPath = ADMIN_PREFS + account.getOrgLevelFilenameSuffix();
		}
    	final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext()
    			.getSharedPreferences(sharedPrefPath, Context.MODE_PRIVATE);
        final Editor editor = sp.edit();
        editor.clear();
        editor.commit();
    }

    /**
     * Clears the stored admin prefs for all users.
     */
    public void resetAll() {

    	// Deletes the underlying admin pref files for all orgs.
    	final String sharedPrefPath = SalesforceSDKManager.getInstance().getAppContext().getApplicationInfo().dataDir + "/shared_prefs";
    	final File dir = new File(sharedPrefPath);
    	final AdminPrefFileFilter fileFilter = new AdminPrefFileFilter();
    	for (final File file : dir.listFiles()) {
    		if (file != null && fileFilter.accept(dir, file.getName())) {
    			file.delete();
    		}
    	}
    }

    /**
     * This class acts as a filter to identify only the relevant admin pref files.
     *
     * @author bhariharan
     */
    private static class AdminPrefFileFilter implements FilenameFilter {

		@Override
		public boolean accept(File dir, String filename) {
			if (filename != null && filename.startsWith(ADMIN_PREFS)) {
				return true;
			}
			return false;
		}
    }
}

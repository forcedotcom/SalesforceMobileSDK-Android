/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.config;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.app.Features;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Classes responsible for reading runtime configurations (from MDM provider).
 * For an example, see the ConfiguratorApp and ConfiguredApp sample applications.
 */
public class RuntimeConfig {

	private static final String TAG = "RuntimeConfig";

	public enum ConfigKey {

        // The keys here should match the key entries in 'app_restrictions.xml'.
		AppServiceHosts,
		AppServiceHostLabels,
		ManagedAppOAuthID,
		ManagedAppCallbackURL,
		RequireCertAuth,
		ManagedAppCertAlias,
		OnlyShowAuthorizedHosts,
		IDPAppURLScheme
	}

    private boolean isManaged;
	private Bundle configurations;

	private static RuntimeConfig INSTANCE = null;

	private RuntimeConfig(Context ctx) {
		configurations = getRestrictions(ctx);
		isManaged = hasRestrictionsProvider(ctx);

		// Register MDM App Feature for user agent reporting.
		if (isManaged && configurations != null && !configurations.isEmpty()) {
			SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_MDM);
			if (getBoolean(RuntimeConfig.ConfigKey.RequireCertAuth)) {
				SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_CERT_AUTH);
			}
		}

		// Logs analytics event for MDM.
        final ExecutorService threadPool = Executors.newFixedThreadPool(1);
		threadPool.execute(new Runnable() {

			@Override
			public void run() {
				final JSONObject attributes = new JSONObject();
				try {
					attributes.put("mdmIsActive", isManaged);
					if (configurations != null) {
						final JSONObject mdmValues = new JSONObject();
						final Set<String> keys = configurations.keySet();
						for (final String key : keys) {
							mdmValues.put(key, JSONObject.wrap(configurations.get(key)));
						}
						attributes.put("mdmConfigs", mdmValues);
					}
				} catch (JSONException e) {
					SalesforceSDKLogger.e(TAG, "Exception thrown while creating JSON", e);
				}
				EventBuilderHelper.createAndStoreEventSync("mdmConfiguration",
                        null, TAG, attributes);
			}
		});
	}

	/**
     * Method to (build and) get the singleton instance.
     *
	 * @param ctx Context.
	 * @return RuntimeConfig instance.
	 */
	public static RuntimeConfig getRuntimeConfig(Context ctx) {
		if (INSTANCE == null) {
			INSTANCE = new RuntimeConfig(ctx);
		}
		return INSTANCE;
	}

    /**
     * Returns true if application is managed
     * @return boolean
     */
    public boolean isManagedApp() {
        return isManaged;
    }

    /**
     * Get string run time configuration
     * @param configKey key
     * @return string value
     */
	public String getString(ConfigKey configKey) {
		return (configurations == null ? null : configurations.getString(configKey.name()));
	}

    /**
     * Get string array run time configuration
     * @param configKey key
     * @return string array value
     */
	public String[] getStringArray(ConfigKey configKey) {
		return (configurations == null ? null : configurations.getStringArray(configKey.name()));
	}

    /**
     * Get boolean run time configuration
     * @param configKey key
     * @return boolean value
     */
	public Boolean getBoolean(ConfigKey configKey) {
        return (configurations != null && configurations.getBoolean(configKey.name()));
	}

	private JSONArray getJSONArray(ConfigKey configKey) throws JSONException {
		String[] array = getStringArray(configKey);
		return array == null ? null : new JSONArray(array);
	}

	/**
	 * Get run time config as a JSONObject
	 * @return JSONObject for run time config.
	 */
	public JSONObject asJSON() {
		try {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(ConfigKey.AppServiceHosts.name(), getJSONArray(ConfigKey.AppServiceHosts));
			jsonObject.put(ConfigKey.AppServiceHostLabels.name(), getJSONArray(ConfigKey.AppServiceHostLabels));
			jsonObject.put(ConfigKey.ManagedAppOAuthID.name(), getString(ConfigKey.ManagedAppOAuthID));
			jsonObject.put(ConfigKey.ManagedAppCallbackURL.name(), getJSONArray(ConfigKey.ManagedAppCallbackURL));
			jsonObject.put(ConfigKey.RequireCertAuth.name(), getBoolean(ConfigKey.RequireCertAuth));
			jsonObject.put(ConfigKey.ManagedAppCertAlias.name(), getString(ConfigKey.ManagedAppCertAlias));
			jsonObject.put(ConfigKey.OnlyShowAuthorizedHosts.name(), getJSONArray(ConfigKey.OnlyShowAuthorizedHosts));
			jsonObject.put(ConfigKey.IDPAppURLScheme.name(), getString(ConfigKey.IDPAppURLScheme));
			return jsonObject;
		}
		catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	Bundle getRestrictions(Context ctx) {
		RestrictionsManager restrictionsManager = (RestrictionsManager) ctx.getSystemService(Context.RESTRICTIONS_SERVICE);
		return restrictionsManager.getApplicationRestrictions();
	}

    private boolean hasRestrictionsProvider(Context ctx) {
        RestrictionsManager restrictionsManager = (RestrictionsManager) ctx.getSystemService(Context.RESTRICTIONS_SERVICE);
        return restrictionsManager.hasRestrictionsProvider();
    }
}

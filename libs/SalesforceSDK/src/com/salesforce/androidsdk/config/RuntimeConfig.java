/*
 * Copyright (c) 2014-2015, salesforce.com, inc.
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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Build;
import android.os.Bundle;

/**
 * Classes responsible for reading runtime configurations (from MDM provider).
 * For an example, see the ConfiguratorApp and ConfiguredApp sample applications.
 */
public class RuntimeConfig {
	
	public enum ConfigKey {
		AppServiceHosts,
		AppServiceHostLabels,
		ManagedAppOAuthID,
		ManagedAppCallbackURL,
		RequireCertAuth,
		ManagedAppCertAlias;
	}

    private boolean isManaged = false;
	private Bundle configurations = null;
	
	private static RuntimeConfig INSTANCE = null;

	private RuntimeConfig(Context ctx) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			configurations = getRestrictions(ctx);
            isManaged = hasRestrictionsProvider(ctx);
        }
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
		return (configurations == null ? false : configurations.getBoolean(configKey.name()));
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP) 
	private Bundle getRestrictions(Context ctx) {
		RestrictionsManager restrictionsManager = (RestrictionsManager) ctx.getSystemService(Context.RESTRICTIONS_SERVICE);
		return restrictionsManager.getApplicationRestrictions();
	}

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean hasRestrictionsProvider(Context ctx) {
        RestrictionsManager restrictionsManager = (RestrictionsManager) ctx.getSystemService(Context.RESTRICTIONS_SERVICE);
        return restrictionsManager.hasRestrictionsProvider();
    }
}
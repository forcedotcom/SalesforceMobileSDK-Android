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

import org.json.JSONArray;

import android.util.Log;

import com.phonegap.api.Plugin;
import com.phonegap.api.PluginResult;

/**
 * Abstract super class for all Salesforce plugins
 */
public abstract class ForcePlugin extends Plugin {
	
	/**
	 * Enum to represent SDK version that the javascript code tries to invoke
	 *
	 */
	public enum JavaScriptPluginVersion {
		UNKNOWN(""),
		V_2_0("v2.0");
		
		private String version;
		
		private JavaScriptPluginVersion(String version) {
			this.version = version;
		}
		
		public static JavaScriptPluginVersion fromString(String version) {
			for (JavaScriptPluginVersion jsVersion : values()) {
				if (jsVersion.version.equals(version)) {
					return jsVersion;
				}
			}
			return UNKNOWN;
		}
	}
	
	
    /**
     * Executes the plugin request and returns PluginResult.
     *
     * @param actionVersionStr     The action with version to exectute
     * @param args          JSONArray of arguments for the plugin.
     * @param callbackId    The callback ID used when calling back into JavaScript.
     * @return              A PluginResult object with a status and message.
     */
    public PluginResult execute(String actionVersionStr, JSONArray args, String callbackId) {
        Log.i(getClass().getSimpleName() + ".execute", "actionVersionStr: " + actionVersionStr);
        
	    String[] actionVersion = actionVersionStr.split("/");
	    String actionStr = actionVersion[0];
	    String versionStr = actionVersion.length > 0 ? actionVersion[1] : "";
	    JavaScriptPluginVersion jsVersion = JavaScriptPluginVersion.fromString(versionStr);
	    Log.i(getClass().getSimpleName() + ".execute", "action: " + actionStr + ",version:" + jsVersion.name());
	        
        return execute(actionStr, jsVersion, args, callbackId);
    }


	/**
	 * Abstract method to concrete subclass need to implement
     * @param actionStr     The action to execute
     @ @param jsVersion     The version targeted
     * @param args          JSONArray of arguments for the plugin.
     * @param callbackId    The callback ID used when calling back into JavaScript.
     * @return              A PluginResult object with a status and message.
	 * @return
	 */
	abstract protected PluginResult execute(String actionStr, JavaScriptPluginVersion jsVersion, JSONArray args, String callbackId);
}
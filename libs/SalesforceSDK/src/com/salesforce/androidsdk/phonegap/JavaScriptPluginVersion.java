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

import com.salesforce.androidsdk.app.SalesforceSDKManager;

/**
 * Object that encapsulate the version reported by the javascript side
 */
public class JavaScriptPluginVersion {
	private String version;
	private int comparedToNative = -1; // -1 older, 0 same, 1 newer

	/**
	 * @param version
	 */
	public JavaScriptPluginVersion(String version) {
		this.version = version;
		this.comparedToNative = compareVersions(version, SalesforceSDKManager.SDK_VERSION);
	}
	
	/**
	 * @param version1
	 * @param version2
	 * @return -1/0/1 if version1 is older/sane/newer than version2
	 * unstable version is assumed to precede the corresponding version 2.0.unstable is older than 2.0
	 */
	public static int compareVersions(String version1, String version2) {
		// If same strings, we are done
		if (version1.equals(version2)) return 0;

		// Split
		String[] version1parts = version1.split("\\.");
		String[] version2parts = version2.split("\\.");
		int minLength = Math.min(version1parts.length, version2parts.length);

		// Compare each part
		for (int i=0; i<minLength; i++) {
			int version1part = safeParseInt(version1parts[i], -1);
			int version2part = safeParseInt(version2parts[i], -1);
			if (version1part != version2part) {
				return (version1part < version2part ? -1 : 1);
			}
		}

		// If one version is simply the unstable form of the other, it's the older one
		if (version1parts.length == minLength + 1 && version1parts[minLength].equals("unstable")) {
			return -1;
		}
		if (version2parts.length == minLength + 1 && version2parts[minLength].equals("unstable")) {
			return 1;
		}
		
		
		// Same up to here, but one is longer, the longer one is a patch on the other one
		return (version1parts.length > version2parts.length ? 1 : -1);
	}
	
	/**
	 * @poram fallback
	 * @return integer contained in string or fallback if not a number
	 */
	public static int safeParseInt(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		}
		catch (NumberFormatException e) {
			return fallback;
		}
	}

	/**
	 * @return true if the javascript side of the plugin is from the same SDK version than the native side
	 */
	public boolean isCurrent() {
		return comparedToNative == 0;
	}
	
	/**
	 * @return true if the javascript side of the plugin is from an older version of the SDK than the native side
	 */
	public boolean isOlder() {
		return comparedToNative < 0;
	}

	/**
	 * @return true if the javascript side of the plugin is from an newer version of the SDK than the native side
	 */
	public boolean isNewer() {
		return comparedToNative > 0;						
	}		

	@Override
	public String toString() {
		return version;
	}
	
}
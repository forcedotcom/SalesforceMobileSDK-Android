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

/**
 * Class encapsulating the application configuration (consumer key, oauth scopes, refresh behavior)
 * 
 */
public class BootConfig {

	private String remoteAccessConsumerKey;
	private String oauthRedirectURI;
	private String[] oauthScopes;
	private boolean isLocal;
	private String startPage;
	private boolean autoRefreshOnForeground;
	private boolean autoRefreshPeriodically;
	private boolean attemptOfflineLoad;
	
	/**
	 * Read boot configuration from xml
	 */
	private void readFromXML() 
	{
		// TODO
	}
	
	/**
	 * @return consumer key value specified for your remote access object or connected app
	 */
	public String getRemoteAccessConsumerKey() {
		return remoteAccessConsumerKey;
	}
	
	/**
	 * @return redirect URI value specified for your remote access object or connected app
	 */
	public String getOauthRedirectURI() {
		return oauthRedirectURI;
	}
	
	/**
	 * @return  authorization/access scope(s) that the application needs to ask for at login
	 */
	public String[] getOauthScopes() {
		return oauthScopes;
	}

	/**
	 * @return true if start page is www/assets and false if it's a VF page
	 */
	public boolean isLocal() {
		return isLocal;
	}
	
	/**
	 * @return path to start page (e.g. index.html or /apex/basicpage)
	 */
	public String getStartPage() {
		return startPage;
	}

	/**
	 * @return true if app should refresh oauth session when foregrounded
	 */
	public boolean autoRefreshOnForeground() {
		return autoRefreshOnForeground;
	}

	/**
	 * @return true if app should refresh oauth session periodically
	 */
	public boolean autoRefreshPeriodically() {
		return autoRefreshPeriodically;
	}

	/**
	 * @return true, if app should attempt to load previously cached content when offline
	 */
	public boolean attemptOfflineLoad() {
		return attemptOfflineLoad;
	}
}

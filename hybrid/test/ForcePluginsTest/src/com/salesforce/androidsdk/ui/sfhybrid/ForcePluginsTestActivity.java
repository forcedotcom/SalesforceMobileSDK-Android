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

package com.salesforce.androidsdk.ui.sfhybrid;

import java.net.URI;
import java.net.URISyntaxException;

import android.content.Context;
import android.util.Log;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.HttpAccess;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.ClientInfo;
import com.salesforce.androidsdk.ui.sfhybrid.SalesforceDroidGapActivity;

/**
 * Sub-class of SalesforceDroidGapActivity that authenticates using hard-coded credentials
 *
 */
public class ForcePluginsTestActivity extends SalesforceDroidGapActivity {

	@Override
	protected ClientManager buildClientManager() {
		return new MockClientManager(this, SalesforceSDKManager.getInstance()
				.getAccountType(), SalesforceSDKManager.getInstance()
				.getLoginOptions(), SalesforceSDKManager.getInstance()
				.shouldLogoutWhenTokenRevoked());
	}

	private static class MockClientManager extends ClientManager {

		public MockClientManager(Context ctx, String accountType,
				LoginOptions loginOptions, boolean revokedTokenShouldLogout) {
			super(ctx, accountType, loginOptions, revokedTokenShouldLogout);
		}

		@Override
		public RestClient peekRestClient() {
			String username = "sdktest@cs1.com";
			String accountName = username + " (ForcePluginsTest)";
			String refreshToken = "5Aep861KIwKdekr90KlxVVUI47zdR6dX_VeBWZBS.SiQYYAy5LuEc0OGFQRIHGNkCvWU1XiK0TI7w==";
			String authToken = "";
			String identityUrl = "https://test.salesforce.com";
			String instanceUrl = "https://cs1.salesforce.com";
			String loginUrl = "https://test.salesforce.com";
			String orgId = "00DS0000000HDptMAG";
			String userId = "005S0000003yO7jIAE";
			LoginOptions loginOptions = SalesforceSDKManager.getInstance().getLoginOptions();
			
			try {
				AccMgrAuthTokenProvider authTokenProvider = new AccMgrAuthTokenProvider(
						this, authToken, refreshToken);
				ClientInfo clientInfo = new ClientInfo(
						loginOptions.oauthClientId, new URI(instanceUrl),
						new URI(loginUrl), new URI(identityUrl),
						accountName, username, userId, orgId);
				return new RestClient(clientInfo, authToken,
						HttpAccess.DEFAULT, authTokenProvider);
			} catch (URISyntaxException e) {
				Log.w("ClientManager:peekRestClient", "Invalid server URL", e);
				throw new AccountInfoNotFoundException("invalid server url", e);
			}
		}
	}
	
}

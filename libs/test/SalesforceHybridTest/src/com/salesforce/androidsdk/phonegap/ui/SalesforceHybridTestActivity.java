/*
 * Copyright (c) 2013-present, salesforce.com, inc.
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

package com.salesforce.androidsdk.phonegap.ui;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;

/**
 * Sub-class of SalesforceDroidGapActivity that authenticates using hard-coded credentials
 *
 */
public class SalesforceHybridTestActivity extends SalesforceDroidGapActivity {

	static String username = "ut2@cs1.mobilesdk.org";
	static String accountName = username + " (SalesforceHybridTest)";
	static String refreshToken = "5Aep861KIwKdekr90IDidO4EhfJiYo3fzEvTvsEgM9sfDpGX0qFFeQzHG2mZeUH_.XNSBE0Iz38fnWsyYYkUgTz";
	static String authToken = "--will-be-set-through-refresh--";
	static String identityUrl = "https://test.salesforce.com";
	static String instanceUrl = "https://cs1.salesforce.com";
	static String loginUrl = "https://test.salesforce.com";
	static String orgId = "00DS0000003E98jMAC";
	static String userId = "005S0000004s2iyIAA";
	
	@Override
	protected ClientManager buildClientManager() {
		final ClientManager clientManager = super.buildClientManager();
		final LoginOptions loginOptions = SalesforceSDKManager.getInstance().getLoginOptions();

		clientManager.createNewAccount(accountName,
        		username, refreshToken,
        		authToken, instanceUrl,
        		loginUrl, identityUrl,
        		loginOptions.oauthClientId, orgId,
        		userId, null);
	
		return clientManager;
	}
}

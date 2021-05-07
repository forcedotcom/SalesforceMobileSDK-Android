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

import com.salesforce.androidsdk.rest.ClientManager;
import com.salesforce.androidsdk.util.test.TestCredentials;

/**
 * Sub-class of SalesforceDroidGapActivity that authenticates using hard-coded credentials.
 */
public class SalesforceHybridTestActivity extends SalesforceDroidGapActivity {

	static String username = TestCredentials.USERNAME;
	static String accountName = TestCredentials.ACCOUNT_NAME;
	static String refreshToken = TestCredentials.REFRESH_TOKEN;
	static String authToken = "--will-be-set-through-refresh--";
	static String identityUrl = TestCredentials.IDENTITY_URL;
	static String instanceUrl = TestCredentials.INSTANCE_URL;
	static String loginUrl = TestCredentials.LOGIN_URL;
	static String orgId = TestCredentials.ORG_ID;
	static String userId = TestCredentials.USER_ID;
    static String photoUrl = TestCredentials.PHOTO_URL;
	static String clientId = TestCredentials.CLIENT_ID;

	@Override
	public ClientManager buildClientManager() {
		final ClientManager clientManager = super.buildClientManager();
		clientManager.createNewAccount(accountName, username, refreshToken, authToken, instanceUrl,
        		loginUrl, identityUrl, clientId, orgId, userId,
				null, null, null, null, null,
                null, photoUrl, null, null, null,
				null, null, null, null, null, null);
		return clientManager;
	}
}

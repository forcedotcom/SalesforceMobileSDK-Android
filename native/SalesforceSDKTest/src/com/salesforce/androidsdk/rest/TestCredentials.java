/*
 * Copyright (c) 2011, salesforce.com, inc.
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


/**
 * Authentication credentials used to make live server calls in tests
 * 
 * Use web app to figure out login/instance urls, orgId, userId, username and clientId
 * 
 * For refresh token:
 * - run RestExplorer in emulator, login to a test org
 * - in DDMS, pull /data/system/accounts.db out of emulator
 * - from a shell, do sqlite3 accounts.db 
 * - then do: select * from accounts; 
 * 
 *  TODO don't checkin actual values
 */
public class TestCredentials {

	public static final String API_VERSION = "v23.0";
	public static final String ACCOUNT_TYPE = "com.salesforce.androidsdk.test"; // must match authenticator.xml
	
	public static final String ORG_ID = "00DT0000000FPl2MAG";
	public static final String USERNAME = "w@cs0.com";
	public static final String USER_ID = "005T0000000rr9rIAA";
	public static final String LOGIN_URL = "https://test.salesforce.com";
	public static final String INSTANCE_URL = "https://tapp0.salesforce.com";

	public static final String CLIENT_ID = "3MVG92.uWdyphVj4bnolD7yuIpCQsNgddWtqRND3faxrv9uKnbj47H4RkwheHA2lKY4cBusvDVp0M6gdGE8hp";
	public static final String REFRESH_TOKEN = "5Aep861_OKMvio5gy9sGt9Z9mdt62xXK.9ugif6j6O_rF1QcY8WR6lnHr3G5o0cBdXLPYAtELonLQ==";
	
}
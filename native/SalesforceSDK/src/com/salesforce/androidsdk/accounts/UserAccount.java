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
package com.salesforce.androidsdk.accounts;

import com.salesforce.androidsdk.security.PasscodeManager;

/**
 * This class represents a single user account that is currently
 * logged in against a Salesforce endpoint. It encapsulates data
 * that is used to uniquely identify a single user account.
 *
 * @author bhariharan
 */
public class UserAccount {

	private SFDCUser sfdcUser;
	private SFDCOrg sfdcOrg;
	private SFDCCommunity sfdcCommunity;
	private PasscodeManager passcodeManager;

	/**
	 * Returns the file storage path for this user account.
	 *
	 * @return File storage path.
	 */
	public String getFileStoragePath() {
		/*
		 * TODO:
		 */
		return null;
	}

	/**
	 * Returns the database storage path for this user account.
	 *
	 * @return Database storage path.
	 */
	public String getDatabaseStoragePath() {
		/*
		 * TODO:
		 */
		return null;
	}

	/**
	 * Returns the shared pref storage path for this user account.
	 *
	 * @return Shared pref storage path.
	 */
	public String getSharedPrefStoragePath() {
		/*
		 * TODO:
		 */
		return null;
	}
}

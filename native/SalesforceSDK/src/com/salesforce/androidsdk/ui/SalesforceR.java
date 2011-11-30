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
package com.salesforce.androidsdk.ui;




/**
 * Since the SalesforceSDK is packaged as a jar, it can't have resources.
 * Class that allows references to resources defined outside the SDK.
 * Projects making use of the SDK need to provide an implementation. 
 */

public interface SalesforceR {
	/* Login */
	int stringAccountType();
	int layoutLogin();
	int idLoginWebView();
	int stringGenericError();
	int stringGenericAuthenticationErrorTitle();
	int stringGenericAuthenticationErrorBody();
	/* Passcode */
	int layoutPasscode();
	int idPasscodeTitle();
	int idPasscodeError();
	int idPasscodeInstructions();
	int idPasscodeText();
	int stringPasscodeCreateTitle();
	int stringPasscodeEnterTitle();
	int stringPasscodeConfirmTitle();
	int stringPasscodeEnterInstructions();
	int stringPasscodeCreateInstructions();
	int stringPasscodeConfirmInstructions();
	int stringPasscodeMinLength();
	int stringPasscodeTryAgain();
	int stringPasscodeFinal();
	int stringPasscodesDontMatch();
	/* Server picker */
	int idPickerCustomLabel();
	int idPickerCustomUrl();
	int stringServerUrlDefaultCustomLabel();
	int stringServerUrlDefaultCustomUrl();
	int stringServerUrlAddTitle();
	int stringServerUrlEditTitle();
	int layoutCustomServerUrl();
	int idApplyButton();
	int idCancelButton();
	int stringInvalidServerUrl();
	int idServerListGroup();
	int layoutServerPicker();
	int stringAuthLoginProduction();
	int stringAuthLoginSandbox();
	int menuClearCustomUrl();
	int idMenuClearCustomUrl();
	int drawableEditIcon();
	int idShowCustomUrlEdit();
}

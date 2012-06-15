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
 * Projects making use of the SDK need to provide a subclass that returns the actual resource id. 
 */

public class SalesforceR {
	/* Login */
	public int stringAccountType() { return -1; }
	public int layoutLogin() { return -1; }
	public int idLoginWebView() { return -1; }
	public int stringGenericError() { return -1; }
	public int stringGenericAuthenticationErrorTitle() { return -1; }
	public int stringGenericAuthenticationErrorBody() { return -1; }
	public int menuLogin() { return -1; }
	public int idAuthContainer() { return -1; }
	public int idItemClearCookies() { return -1; }
	public int idItemPickServer() { return -1; }
	public int idItemReload() { return -1; }
	public int idLoadSpinner() { return -1; }
	public int idLoadSeparator() { return -1; }
	public int idServerName() { return -1; }
	public int styleTextHostName() { return -1; }
	public int styleTextHostUrl() { return -1; }
	/* Passcode */
	public int layoutPasscode() { return -1; }
	public int idPasscodeTitle() { return -1; }
	public int idPasscodeError() { return -1; }
	public int idPasscodeInstructions() { return -1; }
	public int idPasscodeText() { return -1; }
	public int stringPasscodeCreateTitle() { return -1; }
	public int stringPasscodeEnterTitle() { return -1; }
	public int stringPasscodeConfirmTitle() { return -1; }
	public int stringPasscodeEnterInstructions() { return -1; }
	public int stringPasscodeCreateInstructions() { return -1; }
	public int stringPasscodeConfirmInstructions() { return -1; }
	public int stringPasscodeMinLength() { return -1; }
	public int stringPasscodeTryAgain() { return -1; }
	public int stringPasscodeFinal() { return -1; }
	public int stringPasscodesDontMatch() { return -1; }
	/* Server picker */
	public int idPickerCustomLabel() { return -1; }
	public int idPickerCustomUrl() { return -1; }
	public int stringServerUrlDefaultCustomLabel() { return -1; }
	public int stringServerUrlDefaultCustomUrl() { return -1; }
	public int stringServerUrlAddTitle() { return -1; }
	public int stringServerUrlEditTitle() { return -1; }
	public int layoutCustomServerUrl() { return -1; }
	public int idApplyButton() { return -1; }
	public int idCancelButton() { return -1; }
	public int stringInvalidServerUrl() { return -1; }
	public int idServerListGroup() { return -1; }
	public int layoutServerPicker() { return -1; }
	public int stringAuthLoginProduction() { return -1; }
	public int stringAuthLoginSandbox() { return -1; }
	public int menuClearCustomUrl() { return -1; }
	public int idMenuClearCustomUrl() { return -1; }
	public int drawableEditIcon() { return -1; }
	public int idShowCustomUrlEdit() { return -1; }
}

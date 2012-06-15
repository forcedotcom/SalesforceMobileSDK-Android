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
package com.salesforce.samples.contactexplorer;

import com.salesforce.androidsdk.ui.SalesforceR;


/**
 * Since the SalesforceSDK is packaged as a jar, it can't have resources.
 * Class that allows references to resources defined outside the SDK.
 */
public class SalesforceRImpl extends SalesforceR {
	/* Login */
	public int stringAccountType() { return R.string.account_type; }
	public int layoutLogin() {return R.layout.sf__login; }
	public int idLoginWebView() {return R.id.sf__oauth_webview; }
	public int stringGenericError() {return R.string.sf__generic_error; }
	public int stringGenericAuthenticationErrorTitle() {return R.string.sf__generic_authentication_error_title; } 
	public int stringGenericAuthenticationErrorBody() {return R.string.sf__generic_authentication_error; }
	public int menuLogin() { return R.menu.sf__login; }
	public int idAuthContainer() { return R.id.sf__auth_container; }
	public int idItemClearCookies() { return R.id.sf__menu_clear_cookies; }
	public int idItemPickServer() { return R.id.sf__menu_pick_server; }
	public int idItemReload() { return R.id.sf__menu_reload; }
	public int idLoadSeparator() { return R.id.sf__load_separator; }
	public int idLoadSpinner() { return R.id.sf__load_spinner; }
	public int idServerName() { return R.id.sf__server_name; }
	public int styleTextHostName() { return R.style.Text_HostName; }
	public int styleTextHostUrl() { return R.style.Text_HostUrl; }
	/* Passcode */
	public int layoutPasscode() {return R.layout.sf__passcode; }
	public int idPasscodeTitle() {return R.id.sf__passcode_title; }
	public int idPasscodeError() {return R.id.sf__passcode_error; }
	public int idPasscodeInstructions() {return R.id.sf__passcode_instructions; }
	public int idPasscodeText() {return R.id.sf__passcode_text; }
	public int stringPasscodeCreateTitle() {return R.string.sf__passcode_create_title; }
	public int stringPasscodeEnterTitle() {return R.string.sf__passcode_enter_title; }
	public int stringPasscodeConfirmTitle() {return R.string.sf__passcode_confirm_title; }
	public int stringPasscodeEnterInstructions() {return R.string.sf__passcode_enter_instructions; }
	public int stringPasscodeCreateInstructions() {return R.string.sf__passcode_create_instructions; }
	public int stringPasscodeConfirmInstructions() {return R.string.sf__passcode_confirm_instructions; }
	public int stringPasscodeMinLength() {return R.string.sf__passcode_min_length; }
	public int stringPasscodeTryAgain() {return R.string.sf__passcode_try_again; }
	public int stringPasscodeFinal() {return R.string.sf__passcode_final; }
	public int stringPasscodesDontMatch() {return R.string.sf__passcodes_dont_match; }
	/* Server picker */
	public int idPickerCustomLabel() {return R.id.sf__picker_custom_label; }
	public int idPickerCustomUrl() {return R.id.sf__picker_custom_url; }
	public int stringServerUrlDefaultCustomLabel() {return R.string.sf__server_url_default_custom_label;}
	public int stringServerUrlDefaultCustomUrl() {return R.string.sf__server_url_default_custom_url;}
	public int stringServerUrlAddTitle() {return R.string.sf__server_url_add_title;}
	public int stringServerUrlEditTitle() {return R.string.sf__server_url_edit_title;}
	public int layoutCustomServerUrl() {return R.layout.sf__custom_server_url; }
	public int idApplyButton() {return R.id.sf__apply_button;}
	public int idCancelButton() {return R.id.sf__cancel_button;}
	public int stringInvalidServerUrl() {return R.string.sf__invalid_server_url;}
	public int idServerListGroup() {return R.id.sf__server_list_group; }
	public int layoutServerPicker() {return R.layout.sf__server_picker; }
	public int stringAuthLoginProduction() {return R.string.sf__auth_login_production;}
	public int stringAuthLoginSandbox() {return R.string.sf__auth_login_sandbox;}
	public int menuClearCustomUrl() {return R.menu.sf__clear_custom_url; }
	public int idMenuClearCustomUrl() {return R.id.sf__menu_clear_custom_url;}
	public int drawableEditIcon() {return R.drawable.sf__edit_icon; }
	public int idShowCustomUrlEdit() {return R.id.sf__show_custom_url_edit;}
}

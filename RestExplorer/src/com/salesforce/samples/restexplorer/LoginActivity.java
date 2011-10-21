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
package com.salesforce.samples.restexplorer;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Toast;

import com.salesforce.androidsdk.auth.AbstractLoginActivity;
import com.salesforce.androidsdk.auth.OAuth2;

/**
 * Activity responsible for login
 *
 */
public class LoginActivity extends AbstractLoginActivity {
	
	// Key for login servers properties stored in preferences
	public static final String SERVER_URL_PREFS_SETTINGS = "server_url_prefs";
	public static final String SERVER_URL_PREFS_CUSTOM_LABEL = "server_url_custom_label";
	public static final String SERVER_URL_PREFS_CUSTOM_URL = "server_url_custom_url";
	public static final String SERVER_URL_PREFS_WHICH_SERVER = "which_server_index";
	public static final String SERVER_URL_CURRENT_SELECTION = "server_url_current_string";
	
	// Request code when calling PickServerActivity
    public static final int PICK_SERVER_CODE = 10;
	
    /**************************************************************************************************
     *
     * Implementations for abstract methods of AbstractLoginActivity
     * 
     **************************************************************************************************/
    
	@Override
	protected int getLayoutId() {
		return R.layout.login;
	}

	@Override
	protected int getWebViewId() {
		return R.id.oauth_webview;
	}
	
	@Override
	protected void showError(Exception exception) {
		Toast.makeText(this,
				getString(R.string.generic_error, exception.toString()),
				Toast.LENGTH_LONG).show();
	}


	@Override
	protected String getGenericAuthErrorTitle() {
		return getString(R.string.generic_authentication_error_title);
	}
	
	@Override
	protected String getGenericAuthErrorBody() {
		return getString(R.string.generic_authentication_error);
	}
	
	@Override
	protected String getOAuthClientId() {
		return getString(R.string.oauth_client_id);
	}

	@Override
	protected String getOAuthCallbackUrl() {
		return getString(R.string.oauth_callback_url);
	}
	
	@Override
	protected String getApiVersion() {
		return getString(R.string.api_version_id);
	}
	
	@Override
	protected String getLoginServerUrl() {
		SharedPreferences settings = getSharedPreferences(
				SERVER_URL_PREFS_SETTINGS, Context.MODE_PRIVATE);

		return settings.getString(LoginActivity.SERVER_URL_CURRENT_SELECTION, OAuth2.DEFAULT_LOGIN_URL);
	}

	@Override
	protected String getAccountType() {
		return getString(R.string.account_type);
	}
	
	
    /**************************************************************************************************
     *
     * Buttons click handlers
     * 
     **************************************************************************************************/

	/**
	 * Called when "Clear cookies" button is clicked.
	 * Clear cookies and reload login page.
	 * @param v
	 */
	public void onClearCookiesClick(View v) {
		clearCookies();
	}
	
	/**
	 * Called when "Pick server" button is clicked.
	 * Start ServerPickerActivity
	 * @param v
	 */
	public void onPickServerClick(View v) {
	    Intent i = new Intent(this, ServerPickerActivity.class);
	    startActivityForResult(i, PICK_SERVER_CODE);
	}
	
	/*
	 * Called when ServerPickerActivity completes.
	 * Reload login page.
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PICK_SERVER_CODE && resultCode == Activity.RESULT_OK) {
            loadLoginPage();
		}
		else {
	        super.onActivityResult(requestCode, resultCode, data);
	    }
	}
}

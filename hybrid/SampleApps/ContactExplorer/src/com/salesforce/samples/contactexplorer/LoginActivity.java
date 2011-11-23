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


import android.view.View;
import android.widget.Toast;

import com.salesforce.androidsdk.auth.AbstractLoginActivity;

/**
 * Activity responsible for login
 *
 */
public class LoginActivity extends AbstractLoginActivity {
	
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
}

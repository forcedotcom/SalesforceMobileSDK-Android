/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Abstract base class for all Salesforce activities.
 */
public abstract class SalesforceActivity extends AppCompatActivity implements SalesforceActivityInterface {

	private final SalesforceActivityDelegate delegate;

    /**
     * Determines if the Salesforce Mobile SDK (MSDK) REST Client is built when this activity
     * resumes. This defaults to true.
     * <p>
     * The default is beneficial so apps using this activity as their main activity will create the
     * MSDK networking client and start MSDK's default LoginActivity when no user is authenticated.
     * This provides a lower-code path to implementing an app which automatically authenticates a
     * user with Salesforce web login.
     * <p>
     * Whenever needed, an app may disable this feature.  Disabling this feature gives the app
     * more control over when and which activities are presented to the user when this activity is
	 * resumed.  However, that does require the app start MSDK's LoginActivity when required and
     * with valid parameters for the app's authentication needs.
     */
    protected boolean isBuildRestClientOnResumeEnabled = true;

	public SalesforceActivity() {
		super();
		this.delegate = new SalesforceActivityDelegate(this);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		delegate.onCreate();
	}

	@Override
	public void onResume() {
		super.onResume();
		delegate.onResume(isBuildRestClientOnResumeEnabled);
	}

    @Override
    public void onPause() {
        super.onPause();
        delegate.onPause();
    }

    @Override
    public void onDestroy() {
		delegate.onDestroy();
    	super.onDestroy();
    }

    @Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return delegate.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event);
	}

    @Override
    public void onLogoutComplete() {
    }

	@Override
	public void onUserSwitched() {
		delegate.onResume(true);
	}
}

/*
 * Copyright (c) 2011-2013, salesforce.com, inc.
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.salesforce.androidsdk.app.SalesforceSDKManager;

/**
 * Displays an activity that gives the user the option to clear app data
 * and log out, or cancel the clear user data option.
 *
 * @author bhariharan
 */
public class ManageSpaceActivity extends Activity {

	private SalesforceR salesforceR;
	private AlertDialog manageSpaceDialog;

	@Override
	public void onCreate(Bundle savedState) {
		super.onCreate(savedState);
		salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();
		setContentView(salesforceR.layoutManageSpace());
		manageSpaceDialog = buildManageSpaceDialog();
		manageSpaceDialog.show();
	}

	@Override
	public void onDestroy() {
		manageSpaceDialog.dismiss();
		super.onDestroy();
	}

	/**
	 * Builds the manage space alert dialog. Subclasses can
	 * override this method to provide their own implementation
	 * or a custom dialog.
	 *
	 * @return Manage space alert dialog.
	 */
    protected AlertDialog buildManageSpaceDialog() {
        return new AlertDialog.Builder(this)
        .setMessage(salesforceR.stringManageSpaceConfirmation())
        .setPositiveButton(getString(salesforceR.stringPasscodeLogoutYes()),
        new DialogInterface.OnClickListener() {

        	@Override
        	public void onClick(DialogInterface dialog, int which) {
        		SalesforceSDKManager.getInstance().logout(ManageSpaceActivity.this, false);
        	}
        }).setNegativeButton(getString(salesforceR.stringPasscodeLogoutNo()),
        new DialogInterface.OnClickListener() {

        	@Override
        	public void onClick(DialogInterface dialog, int which) {
        		finish();
        	}
        }).create();
    }
}

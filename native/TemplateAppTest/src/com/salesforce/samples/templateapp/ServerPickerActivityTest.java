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
package com.salesforce.samples.templateapp;

import com.salesforce.androidsdk.ui.ServerPickerActivity;
import com.salesforce.androidsdk.util.BaseActivityInstrumentationTestCase;

import android.widget.Button;
import android.widget.EditText;

/**
 * Tests for ServerPickerActivity
 */
public class ServerPickerActivityTest extends
		BaseActivityInstrumentationTestCase<ServerPickerActivity> {

	private Button btnCustomEdit;
	private Button btnCancel;
	private Button btnApply;
	private EditText txtLabel;
	private EditText txtUrl;

	public ServerPickerActivityTest() {
		super("com.salesforce.samples.templateapp", ServerPickerActivity.class);
	}
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		setActivityInitialTouchMode(false);
	}

	/**
	 * Test that the cancel button can be clicked and the URL not saved
	 * 
	 * @throws Throwable
	 */
	public void testCancelButton() throws Throwable {
		openCustomEditDialog();

		clickView(btnCancel);

		assertFalse("Custom URL dialog should be closed",
				getActivity().urlEditDialog.isShowing());
	}

	/**
	 * Test a valid URL can be entered and saved
	 * 
	 * @throws Throwable
	 */
	public void testAddCustomInstance() throws Throwable {
		String label = "My Custom URL";
		String url = "https://valid.url.com";
		addCustomUrl(label, url);

		clickView(btnApply);

		openCustomEditDialog();
		assertTrue("Custom Label does not match Expected: " + label
				+ " Actual: " + txtLabel.getEditableText().toString(), label
				.equalsIgnoreCase(txtLabel.getEditableText().toString()));
		assertTrue("Custom URL does not match Expected: " + url + " Actual: "
				+ txtUrl.getEditableText().toString(), url
				.equalsIgnoreCase(txtUrl.getEditableText().toString()));
	}

	/**
	 * Test that "https" is required
	 * 
	 * @throws Throwable
	 */
	public void testAddInvalidUrl() throws Throwable {
		String label = "My URL";
		String url = "http://invalid.url.com";
		addCustomUrl(label, url);

		clickView(btnApply);

		assertTrue("Custom URL dialog should still be open",
				getActivity().urlEditDialog.isShowing());
		assertTrue("URL field should still have focus", txtUrl.hasFocus());

		url = "https://valid.url.com";
		addCustomUrl(label, url);

		clickView(btnApply);

		assertFalse("Custom URL dialog should be closed",
				getActivity().urlEditDialog.isShowing());
	}

	
	private void openCustomEditDialog() throws Throwable {
		btnCustomEdit = (Button) getActivity().findViewById(
				R.id.sf__show_custom_url_edit);
		assertNotNull("Custom URL Edit dialog does not exist", btnCustomEdit);

		runTestOnUiThread(new Runnable() {
			@Override
			public void run() {
				assertTrue("Unable to click open Custom URL Edit Dialog",
						btnCustomEdit.performClick());
				if (btnApply == null || btnCancel == null || txtLabel == null
						|| txtUrl == null) {
					btnApply = (Button) getActivity().urlEditDialog
							.findViewById(R.id.sf__apply_button);
					btnCancel = (Button) getActivity().urlEditDialog
							.findViewById(R.id.sf__cancel_button);
					txtLabel = (EditText) getActivity().urlEditDialog
							.findViewById(R.id.sf__picker_custom_label);
					txtUrl = (EditText) getActivity().urlEditDialog
							.findViewById(R.id.sf__picker_custom_url);
				}
				txtLabel.requestFocus();
			}
		});
	}

	private void removeCurrentCustomServer() throws Throwable {
		if (txtLabel.getEditableText().length() > 0) {
			setText(txtLabel, "");
		}

		if (txtUrl.getEditableText().length() > 0) {
			setText(txtUrl, "");
		}
	}

	private void addCustomUrl(String label, String url) throws Throwable {
		openCustomEditDialog();
		removeCurrentCustomServer();
		setText(txtLabel, label);
		setFocus(txtUrl);
		setText(txtUrl, url);
	}
}

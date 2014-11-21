/*
 * Copyright (c) 2011-2014, salesforce.com, inc.
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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.ui.CustomServerUrlEditor;
import com.salesforce.androidsdk.ui.ServerPickerActivity;
import com.salesforce.androidsdk.util.EventsObservable.EventType;
import com.salesforce.androidsdk.util.test.EventsListenerQueue;

/**
 * Tests for ServerPickerActivity
 */
public class ServerPickerActivityTest extends
		ActivityInstrumentationTestCase2<ServerPickerActivity> {

    private EventsListenerQueue eq;
	private Button btnCustomEdit;
	private Button btnCancel;
	private Button btnApply;
	private EditText txtLabel;
	private EditText txtUrl;
	private ServerPickerActivity activity;

	public ServerPickerActivityTest() {
		super(ServerPickerActivity.class);
	}

	@Override
	public void setUp() throws Exception {
		super.setUp();
        setActivityInitialTouchMode(false);
        eq = new EventsListenerQueue();

        // Waits for app initialization to complete.
        if (SalesforceSDKManager.getInstance() == null) {
            eq.waitForEvent(EventType.AppCreateComplete, 5000);
        }
        activity = getActivity();
        removeFragmentIfRequired();
        assertNotNull("Activity should not be null", activity);
	}

	@Override
	public void tearDown() throws Exception {
		if (eq != null) {
            eq.tearDown();
            eq = null;
        }
		activity.finish();
		activity = null;
		super.tearDown();
	}

	/**
	 * Test that the cancel button can be clicked and the URL not saved
	 *
	 * @throws Throwable
	 */
	public void testCancelButton() throws Throwable {
		openCustomEditDialog();
		clickView(btnCancel);
		assertNull("Custom URL dialog should be closed",
				activity.getCustomServerUrlEditor().getDialog());
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
				activity.getCustomServerUrlEditor().getDialog().isShowing());
		assertTrue("URL field should still have focus", txtUrl.hasFocus());
		url = "https://valid.url.com";
		addCustomUrl(label, url);
		clickView(btnApply);
		assertNull("Custom URL dialog should be closed",
				activity.getCustomServerUrlEditor().getDialog());
	}

	private void openCustomEditDialog() throws Throwable {
		btnCustomEdit = (Button) activity.findViewById(
				com.salesforce.androidsdk.R.id.sf__show_custom_url_edit);
		assertNotNull("Custom URL Edit dialog does not exist", btnCustomEdit);
		clickView(btnCustomEdit);
		final CustomServerUrlEditor dialog = activity.getCustomServerUrlEditor();
		Thread.sleep(3000);
		final View rootView = dialog.getRootView();
		assertNotNull("Root view should not be null", rootView);
		if (btnApply == null || btnCancel == null || txtLabel == null
				|| txtUrl == null) {
			btnApply = (Button) rootView.findViewById(com.salesforce.androidsdk.R.id.sf__apply_button);
			btnCancel = (Button) rootView.findViewById(com.salesforce.androidsdk.R.id.sf__cancel_button);
			txtLabel = (EditText) rootView.findViewById(com.salesforce.androidsdk.R.id.sf__picker_custom_label);
			txtUrl = (EditText) rootView.findViewById(com.salesforce.androidsdk.R.id.sf__picker_custom_url);
		}
		setFocus(txtLabel);
	}

	private void addCustomUrl(String label, String url) throws Throwable {
		if (!activity.getCustomServerUrlEditor().isVisible()) {
			openCustomEditDialog();
		}
		setText(txtLabel, label);
		setFocus(txtUrl);
		setText(txtUrl, url);
	}

	private void removeFragmentIfRequired() {
		final FragmentManager fm = activity.getFragmentManager();
		final Fragment dialog = activity.getFragmentManager().findFragmentByTag("custom_server_dialog");
		if (dialog != null && dialog.isAdded()) {
			final FragmentTransaction ft = fm.beginTransaction();
			ft.remove(dialog);
			ft.commit();
		}
	}

    private void setText(final TextView v, final String text) {
        try {
            runTestOnUiThread(new Runnable() {

                @Override
                public void run() {
                    v.setText(text);
                    if (v instanceof EditText)
                        ((EditText) v).setSelection(v.getText().length());
                }
            });
        } catch (Throwable t) {
            fail("Failed to set text " + text);
        }
    }

    private void setFocus(View v) throws Throwable {
        runTestOnUiThread(new Focuser(v));
    }

    /**
     * A runnable that requests focus for the specified view.
     */
    private static class Focuser implements Runnable {

        Focuser(View v) {
            this.view = v;
        }

        private final View view;

        @Override
        public void run() {
            view.requestFocus();
        }
    }

    private void clickView(final View v) {
        try {
            runTestOnUiThread(new Runnable() {

                @Override
                public void run() {
                    v.performClick();
                }
            });
        } catch (Throwable t) {
            fail("Failed to click view " + v);
        }
    }
}

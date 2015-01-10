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
package com.salesforce.androidsdk.ui;

import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.LoginServerManager;

/**
 * Custom dialog fragment to allow the user to set a label and URL to use for the login.
 */
public class CustomServerUrlEditor extends DialogFragment {

	boolean isDefault;
	private SalesforceR salesforceR;
	private LoginServerManager loginServerManager;
	private Context context;
	private View rootView;

	/**
	 * Default constructor.
	 */
	public CustomServerUrlEditor() {
		context = SalesforceSDKManager.getInstance().getAppContext();

		// Object which allows reference to resources living outside the SDK.
		salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();

		// Login server manager.
		loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        rootView = inflater.inflate(salesforceR.layoutCustomServerUrl(), container);
        final String label = getEditDefaultValue((salesforceR.idPickerCustomLabel()));
		final String urlValue = getEditDefaultValue((salesforceR.idPickerCustomUrl()));
		isDefault = urlValue.equals(getString(salesforceR.stringServerUrlDefaultCustomUrl()));
		if (isDefault) {
			getDialog().setTitle(salesforceR.stringServerUrlAddTitle());
		} else {
			getDialog().setTitle(salesforceR.stringServerUrlEditTitle());
		}
		setEditText(salesforceR.idPickerCustomLabel(), label);
		setEditText(salesforceR.idPickerCustomUrl(), urlValue);

		/*
		 * Sets handlers in the code for the dialog. 
		 */
		final Button applyBtn = (Button) rootView.findViewById(salesforceR.idApplyButton());
		applyBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				final String lbl = validateInput(salesforceR.idPickerCustomLabel());
				if (lbl == null) {
					return;
				}
				final String val = validateInput(salesforceR.idPickerCustomUrl());
				if (val == null) {
					return;
				}

				// Saves state and dismisses the dialog.
				loginServerManager.addCustomLoginServer(lbl, val);
				dismiss();
			}
		});
		final Button cancelBtn = (Button) rootView.findViewById(salesforceR.idCancelButton());
		cancelBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				dismiss();
			}
		});
		return rootView;
    }

	@Override
	public void onDismiss(DialogInterface dialog) {
		final ServerPickerActivity activity = (ServerPickerActivity) getActivity();
		if (activity != null) {
			activity.rebuildDisplay();
		}
	}

	/**
	 * Returns the root view of this fragment (used mainly by tests).
	 *
	 * @return Root view.
	 */
	public View getRootView() {
		return rootView;
	}

	private void setEditText(int editId, String value) {
		if (value == null) {
			throw new RuntimeException("Value cannot be null");
		}
		final EditText et = (EditText) rootView.findViewById(editId);
		final SpannableString labelSpan = new SpannableString(value);
		if (et != null) {
			et.setText(labelSpan);
			if (et.getOnFocusChangeListener() == null) {
				et.setOnFocusChangeListener(new OnFocusChangeListener() {

					@Override
					public void onFocusChange(View v, boolean hasFocus) {
						final EditText et = (EditText) v;
						boolean isDefaultValue = et.getText().toString().equals(
								getEditDefaultValue(et.getId()));
						if (hasFocus && isDefaultValue) {
							et.getText().clear();
						} else if (!hasFocus && et.getText().toString().equals("")) {
							if (et.getId() == salesforceR.idPickerCustomLabel()) {
								setEditText(salesforceR.idPickerCustomLabel(), getEditDefaultValue(et.getId()));
							} else {
								setEditText(salesforceR.idPickerCustomUrl(), getEditDefaultValue(et.getId()));
							}
						}
					}
				});
			}
		}
	}

	private String validateInput(int editId) {
		final EditText et = (EditText) rootView.findViewById(editId);
		final Editable etVal = et.getText();
		boolean isInvalidValue = etVal.toString().equals(getEditDefaultValue(editId))
				|| etVal.toString().equals("");

		/*
		 * Ensures that the URL is a 'https://' URL, since OAuth requires 'https://'.
		 */
		if (editId == salesforceR.idPickerCustomUrl()) {
			isInvalidValue = !URLUtil.isHttpsUrl(etVal.toString());
			if (isInvalidValue) {
				Toast.makeText(context, getString(salesforceR.stringInvalidServerUrl()),
						Toast.LENGTH_SHORT).show();
			}
		}
		if (isInvalidValue) {
			et.selectAll();
			et.requestFocus();
			return null;
		}
		return etVal.toString();
	}

	private String getEditDefaultValue(int editId) {
		if (editId == salesforceR.idPickerCustomLabel()) {
			return getString(salesforceR.stringServerUrlDefaultCustomLabel());
		} else { 
			return getString(salesforceR.stringServerUrlDefaultCustomUrl());
		}
	}
}

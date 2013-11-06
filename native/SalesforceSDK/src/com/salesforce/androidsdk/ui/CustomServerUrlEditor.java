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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.WindowManager.LayoutParams;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.LoginServerManager;
import com.salesforce.androidsdk.auth.LoginServerManager.LoginServer;

/**
 * Custom dialog to allow the user to set a label and url to use for the login.
 */
public class CustomServerUrlEditor extends Dialog {

	private static final String PERSISTED_CTRL_FOCUS = "focusedId";
	private static final String PERSISTED_LABEL = "label";
	private static final String PERSISTED_URL_VALUE = "url";

	boolean isDefault;
	private SalesforceR salesforceR;
	private LoginServerManager loginServerManager;
	private int width;

	public CustomServerUrlEditor(Context context, int width) {
		super(context);
		
		// Object which allows reference to resources living outside the SDK
		salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();
		
		// Login server manager
		loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();
		
		// Width
		this.width = width;
	}

	private String getEditDefaultValue(int editId) {
		if (editId == salesforceR.idPickerCustomLabel()) {
			return getString(salesforceR.stringServerUrlDefaultCustomLabel());
		} else { 
			return getString(salesforceR.stringServerUrlDefaultCustomUrl());
		}
	}

	private String getString(int resourceKey) {
		return getContext().getString(resourceKey);
	}

	@Override
	public void onBackPressed() {
		cancel();
	}

	/**
	 * onStart will build the saved display, this will restore whatever the user
	 * typed before the rotate gets called after onStart, which is nice and
	 * means the controls are already hooked.
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		setEditText(salesforceR.idPickerCustomLabel(), savedInstanceState
				.getString(PERSISTED_LABEL));
		setEditText(salesforceR.idPickerCustomUrl(), savedInstanceState
				.getString(PERSISTED_URL_VALUE));
		if (savedInstanceState.getInt(PERSISTED_CTRL_FOCUS) > 0) {
			EditText et = (EditText) findViewById(savedInstanceState
					.getInt(PERSISTED_CTRL_FOCUS));
			et.requestFocus();
		}
	}

	// save values of edit ctrls
	// save empty as "" not null
	@Override
	public Bundle onSaveInstanceState() {
		Bundle superBundle = super.onSaveInstanceState();
		persistEditCtrlInfo(superBundle, PERSISTED_LABEL, salesforceR.idPickerCustomLabel());
		persistEditCtrlInfo(superBundle, PERSISTED_URL_VALUE, salesforceR.idPickerCustomUrl());
		return superBundle;
	}

	@Override
	protected void onCreate(Bundle savedInstance) {
		LoginServer customServer = loginServerManager.getCustomLoginServer();
		String label = (customServer != null ? customServer.name : 
				getEditDefaultValue((salesforceR.idPickerCustomLabel())));
		String urlValue = (customServer != null ? customServer.url : 
				getEditDefaultValue((salesforceR.idPickerCustomUrl())));
		isDefault = urlValue
				.equals(getString(salesforceR.stringServerUrlDefaultCustomUrl()));
		if (isDefault) {
			setTitle(salesforceR.stringServerUrlAddTitle());
		} else {
			setTitle(salesforceR.stringServerUrlEditTitle());
		}
		setContentView(salesforceR.layoutCustomServerUrl());
		setEditText(salesforceR.idPickerCustomLabel(), label);
		setEditText(salesforceR.idPickerCustomUrl(), urlValue);

		// set handlers in code, otherwise it will default to a dialog listener,
		// which is not what we want here
		Button applyBtn = (Button) findViewById(salesforceR.idApplyButton());
		applyBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				// validate. if the values are non default just accept them
				String lbl = validateInput(salesforceR.idPickerCustomLabel());
				if (null == lbl) {
					return;
				}
				String val = validateInput(salesforceR.idPickerCustomUrl());
				if (null == val) {
					return;
				}

				// save state and finish
				loginServerManager.setCustomLoginServer(lbl, val);
				dismiss();
			}
		});
		Button cancelBtn = (Button) findViewById(salesforceR.idCancelButton());
		cancelBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				cancel();
			}
		});

		// can only get a dialog to resize after the layout has been set
		// we want to take up the full screen...
		LayoutParams params = getWindow().getAttributes();
		params.width = (width != 0 ? width : LayoutParams.FILL_PARENT);
		getWindow().setAttributes(params);
	}

	private void persistEditCtrlInfo(Bundle superBundle, String keyName,
			int ctrlId) {
		EditText et = (EditText) findViewById(ctrlId);
		superBundle.putString(keyName, et.getText().toString());
		if (et.hasFocus()) {
			superBundle.putInt(PERSISTED_CTRL_FOCUS, ctrlId);
		}
	}

	private void setEditText(int editId, String value) {
		if (null == value) {
			throw new RuntimeException("Value cannot be null");
		}
		EditText et = (EditText) findViewById(editId);
		SpannableString labelSpan = new SpannableString(value);
		if (et != null) {
			et.setText(labelSpan);
			if (et.getOnFocusChangeListener() == null) {
				et.setOnFocusChangeListener(new OnFocusChangeListener() {

					@Override
					public void onFocusChange(View v, boolean hasFocus) {
						EditText et = (EditText) v;
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
		EditText et = (EditText) findViewById(editId);
		Editable etVal = et.getText();
		boolean isInvalidValue = etVal.toString().equals(
				getEditDefaultValue(editId))
				|| etVal.toString().equals("");

		// Ensure that the URL is a 'https://' URL, since OAuth requires
		// 'https://'.
		if (editId == salesforceR.idPickerCustomUrl()) {
			isInvalidValue = !URLUtil.isHttpsUrl(etVal.toString());
			if (isInvalidValue) {
				Toast.makeText(getContext(), getContext().getString(salesforceR.stringInvalidServerUrl()),
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
}

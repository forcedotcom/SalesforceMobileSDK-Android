/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.URLUtil;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.LoginServerManager;

import okhttp3.HttpUrl;

/**
 * Custom dialog fragment to allow the user to set a label and URL to use for the login.
 */
public class CustomServerUrlEditor extends DialogFragment {

	private LoginServerManager loginServerManager;
	private Context context;
	private View rootView;

	/**
	 * Default constructor.
	 */
	public CustomServerUrlEditor() {
		context = SalesforceSDKManager.getInstance().getAppContext();

		// Login server manager.
		loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
		boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
        rootView = inflater.inflate(R.layout.sf__custom_server_url, container);
		rootView.getContext().setTheme(isDarkTheme ? R.style.SalesforceSDK_Dialog_Dark : R.style.SalesforceSDK_Dialog);
		getDialog().setTitle(R.string.sf__server_url_add_title);

		// TODO: Remove this when min API becomes 24.
		if (!isDarkTheme && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
			EditText label = getRootView().findViewById(R.id.sf__picker_custom_label);
			label.setTextColor(getResources().getColor(R.color.sf__text_color));
			label.setHintTextColor(getResources().getColor(R.color.sf__hint_color));
			EditText url = getRootView().findViewById(R.id.sf__picker_custom_url);
			url.setTextColor(getResources().getColor(R.color.sf__text_color));
			url.setHintTextColor(getResources().getColor(R.color.sf__hint_color));
		}

		/*
		 * Sets handlers in the code for the dialog. 
		 */
		final Button applyBtn = rootView.findViewById(R.id.sf__apply_button);
		applyBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				final String lbl = validateInput(R.id.sf__picker_custom_label);
				if (lbl == null) {
					return;
				}
				final String val = validateInput(R.id.sf__picker_custom_url);
				if (val == null) {
					return;
				}

				// Saves state and dismisses the dialog.
				loginServerManager.addCustomLoginServer(lbl.trim(), val.trim());
				dismiss();
			}
		});
		final Button cancelBtn = rootView.findViewById(R.id.sf__cancel_button);
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

	private String validateInput(int editId) {
		final EditText et = rootView.findViewById(editId);
		final Editable etVal = et.getText();
		boolean isInvalidValue = etVal.toString().equals(getEditDefaultValue(editId))
				|| etVal.toString().equals("");

		/*
		 * Ensures that the URL is a 'https://' URL, since OAuth requires 'https://'.
		 */
		if (editId == R.id.sf__picker_custom_url) {
			String url = etVal.toString();
			if (!isInvalidValue) {
				if (!URLUtil.isHttpsUrl(url)) {
					if (URLUtil.isHttpUrl(url)) {
						url = url.replace("http://", "https://");
					} else {
						url = "https://".concat(url);
					}
				}
				// Check if string is a valid url
				if (HttpUrl.parse(url) != null) {
					return url;
				}
			}
			Toast.makeText(context, getString(R.string.sf__invalid_server_url), Toast.LENGTH_SHORT).show();
		}

		if (isInvalidValue) {
			et.selectAll();
			et.requestFocus();
			return null;
		}
		return etVal.toString();
	}

	private String getEditDefaultValue(int editId) {
		if (editId == R.id.sf__picker_custom_label) {
			return getString(R.string.sf__server_url_default_custom_label);
		} else { 
			return getString(R.string.sf__server_url_default_custom_url);
		}
	}
}

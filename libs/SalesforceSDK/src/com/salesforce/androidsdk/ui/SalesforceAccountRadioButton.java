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

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.widget.RadioButton;

import com.salesforce.androidsdk.accounts.UserAccount;

/**
 * Custom radio button implementation to represent a Salesforce account.
 * Classes using this radio button should use the custom 'setText()'
 * method to display text in this radio button.
 *
 * @author bhariharan
 */
public class SalesforceAccountRadioButton extends RadioButton {

	private Context context;
	private UserAccount account;

	/**
	 * Parameterized constructor.
	 *
	 * @param context Context.
	 * @param account User account.
	 */
	public SalesforceAccountRadioButton(Context context, UserAccount account) {
		super(context);
		this.context = context;
		this.account = account;
		setText();
	}

	/**
	 * Formats the text the right way, before displaying it.
	 */
	public void setText() {
		final SpannableStringBuilder result = new SpannableStringBuilder();
		if (account != null && account.getUsername() != null && account.getLoginServer() != null) {
			final String username = account.getUsername();
			final String loginServer = account.getLoginServer();
	        final SpannableString titleSpan = new SpannableString(username);
	        titleSpan.setSpan(new TextAppearanceSpan(context,
	                android.R.style.TextAppearance_Medium), 0, username.length(),
	                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	        final SpannableString urlSpan = new SpannableString(loginServer);
	        urlSpan.setSpan(new TextAppearanceSpan(context,
	                android.R.style.TextAppearance_Small), 0, loginServer.length(),
	                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
	        result.append(titleSpan);
	        result.append(System.getProperty("line.separator"));
	        result.append(urlSpan);	
		}
        super.setText(result, BufferType.SPANNABLE);
	}

	/**
	 * Returns the user account associated with this button.
	 *
	 * @return UserAccount instance.
	 */
	public UserAccount getAccount() {
		return account;
	}
}

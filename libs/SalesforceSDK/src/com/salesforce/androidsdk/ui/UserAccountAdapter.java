/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.accounts.UserAccount;

/**
 * A custom array adapter that represents a list of Salesforce user accounts.
 * This is used by the user switcher screen to populate the list of accounts.
 *
 * @author bhariharan
 */
public class UserAccountAdapter extends ArrayAdapter<UserAccount> {

    private Context context;
    private int resource;
    private UserAccount[] accounts;

    /**
     * Parameterized constructor.
     *
     * @param context Context.
     * @param resource Row layout resource.
     * @param accounts List of user accounts.
     */
    public UserAccountAdapter(Context context, int resource, UserAccount[] accounts) {
        super(context, resource, accounts);
        this.context = context;
        this.resource = resource;
        this.accounts = accounts;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        UserAccount account = null;
        if (accounts != null) {
            account = accounts[position];
        }
        if (row == null) {
            final LayoutInflater inflater = ((Activity) context).getLayoutInflater();
            row = inflater.inflate(resource, parent, false);
            if (account != null) {
                final ImageView userIcon = row.findViewById(R.id.sf__user_icon);
                final Bitmap icon = account.getProfilePhoto();
                if (userIcon != null && icon != null) {
                    userIcon.setImageBitmap(icon);
                }
                final TextView userName = row.findViewById(R.id.sf__user_name);
                if (userName != null) {
                    userName.setText(account.getDisplayName());
                }
                final TextView serverName = row.findViewById(R.id.sf__server_name);
                if (serverName != null) {
                    serverName.setText(account.getLoginServer());
                }
                row.setTag(account);
            }
        }
        return row;
    }
}

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
package com.salesforce.androidsdk.auth;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.content.Context;

import com.salesforce.androidsdk.app.SalesforceSDKManager;

/**
 * This class acts as a listener for the account removal event.
 *
 * @author bhariharan
 */
public class AccountWatcher implements OnAccountsUpdateListener {

    private final AccountManager mgr;
    private final AccountRemoved callback;

    public interface AccountRemoved {
        void onAccountRemoved();
    }

    public AccountWatcher(Context ctx, AccountRemoved cb) {
        assert ctx != null : "Context must not be null";
        assert cb  != null : "AccountRemoved callback must not be null";
        this.callback = cb;
        this.mgr = AccountManager.get(ctx);
        this.mgr.addOnAccountsUpdatedListener(this, null, false);
    }

    @Override
    public void onAccountsUpdated(Account[] accounts) {

        // Check if there's an entry for our account type, if not fire the callback.
        for (final Account a : accounts) {
            if (SalesforceSDKManager.getInstance().getAccountType().equals(a.type)) {
                return;
            }
        }
        callback.onAccountRemoved();
    }

    public void remove() {
        mgr.removeOnAccountsUpdatedListener(this);
    }
}

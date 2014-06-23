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
package com.salesforce.androidsdk.phonegap;

import java.util.List;

import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

/**
 * Cordova plugin that provides methods related to user account management.
 *
 * @author bhariharan
 */
public class SFAccountManagerPlugin extends ForcePlugin {

    /**
     * Supported plugin actions.
     */
    enum Action {
        getUsers,
        getCurrentUser,
        logout,
        switchToUser
    }

	@Override
	protected boolean execute(String actionStr,
			JavaScriptPluginVersion jsVersion, JSONArray args,
			CallbackContext callbackContext) throws JSONException {
        Action action = null;
        try {
            action = Action.valueOf(actionStr);
            switch(action) {
                case getUsers:
                	getUsers(callbackContext);
                	return true;
                case getCurrentUser:
                	getCurrentUser(callbackContext);
                	return true;
                case logout:
                	logout(args, callbackContext);
                	return true;
                case switchToUser:
                	switchToUser(args, callbackContext);
                	return true;
                default:
                	return false;
            }
        } catch (IllegalArgumentException e) {
            return false;
        }
	}

    /**
     * Native implementation for the 'getUsers' action.
     *
     * @param callbackContext Used when calling back into Javascript.
     */
    protected void getUsers(CallbackContext callbackContext) {
        Log.i("SFAccountManagerPlugin.getUsers", "getUsers called");
        final List<UserAccount> userAccounts = SalesforceSDKManager.getInstance().getUserAccountManager().getAuthenticatedUsers();
        final JSONArray accounts = new JSONArray();
        if (userAccounts != null && !userAccounts.isEmpty()) {
        	for (final UserAccount account : userAccounts) {
        		accounts.put(account.toJson());
        	}
        }
        callbackContext.success(accounts);
    }

    /**
     * Native implementation for the 'getCurrentUser' action.
     *
     * @param callbackContext Used when calling back into Javascript.
     */
    protected void getCurrentUser(CallbackContext callbackContext) {
        Log.i("SFAccountManagerPlugin.getCurrentUser", "getCurrentUser called");
        final UserAccount userAccount = SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentUser();
        JSONObject account = new JSONObject();
        if (userAccount != null) {
        	account = userAccount.toJson();
        }
        callbackContext.success(account);
    }

    /**
     * Native implementation for the 'logout' action.
     *
     * @param args Arguments passed in, namely the account to logout of.
     * @param callbackContext Used when calling back into Javascript.
     */
    protected void logout(JSONArray args, CallbackContext callbackContext) {
        Log.i("SFAccountManagerPlugin.logout", "logout called");
        UserAccount account = SalesforceSDKManager.getInstance().getUserAccountManager().getCurrentUser();
        if (args != null && args.length() > 0) {
        	final JSONObject user = args.optJSONObject(0);
        	if (user != null) {
        		account = new UserAccount(user);
        	}
        }
        SalesforceSDKManager.getInstance().getUserAccountManager().signoutUser(account, cordova.getActivity());
        callbackContext.success();
    }

    /**
     * Native implementation for the 'switchToUser' action.
     *
     * @param args Arguments passed in, namely the account to switch to.
     * @param callbackContext Used when calling back into Javascript.
     */
    protected void switchToUser(JSONArray args, CallbackContext callbackContext) {
        Log.i("SFAccountManagerPlugin.switchToUser", "switchToUser called");
        UserAccount account = null;
        final List<UserAccount> userAccounts = SalesforceSDKManager.getInstance().getUserAccountManager().getAuthenticatedUsers();

        /*
         * If no user is specified to switch to, we check the number of users
         * available. If only 1 user is signed in, we automatically launch the
         * login activity. If more than 1 user is already signed in, we bring
         * up the default account switcher screen, where a selection can be
         * made on which account to switch to.
         */
        if (args == null || args.length() == 0) {
        	if (userAccounts == null || userAccounts.size() == 1) {
        		SalesforceSDKManager.getInstance().getUserAccountManager().switchToNewUser();
        	} else {
        		final Intent i = new Intent(SalesforceSDKManager.getInstance().getAppContext(),
        				SalesforceSDKManager.getInstance().getAccountSwitcherActivityClass());
        		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        		SalesforceSDKManager.getInstance().getAppContext().startActivity(i);
        	}
        } else {
        	final JSONObject user = args.optJSONObject(0);
        	if (user != null) {
        		account = new UserAccount(user);
        	}
    		SalesforceSDKManager.getInstance().getUserAccountManager().switchToUser(account);
        }
        callbackContext.success();
    }
}

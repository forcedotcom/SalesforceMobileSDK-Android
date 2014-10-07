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
package com.salesforce.samples.smartsyncexplorer.loaders;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.rest.ApiVersionStrings;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.smartsync.manager.NetworkManager;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;

/**
 * A simple AsyncTaskLoader to load object detail for a Salesforce object.
 *
 * @author bhariharan
 */
public class SObjectDetailLoader extends AsyncTaskLoader<SalesforceObject> {

    private static final String TAG = "SmartSyncExplorer: SObjectDetailLoader";

	private String objectId;
	private String objectType;
	private NetworkManager networkMgr;

	/**
	 * Parameterized constructor.
	 *
	 * @param context Context.
	 * @param account User account.
	 * @param objId Object ID.
	 * @param objType Object type.
	 */
	public SObjectDetailLoader(Context context, UserAccount account,
			String objId, String objType) {
		super(context);
		objectId = objId;
		objectType = objType;
		networkMgr = NetworkManager.getInstance(account);
	}

	@Override
	public SalesforceObject loadInBackground() {
    	SalesforceObject sObject = null;
		try {
			final RestRequest request = RestRequest.getRequestForRetrieve(
					ApiVersionStrings.VERSION_NUMBER,
					objectType, objectId, null);
			final RestResponse response = networkMgr.makeRemoteRequest(request);
			if (response != null && response.isSuccess()) {
	            try {
	                final JSONObject responseJSON = response.asJSONObject();
	                if (responseJSON != null) {
	                	sObject = new SalesforceObject(responseJSON);
	                }
	            } catch (IOException ex) {
	                Log.e(TAG, "IOException occurred while reading data", ex);
	            } catch (JSONException ex) {
	                Log.e(TAG, "JSONException occurred while parsing", ex);
	            }
	        }
		} catch (UnsupportedEncodingException e) {
            Log.e(TAG, "UnsupportedEncodingException occurred while making request", e);
        }
		return sObject;
	}
}

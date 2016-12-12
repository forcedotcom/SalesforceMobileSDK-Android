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
package com.salesforce.samples.smartsyncexplorer.loaders;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper.SmartSqlException;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.samples.smartsyncexplorer.objects.ContactObject;

/**
 * A simple AsyncTaskLoader to load object detail for a Contact object.
 *
 * @author bhariharan
 */
public class ContactDetailLoader extends AsyncTaskLoader<ContactObject> {

    private static final String TAG = "ContactDetailLoader";

	private String objectId;
	private SmartStore smartStore;

	/**
	 * Parameterized constructor.
	 *
	 * @param context Context.
	 * @param account User account.
	 * @param objId Object ID.
	 */
	public ContactDetailLoader(Context context, UserAccount account,
			String objId) {
		super(context);
		objectId = objId;
		smartStore = SmartSyncSDKManager.getInstance().getSmartStore(account);
	}

	@Override
	public ContactObject loadInBackground() {
		if (TextUtils.isEmpty(objectId)) {
			return null;
		}
		ContactObject sObject = null;
		if (!smartStore.hasSoup(ContactListLoader.CONTACT_SOUP)) {
			return null;
		}
		final QuerySpec querySpec = QuerySpec.buildExactQuerySpec(
				ContactListLoader.CONTACT_SOUP, Constants.ID, objectId, null, null, 1);
		JSONArray results = null;
		try {
			results = smartStore.query(querySpec, 0);
			if (results != null) {
				sObject = new ContactObject(results.getJSONObject(0));
			}
		} catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while parsing", e);
		} catch (SmartSqlException e) {
            Log.e(TAG, "SmartSqlException occurred while fetching data", e);
		}
		return sObject;
	}
}

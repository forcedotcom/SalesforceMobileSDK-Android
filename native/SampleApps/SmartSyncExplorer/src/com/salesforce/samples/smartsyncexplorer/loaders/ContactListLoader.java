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

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.util.Log;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper.SmartSqlException;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.samples.smartsyncexplorer.objects.ContactObject;

/**
 * A simple AsyncTaskLoader to load a list of Salesforce contacts.
 *
 * @author bhariharan
 */
public class ContactListLoader extends AsyncTaskLoader<List<ContactObject>> {

	public static final String CONTACT_FIELDS_STR = "Id, Name, FirstName,"
			+ " LastName, Title, Phone, Email, Department, HomePhone";
	public static final String CONTACT_SOUP = "contacts";
	public static final Integer LIMIT = 4000;
    private static final String TAG = "SmartSyncExplorer: ContactListLoader";
	private static final String CONTACT_QUERY = "SELECT " + getSelectQuery()
			+ " FROM {" + CONTACT_SOUP + "}";
	private static final String CURLY_BRACE_LEFT = "{";
	private static final String CURLY_BRACE_RIGHT = "}";
	private static final String COLON = ":";
	private static final String COMMA = ",";

	private SmartStore smartStore;

	/**
	 * Parameterized constructor.
	 *
	 * @param context Context.
	 * @param account User account.
	 */
	public ContactListLoader(Context context, UserAccount account) {
		super(context);
		smartStore = SmartSyncSDKManager.getInstance().getSmartStore(account);
	}

	@Override
	public List<ContactObject> loadInBackground() {
		if (!smartStore.hasSoup(CONTACT_SOUP)) {
			return null;
		}
		final QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(CONTACT_QUERY,
				LIMIT);
		JSONArray results = null;
		try {
			results = smartStore.query(querySpec, 0);
		} catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while parsing", e);
		} catch (SmartSqlException e) {
            Log.e(TAG, "SmartSqlException occurred while fetching data", e);
		}
		List<ContactObject> contacts = new ArrayList<ContactObject>();
		if (results != null) {
			for (int i = 0; i < results.length(); i++) {
				final JSONArray obj = results.optJSONArray(i);
				if (obj != null) {
					contacts.add(buildSObject(obj));
				}
			}
		}
		return contacts;
	}

	private static String getSelectQuery() {
		final StringBuilder sb = new StringBuilder();
		for (final String str : ContactObject.CONTACT_FIELDS) {
			sb.append(CURLY_BRACE_LEFT);
			sb.append(CONTACT_SOUP);
			sb.append(COLON);
			sb.append(str);
			sb.append(CURLY_BRACE_RIGHT);
			sb.append(COMMA);
		}
		sb.deleteCharAt(sb.lastIndexOf(COMMA));
		return sb.toString();
	}

	private ContactObject buildSObject(JSONArray array) {
		final JSONObject object = new JSONObject();
		try {
			for (int i = 0; i < ContactObject.CONTACT_FIELDS.length; i++) {
				final String key = ContactObject.CONTACT_FIELDS[i];
				final String value = array.optString(i);
				object.put(key, value);
			}
		} catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while parsing", e);
		}
		return new ContactObject(object);
	}
}

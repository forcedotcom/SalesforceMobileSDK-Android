/*
 * Copyright (c) 2013, salesforce.com, inc.
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
package com.salesforce.samples.nativesqlaggregator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.QuerySpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

/**
 * Interface to smart store.
 *
 * @author bhariharan
 */
public class SmartStoreInterface {

	private static String TAG = "NativeSqlAggregator: SmartStoreInterface";
	private static String ACCOUNTS_SOUP = "Account";
	private static String OPPORTUNITIES_SOUP = "Opportunity";

	// Index spec for accounts.
	private static IndexSpec[] ACCOUNTS_INDEX_SPEC = {
		new IndexSpec("Name", Type.string),
		new IndexSpec("Id", Type.string),
		new IndexSpec("OwnerId", Type.string)
	};

	// Index spec for opportunities.
	private static IndexSpec[] OPPORTUNITIES_INDEX_SPEC = {
		new IndexSpec("Name", Type.string),
		new IndexSpec("Id", Type.string),
		new IndexSpec("AccountId", Type.string),
		new IndexSpec("OwnerId", Type.string),
		new IndexSpec("Amount", Type.integer)
	};

	private SalesforceSDKManagerWithSmartStore sdkManager;
	private SmartStore smartStore;

	/**
	 * Default constructor.
	 */
	public SmartStoreInterface() {
		sdkManager = SalesforceSDKManagerWithSmartStore.getInstance();
		smartStore = sdkManager.getSmartStore();
	}

	/**
	 * Creates a soup for accounts.
	 */
	public void createAccountsSoup() {
		smartStore.registerSoup(ACCOUNTS_SOUP, ACCOUNTS_INDEX_SPEC);
	}

	/**
	 * Creates a soup for opportunities.
	 */
	public void createOpportunitiesSoup() {
		smartStore.registerSoup(OPPORTUNITIES_SOUP, OPPORTUNITIES_INDEX_SPEC);
	}

	/**
	 * Deletes the existing soup for accounts.
	 */
	public void deleteAccountsSoup() {
		if (smartStore.hasSoup(ACCOUNTS_SOUP)) {
			smartStore.dropSoup(ACCOUNTS_SOUP);	
		}
	}

	/**
	 * Deletes the existing soup for opportunities.
	 */
	public void deleteOpportunitiesSoup() {
		if (smartStore.hasSoup(OPPORTUNITIES_SOUP)) {
			smartStore.dropSoup(OPPORTUNITIES_SOUP);	
		}
	}

	/**
	 * Inserts accounts into the accounts soup.
	 *
	 * @param accounts Accounts.
	 */
	public void insertAccounts(JSONArray accounts) {
		try {
			if (accounts != null) {
				for (int i = 0; i < accounts.length(); i++) {
					insertAccount(accounts.getJSONObject(i));
				}
			}
		} catch (JSONException e) {
			Log.e(TAG, "Error occurred while attempting to insert accounts. Please verify validity of JSON data set.");
		}
	}

	/**
	 * Inserts a single account into the accounts soup.
	 *
	 * @param account Account.
	 */
	public void insertAccount(JSONObject account) {
		if (account != null) {
			try {
				smartStore.upsert(ACCOUNTS_SOUP, account);	
			} catch (JSONException exc) {
				Log.e(TAG, "Error occurred while attempting to insert account. Please verify validity of JSON data set.");
			}
		}
	}

	/**
	 * Inserts opportunities into the opportunities soup.
	 *
	 * @param opportunities Opportunities.
	 */
	public void insertOpportunities(JSONArray opportunities) {
		try {
			if (opportunities != null) {
				for (int i = 0; i < opportunities.length(); i++) {
					insertOpportunity(opportunities.getJSONObject(i));
				}
			}
		} catch (JSONException e) {
			Log.e(TAG, "Error occurred while attempting to insert opportunities. Please verify validity of JSON data set.");
		}
	}

	/**
	 * Inserts a single opportunity into the opportunities soup.
	 *
	 * @param opportunity Opportunity.
	 */
	public void insertOpportunity(JSONObject opportunity) {
		if (opportunity != null) {

			/*
			 * Since SmartStore currently supports only 'string'
			 * and 'integer', we need to check if null values exist
			 * for integer fields. Furthermore, since 'Amount'
			 * is a double, we need to typecast it to 'integer'
			 * to store it in SmartStore as an 'integer'. Since
			 * the purpose of this app is to demonstrate aggregate
			 * SQL queries such as 'sum', 'avg', etc., conversions
			 * are required. The ideal approach would be to store
			 * these double values as strings and convert them to
			 * double when and if required (for non-null values).
			 *
			 * TODO: Fix this when SmartStore supports 'double'.
			 */
			double amount = 0;
			try {
				final Object amountObj = opportunity.get("Amount");
				if (amountObj != null) {
					amount = opportunity.getDouble("Amount");
				}
			} catch (JSONException e) {
				Log.e(TAG, "Error occurred while attempting to insert opportunity. Please verify validity of JSON data set.");
			} finally {
				try {
					opportunity.put("Amount", (int) amount);	
				} catch (JSONException ex) {
					Log.e(TAG, "Error occurred while attempting to insert opportunity. Please verify validity of JSON data set.");
				}
			}
			try {
				smartStore.upsert(OPPORTUNITIES_SOUP, opportunity);	
			} catch (JSONException exc) {
				Log.e(TAG, "Error occurred while attempting to insert opportunity. Please verify validity of JSON data set.");
			}
		}
	}

	/**
	 * Returns saved opportunities.
	 *
	 * @return Saved opportunities.
	 */
	public JSONArray getOpportunities() {
		return query("SELECT {Opportunity:Name}, {Opportunity:Id}, {Opportunity:AccountId}, {Opportunity:OwnerId}, {Opportunity:Amount} FROM {Opportunity}");
	}

	/**
	 * Returns saved accounts.
	 *
	 * @return Saved accounts.
	 */
	public JSONArray getAccounts() {
		return query("SELECT {Account:Name}, {Account:Id}, {Account:OwnerId} FROM {Account}");
	}

	/**
	 * Runs a smart SQL query against the smartstore and returns results.
	 *
	 * @param smartSql Smart SQL query string.
	 * @return Results of the query.
	 */
	public JSONArray query(String smartSql) {
		JSONArray result = null;
		QuerySpec querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 10);
		int count = smartStore.countQuery(querySpec);
		querySpec = QuerySpec.buildSmartQuerySpec(smartSql, count);
		try {
			result = smartStore.query(querySpec, 0);	
		} catch (JSONException e) {
			Log.e(TAG, "Error occurred while attempting to run query. Please verify validity of the query.");
		}
		return result;
	}
}

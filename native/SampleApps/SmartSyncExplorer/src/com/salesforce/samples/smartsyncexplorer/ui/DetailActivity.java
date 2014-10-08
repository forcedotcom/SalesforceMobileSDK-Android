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
package com.salesforce.samples.smartsyncexplorer.ui;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.manager.CacheManager.CachePolicy;
import com.salesforce.androidsdk.smartsync.manager.MetadataManager;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectLayoutColumn;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectType;
import com.salesforce.androidsdk.smartsync.model.SalesforceObjectTypeLayout;
import com.salesforce.androidsdk.ui.sfnative.SalesforceActivity;
import com.salesforce.samples.smartsyncexplorer.R;
import com.salesforce.samples.smartsyncexplorer.loaders.SObjectDetailLoader;

/**
 * Object detail activity.
 *
 * @author bhariharan
 */
public class DetailActivity extends SalesforceActivity implements LoaderManager.LoaderCallbacks<SalesforceObject> {

	private static final long REFRESH_INTERVAL = 24 * 60 * 60 * 1000;
	private static final int SOBJECT_DETAIL_LOADER_ID = 2;

    private UserAccount curAccount;
    private String objectId;
    private String objectType;
    private SalesforceObject sObject;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.detail);
		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setIcon(R.drawable.ic_action_back);
		final Intent launchIntent = getIntent();
		if (launchIntent != null) {
			objectId = launchIntent.getStringExtra(MainActivity.OBJECT_ID_KEY);
			objectType = launchIntent.getStringExtra(MainActivity.OBJECT_TYPE_KEY);
			getActionBar().setTitle(launchIntent.getStringExtra(MainActivity.OBJECT_NAME_KEY));
			getActionBar().setSubtitle(objectType);
		}
	}

	@Override
	protected void refreshIfUserSwitched() {
		// TODO: User switch. Change 'client' and reload list. Also add logout functionality.
	}

	@Override
	public void onResume(RestClient client) {
		curAccount = SmartSyncSDKManager.getInstance().getUserAccountManager().getCurrentUser();
		getLoaderManager().initLoader(SOBJECT_DETAIL_LOADER_ID, null, this).forceLoad();
	}

	@Override
	public void onDestroy() {
		getLoaderManager().destroyLoader(SOBJECT_DETAIL_LOADER_ID);
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    final MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.action_bar_menu, menu);
	    final MenuItem searchItem = menu.findItem(R.id.action_search);
	    searchItem.setVisible(false);
	    return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    	case android.R.id.home:
	    		finish();
	    		return true;
	        case R.id.action_refresh:
	        	refreshScreen();
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	public Loader<SalesforceObject> onCreateLoader(int id, Bundle args) {
		return new SObjectDetailLoader(this, curAccount, objectId, objectType);
	}

	@Override
	public void onLoadFinished(Loader<SalesforceObject> loader,
			SalesforceObject data) {
		sObject = data;
		refreshScreen();
	}

	@Override
	public void onLoaderReset(Loader<SalesforceObject> loader) {
		sObject = null;
		refreshScreen();
	}

	private void refreshScreen() {
		if (sObject != null) {
			(new Thread(new SObjectLayoutLoader(sObject, curAccount))).start();
		}
	}

	/**
	 * A simple thread that fetches object layout.
	 *
	 * @author bhariharan
	 */
	private static class SObjectLayoutLoader implements Runnable {

		private SalesforceObject object;
		private UserAccount curAccount;

		/**
		 * Parameterized constructor.
		 *
		 * @param obj Salesforce object.
		 */
		public SObjectLayoutLoader(SalesforceObject obj, UserAccount account) {
			object = obj;
			curAccount = account;
		}

		@Override
		public void run() {
			final MetadataManager metaMgr = MetadataManager.getInstance(curAccount);
			final CachePolicy cachePolicy = CachePolicy.RELOAD_IF_EXPIRED_AND_RETURN_CACHE_DATA;
			final SalesforceObjectType objType = metaMgr.loadObjectType(
					object.getObjectType(), cachePolicy, REFRESH_INTERVAL);
			final SalesforceObjectTypeLayout objLayout = metaMgr.loadObjectTypeLayout(
					objType, cachePolicy, REFRESH_INTERVAL);
			if (objLayout != null) {
				final List<SalesforceObjectLayoutColumn> layoutColumns = objLayout.getColumns();
				if (layoutColumns != null) {
					for (final SalesforceObjectLayoutColumn column : layoutColumns) {
						final String colName = column.getName();
						final String fieldName = column.getField();
						final String colLabel = column.getLabel();
						final String fieldValue = parseColumnValues(colName,
								fieldName, colLabel);
						// TODO: Display colLabel and fieldValue in TextViews here.
					}
				}
			}
		}

		private String parseColumnValues(String colName, String fieldName,
				String colLabel) {
			String fieldValue = null;
			final JSONObject rawData = object.getRawData();
			if (rawData != null) {

				// Some magic to flatten the list of fields to retrieve.
				String[] fieldsArr = fieldName.split("\\.");
				if (fieldsArr == null || fieldsArr.length == 0) {
					fieldsArr = new String[1];
					fieldsArr[0] = fieldName;
				}

				// Some more magic to sanitize the list of fields.
				final List<String> fields = new ArrayList<String>();
				for (int i = 0; i < fieldsArr.length; i++) {
					if (!object.getObjectType().equals(fieldsArr[i])) {
						fields.add(fieldsArr[i]);
					}
				}
				if (fields.size() <= 1) {
					fieldValue = rawData.optString(fields.get(0));
				} else {

					/*
					 * Some more crazy magic to parse nested attributes of
					 * objects in child relationships of the parent object.
					 */
					int j = fields.size();
					int k = 0;
					JSONObject newRawData = rawData;
					while (j > 1) {
						if (newRawData != null) {
							newRawData = newRawData.optJSONObject(fields.get(k));
							j--;
							k++;
						} else {
							break;
						}
					}
					if (newRawData != null) {
						fieldValue = newRawData.optString(fields.get(fields.size() - 1));
					}
				}
			}
			if (TextUtils.isEmpty(fieldValue) || fieldValue.equals("null")) {
				fieldValue = "";
			}
			return fieldValue;
		}
	}
}

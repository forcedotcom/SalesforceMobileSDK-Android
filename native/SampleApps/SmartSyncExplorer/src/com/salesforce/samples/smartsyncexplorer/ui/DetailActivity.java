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

import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.ui.sfnative.SalesforceActivity;
import com.salesforce.samples.smartsyncexplorer.R;
import com.salesforce.samples.smartsyncexplorer.loaders.SObjectDetailLoader;

/**
 * Object detail activity.
 *
 * @author bhariharan
 */
public class DetailActivity extends SalesforceActivity implements LoaderManager.LoaderCallbacks<SalesforceObject> {

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
	public void onPause() {
		getLoaderManager().destroyLoader(SOBJECT_DETAIL_LOADER_ID);
		super.onPause();
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
		/*
		 * TODO: Refresh screen with sObject values.
		 */
	}
}

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

import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.Toast;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.model.SalesforceObject;
import com.salesforce.androidsdk.ui.sfnative.SalesforceListActivity;
import com.salesforce.samples.smartsyncexplorer.R;
import com.salesforce.samples.smartsyncexplorer.loaders.MRUAsyncTaskLoader;

/**
 * Main activity.
 *
 * @author bhariharan
 */
public class MainActivity extends SalesforceListActivity implements
		OnQueryTextListener, OnCloseListener, LoaderManager.LoaderCallbacks<List<SalesforceObject>> {

	private static final int MRU_LOADER_ID = 1;

    private SearchView searchView;
    private MRUListAdapter listAdapter;
    private UserAccount curAccount;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		listAdapter = new MRUListAdapter(this, android.R.layout.simple_list_item_1);
		getListView().setAdapter(listAdapter);
	}

	@Override
	protected void refreshIfUserSwitched() {
		// TODO: User switch. Change 'client' and reload list.
	}

	@Override
	public void onResume(RestClient client) {
		curAccount = SmartSyncSDKManager.getInstance().getUserAccountManager().getCurrentUser();
		getLoaderManager().initLoader(MRU_LOADER_ID, null, this).forceLoad();
    }

	@Override
	public void onPause() {
		getLoaderManager().destroyLoader(MRU_LOADER_ID);
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    final MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.action_bar_menu, menu);
	    final MenuItem searchItem = menu.findItem(R.id.action_search);
	    searchView = new SalesforceSearchView(this);
        searchView.setOnQueryTextListener(this);
        searchView.setOnCloseListener(this);
        searchItem.setActionView(searchView);
	    return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case R.id.action_refresh:
	            refreshList(null); // TODO:
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	public boolean onQueryTextChange(String newText) {
        // TODO: search.
		return true;
    }

	@Override
	public Loader<List<SalesforceObject>> onCreateLoader(int id, Bundle args) {
		return new MRUAsyncTaskLoader(this, curAccount, "");
	}

	@Override
	public void onLoaderReset(Loader<List<SalesforceObject>> loader) {
		listAdapter.setData(new ArrayList<SalesforceObject>());
	}

	@Override
	public void onLoadFinished(Loader<List<SalesforceObject>> loader,
			List<SalesforceObject> data) {
		refreshList(data);
	}

	@Override
	public boolean onClose() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final SalesforceObject sObject = listAdapter.getItem(position);
	    Toast.makeText(this, sObject.getName() + " selected", Toast.LENGTH_LONG).show();
	}

	private void refreshList(List<SalesforceObject> data) {
		listAdapter.setData(data);
	}
	
	/**
	 * Custom search view that clears the search term when dismissed.
	 *
	 * @author bhariharan
	 */
	public static class SalesforceSearchView extends SearchView {

        public SalesforceSearchView(Context context) {
            super(context);
        }

        @Override
        public void onActionViewCollapsed() {
            setQuery("", false);
            super.onActionViewCollapsed();
        }
    }

	/**
	 * Custom array adapter to supply data to the list view.
	 *
	 * @author bhariharan
	 */
	public static class MRUListAdapter extends ArrayAdapter<SalesforceObject> {

		/**
		 * Parameterized constructor.
		 *
		 * @param context Context.
		 * @param textViewResourceId Text view resource ID.
		 */
		public MRUListAdapter(Context context, int textViewResourceId) {
			super(context, textViewResourceId);
		}

		/**
		 * Sets data to this adapter.
		 *
		 * @param data Data.
		 */
		public void setData(List<SalesforceObject> data) {
			if (data != null) {
				clear();
				addAll(data);
				notifyDataSetChanged();
			}
		}
	}
}

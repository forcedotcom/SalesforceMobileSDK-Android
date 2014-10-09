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

import org.json.JSONException;
import org.json.JSONObject;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.manager.MetadataManager;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.manager.SyncManager.Status;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.ui.sfnative.SalesforceListActivity;
import com.salesforce.samples.smartsyncexplorer.R;
import com.salesforce.samples.smartsyncexplorer.loaders.ContactListLoader;
import com.salesforce.samples.smartsyncexplorer.objects.ContactObject;

/**
 * Main activity.
 *
 * @author bhariharan
 */
public class MainActivity extends SalesforceListActivity implements
		OnQueryTextListener, OnCloseListener, LoaderManager.LoaderCallbacks<List<ContactObject>> {

	public static final String OBJECT_ID_KEY = "object_id";
	public static final String OBJECT_TITLE_KEY = "object_title";
	public static final String OBJECT_NAME_KEY = "object_name";
    private static final String TAG = "SmartSyncExplorer: MainActivity";
	private static final int CONTACT_LOADER_ID = 1;
	private static IndexSpec[] CONTACTS_INDEX_SPEC = {
		new IndexSpec("Id", Type.string),
		new IndexSpec("Name", Type.string),
		new IndexSpec("FirstName", Type.string),
		new IndexSpec("LastName", Type.string),
		new IndexSpec("Title", Type.string),
		new IndexSpec("Phone", Type.string),
		new IndexSpec("Email", Type.string),
		new IndexSpec("Department", Type.string),
		new IndexSpec("HomePhone", Type.string)
	};

    private SearchView searchView;
    private MRUListAdapter listAdapter;
    private UserAccount curAccount;
	private NameFieldFilter nameFilter;
	private List<ContactObject> originalData;
	private SyncReceiver syncReceiver;
	private SyncManager syncMgr;
	private SmartStore smartStore;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		getActionBar().setTitle(R.string.main_activity_title);
		listAdapter = new MRUListAdapter(this, R.layout.list_item);
		getListView().setAdapter(listAdapter);
		nameFilter = new NameFieldFilter(listAdapter, originalData);
		syncReceiver = new SyncReceiver();
		registerReceiver(syncReceiver, new IntentFilter(SyncManager.SYNC_INTENT_ACTION));
	}

	@Override
	protected void refreshIfUserSwitched() {
		// TODO: User switch. Change 'client' and reload list. Also add logout functionality.
	}

	@Override
	public void onResume(RestClient client) {
		curAccount = SmartSyncSDKManager.getInstance().getUserAccountManager().getCurrentUser();
		syncMgr = SyncManager.getInstance(curAccount);
		smartStore = SmartSyncSDKManager.getInstance().getSmartStore(curAccount);
		syncDownContacts();
		getLoaderManager().initLoader(CONTACT_LOADER_ID, null, this);
    }

	@Override
	public void onDestroy() {
		unregisterReceiver(syncReceiver);
		getLoaderManager().destroyLoader(CONTACT_LOADER_ID);
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    final MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.action_bar_menu, menu);
	    final MenuItem searchItem = menu.findItem(R.id.action_search);
	    searchView = new SearchView(this);
	    searchView.setOnQueryTextListener(this);
        searchView.setOnCloseListener(this);
        searchItem.setActionView(searchView);
	    return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case R.id.action_refresh:
	        	refreshList(originalData);
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	public Loader<List<ContactObject>> onCreateLoader(int id, Bundle args) {
		return new ContactListLoader(this, curAccount);
	}

	@Override
	public void onLoaderReset(Loader<List<ContactObject>> loader) {
		originalData = null;
		refreshList(null);
	}

	@Override
	public void onLoadFinished(Loader<List<ContactObject>> loader,
			List<ContactObject> data) {
		originalData = data;
		nameFilter.setOrigData(originalData);
		refreshList(data);
	}

	@Override
	public boolean onClose() {
		refreshList(originalData);
		return true;
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		filterList(query);
		return true;
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		filterList(newText);
		return true;
    }

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final ContactObject sObject = listAdapter.getItem(position);
		final Intent detailIntent = new Intent(this, DetailActivity.class);
		detailIntent.addCategory(Intent.CATEGORY_DEFAULT);
		detailIntent.putExtra(OBJECT_ID_KEY, sObject.getObjectId());
		detailIntent.putExtra(OBJECT_TITLE_KEY, sObject.getTitle());
		detailIntent.putExtra(OBJECT_NAME_KEY, sObject.getName());
		startActivity(detailIntent);
	}

	private void refreshList(List<ContactObject> data) {
		listAdapter.setData(data);
	}

	private void filterList(String filterTerm) {
		nameFilter.filter(filterTerm);
	}

	private void syncDownContacts() {
		if (!smartStore.hasSoup(ContactListLoader.CONTACT_SOUP)) {
			smartStore.registerSoup(ContactListLoader.CONTACT_SOUP,
					CONTACTS_INDEX_SPEC);
		}
		try {
			final JSONObject target = new JSONObject();
			target.put(SyncManager.QUERY_TYPE, SyncManager.QueryType.soql);
			final String soqlQuery = SOQLBuilder.getInstanceWithFields(ContactObject.CONTACT_FIELDS)
					.from(Constants.CONTACT).limit(ContactListLoader.LIMIT).build();
			target.put(SyncManager.QUERY, soqlQuery);
			final JSONObject sync = syncMgr.recordSync(SyncManager.Type.syncDown,
					target, ContactListLoader.CONTACT_SOUP, null);
			long syncId = sync.getLong(SmartStore.SOUP_ENTRY_ID);
			syncMgr.runSync(syncId);
		} catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while parsing", e);
		}
	}

	/**
	 * Custom array adapter to supply data to the list view.
	 *
	 * @author bhariharan
	 */
	private static class MRUListAdapter extends ArrayAdapter<ContactObject> {

		private int listItemLayoutId;
		private List<ContactObject> sObjects;

		/**
		 * Parameterized constructor.
		 *
		 * @param context Context.
		 * @param listItemLayoutId List item view resource ID.
		 */
		public MRUListAdapter(Context context, int listItemLayoutId) {
			super(context, listItemLayoutId);
			this.listItemLayoutId = listItemLayoutId;
		}

		/**
		 * Sets data to this adapter.
		 *
		 * @param data Data.
		 */
		public void setData(List<ContactObject> data) {
			clear();
			sObjects = data;
			if (data != null) {
				addAll(data);
				notifyDataSetChanged();
			}
		}

		@Override
		public View getView (int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(listItemLayoutId, null);
		    }
			if (sObjects != null) {
				final ContactObject sObject = sObjects.get(position);
				if (sObject != null) {
			        final TextView objName = (TextView) convertView.findViewById(R.id.obj_name);
			        final TextView objType = (TextView) convertView.findViewById(R.id.obj_type);
					final ImageView objImage = (ImageView) convertView.findViewById(R.id.obj_image);
			        if (objName != null) {
			        	objName.setText(sObject.getName());
			        	objName.setTextColor(Color.GREEN);
			        }
			        if (objType != null) {
			        	objType.setText(sObject.getTitle());
			        	objType.setTextColor(Color.RED);
			        }
			        if (objImage != null) {
			    		final MetadataManager metadataMgr = MetadataManager.getInstance(
			    				SmartSyncSDKManager.getInstance().getUserAccountManager().getCurrentUser());
			    		if (metadataMgr != null) {
			    			int color = metadataMgr.getColorResourceForObjectType(sObject.getObjectType());
			    			objImage.setImageResource(color);
			    		}
			        }
				}
			}
		    return convertView;
		}
	}

	/**
	 * A simple utility class to implement filtering.
	 *
	 * @author bhariharan
	 */
	private static class NameFieldFilter extends Filter {

		private MRUListAdapter adpater;
		private List<ContactObject> origList;

		/**
		 * Parameterized constructor.
		 *
		 * @param adapter List adapter.
		 * @param origList List to perform filtering against.
		 */
		public NameFieldFilter(MRUListAdapter adapter, List<ContactObject> origList) {
			this.adpater = adapter;
			this.origList = origList;
		}

		/**
		 * Sets the original data set.
		 *
		 * @param origData Original data set.
		 */
		public void setOrigData(List<ContactObject> origData) {
			origList = origData;
		}

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			if (origList == null) {
				return null;
			}
			final FilterResults results = new FilterResults();
			if (TextUtils.isEmpty(constraint)) {
				results.values = origList;
				results.count = origList.size();
				return results;
			}
			final String filterString = constraint.toString().toLowerCase();
			int count = origList.size();
			String filterableString;
			final List<ContactObject> resultSet = new ArrayList<ContactObject>();
			for (int i = 0; i < count; i++) {
				filterableString = origList.get(i).getName();
				if (filterableString.toLowerCase().contains(filterString)) {
					resultSet.add(origList.get(i));
				}
			}
			results.values = resultSet;
			results.count = resultSet.size();
			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results) {
			if (results != null && results.values != null) {
				adpater.setData((List<ContactObject>) results.values);
			}
		}
	}

	/**
	 * A simple receiver for the sync completed event.
	 *
	 * @author bhariharan
	 */
	private class SyncReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent != null) {
				final String action = intent.getAction();
				if (action != null && action.equals(SyncManager.SYNC_INTENT_ACTION)) {
					final String syncStatus = intent.getStringExtra(SyncManager.SYNC_STATUS);
					if (syncStatus != null && syncStatus.equals(Status.DONE.name())) {
						getLoaderManager().getLoader(CONTACT_LOADER_ID).forceLoad();
					}
				}
			}
		}
	}
}

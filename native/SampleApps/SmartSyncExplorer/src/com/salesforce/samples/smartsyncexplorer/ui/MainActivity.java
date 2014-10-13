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
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.Toast;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.smartstore.store.IndexSpec;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;
import com.salesforce.androidsdk.smartsync.util.SyncTarget;
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
		new IndexSpec("FirstName", Type.string),
		new IndexSpec("LastName", Type.string),
		new IndexSpec(SyncManager.LOCALLY_CREATED, Type.string),
		new IndexSpec(SyncManager.LOCALLY_UPDATED, Type.string),
		new IndexSpec(SyncManager.LOCALLY_DELETED, Type.string),
		new IndexSpec(SyncManager.LOCAL, Type.string)
	};
	private static final int CONTACT_COLORS[] = {
		Color.rgb(26, 188, 156),
		Color.rgb(46, 204, 113),
		Color.rgb(52, 152, 219),
		Color.rgb(155, 89, 182),
		Color.rgb(52, 73, 94),
		Color.rgb(22, 160, 133),
		Color.rgb(39, 174, 96),
		Color.rgb(41, 128, 185),
		Color.rgb(142, 68, 173),
		Color.rgb(44, 62, 80),
		Color.rgb(241, 196, 15),
		Color.rgb(230, 126, 34),
		Color.rgb(231, 76, 60),
		Color.rgb(149, 165, 166),
		Color.rgb(243, 156, 18),
		Color.rgb(211, 84, 0),
		Color.rgb(192, 57, 43),
		Color.rgb(189, 195, 199),
		Color.rgb(127, 140, 141)
	};
	
    private SearchView searchView;
    private ContactListAdapter listAdapter;
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
		listAdapter = new ContactListAdapter(this, R.layout.list_item);
		getListView().setAdapter(listAdapter);
		nameFilter = new NameFieldFilter(listAdapter, originalData);
		syncReceiver = new SyncReceiver();
		registerReceiver(syncReceiver, new IntentFilter(SyncManager.SYNC_INTENT_ACTION));
	}

	@Override
	public void onResume(RestClient client) {
		curAccount = SmartSyncSDKManager.getInstance().getUserAccountManager().getCurrentUser();
		syncMgr = SyncManager.getInstance(curAccount);
		smartStore = SmartSyncSDKManager.getInstance().getSmartStore(curAccount);
		getLoaderManager().initLoader(CONTACT_LOADER_ID, null, this);
		syncDownContacts();
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
				Toast.makeText(this, "Synchronizing...", Toast.LENGTH_SHORT).show();
				syncUpContacts();
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
			try {
				final String soqlQuery = SOQLBuilder.getInstanceWithFields(ContactObject.CONTACT_FIELDS)
						.from(Constants.CONTACT).limit(ContactListLoader.LIMIT).build();
				final SyncTarget target = SyncTarget.targetForSOQLSyncDown(soqlQuery);
				syncMgr.syncDown(target, ContactListLoader.CONTACT_SOUP);
			} catch (JSONException e) {
	            Log.e(TAG, "JSONException occurred while parsing", e);
			}
		} else {
			getLoaderManager().getLoader(CONTACT_LOADER_ID).forceLoad();
		}
	}

	private void syncUpContacts() {
		final SyncOptions options = SyncOptions.optionsForSyncUp(Arrays.asList(ContactObject.CONTACT_FIELDS));
		try {
			syncMgr.syncUp(options, ContactListLoader.CONTACT_SOUP);
		} catch (JSONException e) {
            Log.e(TAG, "JSONException occurred while parsing", e);
		}
	}

	/**
	 * Custom array adapter to supply data to the list view.
	 *
	 * @author bhariharan
	 */
	private static class ContactListAdapter extends ArrayAdapter<ContactObject> {

		private int listItemLayoutId;
		private List<ContactObject> sObjects;

		/**
		 * Parameterized constructor.
		 *
		 * @param context Context.
		 * @param listItemLayoutId List item view resource ID.
		 */
		public ContactListAdapter(Context context, int listItemLayoutId) {
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
					final TextView objImage = (TextView) convertView.findViewById(R.id.obj_image);
			        if (objName != null) {
			        	objName.setText(sObject.getName());
			        }
			        if (objType != null) {
			        	objType.setText(sObject.getTitle());
			        }
			        if (objImage != null) {
			        	final String firstName = sObject.getFirstName();
			        	String initials = Constants.EMPTY_STRING;
			        	if (firstName.length() > 0) {
			        		initials = firstName.substring(0, 1);
			        	}
			        	objImage.setText(initials);
			        	setBubbleColor(objImage, firstName);
			        }
			        final ImageView syncImage = (ImageView) convertView.findViewById(R.id.sync_status_view);
			        if (syncImage != null && sObject.isLocallyModified()) {
			        	syncImage.setImageResource(R.drawable.sync_local);
			        } else {
			        	syncImage.setImageResource(R.drawable.sync_success);
			        }
				}
			}
		    return convertView;
		}

		private void setBubbleColor(TextView tv, String firstName) {
			firstName = firstName.trim();
			int code = 0;
			if (!TextUtils.isEmpty(firstName)) {
				for (int i = 0; i < firstName.length(); i++) {
					code += firstName.charAt(i);
				}
			}
			int colorIndex = code % CONTACT_COLORS.length;
			int color = CONTACT_COLORS[colorIndex];
			final GradientDrawable drawable = new GradientDrawable();
			drawable.setColor(color);
			drawable.setShape(GradientDrawable.OVAL);
			tv.setBackground(drawable);
		}
	}

	/**
	 * A simple utility class to implement filtering.
	 *
	 * @author bhariharan
	 */
	private static class NameFieldFilter extends Filter {

		private ContactListAdapter adpater;
		private List<ContactObject> origList;

		/**
		 * Parameterized constructor.
		 *
		 * @param adapter List adapter.
		 * @param origList List to perform filtering against.
		 */
		public NameFieldFilter(ContactListAdapter adapter, List<ContactObject> origList) {
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
					final String syncStatus = intent.getStringExtra(SyncState.SYNC_STATUS);
					final String syncType = intent.getStringExtra(SyncState.SYNC_TYPE);
					if (syncStatus != null && syncStatus.equals(SyncState.Status.DONE.name())) {
						if (syncType != null) {
							if (syncType.equals(com.salesforce.androidsdk.smartsync.util.SyncState.Type.syncDown)) {
								Toast.makeText(MainActivity.this,
										"Sync down successful!",
										Toast.LENGTH_LONG).show();
							} else if (syncType.equals(com.salesforce.androidsdk.smartsync.util.SyncState.Type.syncUp)) {
								Toast.makeText(MainActivity.this,
										"Sync up successful!",
										Toast.LENGTH_LONG).show();
							}
						}
						getLoaderManager().getLoader(CONTACT_LOADER_ID).forceLoad();
					}
				}
			}
		}
	}
}

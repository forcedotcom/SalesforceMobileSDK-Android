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
package com.salesforce.samples.mobilesyncexplorer.ui;

import static android.view.LayoutInflater.from;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager;
import com.salesforce.androidsdk.mobilesync.util.Constants;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity;
import com.salesforce.androidsdk.ui.SalesforceActivity;
import com.salesforce.samples.mobilesyncexplorer.R;
import com.salesforce.samples.mobilesyncexplorer.loaders.ContactListLoader;
import com.salesforce.samples.mobilesyncexplorer.objects.ContactObject;
import com.salesforce.samples.mobilesyncexplorer.sync.ContactSyncAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main activity.
 *
 * @author bhariharan
 */
public class MainActivity extends SalesforceActivity implements
		OnQueryTextListener,
		LoaderManager.LoaderCallbacks<List<ContactObject>> {

	public static final String OBJECT_ID_KEY = "object_id";
	public static final String OBJECT_TITLE_KEY = "object_title";
	public static final String OBJECT_NAME_KEY = "object_name";
	private static final String SYNC_CONTENT_AUTHORITY = "com.salesforce.samples.mobilesyncexplorer.sync.contactsyncadapter";
	private static final long SYNC_FREQUENCY_ONE_HOUR = 60 * 60;
	private static final int CONTACT_LOADER_ID = 1;
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

    private ContactListAdapter listAdapter;
	private NameFieldFilter nameFilter;
    private LogoutDialogFragment logoutConfirmationDialog;
    private ContactListLoader contactLoader;
    private LoadCompleteReceiver loadCompleteReceiver;
    private AtomicBoolean isRegistered;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
		setTheme(isDarkTheme ? R.style.SalesforceSDK_Dark : R.style.SalesforceSDK);
		// This makes the navigation bar visible on light themes.
		SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);

		setContentView(R.layout.main);
		getSupportActionBar().setTitle(R.string.main_activity_title);

		RecyclerView recyclerView = ((RecyclerView)findViewById(R.id.recycler_view));
		listAdapter = new ContactListAdapter(
				R.layout.list_item,
				this::onListItemClick);
		LinearLayoutManager layoutManager = new LinearLayoutManager(this);
		DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(
				this,
				layoutManager.getOrientation()
		);
		recyclerView.addItemDecoration(dividerItemDecoration);
		recyclerView.setAdapter(listAdapter);
		recyclerView.setLayoutManager(layoutManager);

		nameFilter = new NameFieldFilter(listAdapter, null);
		logoutConfirmationDialog = new LogoutDialogFragment();
		loadCompleteReceiver = new LoadCompleteReceiver();
		isRegistered = new AtomicBoolean(false);
	}

	@Override
	public void onLogoutComplete() {
		super.onLogoutComplete();
		// If refresh token is revoked - ClientManager does a logout that doesn't finish top activity activity or show login
		if (!isChild()) {
            recreate();
        }
	}

	@Override
	public void onResume(RestClient client) {
		// Loader initialization and receiver registration
		getLoaderManager().initLoader(CONTACT_LOADER_ID, null, this);
		if (!isRegistered.get()) {
			registerReceiver(loadCompleteReceiver,
					new IntentFilter(ContactListLoader.LOAD_COMPLETE_INTENT_ACTION));
		}
		isRegistered.set(true);

		// Setup periodic sync
		setupPeriodicSync();

		// Sync now
		requestSync(true /* sync down only */);
	}

	/**
	 * Setup periodic sync
	 */
	private void setupPeriodicSync() {
		Account account = MobileSyncSDKManager.getInstance().getUserAccountManager().getCurrentAccount();
		/*
		 * Enables sync automatically for this provider. To enable almost
		 * instantaneous sync when records are modified locally, a call needs
		 * to be made by the content provider to notify the sync provider
		 * that the underlying data set has changed. Since we don't use cursors
		 * in this sample application, we simply enable periodic sync every hour.
		 */
		ContentResolver.setSyncAutomatically(account, SYNC_CONTENT_AUTHORITY, true);
		ContentResolver.addPeriodicSync(account, SYNC_CONTENT_AUTHORITY,
				Bundle.EMPTY, SYNC_FREQUENCY_ONE_HOUR);
	}

	/**
	 * Request a sync
	 * @param syncDownOnly if true, only a sync down is done, if false a sync up followed by a sync down is done
	 */
	private void requestSync(boolean syncDownOnly) {
		Account account = MobileSyncSDKManager.getInstance().getUserAccountManager().getCurrentAccount();
		Bundle extras = new Bundle();
		extras.putBoolean(ContactSyncAdapter.SYNC_DOWN_ONLY, syncDownOnly);
		ContentResolver.requestSync(account, SYNC_CONTENT_AUTHORITY, extras);
	}

	@Override
	public void onPause() {
    	if (isRegistered.get()) {
        	unregisterReceiver(loadCompleteReceiver);
    	}
    	isRegistered.set(false);
    	getLoaderManager().destroyLoader(CONTACT_LOADER_ID);
		contactLoader = null;
		super.onPause();
	}

    @Override
    public void onDestroy() {
    	loadCompleteReceiver = null;
    	super.onDestroy();
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    final MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.action_bar_menu, menu);
	    final MenuItem searchItem = menu.findItem(R.id.action_search);
		final SearchView searchView = new SearchView(this);
	    searchView.setOnQueryTextListener(this);
		MenuItem searchViewItem = menu.findItem(R.id.action_search);
		searchViewItem.setOnActionExpandListener(new OnActionExpandListener() {
			@Override
			public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
				return true;
			}

			@Override
			public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
				MainActivity.this.onQueryTextChange("");
				return true;
			}
		});
		searchItem.setActionView(searchView);
	    return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case R.id.action_refresh:
				Toast.makeText(this, "Synchronizing...", Toast.LENGTH_SHORT).show();
				requestSync(false /* sync up + sync down */);
	            return true;
	        case R.id.action_logout:
	    		logoutConfirmationDialog.show(getSupportFragmentManager(), "LogoutDialog");
	            return true;
			case R.id.action_switch_user:
				launchAccountSwitcherActivity();
				return true;
			case R.id.action_inspect_db:
				launchSmartStoreInspectorActivity();
				return true;
	        case R.id.action_add:
	        	launchDetailActivity(Constants.EMPTY_STRING, "New Contact",
	        			Constants.EMPTY_STRING);
				return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	private void launchSmartStoreInspectorActivity() {
		this.startActivity(SmartStoreInspectorActivity.getIntent(this, false, null));
	}

	private void launchAccountSwitcherActivity() {
		final Intent i = new Intent(this, SalesforceSDKManager.getInstance().getAccountSwitcherActivityClass());
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		this.startActivity(i);
	}

	@Override
	public Loader<List<ContactObject>> onCreateLoader(int id, Bundle args) {
		contactLoader = new ContactListLoader(this, MobileSyncSDKManager.getInstance().getUserAccountManager().getCurrentUser());
		return contactLoader;
	}

	@Override
	public void onLoaderReset(Loader<List<ContactObject>> loader) {
		refreshList(null);
	}

	@Override
	public void onLoadFinished(Loader<List<ContactObject>> loader,
			List<ContactObject> data) {
		refreshList(data);
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		nameFilter.setFilterTerm(query);
		return true;
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		nameFilter.setFilterTerm(newText);
		return true;
    }

	protected void onListItemClick(ContactObject contact) {
		launchDetailActivity(
				contact.getObjectId(),
				contact.getName(),
				contact.getTitle());
	}

    private void refreshList() {
        getLoaderManager().getLoader(CONTACT_LOADER_ID).forceLoad();
    }

	private void refreshList(List<ContactObject> data) {
		// NB: We feed the data to nameFilter, and in turns it feeds the (filtered) data to listAdapter
		nameFilter.setData(data);
	}

	private void launchDetailActivity(String objId, String objName,
			String objTitle) {
		final Intent detailIntent = new Intent(this, DetailActivity.class);
		detailIntent.addCategory(Intent.CATEGORY_DEFAULT);
		detailIntent.putExtra(OBJECT_ID_KEY, objId);
		detailIntent.putExtra(OBJECT_TITLE_KEY, objTitle);
		detailIntent.putExtra(OBJECT_NAME_KEY, objName);
		startActivity(detailIntent);
	}

	private void filterList(String filterTerm) {
		nameFilter.filter(filterTerm);
	}

	/**
	 * Custom recycler view adapter to supply data to the recycler view.
	 *
	 * @author bhariharan
	 */
	private static class ContactListAdapter extends Adapter<ContactListAdapter.ContactViewHolder> {

		private int listItemLayoutId;
		private List<ContactObject> sObjects;
		private final OnItemClickedListener onClickListener;

		/**
		 * Parameterized constructor.
		 *
		 * @param listItemLayoutId List item view resource ID.
		 */
		public ContactListAdapter(
				int listItemLayoutId,
				OnItemClickedListener onClickListener) {
			super();

			this.listItemLayoutId = listItemLayoutId;
			this.onClickListener = onClickListener;
		}

		/**
		 * Sets data to this adapter.
		 *
		 * @param data Data.
		 */
		@SuppressLint("NotifyDataSetChanged")
		public void setData(List<ContactObject> data) {
			sObjects = data;
			notifyDataSetChanged();
		}

		@Override
		public void onBindViewHolder(@NonNull ContactViewHolder holder,
									 int position) {

			final ContactObject sObject = sObjects.get(position);
			View itemView = holder.itemView;

			itemView.setOnClickListener(v -> onClickListener.itemClicked(sObject));

			if (sObject != null) {
				final TextView objName = (TextView) itemView.findViewById(R.id.obj_name);
				final TextView objType = (TextView) itemView.findViewById(R.id.obj_type);
				final TextView objImage = (TextView) itemView.findViewById(R.id.obj_image);
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
				final ImageView syncImage = itemView.findViewById(R.id.sync_status_view);
				if (syncImage != null && sObject.isLocallyModified()) {
					syncImage.setImageResource(R.drawable.sync_local);
				} else {
					syncImage.setImageResource(R.drawable.sync_save);
				}
			}
		}

		@NonNull
		@Override
		public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
													int viewType) {
			return new ContactViewHolder(
					from(parent.getContext()).inflate(
							listItemLayoutId,
							null));
		}

		@Override
		public int getItemCount() {
			return sObjects == null ? 0 : sObjects.size();
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

		static class ContactViewHolder extends ViewHolder {

			public ContactViewHolder(@NonNull View itemView) {
				super(itemView);
			}
		}

		interface OnItemClickedListener {

			void itemClicked(ContactObject contact);
		}
	}

	/**
	 * A simple utility class to implement filtering.
	 *
	 * @author bhariharan
	 */
	private class NameFieldFilter extends Filter {

		private ContactListAdapter adpater;
		private List<ContactObject> data;
		private String filterTerm;

		/**
		 * Parameterized constructor.
		 *
		 * @param adapter List adapter.
		 * @param origList List to perform filtering against.
		 */
		public NameFieldFilter(ContactListAdapter adapter, List<ContactObject> origList) {
			this.adpater = adapter;
			this.data = origList;
			this.filterTerm = null;
		}

		/**
		 * Sets the original data set.
		 *
		 * @param data Original data set.
		 */
		public void setData(List<ContactObject> data) {
			this.data = data;
			filter(filterTerm);
		}

		/**
		 * Sets the filter term
		 * @param filterTerm
		 * @return
		 */
		public void setFilterTerm(String filterTerm) {
			this.filterTerm = filterTerm;
			filter(filterTerm);
		}

		@Override
		protected FilterResults performFiltering(CharSequence constraint) {
			if (data == null) {
				return null;
			}
			final FilterResults results = new FilterResults();
			if (TextUtils.isEmpty(constraint)) {
				results.values = data;
				results.count = data.size();
				return results;
			}
			final String filterString = constraint.toString().toLowerCase();
			int count = data.size();
			String filterableString;
			final List<ContactObject> resultSet = new ArrayList<ContactObject>();
			for (int i = 0; i < count; i++) {
				filterableString = data.get(i).getName();
				if (filterableString.toLowerCase().contains(filterString)) {
					resultSet.add(data.get(i));
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
				MainActivity.this.findViewById(R.id.empty).setVisibility(results.count > 0 ? View.GONE : View.VISIBLE);
				adpater.setData((List<ContactObject>) results.values);
			}
		}
	}

	/**
	 * A simple receiver for load complete events.
	 *
	 * @author bhariharan
	 */
	private class LoadCompleteReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent != null) {
				final String action = intent.getAction();
				if (ContactListLoader.LOAD_COMPLETE_INTENT_ACTION.equals(action)) {
			        refreshList();
				}
			}
		}
	}
}

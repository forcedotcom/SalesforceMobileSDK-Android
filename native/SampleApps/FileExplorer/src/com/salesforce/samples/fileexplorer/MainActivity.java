/*
 * Copyright (c) 2012, salesforce.com, inc.
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
package com.salesforce.samples.fileexplorer;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue.RequestFilter;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageLoader.ImageCache;
import com.android.volley.toolbox.NetworkImageView;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestClient.AsyncRequestCallback;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.rest.files.FileRequests;
import com.salesforce.androidsdk.rest.files.RenditionType;
import com.salesforce.androidsdk.ui.sfnative.SalesforceActivity;

/**
 * Main activity for the FileExplorer application
 */
public class MainActivity extends SalesforceActivity {

    private RestClient client;
    private ListItemAdapter listAdapter;
	private ImageLoader imageLoader;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Setup view
		setContentView(R.layout.main);
	}
	
	@Override 
	public void onResume() {
		// Hide everything until we are logged in
		findViewById(R.id.root).setVisibility(View.INVISIBLE);

		// Create list adapter
		listAdapter = new ListItemAdapter(this, new ArrayList<FileInfo>());
		((ListView) findViewById(R.id.files_list)).setAdapter(listAdapter);				
		
		super.onResume();
	}		
	
	@Override
	public void onResume(RestClient client) {
        // Keeping reference to rest client
        this.client = client; 

		imageLoader = new ImageLoader(client.getRequestQueue(), new BitmapCache(16));
        
		// Show everything
		findViewById(R.id.root).setVisibility(View.VISIBLE);
	}

	/**
	 * Called when "Logout" button is clicked. 
	 * 
	 * @param v
	 */
	public void onLogoutClick(View v) {
		SalesforceSDKManager.getInstance().logout(this);
	}
	
	/**
	 * Called when "Clear" button is clicked. 
	 * 
	 * @param v
	 */
	public void onClearClick(View v) {
		listAdapter.clear();
	}	

	/**
	 * Called when "Cancel" button is clicked. 
	 * 
	 * @param v
	 */
	public void onCancelClick(View v) {
		CountingFilter countingFilter = new CountingFilter();
		client.getRequestQueue().cancelAll(countingFilter);
		int count = countingFilter.getCancelCount();
		if (count > 0) {
			Toast.makeText(MainActivity.this, count + " request" + (count > 1 ? "s" : "") + " cancelled", Toast.LENGTH_LONG).show();
		}
	}		
	
	/**
	 * Called when "Files In My Groups" button is clicked
	 * 
	 * @param v
	 */
	public void onFilesInGroupsClick(View v) {
		performRequest(FileRequests.filesInUsersGroups(null, null));
	}

	/**
	 * Called when "Files Owned By Me" button is clicked
	 * 
	 * @param v
	 */
	public void onFilesOwnedClick(View v) {
		performRequest(FileRequests.ownedFilesList(null, null));
	}
	
	/**
	 * Called when "Files Shared With Me" button is clicked
	 * 
	 * @param v
	 */
	public void onFilesSharedClick(View v) {
		performRequest(FileRequests.filesSharedWithUser(null, null));
	}

	/**
	 * Perform the given request
	 * @param request
	 */
	protected void performRequest(RestRequest request) {
		client.sendAsync(request, new AsyncRequestCallback() {
			@Override
			public void onSuccess(RestRequest request, RestResponse result) {
				try {
					listAdapter.clear();
					JSONArray records = result.asJSONObject().getJSONArray("files");
					if (records.length() == 0) {
						Toast.makeText(MainActivity.this, "No files found", Toast.LENGTH_LONG).show();
					}
					else {
						for (int i = 0; i < records.length(); i++) {
							listAdapter.add(new FileInfo(records.getJSONObject(i)));
						}
					}
				} catch (Exception e) {
					onError(e);
				}
			}
			
			@Override
			public void onError(Exception exception) {
                Toast.makeText(MainActivity.this,
                               MainActivity.this.getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), exception.toString()),
                               Toast.LENGTH_LONG).show();
			}
		});
	}
	
	/**
	 * Simple class to hold file details
	 *
	 */
	class FileInfo {
		private JSONObject rawData;

		public FileInfo(JSONObject rawData) {
			this.rawData = rawData;
		}
		
		public String getTitle() { 
			try {
				return rawData.getString("title");
			} catch (JSONException e) {
				e.printStackTrace();
				return "Unknown File Name"; // should never happen
			}
		}
		
		public String getOwnerName() {
			try {
				return rawData.getJSONObject("owner").getString("name");
			} catch (JSONException e) {
				e.printStackTrace();
				return "Unknown Owner"; // should never happen
			}
		}
		
		public String getThumbnailUrl() {
			try {
				RestRequest request = FileRequests.fileRendition(rawData.getString("id"), null, RenditionType.THUMB120BY90, 0);
				return client.getClientInfo().resolveUrl(request.getPath()).toString();
			} catch (JSONException e) {
				e.printStackTrace();
				return null;
			}
		}
	}
	
	/**
	 * Adapter between FileInfo's and R.layout.list_item
	 */
	class ListItemAdapter extends ArrayAdapter<FileInfo> {

		private ArrayList<FileInfo> items;

		public ListItemAdapter(Context context, ArrayList<FileInfo> items) {
			super(context, R.layout.file_list_item, items);
			this.items = items;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.file_list_item, null);
			}
			FileInfo fileInfo = items.get(position);
			if (fileInfo != null) {
				((TextView) v.findViewById(R.id.toptext)).setText(fileInfo.getTitle());
				((TextView) v.findViewById(R.id.bottomtext)).setText(fileInfo.getOwnerName());
				((NetworkImageView) v.findViewById(R.id.thumbnail)).setImageUrl(fileInfo.getThumbnailUrl(), imageLoader);
			}
			return v;
		}
	}
	
	/**
	 * Basic thumbnail cache (memory only)
	 */
	class BitmapCache extends LruCache<String, Bitmap> implements ImageCache {
	    public BitmapCache(int maxSize) {
	        super(maxSize);
	    }
	 
	    @Override
	    public Bitmap getBitmap(String url) {
	        return get(url);
	    }
	 
	    @Override
	    public void putBitmap(String url, Bitmap bitmap) {
	        put(url, bitmap);
	    }
	}
	
	/**
	 * Request filter that cancels all requests and also counts the number of requests cancelled
	 *
	 */
	class CountingFilter implements RequestFilter {

		private int count = 0;
		
		public int getCancelCount() {
			return count;
		}
		
		@Override
		public boolean apply(Request<?> request) {
			count++;
			return true;
		}
		
	}
}

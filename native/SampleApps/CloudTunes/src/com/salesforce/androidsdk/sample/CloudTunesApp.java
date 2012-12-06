package com.salesforce.androidsdk.sample;

import android.app.Activity;

import com.salesforce.androidsdk.app.ForceApp;

public class CloudTunesApp extends ForceApp {

	@Override
	public Class<? extends Activity> getMainActivityClass() {
		return AlbumListActivity.class;
	}
	
	@Override
	protected String getKey(String name) {
		return null;
	}
}
package com.salesforce.androidsdk.sample;

import android.app.Activity;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.ui.SalesforceR;

public class CloudTunesApp extends ForceApp {

	SalesforceR salesforceR = new SalesforceRImpl();
	
	@Override
	public Class<? extends Activity> getMainActivityClass() {
		return AlbumListActivity.class;
	}
	
	@Override
	public int getLockTimeoutMinutes() {
		return 0;
	}

	@Override
	protected String getKey(String name) {
		return null;
	}

	@Override
	public SalesforceR getSalesforceR() {
		return salesforceR;
	}
}
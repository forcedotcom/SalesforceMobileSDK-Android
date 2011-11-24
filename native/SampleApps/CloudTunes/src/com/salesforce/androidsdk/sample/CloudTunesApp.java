package com.salesforce.androidsdk.sample;

import android.app.Activity;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.auth.AbstractLoginActivity;
import com.salesforce.androidsdk.security.AbstractPasscodeActivity;

public class CloudTunesApp extends ForceApp {

	@Override
	public Class<? extends Activity> getMainActivityClass() {
		return AlbumListActivity.class;
	}
	
	@Override
	public Class<? extends AbstractLoginActivity> getLoginActivityClass() {
		return LoginActivity.class;
	}

	@Override
	public Class<? extends AbstractPasscodeActivity> getPasscodeActivityClass() {
		return null;
	}

	@Override
	public String getAccountType() {
		return getString(R.string.account_type);		
	}

	@Override
	public int getLockTimeoutMinutes() {
		return 10;
	}

	@Override
	protected String getKey(String name) {
		return null;
	}
}
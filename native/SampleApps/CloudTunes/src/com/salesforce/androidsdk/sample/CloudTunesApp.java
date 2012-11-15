package com.salesforce.androidsdk.sample;

import android.app.Activity;

import com.salesforce.androidsdk.app.ForceApp;
import com.salesforce.androidsdk.rest.ClientManager.LoginOptions;

public class CloudTunesApp extends ForceApp {

	@Override
	public Class<? extends Activity> getMainActivityClass() {
		return AlbumListActivity.class;
	}

	@Override
	public LoginOptions getLoginOptions() {
    	LoginOptions loginOptions = new LoginOptions(
    			null, // login host is chosen by user through the server picker 
    			ForceApp.APP.getPasscodeHash(),
    			getString(R.string.oauth_callback_url),
    			getString(R.string.oauth_client_id),
    			new String[] {"api"});
    	return loginOptions;
	}
	
	@Override
	protected String getKey(String name) {
		return null;
	}
}

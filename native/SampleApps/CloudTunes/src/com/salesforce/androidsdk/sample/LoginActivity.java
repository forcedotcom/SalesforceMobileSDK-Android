package com.salesforce.androidsdk.sample;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.salesforce.androidsdk.auth.AbstractLoginActivity;
import com.salesforce.androidsdk.auth.OAuth2;


public class LoginActivity extends AbstractLoginActivity {

	// Key for login servers properties stored in preferences
	public static final String SERVER_URL_PREFS_SETTINGS = "server_url_prefs";
	public static final String SERVER_URL_PREFS_CUSTOM_LABEL = "server_url_custom_label";
	public static final String SERVER_URL_PREFS_CUSTOM_URL = "server_url_custom_url";
	public static final String SERVER_URL_PREFS_WHICH_SERVER = "which_server_index";
	public static final String SERVER_URL_CURRENT_SELECTION = "server_url_current_string";

	// Request code when calling PickServerActivity
	public static final int PICK_SERVER_CODE = 10;

	@Override
	protected void loadLoginPage() {
		// Read login url from pref before loading web view
		SharedPreferences settings = getSharedPreferences(
				SERVER_URL_PREFS_SETTINGS, Context.MODE_PRIVATE);

		setLoginUrl(settings.getString(LoginActivity.SERVER_URL_CURRENT_SELECTION, OAuth2.DEFAULT_LOGIN_URL));
		super.loadLoginPage();
	}

	@Override 
	public void onResume() {
		super.onResume();
	}    

	/**************************************************************************************************
	 *
	 * Implementations for abstract methods of AbstractLoginActivity
	 * 
	 **************************************************************************************************/

	@Override
	protected int getLayoutId() {
		return R.layout.login;
	}

	@Override
	protected int getWebViewId() {
		return R.id.oauth_webview;
	}

	@Override
	protected void showError(Exception exception) {
		Toast.makeText(this,
				getString(R.string.generic_error, exception.toString()),
				Toast.LENGTH_LONG).show();
	}


	@Override
	protected String getGenericAuthErrorTitle() {
		return getString(R.string.generic_authentication_error_title);
	}

	@Override
	protected String getGenericAuthErrorBody() {
		return getString(R.string.generic_authentication_error);
	}

	@Override
	protected String getAccountType() {
		return getString(R.string.account_type);
	}
}

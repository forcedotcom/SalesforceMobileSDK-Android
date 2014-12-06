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
package com.salesforce.samples.appconfigurator.ui;

import java.util.List;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.RestrictionEntry;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.salesforce.samples.appconfigurator.AppConfiguratorAdminReceiver;
import com.salesforce.samples.appconfigurator.R;

/**
 * This fragment provides UI and functionality to configure target application
 * sample.
 */
public class ConfigureAppFragment extends Fragment implements View.OnClickListener {

	/**
	 * Default login host
	 */
    private static final String DEFAULT_LOGIN_HOST = "--default-app-configurator--";

	/**
     * Package name of the target app sample.
     */
    private static final String PACKAGE_NAME_TARGET_APP = "com.salesforce.samples.configuredapp";

    /**
     * Key for {@link SharedPreferences}
     */
    private static final String PREFS_KEY = "ConfigureAppFragment";

    /**
     * Key for the string restriction in target app.
     */
    private static final String CONFIGURATION_LOGIN_HOST = "login_host";


    // UI Components
    private TextView mTextStatus;
    private Button mButtonSave;
    private EditText mLoginHost;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure_app, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mTextStatus = (TextView) view.findViewById(R.id.status);
        mLoginHost = (EditText) view.findViewById(R.id.login_host);
        mButtonSave = (Button) view.findViewById(R.id.save);
        mButtonSave.setOnClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUi(getActivity());
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.save: 
            	saveLoginHost(getActivity(), mLoginHost.getText().toString());
                break;
        }
    }

	/**
     * Updates the UI components according to the current status of AppRestrictionSchema and its
     * restriction.
     *
     * @param activity The activity
     */
    private void updateUi(Activity activity) {
        PackageManager packageManager = activity.getPackageManager();
        int status = -1; // ready
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(PACKAGE_NAME_TARGET_APP, PackageManager.GET_UNINSTALLED_PACKAGES);
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) activity.getSystemService(Activity.DEVICE_POLICY_SERVICE);
            if ((info.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                // Need to reinstall the sample app
            	status = R.string.status_need_reinstall;
            }
            else if (devicePolicyManager.isApplicationHidden(AppConfiguratorAdminReceiver.getComponentName(activity), PACKAGE_NAME_TARGET_APP)) {
                // The app is installed but hidden in this profile
            	status = R.string.status_not_activated;
            }
        } catch (PackageManager.NameNotFoundException e) {
        	status = R.string.status_not_installed;
        }

        if (status < 0) {
        	mLoginHost.setText(getLoginHost(activity));
	        mTextStatus.setVisibility(View.GONE);
	        mLoginHost.setVisibility(View.VISIBLE);
	        mButtonSave.setVisibility(View.VISIBLE);
        }
        else {
        	mTextStatus.setText(status);
	        mTextStatus.setVisibility(View.VISIBLE);
	        mLoginHost.setVisibility(View.GONE);
	        mButtonSave.setVisibility(View.GONE);
        }
    }

    /**
     * Return login host from app restrictions
     * 
     * @param activity
     */
    private String getLoginHostFromAppRestrictions(Activity activity) {
        RestrictionsManager restrictionsManager =
                (RestrictionsManager) activity.getSystemService(Context.RESTRICTIONS_SERVICE);
        List<RestrictionEntry> restrictions =
                restrictionsManager.getManifestRestrictions(PACKAGE_NAME_TARGET_APP);
        if (restrictions != null) {
	        for (RestrictionEntry restriction : restrictions) {
	            if (CONFIGURATION_LOGIN_HOST.equals(restriction.getKey())) {
	                return restriction.getSelectedString();
	            }
	        }
        }
        return DEFAULT_LOGIN_HOST;
    }

    /**
     * Return login host from preferences or if not found from app restrictions
     * 
     * @param activity
     */
    private String getLoginHost(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE);
        return prefs.getString(CONFIGURATION_LOGIN_HOST, getLoginHostFromAppRestrictions(activity));
    }

    /**
     * Set login host in preferences and in application restriction
     * 
     * @param activity
     * @param loginHost
     */
    private void saveLoginHost(Activity activity, String loginHost) {
        DevicePolicyManager devicePolicyManager
                = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        Bundle restrictions = new Bundle();
        restrictions.putString(CONFIGURATION_LOGIN_HOST, loginHost);
        devicePolicyManager.setApplicationRestrictions(
                AppConfiguratorAdminReceiver.getComponentName(activity),
                PACKAGE_NAME_TARGET_APP, restrictions);
        // The profile/device owner needs to remember the current state of restrictions on its own
        activity.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
                .edit()
                .putString(CONFIGURATION_LOGIN_HOST, loginHost)
                .apply();

        Toast toast = Toast.makeText(activity, loginHost + " saved", Toast.LENGTH_SHORT);
        toast.show();
    }

}

/*
 * Copyright (c) 2014-2015, salesforce.com, inc.
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

import android.app.Activity;
import android.app.Fragment;
import android.app.admin.DevicePolicyManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.salesforce.samples.appconfigurator.AppConfiguratorAdminReceiver;
import com.salesforce.samples.appconfigurator.AppConfiguratorState;
import com.salesforce.samples.appconfigurator.R;

/**
 * This fragment provides UI and functionality to configure target application
 * sample.
 */
public class ConfigureAppFragment extends Fragment implements View.OnClickListener {

    // UI Components
    private TextView mTextStatus;
    private Button mButtonSave;
    private EditText mLoginServers;
    private EditText mLoginServersLabels;
    private EditText mRemoteAccessConsumerKey;
    private EditText mOauthRedirectURI;
    private EditText mCertAlias;
    private EditText[] mEditTexts;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_configure_app, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mTextStatus = (TextView) view.findViewById(R.id.status);
        mLoginServers = (EditText) view.findViewById(R.id.login_servers);
        mLoginServersLabels = (EditText) view.findViewById(R.id.login_servers_labels);
        mRemoteAccessConsumerKey = (EditText) view.findViewById(R.id.remote_access_consumer_key);
        mOauthRedirectURI = (EditText) view.findViewById(R.id.oauth_redirect_uri);
        mCertAlias = (EditText) view.findViewById(R.id.cert_alias);
        mButtonSave = (Button) view.findViewById(R.id.save);
        mButtonSave.setOnClickListener(this);
        mEditTexts = new EditText[] {
        		mLoginServers, mLoginServersLabels, mRemoteAccessConsumerKey,
        		mOauthRedirectURI, mCertAlias
        };
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
                AppConfiguratorState state = AppConfiguratorState.getInstance(getActivity());
                boolean isCertAuthEnabled = false;
                if (mCertAlias.getText() != null && !mCertAlias.getText().toString().trim().isEmpty()) {
                	isCertAuthEnabled = true;
                }
                state.saveConfigurations(getActivity(),
                        mLoginServers.getText().toString(),
                        mLoginServersLabels.getText().toString(),
                        mRemoteAccessConsumerKey.getText().toString(),
                        mOauthRedirectURI.getText().toString(),
                        isCertAuthEnabled,
                        mCertAlias.getText().toString());
                Toast.makeText(getActivity(), R.string.saved, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void updateUi(Activity activity) {
        AppConfiguratorState state = AppConfiguratorState.getInstance(activity);
        PackageManager packageManager = activity.getPackageManager();
        int status = -1; // ready
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(state.getTargetApp(), PackageManager.GET_UNINSTALLED_PACKAGES);
            DevicePolicyManager devicePolicyManager = (DevicePolicyManager) activity.getSystemService(Activity.DEVICE_POLICY_SERVICE);
            if ((info.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                // Need to reinstall the sample app
                status = R.string.status_need_reinstall;
            } else if (devicePolicyManager.isApplicationHidden(AppConfiguratorAdminReceiver.getComponentName(activity), state.getTargetApp())) {
                // The app is installed but hidden in this profile
                status = R.string.status_not_activated;
            }
        } catch (PackageManager.NameNotFoundException e) {
            status = R.string.status_not_installed;
        }

        if (status < 0) {
            mLoginServers.setText(state.getLoginServers());
            mLoginServersLabels.setText(state.getLoginServersLabels());
            mRemoteAccessConsumerKey.setText(state.getRemoteAccessConsumerKey());
            mOauthRedirectURI.setText(state.getOauthRedirectURI());
            mCertAlias.setText(state.getCertAlias());
            mTextStatus.setVisibility(View.GONE);
            for(EditText editText : mEditTexts) {
                editText.setVisibility(View.VISIBLE);
            }
            mButtonSave.setVisibility(View.VISIBLE);
        } else {
            mTextStatus.setText(status);
            mTextStatus.setVisibility(View.VISIBLE);
            for(EditText editText : mEditTexts) {
                editText.setVisibility(View.GONE);
            }
            mButtonSave.setVisibility(View.GONE);
        }
    }
}

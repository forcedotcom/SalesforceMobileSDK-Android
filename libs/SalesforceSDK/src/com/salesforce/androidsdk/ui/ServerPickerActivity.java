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
package com.salesforce.androidsdk.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer;
import com.salesforce.androidsdk.util.AuthConfigTask;

import java.util.List;

/**
 * This class provides UI to change the login server URL to use
 * during an OAuth flow. The user can add a number of custom servers,
 * or switch between the list of existing servers in the list.
 *
 * @author bhariharan
 */
public class ServerPickerActivity extends Activity implements AuthConfigTask.AuthConfigCallbackInterface {

    public static final String CHANGE_SERVER_INTENT = "com.salesforce.SERVER_CHANGED";

    private LoginServerManager loginServerManager;
    private boolean shouldUncheckItems = false;
    private ProgressBar progressBar;
    private String lastSavedServerURL;
    private boolean isUpdating = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
        setTheme(isDarkTheme ? R.style.SalesforceSDK_Dark : R.style.SalesforceSDK);
        // This makes the navigation bar visible on light themes.
        SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);
        loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();
        setContentView(R.layout.sf__server_picker);

        final ActionBar actionBar = getActionBar();
        actionBar.setTitle(R.string.sf__server_picker_title);
        actionBar.setDisplayHomeAsUpEnabled(true);

        progressBar = findViewById(R.id.progressBar);

        Intent i = getIntent();
        shouldUncheckItems = i.getBooleanExtra(LoginActivity.SHOULD_UNCHECK_ITEMS, false);
        lastSavedServerURL = loginServerManager.getSelectedLoginServer().url;

        final List<LoginServer> servers = loginServerManager.getLoginServers();
        ServerPickerAdapter adapter = new ServerPickerAdapter(this, R.layout.sf__server_list_item, servers, loginServerManager, shouldUncheckItems);
        ListView listView = findViewById(R.id.sf__server_list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (isUpdating) {
                return;
            }
            LoginServer selectedServer = servers.get(position);
            if (null != lastSavedServerURL && lastSavedServerURL.equals(selectedServer.url)) {
                updateDistrictSelectionStatus();
                finish();
            } else {
                loginServerManager.setSelectedLoginServer(new LoginServer(selectedServer.name,
                        selectedServer.url, selectedServer.isCustom));
                progressBar.setVisibility(View.VISIBLE);
                (new AuthConfigTask(this)).execute();
            }
            isUpdating = true;
            adapter.notifyDataSetChanged();
        });
    }

    /**
     * Sets the return value of the activity. Selection is stored in the
     * shared prefs file, AuthActivity pulls from the file or a default value.
     */
    @Override
    public void onBackPressed() {
        if (isUpdating) {
            return;
        }
        if (shouldUncheckItems) {
            Toast.makeText(this, R.string.sf__server_not_selected, Toast.LENGTH_SHORT).show();
        } else {
            finish();
        }
    }

    @Override
    public boolean onNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onAuthConfigFetched() {
        setResult(Activity.RESULT_OK, null);
        updateDistrictSelectionStatus();
        progressBar.setVisibility(View.GONE);
        final Intent changeServerIntent = new Intent(CHANGE_SERVER_INTENT);
        sendBroadcast(changeServerIntent);
        finish();
    }

    private void updateDistrictSelectionStatus() {
        final SharedPreferences sp = getSharedPreferences(LoginActivity.SERVER_SETTINGS, MODE_PRIVATE);
        final SharedPreferences.Editor ed = sp.edit();
        ed.putBoolean(LoginActivity.DISTRICT_SELECTED, true);
        ed.commit();
    }

    public void rebuildDisplay() {

    }
}

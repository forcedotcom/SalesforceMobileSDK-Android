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
package com.salesforce.androidsdk.ui;

import java.util.List;

import android.app.Activity;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RadioGroup;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.LoginServerManager;
import com.salesforce.androidsdk.config.LoginServerManager.LoginServer;

/**
 * This class provides UI to change the login server URL to use
 * during an OAuth flow. The user can add a number of custom servers,
 * or switch between the list of existing servers in the list.
 *
 * @author bhariharan
 */
public class ServerPickerActivity extends Activity implements
        android.widget.RadioGroup.OnCheckedChangeListener {

    private static final String SERVER_DIALOG_NAME = "custom_server_dialog";

    private CustomServerUrlEditor urlEditDialog;
    private SalesforceR salesforceR;
    private LoginServerManager loginServerManager;

    /**
     * Clears any custom URLs that may have been set.
     */
    private void clearCustomUrlSetting() {
    	loginServerManager.reset();
    	rebuildDisplay();
    }

    /**
     * Method called when the 'Cancel' button is clicked.
     *
     * @param v View that was clicked.
     */
    public void setCancelReturnValue(View v) {
        onBackPressed();
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
    	if (group != null) {
    		final SalesforceServerRadioButton rb = (SalesforceServerRadioButton) group.findViewById(checkedId);
    		if (rb != null) {
    			final String name = rb.getName();
    			final String url = rb.getUrl();
    			boolean isCustom = rb.isCustom();
    			loginServerManager.setSelectedLoginServer(new LoginServer(name,
    					url, isCustom));
    		}
    	}
    }

    /**
     * Called when the 'Reset' button is clicked. Clears custom URLs.
     *
     * @param v View that was clicked.
     */
    public void onResetClick(View v) {
        clearCustomUrlSetting();
    }

    /**
     * Returns the server list group ID.
     *
     * @return Server list group ID.
     */
    protected int getServerListGroupId() {
        return salesforceR.idServerListGroup();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();
        loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();
        setContentView(salesforceR.layoutServerPicker());
        final RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
        radioGroup.setOnCheckedChangeListener(this);
    	urlEditDialog = new CustomServerUrlEditor();
    	urlEditDialog.setRetainInstance(true);
    }

    @Override
    public void onResume() {
    	super.onResume();
    	rebuildDisplay();
    }

    @Override
    public void onDestroy() {
        final RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
        radioGroup.setOnCheckedChangeListener(null);
        urlEditDialog = null;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(salesforceR.menuClearCustomUrl(), menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == salesforceR.idMenuClearCustomUrl()) {
            clearCustomUrlSetting();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Sets the return value of the activity. Selection is stored in the
     * shared prefs file, AuthActivity pulls from the file or a default value.
     *
     * @param v View.
     */
    public void setPositiveReturnValue(View v) {
        setResult(Activity.RESULT_OK, null);
        finish();
    }

    /**
     * Shows the custom URL dialog.
     *
     * @param v View.
     */
    public void showCustomUrlDialog(View v) {
    	final FragmentManager fragMgr = getFragmentManager();
    	urlEditDialog.show(fragMgr, SERVER_DIALOG_NAME);
    }

    /**
     * Returns the custom URL editor dialog.
     *
     * @return Custom URL editor dialog.
     */
    public CustomServerUrlEditor getCustomServerUrlEditor() {
        return urlEditDialog;
    }

    /**
     * Sets the radio state.
     *
     * @param radioGroup RadioGroup instance.
     * @param server Login server.
     */
    private void setRadioState(RadioGroup radioGroup, LoginServer server) {
    	final SalesforceServerRadioButton rb = new SalesforceServerRadioButton(this,
    			server.name, server.url, server.isCustom);
    	radioGroup.addView(rb);
    }

    /**
     * Controls the elements in the layout based on past user choices.
     */
    protected void setupRadioButtons() {
        final RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
        final List<LoginServer> servers = loginServerManager.getLoginServers();
        if (servers != null) {
            for (final LoginServer currentServer : servers) {
                setRadioState(radioGroup, currentServer);
            }
        }
    }

    /**
     * Rebuilds the display.
     */
    public void rebuildDisplay() {
        final RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
        radioGroup.removeAllViews();
        setupRadioButtons();

    	/*
    	 * Multiple users could have selected different custom endpoints, while
    	 * logging into different orgs, which makes it difficult for us to
    	 * choose which one to check by default. Hence, we check the first server
    	 * on the list (usually production) by default.
    	 */
    	final SalesforceServerRadioButton rb = (SalesforceServerRadioButton) radioGroup.getChildAt(0);
    	if (rb != null) {
    		rb.setChecked(true);
    	}
    }
}

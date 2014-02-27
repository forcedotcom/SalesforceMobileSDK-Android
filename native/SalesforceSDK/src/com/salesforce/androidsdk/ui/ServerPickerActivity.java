/*
 * Copyright (c) 2011, salesforce.com, inc.
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

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView.BufferType;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.auth.LoginServerManager;
import com.salesforce.androidsdk.auth.LoginServerManager.LoginServer;

/**
 * UI to change the login server URL to use during an OAuth flow.
 * If the user selects one of the pre-populated servers, we save the selected
 * index and the final URL into a SharedPrefs file (SERVER_URL_PREFS_SETTINGS).
 * If the user selects a custom URL, that subsystem saves the final
 * validated non-null label and URL and saves the selected index.
 */
public class ServerPickerActivity extends Activity implements
        OnDismissListener, OnCancelListener, View.OnClickListener,
        android.widget.RadioGroup.OnCheckedChangeListener {

	/**
	 * Specifies the saved configuration for the login URL.
	 */
    private static class SavedConfig {

        public final int currentSelection;
        public final int originalStartingIndex;

        SavedConfig(int currentSelection, int originalStartingIndex) {
            this.currentSelection = currentSelection;
            this.originalStartingIndex = originalStartingIndex;
        }
    }

    private static final int SERVER_DIALOG_ID = 0;

    private int customRadioButtonId = -1;
    private int restoredConfigIndex = -1;
    private int startingIndex = -1;
    public CustomServerUrlEditor urlEditDialog;
    private SalesforceR salesforceR;
    private LoginServerManager loginServerManager;
    boolean wasEditUrlDialogCanceled = false;

    /**
     * Clears any custom URLs that may have been set.
     */
    private void clearCustomUrlSetting() {
    	loginServerManager.reset();
    	rebuildDisplay();
    }

    @Override
    public void onBackPressed() {
        setCancelReturnValue(null);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        wasEditUrlDialogCanceled = true;
    }

    /**
     * Supports a rogue radio button, which only gets hooked up
     * when there is a custom URL.
     */
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

    	/*
    	 * Some things like this work, but most don't.
    	 */
        if (checkedId < 0 || customRadioButtonId < 0
                || checkedId == customRadioButtonId) {
            return;
        }

        if (((RadioButton) group.findViewById(checkedId)).isChecked()) {

            // Turns off rogue radio button by hand (may or may not actually be checked).
            final RadioButton rb = (RadioButton) group.findViewById(customRadioButtonId);
            if (rb != null) {
                rb.setChecked(false);
            } else {
            	Log.w("ServerPickerActivity:onCheckedChanged", "Failed to find custom URL radio button.");
            }
        }
    }

    /**
     * Custom handling for rogue radio button (custom link). This handles
     * the actual click on the rogue radio button.
     */
    @Override
    public void onClick(View v) {
        final RadioButton rb = (RadioButton) v;
        if (rb.isChecked()) {
            final RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
            radioGroup.clearCheck();
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

        // Object which allows reference to resources living outside the SDK.
        salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();
        loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();
        setContentView(salesforceR.layoutServerPicker());
        final SavedConfig savedConfig = (SavedConfig) getLastNonConfigurationInstance();
        if (savedConfig == null) {

            // Saves starting state to restore on cancel.
            saveStartingState();
        } else {
            startingIndex = savedConfig.originalStartingIndex;
            restoredConfigIndex = savedConfig.currentSelection;
        }
        rebuildDisplay();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == SERVER_DIALOG_ID) {
            if (SalesforceSDKManager.isTablet()) {
                urlEditDialog = new CustomServerUrlEditor(this, findViewById(
                        salesforceR.idAuthContainer()).getLayoutParams().width);
            } else {
                urlEditDialog = new CustomServerUrlEditor(this, 0);
            }
            urlEditDialog.setOnDismissListener(this);
            urlEditDialog.setOnCancelListener(this);
            return urlEditDialog;
        }
        return super.onCreateDialog(id);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(salesforceR.menuClearCustomUrl(), menu);
        return true;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (!wasEditUrlDialogCanceled) {
            rebuildDisplay();
        }
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if(item.getItemId() == salesforceR.idMenuClearCustomUrl()) {
            clearCustomUrlSetting();
            return true;
        } else {
            return super.onMenuItemSelected(featureId, item);
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        final RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
        return new SavedConfig(radioGroup.getCheckedRadioButtonId(),
                startingIndex);
    }

    /**
     * Rebuilds the display.
     */
    private void rebuildDisplay() {
        final RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
        radioGroup.removeAllViews();
        radioGroup.clearCheck();
        radioGroup.setOnCheckedChangeListener(null);
        customRadioButtonId = -1;
        setupRadioButtons();
    }

    /**
     * Restores the starting state.
     */
    private void restoreStartingState() {
    	loginServerManager.setSelectedLoginServerByIndex(startingIndex);
    }

    /**
     * Restore the starting index, if the user cancels after setting a custom URL.
     */
    private void saveStartingState() {
        startingIndex = loginServerManager.getSelectedLoginServer().index;
    }

    /**
     * Saves the current selection state into a shared prefs file.
     */
    private void saveUrlEdits() {

    	/*
    	 * The ID is also the index, but we use 'findViewById()' to get the
    	 * actual radio button, and not 'childAtIndex()', which could be anything.
    	 */
        final RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());

        // If the selected item is the custom radio this won't return it.
        int selectedId = radioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            selectedId = customRadioButtonId;
        }
        final View selectedView = radioGroup.findViewById(selectedId);
        if (selectedView == null) {

            // This should never happen, but you know how that goes.
        	Log.w("ServerPickerActivity:saveUrlEdits",
        			"Failed to save state, could not find selected URL.");
            rebuildDisplay();
            return;
        }
        loginServerManager.setSelectedLoginServerByIndex(selectedId);
    }
    
    /**
     * Method called when the 'Cancel' or back buttons are clicked.
     *
     * @param v View that was clicked.
     */
    public void setCancelReturnValue(View v) {
        setResult(Activity.RESULT_CANCELED, null);
        restoreStartingState();
        finish();
    }

    /**
     * Sets the return value of the activity. Selection is stored in the
     * shared prefs file, AuthActivity pulls from the file or a default value.
     *
     * @param v View.
     */
    public void setPositiveReturnValue(View v) {
        saveUrlEdits();
        setResult(Activity.RESULT_OK, null);
        finish();
    }

    /**
     * The index is set as the ID of the actual radio buttons. Other views do not
     * get an explicit ID, but it means you can't get radio buttons by
     * selectedId -> childAtIndex, and have to use 'findViewById()' instead.
     *
     * @param radioGroup RadioGroup instance.
     * @param server Login server.
     */
    private void setRadioState(RadioGroup radioGroup, LoginServer server) {
    	int index = server.index;
    	boolean isCustom = server.isCustom;
    	String titleText = server.name;
    	String urlText = server.url;
        final RadioButton rb = new RadioButton(this);
        rb.setId(index);
        final SpannableStringBuilder result = new SpannableStringBuilder();
        final SpannableString titleSpan = new SpannableString(titleText);
        titleSpan.setSpan(new TextAppearanceSpan(this,
                SalesforceSDKManager.isTablet() ? salesforceR.styleTextHostName()
                : android.R.style.TextAppearance_Medium), 0, titleText.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        final SpannableString urlSpan = new SpannableString(urlText);
        urlSpan.setSpan(new TextAppearanceSpan(this,
                SalesforceSDKManager.isTablet() ? salesforceR.styleTextHostUrl()
                : android.R.style.TextAppearance_Small), 0, urlText.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.append(titleSpan);
        result.append(System.getProperty("line.separator"));
        result.append(urlSpan);
        rb.setText(result, BufferType.SPANNABLE);
        rb.setTag(urlText);
        if (isCustom) {

            // Keeps the edit link always to the right of the row.
            final LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.FILL_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.weight = 1;
            final LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            buttonParams.weight = (float) 0.75;
            final LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.FILL_PARENT);

            /*
             * Interesting side effect of this is that if the URL gets bug,
             * the runtime will shrink this guy, and it doesn't matter if
             * width/height are set.
             */
            linkParams.gravity = Gravity.CENTER_HORIZONTAL
                    | Gravity.CENTER_VERTICAL;
            final ImageView iv = new ImageView(this);
            iv.setImageResource(salesforceR.drawableEditIcon());
            iv.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    showCustomUrlDialog(null);
                }
            });
            customRadioButtonId = index;

            /* 
             * The problem here is that if the radio button is not the top level
             * child view when adding, the group does not control it
             * (check the radio group code), so we do it by hand.
             * It sort of controls it, you can select it through the group,
             * but it does not get de-selected correctly and is not
             * returned by a standard 'getSelected()' call on the group.
             */
            rb.setOnClickListener(this);
            final LinearLayout lay = new LinearLayout(this);
            lay.addView(rb, 0, buttonParams);
            lay.addView(iv, 1, linkParams);
            radioGroup.addView(lay, rowParams);
            radioGroup.setOnCheckedChangeListener(this);
        } else {
            radioGroup.addView(rb);
        }

        // Spacer line.
        final View spacerView = new View(this);
        spacerView.setBackgroundColor(0xffdcdcdc);
        LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT, 1);
        spacerView.setLayoutParams(lp);
        radioGroup.addView(spacerView);
    }

    /**
     * Controls the elements in the layout based on past user choices.
     */
    protected void setupRadioButtons() {
        final RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
        for (final LoginServer currentServer : loginServerManager.getDefaultLoginServers()) {
            setRadioState(radioGroup, currentServer);
        }

        /*
         * Custom URL is set, but may or may not be selected. UI is a radio button
         * with an edit link either way.
         */
        final View postView = findViewById(salesforceR.idShowCustomUrlEdit());
        final LoginServer customServer = loginServerManager.getCustomLoginServer();
        if (customServer != null) {
        	postView.setVisibility(View.GONE);
            setRadioState(radioGroup, customServer);
        } else {
        	postView.setVisibility(View.VISIBLE);
        }

        // Sets selection.
        int which = -1;
        if (restoredConfigIndex >= 0) {
            which = restoredConfigIndex;
            restoredConfigIndex = -1;
        } else {
            which = loginServerManager.getSelectedLoginServer().index;
        }
        radioGroup.check(which);
    }

    /**
     * Shows the custom URL dialog.
     *
     * @param v View.
     */
    public void showCustomUrlDialog(View v) {
        wasEditUrlDialogCanceled = false;
        showDialog(SERVER_DIALOG_ID);
    }
}

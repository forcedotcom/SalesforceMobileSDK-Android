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

import java.util.logging.Level;
import java.util.logging.Logger;

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
 *
 * UI to change the login server url to use during an oAuth flow
 *
 * If the user selects one of the prefabs, save the selected index and the final
 * url into SharedPrefs[SERVER_URL_PREFS_SETTINGS] If the user selects a custom
 * url, that subsystem saves the final validated not null label and url AND
 * saves the selected index.
 */
public class ServerPickerActivity extends Activity implements
        OnDismissListener, OnCancelListener, View.OnClickListener,
        android.widget.RadioGroup.OnCheckedChangeListener {

    private static class SavedConfig {
        public final int currentSelection;
        public final int originalStartingIndex;

        SavedConfig(int currentSelection, int originalStartingIndex) {
            this.currentSelection = currentSelection;
            this.originalStartingIndex = originalStartingIndex;
        }
    }

    private static final int SERVER_DIALOG_ID = 0;
    // custom url radio button has, sigh, custom event handling
    private int customRadioButtonId = -1;

    private Logger logger;
    private int restoredConfigIndex = -1;
    // used to restore radio index on a cancel (case where it gets saved by the
    // edit dialog)
    private int startingIndex = -1;

    /** who we are in the logs */
    protected final String TAG = this.getClass().getSimpleName();

    public CustomServerUrlEditor urlEditDialog;
    private SalesforceR salesforceR;
    private LoginServerManager loginServerManager;

    /**
     * hooks for the edit url dialog dismiss is positive (apply) cancel is back
     * or cancel onDimiss is still called when the dialog cancel() method is
     * invoked...
     */
    boolean wasEditUrlDialogCanceled = false;

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
     * support rogue radio button, only gets hooked when there is a custom url
     */
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {

        // right? some things like this work, but most don't
        if (checkedId < 0 || customRadioButtonId < 0
                || checkedId == customRadioButtonId) {
            return;
        }

        if (((RadioButton) group.findViewById(checkedId)).isChecked()) {

            // turn off rogue by hand (may or may not actually be checked)
            RadioButton rb = (RadioButton) group
                    .findViewById(customRadioButtonId);

            if (rb != null) {
                rb.setChecked(false);
            } else {
                logger.logp(Level.WARNING, TAG, "onCheckedChanged",
                        "Failed to find custom URL radio");
            }
        }
    }

    /**
     * custom handling for rogue radio button (custom link) actual click of the
     * rogue
     */
    @Override
    public void onClick(View v) {
        RadioButton rb = (RadioButton) v;
        if (rb.isChecked()) {
            RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
            radioGroup.clearCheck();
        }
    }

    /**
     * Called when "Reset" button is clicked.
     * Clear custom urls.
     * @param v
     */
    public void onResetClick(View v) {
        clearCustomUrlSetting();
    }

    protected int getServerListGroupId() {
        return salesforceR.idServerListGroup();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Object which allows reference to resources living outside the SDK
        salesforceR = SalesforceSDKManager.getInstance().getSalesforceR();
        
        loginServerManager = SalesforceSDKManager.getInstance().getLoginServerManager();

        setContentView(salesforceR.layoutServerPicker());

        SavedConfig savedConfig = (SavedConfig) getLastNonConfigurationInstance();

        if (null == savedConfig) {
            // save starting state to restore on cancel.
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
        }
        else {
            return super.onMenuItemSelected(featureId, item);
        }
    }

    /**
     * this is called by the OS before it kills & restarts the activity due to
     * device rotation, we save our state here, and recreate it in create, see
     * http
     * ://developer.android.com/resources/articles/faster-screen-orientation-
     * change.html
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
        return new SavedConfig(radioGroup.getCheckedRadioButtonId(),
                startingIndex);
    }

    private void rebuildDisplay() {
        RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());
        radioGroup.removeAllViews();
        radioGroup.clearCheck();

        radioGroup.setOnCheckedChangeListener(null);
        customRadioButtonId = -1;

        setupRadioButtons();
    }

    private void restoreStartingState() {
    	loginServerManager.setSelectedLoginServerByIndex(startingIndex);
    }

    /**
     * restore the starting index if the user cancels after setting a custom url
     */
    private void saveStartingState() {
        startingIndex = loginServerManager.getSelectedLoginServer().index;
    }

    /**
     * save the current selection state into shared prefs
     *
     * @return
     */
    private void saveUrlEdits() {

        // the id is also the index, but use findViewById to get the actual
        // radio button,
        // not childAtIndex, which can be anything
        RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());

        // if the selected item is the custom radio this won't return it
        int selectedId = radioGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            selectedId = customRadioButtonId;
        }

        View selectedView = radioGroup.findViewById(selectedId);
        if (selectedView == null) {
            // should never happen, but you know how that goes
            logger.logp(Level.WARNING, TAG, "saveUrlEdits",
                    "Failed to save state, could not find a selected URL");
            rebuildDisplay();
            return;
        }

        loginServerManager.setSelectedLoginServerByIndex(selectedId);
    }
    
    /**
     * clicked cancel or back button
     *
     * @param v
     */
    public void setCancelReturnValue(View v) {
        setResult(Activity.RESULT_CANCELED, null);
        restoreStartingState();
        finish();
    }

    /**
     * set the retval of the activity. selection is stored in the prefs,
     * AuthActivity pull from the prefs or an OAuth2 default
     *
     * @param v
     */
    public void setPositiveReturnValue(View v) {
        saveUrlEdits();
        setResult(Activity.RESULT_OK, null);
        finish();
    }

    /**
     * the index is set as the id of the actual radio buttons other view do not
     * get an explicit id, but it means you can't get radio buttons by
     * selectedId -> childAtIndex, use findViewById
     *
     * @param radioGroup
     * @param serer
     * @param index
     * @param isCustom
     * @param titleText
     * @param urlText
     */
    private void setRadioState(RadioGroup radioGroup, LoginServer server) {
    	int index = server.index;
    	boolean isCustom = server.isCustom;
    	String titleText = server.name;
    	String urlText = server.url;
    	
        RadioButton rb = new RadioButton(this);

        rb.setId(index);

        SpannableStringBuilder result = new SpannableStringBuilder();

        SpannableString titleSpan = new SpannableString(titleText);
        titleSpan.setSpan(new TextAppearanceSpan(this,
                SalesforceSDKManager.isTablet() ? salesforceR.styleTextHostName() : android.R.style.TextAppearance_Medium), 0, titleText.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        SpannableString urlSpan = new SpannableString(urlText);
        urlSpan.setSpan(new TextAppearanceSpan(this,
                SalesforceSDKManager.isTablet() ? salesforceR.styleTextHostUrl() : android.R.style.TextAppearance_Small), 0, urlText.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        result.append(titleSpan);
        result.append(System.getProperty("line.separator"));
        result.append(urlSpan);

        rb.setText(result, BufferType.SPANNABLE);
        rb.setTag(urlText);

        if (isCustom) {

            // keep the edit link always to the right of the row
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
            // interesting side effect of this? if the url gets big, the runtime
            // will shrink this guy, doesn't matter if the w/h are set
            // linkParams.weight=(float)0.25;
            linkParams.gravity = Gravity.CENTER_HORIZONTAL
                    | Gravity.CENTER_VERTICAL;

            ImageView iv = new ImageView(this);
            iv.setImageResource(salesforceR.drawableEditIcon());
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCustomUrlDialog(null);
                }
            });

            customRadioButtonId = index;

            // problem here is that if the radio button is not the top level
            // child view when adding,
            // the group does not control it (check the radio group code) so do
            // it by hand.
            // **It sort of controls it, you can select it through through the
            // group but it does not get deselected correctly and is not
            // returned by a get selected call on the group
            rb.setOnClickListener(this);

            LinearLayout lay = new LinearLayout(this);
            lay.addView(rb, 0, buttonParams);
            lay.addView(iv, 1, linkParams);

            radioGroup.addView(lay, rowParams);

            radioGroup.setOnCheckedChangeListener(this);

        } else {
            radioGroup.addView(rb);
        }

        // spacer line
        View spacerView = new View(this);
        spacerView.setBackgroundColor(0xffdcdcdc);
        LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.FILL_PARENT, 1);
        spacerView.setLayoutParams(lp);

        radioGroup.addView(spacerView);
    }

    /**
     * control the elements in the layout based on past user choices said
     * another way, select the correct radio button, show the custom url if set
     */
    protected void setupRadioButtons() {
        RadioGroup radioGroup = (RadioGroup) findViewById(getServerListGroupId());

        for (LoginServer currentServer : loginServerManager.getDefaultLoginServers()) {
            setRadioState(radioGroup, currentServer);
        }

        // custom url set, may or may not be selected. ui is a radio with an
        // edit link either way.
        View postView = findViewById(salesforceR.idShowCustomUrlEdit());

        LoginServer customServer = loginServerManager.getCustomLoginServer();
        if (customServer != null) {
        	postView.setVisibility(View.GONE);
            setRadioState(radioGroup, customServer);
        }
        else {
        	postView.setVisibility(View.VISIBLE);
        }

        // set selection
        int which = -1;
        if (restoredConfigIndex >= 0) {
            which = restoredConfigIndex;
            restoredConfigIndex = -1;
        } else {
            which = loginServerManager.getSelectedLoginServer().index;
        }

        radioGroup.check(which);
    }

    public void showCustomUrlDialog(View v) {
        // once a dialog is created once, onCreateDialog will most likely not
        // get called again
        // so setup any state here
        wasEditUrlDialogCanceled = false;
        showDialog(SERVER_DIALOG_ID);
    }

}

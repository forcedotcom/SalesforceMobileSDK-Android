package com.salesforce.androidsdk.reactnative.ui;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.facebook.react.ReactActivityDelegate;

import javax.annotation.Nullable;

/**
 * Created by ibogdanov on 11/22/16.
 */

public class SalesforceReactActivityDelegate extends ReactActivityDelegate {
    public SalesforceReactActivityDelegate(Activity activity, @Nullable String mainComponentName) {
        super(activity, mainComponentName);
    }

    public SalesforceReactActivityDelegate(FragmentActivity fragmentActivity, @Nullable String mainComponentName) {
        super(fragmentActivity, mainComponentName);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
    }

    public void onReadyCreate() {
        super.onCreate(null);
    }

}

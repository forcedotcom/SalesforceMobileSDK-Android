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
package com.salesforce.samples.configuredapp.ui;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static com.salesforce.androidsdk.R.style.SalesforceSDK;
import static com.salesforce.androidsdk.R.style.SalesforceSDK_Dark;

import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.ui.SalesforceActivity;
import com.salesforce.samples.configuredapp.R;

import org.json.JSONException;

/**
 * Main activity.
 */
public class MainActivity extends SalesforceActivity {


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		boolean isDarkTheme = SalesforceSDKManager.getInstance().isDarkTheme();
		setTheme(isDarkTheme ? SalesforceSDK_Dark : SalesforceSDK);
		SalesforceSDKManager.getInstance().setViewNavigationVisibility(this);
		setContentView(R.layout.main);
        String bootconfig = "";
        try {
            bootconfig = BootConfig.getBootConfig(this).asJSON().toString(4);
        }
        catch (JSONException e) {
            Log.e("MainActivity.onCreate", "Could not serialize bootconfig", e);
        }
        ((TextView) findViewById(R.id.bootconfig)).setText(bootconfig);

		// Fix UI being drawn behind status and navigation bars on Android 15+
		if (SDK_INT > UPSIDE_DOWN_CAKE) {
			ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), new OnApplyWindowInsetsListener() {
				@NonNull
				@Override
				public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
					Insets mInsets = insets.getInsets(
							WindowInsetsCompat.Type.systemBars()
									& WindowInsetsCompat.Type.displayCutout()
									| WindowInsetsCompat.Type.displayCutout()
					);

					TypedValue outValue = new TypedValue();
					getTheme().resolveAttribute(android.R.attr.actionBarSize, outValue, true);
					int actionBarHeight = TypedValue.complexToDimensionPixelSize(outValue.data, getResources().getDisplayMetrics());
					v.setPadding(mInsets.left, mInsets.top + actionBarHeight, mInsets.right, mInsets.bottom);
					return WindowInsetsCompat.CONSUMED;
				}
			});
		}
	}

	@Override
	public void onResume(RestClient client) {

	}

}

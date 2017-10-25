/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
import android.os.Bundle;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.salesforce.androidsdk.R;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev support activity showing a lot of useful information
 */
public class DevInfoActivity extends Activity {

    public static final String NAME = "name";
    public static final String VALUE = "value";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Title / layout
        getActionBar().setTitle(R.string.sf__dev_support_title);
        setContentView(R.layout.sf__dev_info);

        // Setup list
        List<Map<String, String>> listData = prepareListData(SalesforceSDKManager.getInstance().getDevSupportInfos());
        String[] from = new String[] {NAME, VALUE};
        int[] to = new int[] { android.R.id.text1, android.R.id.text2};
        ((ListView) findViewById(R.id.list)).setAdapter(new SimpleAdapter(this, listData, android.R.layout.simple_list_item_2, from, to));
    }

    private List<Map<String, String>> prepareListData(List<String> rawData) {
        List<Map<String, String>> listData = new ArrayList<>();
        for (int i=0; i<rawData.size(); i+=2) {
            Map<String, String> entry = new HashMap<>();
            entry.put(NAME, rawData.get(i));
            entry.put(VALUE, rawData.get(i+1));
            listData.add(entry);
        }
        return listData;
    }
}

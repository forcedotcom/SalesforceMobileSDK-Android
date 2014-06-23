/*
 * Copyright (c) 2012, salesforce.com, inc.
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
package com.salesforce.androidsdk.app;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.Encryptor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

/**
 * Helper class for UUID generation
 */
public class UUIDManager {

	private static final String UUID_PREF = "uuids2";

    /**
     * Random keys persisted encrypted in a private preference file
     * This is provided as an example.
     * We recommend you provide you own implementation for creating the HashConfig's.
     */
    private static Map<String, String> uuids = new HashMap<String, String>();
    
    public static synchronized String getUuId(String name) {
    	String cached = uuids.get(name);
    	if (cached != null) return cached;
        SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(UUID_PREF, Context.MODE_PRIVATE);
        String key = SalesforceSDKManager.getInstance().getKey(name);
        if (!sp.contains(name)) {
            String uuid = UUID.randomUUID().toString();
            Editor e = sp.edit();
            e.putString(name, Encryptor.encrypt(uuid, key));
            e.commit();
        }
        cached = Encryptor.decrypt(sp.getString(name, null), key);
        if (cached != null)
        	uuids.put(name, cached);
        return cached;
    }	
    
    /**
     * Resets the generated UUIDs and wipes out the shared pref file that houses them.
     */
    public static synchronized void resetUuids() {
        uuids.clear();
        final SharedPreferences sp = SalesforceSDKManager.getInstance().getAppContext().getSharedPreferences(UUID_PREF, Context.MODE_PRIVATE);
        if (sp != null) {
            sp.edit().clear().commit();
        }
    }   
}
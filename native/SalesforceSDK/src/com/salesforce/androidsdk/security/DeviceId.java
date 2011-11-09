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
package com.salesforce.androidsdk.security;

import java.util.UUID;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

/**
 * See http://stackoverflow.com/questions/2785485/is-there-a-unique-android-device-id
 * and http://android-developers.blogspot.com/2011/03/identifying-app-installations.html
 *
 * for good info/commentary on all the issues related to a deviceId. We'll skip that and use
 * a one per install generated Id.
 */
public class DeviceId {

	private static final String PREF_NAME = "device";
	private static final String KEY_DEVICE_ID ="deviceId";
	private static String id;
	
	public static synchronized String getDeviceId(Context ctx) {
		if (id != null) return id;
		SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
		if (!sp.contains(KEY_DEVICE_ID)) {
			final String devId = UUID.randomUUID().toString();
			Editor e = sp.edit();
			e.putString(KEY_DEVICE_ID, Encryptor.encrypt(devId, getKey()));
			e.commit();
		}
		id = Encryptor.decrypt(sp.getString(KEY_DEVICE_ID, null), getKey());
		return id;
	}
	
	private static String getKey() {
		return "5L/sZbQBhIcGs+dkP1GZHITEYDbV1AyFUgSEoYylDAk=";
	}
}

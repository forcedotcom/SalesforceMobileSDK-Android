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

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;

/**
 * Helper class to encrypt/decrypt strings
 *
 */
public class Encryptor {
	
	private static boolean fileSystemEncrypted;

	public static void init(Context ctx) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			fileSystemEncrypted = false;
		}
		else {
			DevicePolicyManager devicePolicyManager = (DevicePolicyManager) ctx.getSystemService(Service.DEVICE_POLICY_SERVICE);
			// Note: Following method only exists if linking to an android.jar api 11 and above
			fileSystemEncrypted = devicePolicyManager.getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
		}
	}
	
	/**
	 * @return true if file system encryption is available and active
	 */
	public static boolean isFileSystemEncrypted() {
		return fileSystemEncrypted;
	}
	
	/**
	 * Encrypt data using key
	 * 
	 * @param key
	 * @param data
	 * @return
	 * 
	 * FIXME do an actual encryption
	 */
	public static String encrypt(String key, String data) {
		return data;
	}

	/**
	 * Decrypt data using key
	 * 
	 * @param key
	 * @param data
	 * @return
	 * 
	 *         FIXME do an actual decryption
	 */
	public static String decrypt(String key, String data) {
		return data;
	}
}

/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.push;

import android.text.TextUtils;
import android.util.Base64;

import com.google.firebase.messaging.RemoteMessage;
import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.security.KeyStoreWrapper;
import com.salesforce.androidsdk.security.SalesforceKeyGenerator;

import java.security.PrivateKey;
import java.util.Map;

/**
 * This class processes incoming push notifications and passes them along to the app.
 * It decrypts the incoming notification if encryption of push notifications is enabled.
 *
 * @author bhariharan
 */
class PushNotificationDecryptor {

    private static String CONTENT_KEY = "content";
    private static PushNotificationDecryptor INSTANCE = new PushNotificationDecryptor();

    /**
     * Returns a singleton instance of this class.
     *
     * @return Singleton instance of this class.
     */
    static synchronized PushNotificationDecryptor getInstance() {
        return INSTANCE;
    }

    void onPushMessageReceived(RemoteMessage message) {
        final Map<String, String> data = processNotificationPayload(message.getData());
        if (data != null && SalesforceSDKManager.hasInstance()) {
            final PushNotificationInterface pnInterface = SalesforceSDKManager.getInstance().getPushNotificationReceiver();
            if (pnInterface != null) {
                pnInterface.onPushMessageReceived(data);
            }
        }
    }

    private Map<String, String> processNotificationPayload(Map<String, String> data) {

        // Checks if the payload is encrypted.
        final String isEncryptedKey = "encrypted";
        final boolean encrypted = data.containsKey(isEncryptedKey)
                && Boolean.parseBoolean(data.get(isEncryptedKey));
        if (!encrypted) {
            return data;
        }

        // Checks if the payload contains a decryption key for the payload.
        final String secretKey = "secret";
        final String encryptedSecretKey = data.get(secretKey);
        if (encryptedSecretKey == null) {
            return data;
        }

        // Removes the decryption key and checks if the bundle contains a payload to decrypt.
        data.remove(secretKey);
        if (!data.containsKey(CONTENT_KEY)) {
            return data;
        }
        return decryptPayload(encryptedSecretKey, data);
    }

    private Map<String, String> decryptPayload(String encryptedSecretKey, Map<String, String> data) {
        final String encryptedData = data.get(CONTENT_KEY);
        if (encryptedData == null) {
            return data;
        }
        final PrivateKey privateKey = getRSAPrivateKey();
        if (privateKey == null) {
            return data;
        }
        byte[] symmetricKey = Encryptor.decryptWithRSABytes(privateKey, encryptedSecretKey);
        if (symmetricKey == null) {
            return data;
        }
        byte[] key = new byte[16];
        System.arraycopy(symmetricKey, 0, key, 0, 16);
        byte[] iv = new byte[16];
        System.arraycopy(symmetricKey, 16, iv, 0, 16);
        final byte[] encryptedPayload = Base64.decode(encryptedData, Base64.DEFAULT);
        if (encryptedPayload == null) {
            return data;
        }
        final String decryptedData = Encryptor.decryptBytes(encryptedPayload, key, iv);
        if (decryptedData != null) {
            data.put(CONTENT_KEY, decryptedData);
        }
        return data;
    }

    private synchronized PrivateKey getRSAPrivateKey() {
        PrivateKey rsaPrivateKey = null;
        final String name = SalesforceKeyGenerator.getUniqueId(PushService.PUSH_NOTIFICATION_KEY_NAME);
        final String sanitizedName = name.replaceAll("[^A-Za-z0-9]", "");
        if (!TextUtils.isEmpty(sanitizedName)) {
            rsaPrivateKey = KeyStoreWrapper.getInstance().getRSAPrivateKey(sanitizedName);
        }
        return rsaPrivateKey;
    }
}

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

    private static String IS_ENCRYPTED_KEY = "encrypted";
    private static String SECRET_KEY = "secret";
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
        final Map<String, String> data = message.getData();
        decryptNotificationPayload(data);
        if (!data.containsKey(CONTENT_KEY)) {
            passMessageToApp(message);
        } else {
            final String content = data.get(CONTENT_KEY);
            if (content == null) {
                passMessageToApp(message);
            } else {

            }

            // TODO: Replace with processed message.
            passMessageToApp(message);
        }
    }

    private void decryptNotificationPayload(Map<String, String> data) {

        // Checks if the payload is encrypted.
        final boolean encrypted = data.containsKey(IS_ENCRYPTED_KEY)
                && Boolean.parseBoolean(data.get(IS_ENCRYPTED_KEY));
        if (!encrypted) {
            return;
        }

        // Checks if the payload contains a decryption key for the payload.
        final String secretKey = data.get(SECRET_KEY);
        if (secretKey == null) {
            return;
        }

        // Removes the decryption key and checks if the bundle contains a payload to decrypt.
        data.remove(SECRET_KEY);
        if (!data.containsKey(CONTENT_KEY)) {
            return;
        }
        final String encryptedData = data.get(CONTENT_KEY);
        if (encryptedData == null) {
            return;
        }
        final String decryptedData = decrypt(secretKey, encryptedData);
        if (decryptedData == null) {
            return;
        }
        data.put(CONTENT_KEY, decryptedData);
    }

    private String decrypt(String key, String data) {
        final PrivateKey privateKey = getRSAPrivateKey();
        if (privateKey != null) {
            final String symmetricKey = Encryptor.decryptWithRSA(privateKey, data);
            if (symmetricKey != null) {

            }
        }
        //final String symmetricKey = Encryptor.decryptWithRSABytes(key.getBytes(), )

        /*val symmetricKey: ByteArray? = Encryptor.decryptWithRSABytes(privateKey!!.data, secretKey)
        return when (symmetricKey) {
            null -> null
            else -> {
                val key = ByteArray(16)
                System.arraycopy(symmetricKey, 0, key, 0, 16)
                val iv = ByteArray(16)
                System.arraycopy(symmetricKey, 16, iv, 0, 16)

                // Decrypts the content using the extracted symmetric key and IV.
                Decrypted(Encryptor.decryptBytes(Base64.decode(encrypted, Base64.DEFAULT), key, iv))
            }
        }*/
        return null;
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

    private void passMessageToApp(RemoteMessage message) {
        if (message != null && SalesforceSDKManager.hasInstance()) {
            final PushNotificationInterface pnInterface = SalesforceSDKManager.getInstance().getPushNotificationReceiver();
            if (pnInterface != null) {
                pnInterface.onPushMessageReceived(message);
            }
        }
    }
}

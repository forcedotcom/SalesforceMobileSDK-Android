/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.analytics.security;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Helper class for encryption/decryption/hash computations.
 */
public class Encryptor {

    private static final String TAG = "Encryptor";
    private static final String UTF8 = "UTF-8";
    private static final String PREFER_CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String MAC_TRANSFORMATION = "HmacSHA256";
    private static String bestCipherAvailable;
    private static boolean isFileSystemEncrypted;

    /**
     * Initializes this module.
     *
     * @param ctx Context.
     * @return True - if the cryptographic module was successfully initialized, False - otherwise.
     */
    public static boolean init(Context ctx) {

    	// Checks if file system encryption is available and active.
        DevicePolicyManager devicePolicyManager = (DevicePolicyManager) ctx.getSystemService(Service.DEVICE_POLICY_SERVICE);
        isFileSystemEncrypted = devicePolicyManager.getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;

        // Make sure the cryptographic transformations we want to use are available.
        bestCipherAvailable = null;
        try {
            getBestCipher();
        } catch (GeneralSecurityException gex) {
            SalesforceAnalyticsLogger.e(ctx, TAG, "Security exception thrown", gex);
        }
        if (bestCipherAvailable == null) {
            return false;
        }
        try {
            Mac.getInstance(MAC_TRANSFORMATION, "BC");
        } catch (GeneralSecurityException e) {
            SalesforceAnalyticsLogger.e(ctx, TAG, "No MAC transformation available", e);
            return false;
        }
        return true;
    }

    /**
     * Returns the best cipher available.
     *
     * @return Best cipher available.
     * @throws GeneralSecurityException
     */
    public static Cipher getBestCipher() throws GeneralSecurityException {
        Cipher cipher = null;
        if (bestCipherAvailable != null) {
            return Cipher.getInstance(bestCipherAvailable, "BC");
        }
        try {
            cipher = Cipher.getInstance(PREFER_CIPHER_TRANSFORMATION, "BC");
            if (cipher != null) {
                bestCipherAvailable = PREFER_CIPHER_TRANSFORMATION;
            }
        } catch (GeneralSecurityException gex1) {
            SalesforceAnalyticsLogger.e(null, TAG, "Preferred combo not available", gex1);
        }
        if (bestCipherAvailable == null) {
            SalesforceAnalyticsLogger.e(null, TAG, "No cipher transformation available");
        }
        return cipher;
    }

    /**
     * Checks if the file system is encrypted.
     *
     * @return True - if file system encryption is available and active, False - otherwise.
     */
    public static boolean isFileSystemEncrypted() {
        return isFileSystemEncrypted;
    }

    /**
     * Decrypts data with key using AES-256.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
     * @return Decrypted data.
     */
    public static String decrypt(String data, String key) {
        if (TextUtils.isEmpty(key) || data == null) {
            return data;
        }
        return decrypt(data.getBytes(), key);
    }

    public static String decrypt(byte[] data, String key) {
        if (TextUtils.isEmpty(key)) {
            if (data != null) {
                return new String(data, Charset.forName(UTF8));
            } else {
                return null;
            }
        }
        try {
            // Decodes with Base64.
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
            byte[] dataBytes = Base64.decode(data, Base64.DEFAULT);

            // Decrypts with AES-256.
            byte[] decryptedData = decrypt(dataBytes, 0, dataBytes.length, keyBytes);
            return new String(decryptedData, 0, decryptedData.length, UTF8);
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during decryption", ex);
        }
        return null;
    }

    /**
     * Encrypts data with key using AES-256.
     *
     * @param data Data.
     * @param key Base64 encoded 256 bit key or null (to leave data unchanged).
     * @return Base64, AES-256 encrypted data.
     */
    public static String encrypt(String data, String key) {
        if (TextUtils.isEmpty(key) || data == null) {
            return data;
        }
        byte[] bytes = encryptBytes(data, key);
        if (bytes == null) {
            return null;
        }
        try {
            // do as Base64.encodeToString does... return US-ASCII string with the already Base64 encoded bytes.
            return new String(bytes, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", e);
        }
        return null;
    }

    /**
     * Encrypts data with key using AES-256. Returns Base64 byte[] array.
     * @param data
     * @param key
     * @return
     */
    public static byte[] encryptBytes(String data, String key) {
        if (TextUtils.isEmpty(key)) {
            if (data == null) {
                return null;
            } else {
                return data.getBytes();
            }
        }
        try {
            // Encrypts with our preferred cipher.
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
            byte[] dataBytes = data.getBytes(UTF8);
            return Base64.encode(encrypt(dataBytes, keyBytes), Base64.DEFAULT);
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during encryption", ex);
        }
        return null;
    }

    /**
     * Checks if the string is Base64 encoded.
     *
     * @param key String.
     * @return True - if encoded, False - otherwise.
     */
    public static boolean isBase64Encoded(String key) {
        try {
            Base64.decode(key, Base64.DEFAULT);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Return HMAC SHA-256 hash of data using key.
     *
     * @param data Data.
     * @param key Key.
     * @return Hash.
     */
    public static String hash(String data, String key) {
        try {

            // Signs with SHA-256.
            byte [] keyBytes = key.getBytes(UTF8);
            byte [] dataBytes = data.getBytes(UTF8);
            Mac sha = Mac.getInstance(MAC_TRANSFORMATION, "BC");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, sha.getAlgorithm());
            sha.init(keySpec);
            byte [] sig = sha.doFinal(dataBytes);

            // Encodes with Base64.
            return Base64.encodeToString(sig, Base64.NO_WRAP);
        } catch (Exception ex) {
            SalesforceAnalyticsLogger.w(null, TAG, "Error during hashing", ex);
            return null;
        }
    }

    private static byte[] generateInitVector() throws NoSuchAlgorithmException, NoSuchProviderException {
        final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        return iv;
    }

    private static byte[] encrypt(byte[] data, byte[] key) throws GeneralSecurityException {
        final Cipher cipher = getBestCipher();
        final SecretKeySpec skeySpec = new SecretKeySpec(key, cipher.getAlgorithm());

        // Generates a unique IV per encryption.
        byte[] initVector = generateInitVector();
        final IvParameterSpec ivSpec = new IvParameterSpec(initVector);
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);
        byte[] meat = cipher.doFinal(data);

        // Prepends the IV to the encoded data (first 16 bytes / 128 bits).
        byte[] result = new byte[initVector.length + meat.length];
        System.arraycopy(initVector, 0, result, 0, initVector.length);
        System.arraycopy(meat, 0, result, initVector.length, meat.length);
        return result;
    }

    private static byte[] decrypt(byte[] data, int offset, int length, byte[] key) throws GeneralSecurityException {

        // Grabs the init vector prefix (first 16 bytes / 128 bits).
        byte[] initVector = new byte[16];
        System.arraycopy(data, offset, initVector, 0, initVector.length);

        // Grabs the encrypted body after the init vector prefix.
        int meatLen = length - initVector.length;
        int meatOffset = offset + initVector.length;
        byte[] meat = new byte[meatLen];
        System.arraycopy(data, meatOffset, meat, 0, meatLen);
        final Cipher cipher = getBestCipher();
        final SecretKeySpec skeySpec = new SecretKeySpec(key, cipher.getAlgorithm());
        final IvParameterSpec ivSpec = new IvParameterSpec(initVector);
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
        byte[] padded = cipher.doFinal(meat, 0, meatLen);
        byte[] result = padded;
        byte paddingValue = padded[padded.length - 1];
        if (0 <= paddingValue) {
            if (paddingValue < (byte) 16) {
                byte compare = padded[padded.length - paddingValue];
                if (compare == paddingValue) {
                    result = new byte[padded.length - paddingValue];
                    System.arraycopy(padded, 0, result, 0, result.length);
                }
            }
        }
        return result;
    }
}

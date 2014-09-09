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

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

/**
 * Helper class for encryption/decryption/hash computations
 */
public class Encryptor {

    private static final String TAG = "Encryptor";
    private static final String UTF8 = "UTF-8";
    private static final String PREFER_CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String MAC_TRANSFORMATION = "HmacSHA256";
    private static String bestCipherAvailable;
    private static boolean isFileSystemEncrypted;

    /**
     * @param ctx
     * @return true if the cryptographic module was successfully initialized
     * @throws GeneralSecurityException
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
        }
        if (bestCipherAvailable == null) {
            return false;
        }
        try {
            Mac.getInstance(MAC_TRANSFORMATION, "BC");
        } catch (GeneralSecurityException e) {
            Log.e(TAG, "No mac transformation available");
            return false;
        }
        return true;
    }

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
            // Preferred combo not available.
        }
        if (bestCipherAvailable == null) {
            Log.e(TAG, "No cipher transformation available");
        }
        return cipher;
    }

    /**
     * @return true if file system encryption is available and active
     */
    public static boolean isFileSystemEncrypted() {
        return isFileSystemEncrypted;
    }

    /**
     * Decrypt data with key using aes256
     * @param data
     * @param key base64 encoded 256 bits key or null to leave data unchanged
     * @return decrypted data
     */
    public static String decrypt(String data, String key) {
        if (key == null || data == null) {
            return data;
        }
        try {

            // Decode with base64.
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
            byte[] dataBytes = Base64.decode(data, Base64.DEFAULT);

            // Decrypt with aes256.
            byte[] decryptedData = decrypt(dataBytes, 0, dataBytes.length, keyBytes);
            return new String(decryptedData, 0, decryptedData.length, UTF8);
        } catch (Exception ex) {
            Log.w("Encryptor:decrypt", "error during decryption", ex);
        }
        return null;
    }

    /**
     * Encrypt data with key using aes256
     * @param data
     * @param key base64 encoded 256 bits key or null to leave data unchanged
     * @return base64, aes256 encrypted data
     */
    public static String encrypt(String data, String key) {
        if (key == null || data == null) {
            return data;
        }
        try {

            // Encrypt with our preferred cipher.
            byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
            byte[] dataBytes = data.getBytes(UTF8);
            byte[] encryptedData = encrypt(dataBytes, keyBytes);

            // Encode with base64.
            return Base64.encodeToString(encryptedData, Base64.DEFAULT);
        } catch (Exception ex) {
            Log.w("Encryptor:encrypt", "error during encryption", ex);
            return null;
        }
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
     * Return hmac-sha256 hash of data using key
     * @param data
     * @param key
     * @return
     */
    public static String hash(String data, String key) {
        try {
            // Sign with sha256
            byte [] keyBytes = key.getBytes(UTF8);
            byte [] dataBytes = data.getBytes(UTF8);
            Mac sha = Mac.getInstance(MAC_TRANSFORMATION, "BC");
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, sha.getAlgorithm());
            sha.init(keySpec);
            byte [] sig = sha.doFinal(dataBytes);

            // Encode with base64.
            String hash = Base64.encodeToString(sig, Base64.DEFAULT);

            /*
             * Android 4.3 has a bug where a newline character is appended
             * at the end of the base64 encoded string. We remove this newline
             * character to prevent a mismatch between the stored hash
             * and computed hash.
             */
            hash = removeNewLine(hash);
            return hash;
        } catch (Exception ex) {
            Log.w("Encryptor:hash", "error during hashing", ex);
            return null;
        }
    }

    /**
     * Removes a trailing newline character from the hash.
     *
     * @param hash Hash.
     * @return Hash with trailing newline character removed.
     */
    public static String removeNewLine(String hash) {
        if (hash != null && hash.endsWith("\n")) {
            return hash.substring(0, hash.lastIndexOf("\n"));
        }
        return hash;
    }

    private static byte[] generateInitVector() throws NoSuchAlgorithmException, NoSuchProviderException {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        return iv;
    }

    /**
     * Encrypt data bytes using key
     * @param data
     * @param key
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    private static byte[] encrypt(byte[] data, byte[] key) throws GeneralSecurityException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        // encrypt
        Cipher cipher = getBestCipher();
        SecretKeySpec skeySpec = new SecretKeySpec(key, cipher.getAlgorithm());

        //generate a unique IV per encrypt
        byte[] initVector = generateInitVector();
        IvParameterSpec ivSpec = new IvParameterSpec(initVector);
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);
        byte[] meat = cipher.doFinal(data);

        //prepend the IV to the encoded data (first 16 bytes / 128 bits )
        byte[] result = new byte[initVector.length + meat.length];
        System.arraycopy(initVector, 0, result, 0, initVector.length);
        System.arraycopy(meat, 0, result, initVector.length, meat.length);
        return result;
    }

    /**
     * Decrypt data bytes using key
     * @param data
     * @param offset
     * @param length
     * @param key
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    private static byte[] decrypt(byte[] data, int offset, int length, byte[] key) throws GeneralSecurityException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        //grab the init vector prefix (first 16 bytes / 128 bits)
        byte[] initVector = new byte[16];
        System.arraycopy(data, offset, initVector, 0, initVector.length);

        //grab the encrypted body after the init vector prefix
        int meatLen = length - initVector.length;
        int meatOffset = offset + initVector.length;
        byte[] meat = new byte[meatLen];
        System.arraycopy(data, meatOffset, meat, 0, meatLen);
        Cipher cipher = getBestCipher();
        SecretKeySpec skeySpec = new SecretKeySpec(key, cipher.getAlgorithm());
        IvParameterSpec ivSpec = new IvParameterSpec(initVector);
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
        byte[] padded = cipher.doFinal(meat, 0, meatLen);
        byte[] result = padded;
        byte paddingValue = padded[padded.length - 1];
        if (0 != paddingValue) {
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

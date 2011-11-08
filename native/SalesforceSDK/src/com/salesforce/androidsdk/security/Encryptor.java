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

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

/**
 * Helper class to encrypt/decrypt strings
 */
public class Encryptor {
	private static final String UTF8 = "UTF-8";
    private static final String algorithm = "AES";
    private static final String encryptXform = "AES/ECB/NoPadding";
    private static final String decryptXform = "AES/ECB/NoPadding";
    
    // Instead of static screen we could use something unique to the device
    private static final String MASTER_KEY = "hif^'qh*=j91qQr,m#RZsagwQkYDJr}[";
	private static final String NOISE = "jEgN,s.c:v7(^hTV\\1Ra-Im%bSPzTPY*";

	private static boolean isFileSystemEncrypted;
    
	/**
	 * @param ctx
	 * @return true if the cryptographic module was successfully initialized
	 * @throws GeneralSecurityException
	 */
	public static boolean init(Context ctx) {
		// Check if filesystem is available and active 
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			isFileSystemEncrypted = false;
		}
		else {
			DevicePolicyManager devicePolicyManager = (DevicePolicyManager) ctx.getSystemService(Service.DEVICE_POLICY_SERVICE);
			// Note: Following method only exists if linking to an android.jar api 11 and above
			isFileSystemEncrypted = devicePolicyManager.getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
		}
		
		// Initialize encryption module
		try {
	    	byte[] masterKeyBytes = MASTER_KEY.getBytes(UTF8);
	        SecretKeySpec skeySpec = new SecretKeySpec(masterKeyBytes, algorithm);
	        Cipher cipher = Cipher.getInstance(decryptXform);
	        cipher.init(Cipher.DECRYPT_MODE, skeySpec);
		}
		catch (Exception e) {
			Log.w("Encryptor:init", e);
			return false;
		}
		
		return true;
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
     * @param key when null - data is returned unchanged
     * @return decrypted data
     */
    public static String decrypt(String data, String key) {
    	if (key == null)
    		return data;

    	try {
        	byte[] keyBytes = getKeyBytes(key);

        	// Decode with base64
            byte[] dataBytes = Base64.decode(data, Base64.DEFAULT);
            
            // Decrypt with aes256
        	byte[] decryptedData = decrypt(dataBytes, 0, dataBytes.length, keyBytes);
        	
            // ignore the padding in decrypted text, if any:
            int decryptedLength = decryptedData.length;
            int end = decryptedLength;
            for (int i = 0; i < decryptedLength; ++i) {
                if (decryptedData[i] == 0) {
                    end = i;
                    break;
                }
            }
            return new String(decryptedData, 0, end, UTF8);

        } catch (Exception ex) {
        	Log.w("Encryptor:decrypt", "error during decryption", ex);
            return null;
        }
    }

    /**
     * Encrypt data with key using aes256
     * @param data
     * @param key when null - data is returned unchanged
     * @return base64, aes256 encrypted data
     */
    public static String encrypt(String data, String key) {
    	if (key == null)
    		return data;
    	
        try {
        	byte[] keyBytes = getKeyBytes(key);
        	
            // Encrypt with aes256, use 0 as the padding value, not the default of 0xFF
            byte[] dataBytes = data.getBytes(UTF8);
            byte[] encryptedData = encrypt(dataBytes, keyBytes, (byte)0);
            
            // Encode with base64
            return Base64.encodeToString(encryptedData, Base64.DEFAULT);
            
        } catch (Exception ex) {
            Log.w("Encryptor:encrypt", "error during encryption", ex);
            return null;
        }
    }
    
    /**
     * Encrypt data bytes using key
     * @param data
     * @param key
     * @param paddingValue
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    private static byte[] encrypt(byte[] data, byte[] key, byte paddingValue) throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

    	// must be a multiple of a block length (16 bytes)
    	int length = data == null ? 0 : data.length;
        int len = (length + 15) & ~15;
        byte[] padded = new byte[len];
        System.arraycopy(data, 0, padded, 0, length);

        // must pad with known data (typically 0xFF, but not always)
        for (int i = length; i < len; i++)
            padded[i] = paddingValue;

        // update length to be what we will actually send
        length = len;

        // encrypt
        SecretKeySpec skeySpec = new SecretKeySpec(key, algorithm);
        Cipher cipher = Cipher.getInstance(encryptXform);
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        return cipher.doFinal(padded);
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
    private static byte[] decrypt(byte[] data, int offset, int length, byte[] key) throws NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
    	SecretKeySpec skeySpec = new SecretKeySpec(key, algorithm);
        Cipher cipher = Cipher.getInstance(decryptXform);
        cipher.init(Cipher.DECRYPT_MODE, skeySpec);
    	
        return cipher.doFinal(data, offset, length);
    }
    
    /**
     * @param keyString
     * @return 256 bytes key built from keyString 
     * @throws UnsupportedEncodingException 
     */
    private static byte[] getKeyBytes(String keyString) throws UnsupportedEncodingException {
    	return (keyString + NOISE).substring(0, 32).getBytes(UTF8); 
    }
}

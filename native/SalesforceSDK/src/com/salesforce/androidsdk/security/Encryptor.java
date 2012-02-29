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
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

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
import android.os.Build;
import android.util.Base64;
import android.util.Log;

/**
 * Helper class for encryption/decryption/hash computations
 */
public class Encryptor {
	private static final String TAG = "Encryptor";

	private static final String UTF8 = "UTF-8";
    private static final String PREFER_CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String BACKUP_CIPHER_TRANSFORMATION = "AES/ECB/NoPadding";

    private static final String MAC_TRANSFORMATION = "HmacSHA256";

	private static boolean isFileSystemEncrypted;
	private static String bestCipherAvailable;

	/**
	 * @param ctx
	 * @return true if the cryptographic module was successfully initialized
	 * @throws GeneralSecurityException
	 */
	public static boolean init(Context ctx) {
		// Check if file system encryption is available and active 
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			isFileSystemEncrypted = false;
		}
		else {
			DevicePolicyManager devicePolicyManager = (DevicePolicyManager) ctx.getSystemService(Service.DEVICE_POLICY_SERVICE);
			// Note: Following method only exists if linking to an android.jar api 11 and above
			isFileSystemEncrypted = devicePolicyManager.getStorageEncryptionStatus() == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
		}
		
		// Make sure the cryptographic transformations we want to use are available
		bestCipherAvailable = null;

		try {
			getBestCipher();
		}
		catch (GeneralSecurityException gex) {
		}
		
		if (null == bestCipherAvailable)
			return false;

				
		try {
			Mac.getInstance(MAC_TRANSFORMATION);
		}
		catch (GeneralSecurityException e) {
			Log.e(TAG, "No mac transformation available");
			return false;
		}
		
		return true;
	}
	
	public static Cipher getBestCipher() throws GeneralSecurityException {
		Cipher cipher = null;
		
		if (null != bestCipherAvailable) {
			return Cipher.getInstance(bestCipherAvailable);
		}
		
		try {
			cipher = Cipher.getInstance(PREFER_CIPHER_TRANSFORMATION);
			if (null != cipher)
				bestCipherAvailable = PREFER_CIPHER_TRANSFORMATION;
		}
		catch (GeneralSecurityException gex1) {
			//preferered combo not available
		}
		
		if (null == bestCipherAvailable) {
			//preferered combo not available: try next
			try {
				cipher = Cipher.getInstance(BACKUP_CIPHER_TRANSFORMATION);
				if (null != cipher)
					bestCipherAvailable = BACKUP_CIPHER_TRANSFORMATION;
			}
			catch (GeneralSecurityException gex2) {
				//backup combo also not available
			}
		}
		
		if (null == bestCipherAvailable) {
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
    	if (key == null)
    		return data;

    	try {
        	// Decode with base64
    		byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
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
     * @param key base64 encoded 256 bits key or null to leave data unchanged
     * @return base64, aes256 encrypted data
     */
    public static String encrypt(String data, String key) {
    	if (key == null)
    		return data;
    	
        try {
        	// Encrypt with aes256, use 0 as the padding value, not the default of 0xFF
        	byte[] keyBytes = Base64.decode(key, Base64.DEFAULT);
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

			Mac sha = Mac.getInstance(MAC_TRANSFORMATION);
			SecretKeySpec keySpec = new SecretKeySpec(keyBytes, sha.getAlgorithm());
			sha.init(keySpec);
			byte [] sig = sha.doFinal(dataBytes);

			// Encode with bas64
			return Base64.encodeToString(sig, Base64.DEFAULT);
			
        } catch (Exception ex) {
            Log.w("Encryptor:hash", "error during hashing", ex);
            return null;
        }
    }
    
    
    private static byte[] generateInitVector() throws NoSuchAlgorithmException {
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        byte[] iv = new byte[16];
        random.nextBytes(iv);
        return iv;
    }
    
    private static Cipher getDecryptCipher(byte[] key) throws GeneralSecurityException {
        Cipher cipher = getBestCipher();
    	SecretKeySpec skeySpec = new SecretKeySpec(key, cipher.getAlgorithm());
        //cipher.init(Cipher.DECRYPT_MODE, skeySpec);
        byte[] iv = generateInitVector();
        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec); 
        
        return cipher;
    }
    
    private static Cipher getEncryptCipher(byte[] key) throws GeneralSecurityException {        
        Cipher cipher = getBestCipher();
        SecretKeySpec skeySpec = new SecretKeySpec(key, cipher.getAlgorithm());
        //cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] iv = generateInitVector();
        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);

        return cipher;
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
    private static byte[] encrypt(byte[] data, byte[] key, byte paddingValue) throws GeneralSecurityException,
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
        Cipher cipher = getEncryptCipher(key);
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
    private static byte[] decrypt(byte[] data, int offset, int length, byte[] key) throws GeneralSecurityException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
    	
    	Cipher cipher = getDecryptCipher(key);
        return cipher.doFinal(data, offset, length);
    }
}

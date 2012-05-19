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
package com.salesforce.androidsdk.store;

import android.content.Context;


/**
 * Helper class to access local database 
 */
public class DBOperations  {
	private static EncryptedDBOpenHelper encOpenHelper;
	private static DBOpenHelper openHelper;

	/**
	 * Return handle to read from plain database
	 * @param ctx
	 * @return
	 */
	public static Database getReadableDatabase(Context ctx) {
		return new Database(getOpenHelper(ctx).getReadableDatabase());
	}

	/**
	 * Return handle to write to plain database
	 * @param ctx
	 * @return
	 */
	public static Database getWritableDatabase(Context ctx) {
		return new Database(getOpenHelper(ctx).getWritableDatabase());
	}

	/**
	 * Return handle to read from encrypted database
	 * @param ctx
	 * @param passcodeHash
	 * @return
	 */
	public static Database getReadableDatabase(Context ctx, String passcodeHash) {
		return new Database(getEncryptedOpenHelper(ctx).getReadableDatabase(passcodeHash));
	}

	/**
	 * Return handle to write to encrypted database
	 * @param ctx
	 * @param passcodeHash
	 * @return
	 */
	public static Database getWritableDatabase(Context ctx, String passcodeHash) {
		return new Database(getEncryptedOpenHelper(ctx).getWritableDatabase(passcodeHash));
	}
	
	public static synchronized void shutDown() {
		if (openHelper != null) {
			openHelper.close();
			openHelper = null;
		}
		if (encOpenHelper != null) {
			encOpenHelper.close();
			encOpenHelper = null;
		}
	}	
	
	/**
	 * Delete database
	 * @param ctx
	 */
	public static synchronized void resetDatabase(Context ctx) {
		shutDown();
		Database.reset(ctx);
	}
	
	private static synchronized DBOpenHelper getOpenHelper(Context ctx) {
		assert encOpenHelper == null : "You can't use a plain store, you already started using an encrypted one";
		if (openHelper == null) {
			openHelper = new DBOpenHelper(ctx);
		}
		return openHelper;
	}
	
	private static synchronized EncryptedDBOpenHelper getEncryptedOpenHelper(Context ctx) {
		assert openHelper == null : "You can't use an encrypted store, you already started using a plain one";
		if (encOpenHelper == null) {
			encOpenHelper = new EncryptedDBOpenHelper(ctx);
		}
		return encOpenHelper;
	}
	
}
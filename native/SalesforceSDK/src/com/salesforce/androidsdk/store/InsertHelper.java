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

import java.util.Hashtable;
import java.util.Map;

import android.content.ContentValues;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

/**
 * Abstracts out encrypted or non-encrypted InsertHelper Also cache insert
 * helper's
 */
public class InsertHelper {
	// Cache
	private static Map<String, InsertHelper> cachedHelpers = new Hashtable<String, InsertHelper>();

	// Actual InsertHelper
	private DatabaseUtils.InsertHelper insertHelper;
	private info.guardianproject.database.DatabaseUtils.InsertHelper encInsertHelper;

	/**
	 * @param tableName
	 * @return the InsertHelper for this table
	 */
	public static InsertHelper getInsertHelper(SQLiteDatabase db, String tableName) {
		InsertHelper insertHelper = cachedHelpers.get(tableName);

		if (insertHelper == null) {
			insertHelper = new InsertHelper(db, tableName);
			cachedHelpers.put(tableName, insertHelper);
		}
		return insertHelper;
	}

	/**
	 * @param tableName
	 * @return the InsertHelper for this table
	 */
	public static InsertHelper getInsertHelper(info.guardianproject.database.sqlcipher.SQLiteDatabase encdb, String tableName) {
		InsertHelper insertHelper = cachedHelpers.get(tableName);

		if (insertHelper == null) {
			insertHelper = new InsertHelper(encdb, tableName);
			cachedHelpers.put(tableName, insertHelper);
		}
		return insertHelper;
	}
	
	/**
	 * Private constructor
	 * @param db
	 * @param tableName
	 */
	private InsertHelper(SQLiteDatabase db, String tableName) {
		insertHelper = new DatabaseUtils.InsertHelper(db, tableName);
	}

	/**
	 * Private constructor
	 * @param encdb
	 * @param tableName
	 */
	private InsertHelper(info.guardianproject.database.sqlcipher.SQLiteDatabase encdb, String tableName) {
		encInsertHelper = new info.guardianproject.database.DatabaseUtils.InsertHelper(encdb, tableName);
	}

	/**
	 * Insert through underlying InsertHelper
	 * Synchronized because InsertHelper's are re-used
	 * @param contentValues
	 * @return 
	 */
	public synchronized long insert(ContentValues contentValues) {
		if (insertHelper != null)
			return insertHelper.insert(contentValues);
		else
			return encInsertHelper.insert(contentValues);
	}

	/**
	 * Reset cache
	 */
	public static void  reset() {
		cachedHelpers.clear();
	}
}

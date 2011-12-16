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

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.util.Pair;

import com.salesforce.androidsdk.util.LogUtil;


/**
 * SQLiteDatabase that does a bunch of logging 
 */
public class LoggingSQLiteDatabase {

	private SQLiteDatabase db;
	private String tag;

	public LoggingSQLiteDatabase(String tag, SQLiteDatabase db) {
		this.tag = tag;
		this.db = db;
	}
	
	/**
	 * Close underlying database
	 */
	public void close() {
		db.close();
	}

	/**
	 * Execute arbitrary SQL (after first logging it)
	 * @param sql
	 */
	public void execSQL(String sql) {
		Log.i(tag, sql);
		db.execSQL(sql);
	}

	/**
	 * Start transaction (after first logging it)
	 */
	public void beginTransaction() {
		Log.i(tag, "BEGIN TRANSACTION");
		db.beginTransaction();
	}

	/**
	 * Mark transaction as successful - so that endTransaction will do a commit
	 */
	public void setTransactionSuccessful() {
		Log.i(tag, "Calling setTransactionSuccessful");
		db.setTransactionSuccessful();
	}
	
	/**
	 * End transaction (after first logging it)
	 */
	public void endTransaction() {
		Log.i(tag, "END TRANSACTION");
		db.endTransaction();
	}
	
	/**
	 * Runs a query (after first logging the select statement)
	 * @param table
	 * @param columns
	 * @param selection
	 * @param orderBy
	 * @return
	 */
	public Cursor query(String table, String[] columns, String selection, String orderBy) {
		String columnsStr = LogUtil.join(columns, ",");
		columnsStr = (columnsStr.equals("") ? "*" : columnsStr);
		String orderByStr = (orderBy == null ? "" : " ORDER BY " + orderBy); 
		Log.i(tag, String.format("SELECT %s FROM %s WHERE %s%s", columnsStr, table, selection, orderByStr));
		return db.query(table, columns, selection, null, null, null, orderBy);
	}

	/**
	 * Does an insert (after first logging the insert statement)
	 * @param table
	 * @param contentValues
	 * @return row id of inserted row
	 */
	public long insert(String table, ContentValues contentValues) {
		Pair<String, String> columnsValues = LogUtil.getAsStrings(contentValues.valueSet(), ", ");
		Log.i(tag, String.format("INSERT INTO %s (%s) VALUES (%s)", table, columnsValues.first, columnsValues.second));
		return db.insert(table, null, contentValues);
	}

	/**
	 * Does an update (after first logging the insert statement)
	 * @param table
	 * @param contentValues
	 * @param whereClause
	 * @param number of rows affected
	 */
	public int update(String table, ContentValues contentValues, String whereClause) {
		String setStr = LogUtil.zipJoin(contentValues.valueSet(), " = ", ", ");
		Log.i(tag, String.format("UPDATE %s SET %s where %s", table, setStr, whereClause));
		return db.update(table, contentValues, whereClause, null);
	}
	
	/**
	 * Does a delete (after first logging the delete statement)
	 * @param table
	 * @param whereClause
	 */
	public void delete(String table, String whereClause) {
		Log.i(tag, String.format("DELETE FROM %s WHERE %s", table, whereClause));
		db.delete(table, whereClause, null);
	}
}

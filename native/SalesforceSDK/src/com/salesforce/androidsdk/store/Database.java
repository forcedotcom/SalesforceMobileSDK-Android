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
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.salesforce.androidsdk.util.LogUtil;


/**
 * Abstracts out encrypted or non-encrypted sqlite database
 */
public class Database {

	private SQLiteDatabase db;
	private info.guardianproject.database.sqlcipher.SQLiteDatabase encdb;
	private boolean encrypted;
	private String tag;

	public Database(String tag, SQLiteDatabase db) {
		this.tag = tag;
		this.db = db;
		this.encrypted = false;
	}
	
	public Database(String tag, info.guardianproject.database.sqlcipher.SQLiteDatabase encdb) {
		this.tag = tag + " [encrypted]";
		this.encdb = encdb;
		this.encrypted = true;
	}
	
	/**
	 * Close underlying database
	 */
	public void close() {
		if (!encrypted)
			db.close();
		else 
			encdb.close();
	}

	/**
	 * Execute arbitrary SQL (after first logging it)
	 * @param sql
	 */
	public void execSQL(String sql) {
		Log.i(tag, sql);
		if (!encrypted)
			db.execSQL(sql);
		else
			encdb.close();
	}

	/**
	 * Start transaction (after first logging it)
	 */
	public void beginTransaction() {
		Log.i(tag, "BEGIN TRANSACTION");
		if (!encrypted)
			db.beginTransaction();
		else
			encdb.beginTransaction();
	}

	/**
	 * Mark transaction as successful - so that endTransaction will do a commit
	 */
	public void setTransactionSuccessful() {
		Log.i(tag, "Calling setTransactionSuccessful");
		if (!encrypted)
			db.setTransactionSuccessful();
		else
			encdb.setTransactionSuccessful();
	}
	
	/**
	 * End transaction (after first logging it)
	 */
	public void endTransaction() {
		Log.i(tag, "END TRANSACTION");
		if (!encrypted)
			db.endTransaction();
		else 
			encdb.endTransaction();
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
		String columnsStr = (columns == null ? "" : TextUtils.join(",", columns));
		columnsStr = (columnsStr.equals("") ? "*" : columnsStr);
		String orderByStr = (orderBy == null ? "" : " ORDER BY " + orderBy);
		String selectionStr = (selection == null ? "" : " WHERE " + selection);
		Log.i(tag, String.format("SELECT %s FROM %s %s%s", columnsStr, table, selectionStr, orderByStr));
		if (!encrypted)
			return db.query(table, columns, selection, null, null, null, orderBy);
		else
			return encdb.query(table, columns, selection, null, null, null, orderBy);
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
		if (!encrypted)
			return db.insert(table, null, contentValues);
		else
			return encdb.insert(table, null, contentValues);
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
		if (!encrypted)
			return db.update(table, contentValues, whereClause, null);
		else
			return encdb.update(table, contentValues, whereClause, null);
	}
	
	/**
	 * Does a delete (after first logging the delete statement)
	 * @param table
	 * @param whereClause
	 */
	public void delete(String table, String whereClause) {
		Log.i(tag, String.format("DELETE FROM %s WHERE %s", table, whereClause));
		if (!encrypted)
			db.delete(table, whereClause, null);
		else
			encdb.delete(table, whereClause, null);
	}
}

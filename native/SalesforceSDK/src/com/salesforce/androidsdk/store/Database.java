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

	public Database(SQLiteDatabase db) {
		this.db = db;
		this.encrypted = false;
	}
	
	public Database(info.guardianproject.database.sqlcipher.SQLiteDatabase encdb) {
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
		Log.i("Database:execSQL[enc=" + encrypted + "]", sql);
		if (!encrypted)
			db.execSQL(sql);
		else
			encdb.execSQL(sql);
	}

	/**
	 * Start transaction (after first logging it)
	 */
	public void beginTransaction() {
		Log.i("Database:beginTransaction[enc=" + encrypted + "]", "");
		if (!encrypted)
			db.beginTransaction();
		else
			encdb.beginTransaction();
	}

	/**
	 * Mark transaction as successful - so that endTransaction will do a commit
	 */
	public void setTransactionSuccessful() {
		Log.i("Database:setTransactionSuccessful[enc=" + encrypted + "]", "");
		if (!encrypted)
			db.setTransactionSuccessful();
		else
			encdb.setTransactionSuccessful();
	}
	
	/**
	 * End transaction (after first logging it)
	 */
	public void endTransaction() {
		Log.i("Database:endTransaction[enc=" + encrypted + "]", "");
		if (!encrypted)
			db.endTransaction();
		else 
			encdb.endTransaction();
	}

	/**
	 * Runs a count query (after first logging the select statement)
	 * @param table
	 * @param orderBy
	 * @param limit
	 * @param whereClause
	 * @param whereArgs
	 * @return
	 */
	public Cursor countQuery(String table, String whereClause, String... whereArgs) {
		String selectionStr = (whereClause == null ? "" : " WHERE " + whereClause);
		String sql = String.format("SELECT count(*) FROM %s %s", table, selectionStr);
		Log.i("Database:query[enc=" + encrypted + "]", sql + getStringForArgs(whereArgs));
		if (!encrypted)
			return db.rawQuery(sql, whereArgs);
		else
			return encdb.rawQuery(sql, whereArgs);
	}

	/**
	 * Runs a query (after first logging the select statement)
	 * @param table
	 * @param columns
	 * @param orderBy
	 * @param limit
	 * @param whereClause
	 * @param whereArgs
	 * @return
	 */
	public Cursor query(String table, String[] columns, String orderBy, String limit, String whereClause, String... whereArgs) {
		String columnsStr = (columns == null ? "" : TextUtils.join(",", columns));
		columnsStr = (columnsStr.equals("") ? "*" : columnsStr);
		String orderByStr = (orderBy == null ? "" : "ORDER BY " + orderBy);
		String selectionStr = (whereClause == null ? "" : " WHERE " + whereClause);
		String limitStr = (limit == null ? "" : "LIMIT " + limit);
		String sql = String.format("SELECT %s FROM %s %s %s %s", columnsStr, table, selectionStr, orderByStr, limitStr);
//		Log.i("Database:query[enc=" + encrypted + "]", sql + getStringForArgs(whereArgs));
		if (!encrypted)
			return db.query(table, columns, whereClause, whereArgs, null, null, orderBy, limit);
		else
			return encdb.query(table, columns, whereClause, whereArgs, null, null, orderBy, limit);
	}

	/**
	 * Does an insert (after first logging the insert statement)
	 * @param table
	 * @param contentValues
	 * @return row id of inserted row
	 */
	public long insert(String table, ContentValues contentValues) {
		Pair<String, String> columnsValues = LogUtil.getAsStrings(contentValues.valueSet(), ", ");
		String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", table, columnsValues.first, columnsValues.second);
//		Log.i("Database:insert[enc=" + encrypted + "]", sql);
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
	 * @param whereArgs
	 * @return number of rows affected
	 */
	public int update(String table, ContentValues contentValues, String whereClause, String... whereArgs) {
		String setStr = LogUtil.zipJoin(contentValues.valueSet(), " = ", ", ");
		String sql = String.format("UPDATE %s SET %s WHERE %s", table, setStr, whereClause);
//		Log.i("Database:update[enc=" + encrypted + "]", sql + getStringForArgs(whereArgs));
		if (!encrypted)
			return db.update(table, contentValues, whereClause, whereArgs);
		else
			return encdb.update(table, contentValues, whereClause, whereArgs);
	}
	
	/**
	 * Does a delete (after first logging the delete statement)
	 * @param table
	 * @param whereClause
	 * @param whereArgs
	 */
	public void delete(String table, String whereClause, String... whereArgs) {
		String sql = String.format("DELETE FROM %s WHERE %s", table, whereClause);
		Log.i("Database:delete[enc=" + encrypted + "]", sql + getStringForArgs(whereArgs));
		if (!encrypted)
			db.delete(table, whereClause, whereArgs);
		else
			encdb.delete(table, whereClause, whereArgs);
	}
	
	/**
	 * Helper method used for logging binding args
	 * @param whereArgs
	 * @return
	 */
	protected String getStringForArgs(String... whereArgs) {
		return whereArgs == null ? "" : " [Args=" + TextUtils.join(",", whereArgs) + "]";
	}
	
	
}

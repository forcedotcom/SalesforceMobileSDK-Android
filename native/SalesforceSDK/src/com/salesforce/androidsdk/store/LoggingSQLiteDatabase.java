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

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;


/**
 * SQLiteDatabase that does a bunch of logging 
 */
public class LoggingSQLiteDatabase {

	private SQLiteDatabase db;

	public LoggingSQLiteDatabase(SQLiteDatabase db) {
		this.db = db;
	}
	
	/**
	 * Execute arbitrary SQL (after first logging it)
	 * @param tag
	 * @param sql
	 */
	public void execSQL(String tag, String sql) {
		Log.i(tag, sql);
		db.execSQL(sql);
	}
	
	/**
	 * Runs a query (after first logging the select statement)
	 * 
	 * TODO: we should compile the statements and use bindings
	 * 
	 * @param tag
	 * @param table
	 * @param columns
	 * @param selection
	 * @return
	 */
	public Cursor query(String tag, String table, String[] columns, String selection) {
		Log.i(tag, String.format("SELECT %s FROM %s WHERE %s", join(columns, ","), selection));
		return db.query(table, columns, selection, null, null, null, null);
	}

	/**
	 * Start transaction (after first logging it)
	 * @param tag
	 */
	public void beginTransaction(String tag) {
		Log.i(tag, "BEGIN TRANSACTION");
		db.beginTransaction();
	}

	/**
	 * End transaction (after first logging it)
	 * @param tag
	 */
	public void endTransaction(String tag) {
		Log.i(tag, "END TRANSACTION");
		db.endTransaction();
	}

	/**
	 * Does a insert (after first logging the insert statement)
	 * @param tag
	 * @param table
	 * @param contentValues
	 */
	public void insert(String tag, String table, ContentValues contentValues) {
		List<String> columnNames = new ArrayList<String>();
		List<String> values = new ArrayList<String>();
		for (String key : contentValues.keySet()) {
			columnNames.add(key);
			Object value = contentValues.get(key);
			values.add(value instanceof String ? "'" + value + "'" : value.toString());
		}
		Log.i(tag, String.format("INSERT INTO %s (%s) VALUES (%s)", table, join(columnNames, ","), join(values, ",")));
		db.insert(table, null, contentValues);
	}
	
	/**
	 * Does a delete (after first logging the delete statement)
	 * @param tag
	 * @param table
	 * @param whereClause
	 */
	public void delete(String tag, String table, String whereClause) {
		Log.i(tag, String.format("DELETE FROM %s WHERE %s", table, whereClause));
		db.delete(table, whereClause, null);
	}
	
	
	/**
	 * Take a list and a delimiter and return a delimiter-delimited string representation 
	 * @param list
	 * @param delim
	 * @return
	 */
	private String join(List<String> list, String delim) {
		return join(list.toArray(new String[0]), delim);
	}
	
	/**
	 * Take an array and a delimiter and return a delimiter-delimited string representation 
	 * @param arr
	 * @param delim
	 * @return
	 */
	private String join(String[] arr, String delim) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		for (String elt : arr) {
			sb.append(elt);
			if (i < arr.length - 1) sb.append(delim);
			i++;
		}
		return sb.toString();
	}

}

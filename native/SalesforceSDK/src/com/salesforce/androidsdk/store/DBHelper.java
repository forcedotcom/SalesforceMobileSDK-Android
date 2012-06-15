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

import info.guardianproject.database.DatabaseUtils.InsertHelper;
import info.guardianproject.database.sqlcipher.SQLiteDatabase;
import info.guardianproject.database.sqlcipher.SQLiteDoneException;
import info.guardianproject.database.sqlcipher.SQLiteStatement;

import java.util.HashMap;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.salesforce.androidsdk.store.SmartStore.IndexSpec;


/**
 * SmartStore Database Helper
 * 
 * Singleton class that provides helpful methods for accessing the database underneath the SmartStore
 * It also caches a number of of things to speed things up (e.g. soup table name, index specs, insert helpers etc)
 */
public enum DBHelper  {
	
	INSTANCE;

	// Some queries
	private static final String COUNT_SELECT = "SELECT count(*) FROM %s %s";
	private static final String SEQ_SELECT = "SELECT seq FROM SQLITE_SEQUENCE WHERE name = ?";
	
	// Cache of soup name to soup table names
	private Map<String, String> soupNameToTableNamesMap = new HashMap<String, String>();
	
	// Cache of soup name to index specs
	private Map<String, IndexSpec[]> soupNameToIndexSpecsMap = new HashMap<String, SmartStore.IndexSpec[]>();
	
	// Cache of table name to get-next-id compiled statements
	private Map<String, SQLiteStatement> tableNameToNextIdStatementsMap = new HashMap<String, SQLiteStatement>();
	
	// Cache of table name to insert helpers
	private Map<String, InsertHelper> tableNameToInsertHelpersMap = new HashMap<String, InsertHelper>();
	
	/**
	 * @param soupName
	 * @param tableName
	 */
	public void cacheTableName(String soupName, String tableName) {
		soupNameToTableNamesMap.put(soupName, tableName);
	}
	
	/**
	 * @param soupName
	 * @return
	 */
	public String getCachedTableName(String soupName) {
		return soupNameToTableNamesMap.get(soupName);
	}

	/**
	 * @param soupName
	 * @param tableName
	 */
	public void cacheIndexSpecs(String soupName, IndexSpec[] indexSpecs) {
		soupNameToIndexSpecsMap.put(soupName, indexSpecs.clone());
	}

	/**
	 * @param soupName
	 * @return
	 */
	public IndexSpec[] getCachedIndexSpecs(String soupName) {
		return soupNameToIndexSpecsMap.get(soupName);
	}
	
	/**
	 * @param soupName
	 */
	public void removeFromCache(String soupName) {
		String tableName = soupNameToTableNamesMap.get(soupName);
		if (tableName != null) {
			InsertHelper ih = tableNameToInsertHelpersMap.remove(tableName);
			if (ih != null) 
				ih.close();
			
			SQLiteStatement prog = tableNameToNextIdStatementsMap.remove(tableName);
			if (prog != null) 
				prog.close();
		}
		soupNameToTableNamesMap.remove(soupName);
		soupNameToIndexSpecsMap.remove(soupName);
	}
	
	/**
	 * Get next id for a table
	 * 
	 * @param db
	 * @param tableName
	 * @return long
	 */
	public long getNextId(SQLiteDatabase db, String tableName) {
		SQLiteStatement prog = tableNameToNextIdStatementsMap.get(tableName);
		if (prog == null) {
			prog = db.compileStatement(SEQ_SELECT);
			prog.bindString(1, tableName);
			tableNameToNextIdStatementsMap.put(tableName, prog);
		}
		
		try {
			return prog.simpleQueryForLong() + 1;
		} catch (SQLiteDoneException e) {
			// first time, we don't find any row for the table in the sequence table
			return 1L;
		}
	}

	/**
	 * Get insert helper for a table
	 * @param table
	 * @return
	 */
	public InsertHelper getInsertHelper(SQLiteDatabase db, String table) {
		InsertHelper insertHelper = tableNameToInsertHelpersMap.get(table);

		if (insertHelper == null) {
			insertHelper = new InsertHelper(db, table);
			tableNameToInsertHelpersMap.put(table, insertHelper);
		}
		return insertHelper;
	}

	/**
	 * Does a count query
	 * @param db
	 * @param table
	 * @param whereClause
	 * @param whereArgs
	 * @return
	 */
	public Cursor countQuery(SQLiteDatabase db, String table, String whereClause, String... whereArgs) {
		String selectionStr = (whereClause == null ? "" : " WHERE " + whereClause);
		String sql = String.format(COUNT_SELECT, table, selectionStr);
		return db.rawQuery(sql, whereArgs);
	}
	
	/**
	 * Runs a query
	 * @param db
	 * @param table
	 * @param columns
	 * @param orderBy
	 * @param limit
	 * @param whereClause
	 * @param whereArgs
	 * @return
	 */
	public Cursor query(SQLiteDatabase db, String table, String[] columns, String orderBy, String limit, String whereClause, String... whereArgs) {
		return db.query(table, columns, whereClause, whereArgs, null, null, orderBy, limit);
	}

	/**
	 * Does an insert
	 * @param db
	 * @param table
	 * @param contentValues
	 * @return row id of inserted row
	 */
	public long insert(SQLiteDatabase db, String table, ContentValues contentValues) {
		InsertHelper ih = getInsertHelper(db, table);
		return ih.insert(contentValues);
	}

	/**
	 * Does an update
	 * @param db
	 * @param table
	 * @param contentValues
	 * @param whereClause
	 * @param whereArgs
	 * @return number of rows affected
	 */
	public int update(SQLiteDatabase db, String table, ContentValues contentValues, String whereClause, String... whereArgs) {
		return db.update(table, contentValues, whereClause, whereArgs);
	}
	
	/**
	 * Does a delete (after first logging the delete statement)
	 * @param db
	 * @param table
	 * @param whereClause
	 * @param whereArgs
	 */
	public void delete(SQLiteDatabase db, String table, String whereClause, String... whereArgs) {
		db.delete(table, whereClause, whereArgs);
	}
	
	/**
	 * Reset all cached data and delete database
	 * @param ctx
	 */
	public void reset(Context ctx) {
		// Close all statements
		for(InsertHelper  ih : tableNameToInsertHelpersMap.values()) {
			ih.close();
		}
		for (SQLiteStatement prog : tableNameToNextIdStatementsMap.values()) {
			prog.close();
		}
		
		// Clear all maps
		soupNameToTableNamesMap.clear();
		soupNameToIndexSpecsMap.clear();
		tableNameToInsertHelpersMap.clear();
		tableNameToNextIdStatementsMap.clear();

		DBOpenHelper.deleteDatabase(ctx);
	}

}
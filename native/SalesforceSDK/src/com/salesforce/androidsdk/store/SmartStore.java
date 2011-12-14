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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


/**
 * Smart store 
 * 
 * Provides a secure means for SalesforceMobileSDK Container-based applications to store objects in a persistent
 * and searchable manner. Similar in some ways to CouchDB, SmartStore stores documents as JSON values.  
 * SmartStore is inspired by the Apple Newton OS Soup/Store model. 
 * The main challenge here is how to effectively store documents with dynamic fields, and still allow indexing and searching.
 * 
 * TODO: will eventually be invoke through a content provider
 */
public class SmartStore  {
	// Table to keep track of soup's index specs
	private static final String SOUP_INDEX_MAP_TABLE = "soup_index_map";
	
	// Columns of the soup index map table
	private static final String SOUP_NAME_COL = "soupName";
	private static final String PATH_COL = "path";
	private static final String COLUMN_NAME_COL = "columnName";
	private static final String COLUMN_TYPE_COL = "columnType";
	
	// Column holding actual soup in the soup table
	private static final String SOUP_COL = "soup";
	
	// Key actions - used as tags in log
	private static final String REGISTER_SOUP = "SmartStore:registerSoup";
	private static final String DROP_SOUP = "SmartStore:dropSoup";
	private static final String QUERY_SOUP = "SmartStore:querySoup";
	private static final String CREATE_META_TABLE = "SmartStore:createMetaTable";
	
	/**
	 * Create a soup
	 * 
	 * Create table for soupName with a column for the soup itself and a column per projection to index
	 * Create indexes on the new table to make lookup faster
	 * Create rows in soup index map table for indexSpecs
	 * 
	 * @param ctx
	 * @param soupName
	 * @param indexSpecs
	 */
	public void registerSoup(Context ctx, String soupName, IndexSpec[] indexSpecs) {
		ContentValues values = new ContentValues();             // to be inserted in soup idx map table
		StringBuilder createTableStmt = new StringBuilder();    // to create new soup table
		List<String> createIndexStmts = new ArrayList<String>();  // to create indexes on new soup table
		
		createTableStmt.append("CREATE TABLE ").append(soupName).append(" (")
					   .append(SOUP_COL).append(" ").append(Type.TEXT.toString());
		
		int i = 0;
		for (IndexSpec indexSpec : indexSpecs) {
			String columnName = soupName + "_" + i;
			String indexName = soupName + "_" + i + "_idx";
			String columnType = indexSpec.type.toString();
			
			// for insert
			values.put(SOUP_NAME_COL, soupName);
			values.put(PATH_COL, indexSpec.path);
			values.put(COLUMN_NAME_COL, columnName);
			values.put(COLUMN_TYPE_COL, columnType);
			
			// create table
			createTableStmt.append(columnName).append(" ").append(columnType);
			if (i < indexSpecs.length - 1) {
				createTableStmt.append(",");
			}

			// create index
			createIndexStmts.add(String.format("CREATE INDEX %s on %s ( %s )", indexName, soupName, columnName));;
			
			i++;
		}
		createTableStmt.append(")");
		
		
		LoggingSQLiteDatabase db = DBOperations.getWritableDatabase(ctx);
		db.execSQL(REGISTER_SOUP, createTableStmt.toString());
		for (String createIndexStmt : createIndexStmts) {
			db.execSQL(REGISTER_SOUP, createIndexStmt.toString());
		}
		db.beginTransaction(REGISTER_SOUP);
		db.insert(REGISTER_SOUP, SOUP_INDEX_MAP_TABLE, values);
		db.endTransaction(REGISTER_SOUP);
	}
	
	/**
	 * Destroy a soup
	 * 
	 * Drop table for soupName 
	 * Cleanup entries in soup index map table  
	 * 
	 * @param ctx
	 * @param soupName
	 */
	public void dropSoup(Context ctx, String soupName) {
		LoggingSQLiteDatabase db = DBOperations.getWritableDatabase(ctx);
		db.execSQL(DROP_SOUP, "DROP TABLE " + soupName);
		db.beginTransaction(DROP_SOUP);
		db.delete(DROP_SOUP, SOUP_INDEX_MAP_TABLE, getSoupNamePredicate(soupName));
		db.endTransaction(DROP_SOUP);
	}

	/**
	 * Run a query
	 * @param ctx
	 * @param soupName
	 * @param querySpec
	 * @return
	 * @throws JSONException 
	 */
	public JSONArray querySoup(Context ctx, String soupName, QuerySpec querySpec) throws JSONException {
		LoggingSQLiteDatabase db = DBOperations.getReadableDatabase(ctx);
		String columnName = null;
		
		// First get index column for the querySpec path
		Cursor cursor = null;
		try {
			cursor = db.query(QUERY_SOUP, SOUP_INDEX_MAP_TABLE, new String[] {COLUMN_NAME_COL}, getSoupNamePredicate(soupName) + " AND " + getPathPredicate(querySpec.path));
			if (cursor.moveToFirst()) {
				columnName = cursor.getString(0);
			}
			else {
				throw new RuntimeException(String.format("%s does not have an index on %s", soupName, querySpec.path));
			}
		}
		finally {
			if (cursor != null) {
				cursor.close();
				cursor = null;
			}
		}
		
		// Now get the matching soups
		try {
			cursor = db.query(QUERY_SOUP, soupName, new String[] {SOUP_COL}, getKeyPredicate(columnName, querySpec.beginKey, querySpec.endKey));
			// TODO pass in the querySpec.order as well
			
			
			JSONArray result = new JSONArray();
			if (cursor.moveToFirst()) {
				do {
					JSONObject soup = new JSONObject(cursor.getString(0));
					if (querySpec.projections == null) {
						result.put(soup);
					}
					else {
						JSONObject subSoup = new JSONObject();
						for (String projection : querySpec.projections) {
							subSoup.put(projection, soup.get(projection));
							// TODO can projection be paths or just names?
						}
						result.put(subSoup);
					}
				}
				while (cursor.moveToNext());
			}
			
			return result;			
		}
		finally {
			if (cursor != null) {
				cursor.close();
				cursor = null;
			}
		}
		
	}

	/**
	 * TODO: revisit this method once we support other indexed column type
	 * 
	 * @param columnName
	 * @param beginKey
	 * @param endKey
	 * @return
	 */
	private String getKeyPredicate(String columnName, String beginKey, String endKey) {
		if (endKey == null || endKey.equals(beginKey)) {
			return columnName + " = '" + beginKey + "'";
		}
		else {
			return columnName + " >= '" + beginKey + "' AND " + columnName + " <= '" + endKey; 
		}
	}

	/**
	 * @param soupName
	 * @return
	 */
	private String getSoupNamePredicate(String soupName) {
		return SOUP_NAME_COL + " = '" + soupName + "'";
	}
	
	/**
	 * @param path
	 * @return
	 */
	private String getPathPredicate(String path) {
		return PATH_COL + " = '" + path  + "'";
	}

	/**
	 * Create soup index map table to keep track of soups' index specs
	 * Called when the database is first created
	 * 
	 * @param db
	 */
	public static void createMetaTable(SQLiteDatabase db) {
		final String createStmt = "CREATE TABLE "+ SOUP_INDEX_MAP_TABLE + " (" + 
				  			"soupName TEXT, " +
				  			"path TEXT," +
				  			"columnName TEXT," +
				  			"columnType TEXT" +
				  			")";
		new LoggingSQLiteDatabase(db).execSQL(CREATE_META_TABLE, createStmt);
	}
	
	
	/**
	 * Enum for column type
	 */
	public enum Type {
		TEXT;
	}
	
	/**
	 * Simple class to represent index spec
	 * 
	 * TODO: add constructor that takes JSON
	 */
	public static class IndexSpec {
		public final String path;
		public final Type type;
		
		public IndexSpec(String path, Type type) {
			this.path = path;
			this.type = type;
		}
	}

	/**
	 * Simple class to represent a query spec
	 * 
	 * TODO: add constructor that takes JSON
	 */
	public static class QuerySpec {
		public final String path;
		public final String beginKey;
		public final String endKey;
		public final Order order;
		public final String[] projections;

		public QuerySpec(String path, String matchKey) {
			this(path, matchKey, null);
		}
		
		public QuerySpec(String path, String matchKey, String[] projections) {
			this(path, matchKey, matchKey, null, projections);
		}
		
		public QuerySpec(String path, String beginKey, String endKey, Order order, String[] projections) {
			this.path = path;
			this.beginKey = beginKey;
			this.endKey = endKey;
			this.order = order;
			this.projections = projections;
		}
	}

	/**
	 * Simple class to represent query order (used by QuerySpec)
	 */
	public enum Order {
		ASCENDING,
		DESCENDING;
	}
}
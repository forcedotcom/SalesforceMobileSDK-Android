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
import android.database.Cursor;


/**
 * Smart store 
 * 
 * Provides a secure means for SalesforceMobileSDK Container-based applications to store objects in a persistent
 * and searchable manner. Similar in some ways to CouchDB, SmartStore stores documents as JSON values.  
 * SmartStore is inspired by the Apple Newton OS Soup/Store model. 
 * The main challenge here is how to effectively store documents with dynamic fields, and still allow indexing and searching.
 */
public class SmartStore  {
	// Table to keep track of soup's index specs
	protected static final String SOUP_INDEX_MAP_TABLE = "soup_index_map";
	
	// Columns of the soup index map table
	protected static final String SOUP_NAME_COL = "soupName";
	protected static final String PATH_COL = "path";
	protected static final String COLUMN_NAME_COL = "columnName";
	protected static final String COLUMN_TYPE_COL = "columnType";
	
	// Columns of a soup table
	protected static final String ID_COL = "id";
	protected static final String CREATED_COL = "created";
	protected static final String LAST_MODIFIED_COL = "lastModified";
	protected static final String SOUP_COL = "soup";
	
	
	/**
	 * Create soup index map table to keep track of soups' index specs
	 * Called when the database is first created
	 * 
	 * @param db
	 */
	public static void createMetaTable(Database db) {
		StringBuilder sb = new StringBuilder();
		sb.append("CREATE TABLE ").append(SOUP_INDEX_MAP_TABLE).append(" (") 
				  	.append(SOUP_NAME_COL).append(" ").append(Type.TEXT.toString())
				  	.append(",").append(PATH_COL).append(" ").append(Type.TEXT.toString())
				  	.append(",").append(COLUMN_NAME_COL).append(" ").append(Type.TEXT.toString())
				  	.append(",").append(COLUMN_TYPE_COL).append(" ").append(Type.TEXT.toString())
				  	.append(")");
		db.execSQL(sb.toString());
	}
	
	
	/**
	 * Register a soup 
	 * 
	 * Create table for soupName with a column for the soup itself and columns for paths specified in indexSpecs
	 * Create indexes on the new table to make lookup faster
	 * Create rows in soup index map table for indexSpecs
	 * @param db≈ß
	 * @param soupName
	 * @param indexSpecs
	 */
	public void registerSoup(Database db, String soupName, IndexSpec[] indexSpecs) {
		StringBuilder createTableStmt = new StringBuilder();          // to create new soup table
		List<String> createIndexStmts = new ArrayList<String>();      // to create indices on new soup table
		List<ContentValues> soupIndexMapInserts = new ArrayList<ContentValues>();  // to be inserted in soup index map table
		
		createTableStmt.append("CREATE TABLE ").append(soupName).append(" (")
						.append(ID_COL).append(" ").append(Type.INTEGER.toString()).append(" PRIMARY KEY AUTOINCREMENT")
					    .append(", ").append(SOUP_COL).append(" ").append(Type.TEXT.toString())
					    .append(", ").append(CREATED_COL).append(" ").append(Type.INTEGER.toString())
					    .append(", ").append(LAST_MODIFIED_COL).append(" ").append(Type.INTEGER.toString());
		
		int i = 0;
		for (IndexSpec indexSpec : indexSpecs) {
			// for create table
			String columnName = soupName + "_" + i;
			String columnType = indexSpec.type.toString();
			createTableStmt.append(", ").append(columnName).append(" ").append(columnType);
			
			// for insert
			ContentValues values = new ContentValues();
			values.put(SOUP_NAME_COL, soupName);
			values.put(PATH_COL, indexSpec.path);
			values.put(COLUMN_NAME_COL, columnName);
			values.put(COLUMN_TYPE_COL, columnType);
			soupIndexMapInserts.add(values);
			
			// for create index
			String indexName = soupName + "_" + i + "_idx";
			createIndexStmts.add(String.format("CREATE INDEX %s on %s ( %s )", indexName, soupName, columnName));;
			
			i++;
		}
		createTableStmt.append(")");
		
		
		db.execSQL(createTableStmt.toString());
		for (String createIndexStmt : createIndexStmts) {
			db.execSQL(createIndexStmt.toString());
		}
		
		try {
			db.beginTransaction();
			for (ContentValues values : soupIndexMapInserts) {
				db.insert(SOUP_INDEX_MAP_TABLE, values);
			}
			db.setTransactionSuccessful();
		}
		finally {
			db.endTransaction();
		}
		
	}
	
	/**
	 * Destroy a soup
	 * 
	 * Drop table for soupName 
	 * Cleanup entries in soup index map table  
	 * @param db
	 * @param soupName
	 */
	public void dropSoup(Database db, String soupName) {
		db.execSQL("DROP TABLE IF EXISTS " + soupName);
		try {
			db.beginTransaction();
			db.delete(SOUP_INDEX_MAP_TABLE, getSoupNamePredicate(soupName));
			db.setTransactionSuccessful();
		}
		finally {
			db.endTransaction();
		}
	}

	/**
	 * Run a query
	 * @param db
	 * @param soupName
	 * @param querySpec
	 * @return
	 * @throws JSONException 
	 */
	public JSONArray querySoup(Database db, String soupName, QuerySpec querySpec) throws JSONException {
		String columnName = getColumnNameForPath(db, soupName, querySpec.path);
		
		// Get the matching soups
		Cursor cursor = null;
		try {
			cursor = db.query(soupName, new String[] {ID_COL, SOUP_COL}, getKeyRangePredicate(columnName, querySpec.beginKey, querySpec.endKey), columnName + " " + querySpec.order);
			
			JSONArray results = new JSONArray();
			if (cursor.moveToFirst()) {
				do {
					JSONObject soupElt = new JSONObject(cursor.getString(cursor.getColumnIndex(SOUP_COL)));
					
					if (querySpec.projections == null) {
						results.put(soupElt);
					}
					else {
						JSONObject subSoup = new JSONObject();
						for (String projection : querySpec.projections) {
							subSoup.put(projection, project(soupElt, projection));
						}
						results.put(subSoup);
					}
				}
				while (cursor.moveToNext());
			}
			
			return results;			
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		
	}

	/**
	 * Create 
	 * @param db
	 * @param soupName
	 * @param soupElt
	 * 
	 * @return rowId of inserted row
	 */
	public long create(Database db, String soupName, JSONObject soupElt) {
		IndexSpec[] indexSpecs = getIndexSpecs(db, soupName);
		
		long now = System.currentTimeMillis();
		ContentValues contentValues = new ContentValues();
		contentValues.put(SOUP_COL, soupElt.toString());
		contentValues.put(CREATED_COL, now);
		contentValues.put(LAST_MODIFIED_COL, now);
		for (IndexSpec indexSpec : indexSpecs) {
			contentValues.put(indexSpec.columnName, (String) project(soupElt, indexSpec.path));
		}
		
		try {
			db.beginTransaction();
			long rowId = db.insert(soupName, contentValues);
			db.setTransactionSuccessful();
			return rowId;
		}
		finally {
			db.endTransaction();
		}
	}

	/**
	 * Retrieve 
	 * @param db
	 * @param soupName
	 * @param rowId
	 * 
	 * @return retrieve JSONObject with the given rowId or null if not found
	 * @throws JSONException 
	 */
	public JSONObject retrieve(Database db, String soupName, long rowId) throws JSONException {
		Cursor cursor = null;
		try {
			cursor = db.query(soupName, new String[] {SOUP_COL}, getRowIdPredicate(rowId), null);
			if (!cursor.moveToFirst()) {
				return null;
			}
			String raw = cursor.getString(cursor.getColumnIndex(SOUP_COL));
			return new JSONObject(raw);
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}
	

	/**
	 * Update 
	 * @param db
	 * @param soupName
	 * @param soupElt
	 * @param rowId
	 * 
	 * @return true if successful
	 */
	public boolean update(Database db, String soupName, JSONObject soupElt, long rowId) {
		IndexSpec[] indexSpecs = getIndexSpecs(db, soupName);
		
		long now = System.currentTimeMillis();
		ContentValues contentValues = new ContentValues();
		contentValues.put(SOUP_COL, soupElt.toString());
		contentValues.put(LAST_MODIFIED_COL, now);
		for (IndexSpec indexSpec : indexSpecs) {
			contentValues.put(indexSpec.columnName, (String) project(soupElt, indexSpec.path));
		}
		
		try {
			db.beginTransaction();
			boolean success = db.update(soupName, contentValues, getRowIdPredicate(rowId)) == 1;
			db.setTransactionSuccessful();
			return success;
		}
		finally {
			db.endTransaction();
		}
	}
	
	/**
	 * Delete
	 * @param db
	 * @param soupName
	 * @param rowId
	 */
	public void delete(Database db, String soupName, long rowId) {
		db.beginTransaction();
		db.delete(soupName, getRowIdPredicate(rowId));
		db.setTransactionSuccessful();
		db.endTransaction();
	}

	/**
	 * Return column name in soup table that holds the soup projection for path
	 * @param db
	 * @param soupName
	 * @param path
	 * @return
	 */
	protected String getColumnNameForPath(Database db, String soupName, String path) {
		Cursor cursor = null;
		try {
			cursor = db.query(SOUP_INDEX_MAP_TABLE, new String[] {COLUMN_NAME_COL}, getSoupNamePredicate(soupName) + " AND " + getPathPredicate(path), null);
			if (cursor.moveToFirst()) {
				return cursor.getString(0);
			}
			else {
				throw new RuntimeException(String.format("%s does not have an index on %s", soupName, path));
			}
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}
	
	/**
	 * Read index specs back from the soup index map table
	 * @param db
	 * @param soupName
	 * @return
	 */
	protected IndexSpec[] getIndexSpecs(Database db, String soupName) {
		Cursor cursor = null;
		try {
			cursor = db.query(SOUP_INDEX_MAP_TABLE, new String[] {PATH_COL, COLUMN_NAME_COL, COLUMN_TYPE_COL}, getSoupNamePredicate(soupName), null);
		
			if (!cursor.moveToFirst()) {
				throw new RuntimeException(String.format("%s does not have any indices", soupName));				
			}

			List<IndexSpec> indexSpecs = new ArrayList<IndexSpec>();
			do {
				String path = cursor.getString(cursor.getColumnIndex(PATH_COL));
				String columnName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_COL));
				Type columnType = Type.valueOf(cursor.getString(cursor.getColumnIndex(COLUMN_TYPE_COL)));
				indexSpecs.add(new IndexSpec(path, columnType, columnName));
			}
			while (cursor.moveToNext());
			
			return indexSpecs.toArray(new IndexSpec[0]);
		}
		finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}
	
	/**
	 * FIXME range predicate will be different for non-string keys (won't have the '')
	 * 
	 * @param columnName
	 * @param beginKey
	 * @param endKey
	 * @return range predicate
	 */
	protected String getKeyRangePredicate(String columnName, String beginKey, String endKey) {
		if (endKey == null || endKey.equals(beginKey)) {
			return columnName + " = '" + beginKey + "'";
		}
		else {
			return columnName + " >= '" + beginKey + "' AND " + columnName + " <= '" + endKey + "'"; 
		}
	}

	/**
	 * @param soupName
	 * @return
	 */
	protected String getSoupNamePredicate(String soupName) {
		return SOUP_NAME_COL + " = '" + soupName + "'";
	}
	
	/**
	 * @param rowId
	 * @return
	 */
	protected String getRowIdPredicate(long rowId) {
		return ID_COL + " = " + rowId;
	}
	
	/**
	 * @param path
	 * @return
	 */
	protected String getPathPredicate(String path) {
		return PATH_COL + " = '" + path  + "'";
	}
	
	/**
	 * @param soup
	 * @param path
	 * @return object at path in soup
	 */
	public static Object project(JSONObject soup, String path) {
		if (soup == null) {
			return null;
		}
		if (path == null || path.equals("") || path.equals("/")) {
			return soup;
		}
		
		if (path.startsWith("/"))
			path = path.substring(1);
		
		String[] pathElements = path.split("/");
		Object o = soup;
		for (String pathElement : pathElements) {
			o = ((JSONObject) o).opt(pathElement);
		}
		return o;
	}

	/**
	 * Enum for column type
	 */
	public enum Type {
		TEXT, INTEGER;
	}
	
	/**
	 * Simple class to represent index spec
	 */
	public static class IndexSpec {
		public final String path;
		public final Type type;
		public final String columnName;
		
		public IndexSpec(String path, Type type) {
			this.path = path;
			this.type = type;
			this.columnName = null; // undefined
		}
		
		public IndexSpec(String path, Type type, String columnName) {
			this.path = path;
			this.type = type;
			this.columnName = columnName;
		}
		
	}

	/**
	 * Simple class to represent a query spec
	 */
	public static class QuerySpec {
		public final String path;
		public final String beginKey;
		public final String endKey;
		public final Order order;
		public final String[] projections;

		/**
		 * Exact match (return whole soup element)
		 * @param path
		 * @param matchKey
		 */
		public QuerySpec(String path, String matchKey) {
			this(path, matchKey, matchKey, null, Order.ASC);
		}
		
		
		/**
		 * Exact match (return selected projections)
		 * @param path
		 * @param matchKey
		 * @param projections
		 */
		public QuerySpec(String path, String matchKey, String[] projections) {
			this(path, matchKey, matchKey, projections, Order.ASC);
		}
		
		/**
		 * Range query (return whole soup elements in ascending order for the values at path)
		 * @param path
		 * @param beginKey
		 * @param endKey
		 */
		public QuerySpec(String path, String beginKey, String endKey) {
			this(path, beginKey, endKey, null, Order.ASC);
		}

		/**
		 * Range query (return selected projections in ascending order for the values at path)
		 * @param path
		 * @param beginKey
		 * @param endKey
		 */
		public QuerySpec(String path, String beginKey, String endKey, String[] projections) {
			this(path, beginKey, endKey, projections, Order.ASC);
		}
		
		/**
		 * Range query (return whole soup elements in specified order)
		 * @param path
		 * @param beginKey
		 * @param endKey
		 * @param order
		 */
		public QuerySpec(String path, String beginKey, String endKey, Order order) {
			this(path, beginKey, endKey, null, order);
		}

		/**
		 * Range query (return selected projections in specified order for the values at path)
		 * @param path
		 * @param beginKey
		 * @param endKey
		 * @param projections
		 * @param order
		 */
		public QuerySpec(String path, String beginKey, String endKey, String[] projections, Order order) {
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
		ASC,
		DESC;
	}
}
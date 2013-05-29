/*
 * Copyright (c) 2012, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.store;

import java.util.ArrayList;
import java.util.List;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import com.salesforce.androidsdk.smartstore.store.QuerySpec.QueryType;

/**
 * Smart store
 *
 * Provides a secure means for SalesforceMobileSDK Container-based applications to store objects in a persistent
 * and searchable manner. Similar in some ways to CouchDB, SmartStore stores documents as JSON values.
 * SmartStore is inspired by the Apple Newton OS Soup/Store model.
 * The main challenge here is how to effectively store documents with dynamic fields, and still allow indexing and searching.
 */
public class SmartStore  {
    // Default
    public static final int DEFAULT_PAGE_SIZE = 10;

    // Table to keep track of soup names
    protected static final String SOUP_NAMES_TABLE = "soup_names";

    // Table to keep track of soup's index specs
    protected static final String SOUP_INDEX_MAP_TABLE = "soup_index_map";

    // Columns of the soup index map table
    protected static final String SOUP_NAME_COL = "soupName";
    protected static final String PATH_COL = "path";
    protected static final String COLUMN_NAME_COL = "columnName";
    protected static final String COLUMN_TYPE_COL = "columnType";
    
    // Predicates
    protected static final String SOUP_NAME_PREDICATE = SOUP_NAME_COL + " = ?";
    protected static final String PATH_PREDICATE = PATH_COL + " = ?";

    // Columns of a soup table
    protected static final String ID_COL = "id";
    protected static final String CREATED_COL = "created";
    protected static final String LAST_MODIFIED_COL = "lastModified";
    protected static final String SOUP_COL = "soup";

    // JSON fields added to soup element on insert/update
    public static final String SOUP_ENTRY_ID = "_soupEntryId";
    public static final String SOUP_LAST_MODIFIED_DATE = "_soupLastModifiedDate";
	private static final String SOUP_ENTRY_ID_PREDICATE = ID_COL + " = ?";

    // Backing database
    protected SQLiteDatabase db;

    /**
     * Changes the encryption key on the smartstore.
     *
     * @param db Database object.
     * @param newKey New encryption key.
     */
    public static synchronized void changeKey(SQLiteDatabase db, String newKey) {
        if (newKey != null && !newKey.trim().equals("")) {
            db.execSQL("PRAGMA rekey = '" + newKey + "'");
        }
    }

    /**
     * Create soup index map table to keep track of soups' index specs
     * Create soup name map table to keep track of soup name to table name mappings
     * Called when the database is first created
     *
     * @param db
     */
    public static void createMetaTables(SQLiteDatabase db) {
        // Create soup_index_map table
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(SOUP_INDEX_MAP_TABLE).append(" (")
                      .append(SOUP_NAME_COL).append(" TEXT")
                      .append(",").append(PATH_COL).append(" TEXT")
                      .append(",").append(COLUMN_NAME_COL).append(" TEXT")
                      .append(",").append(COLUMN_TYPE_COL).append(" TEXT")
                      .append(")");
        db.execSQL(sb.toString());
        // Add index on soup_name column
        db.execSQL(String.format("CREATE INDEX %s on %s ( %s )", SOUP_INDEX_MAP_TABLE + "_0", SOUP_INDEX_MAP_TABLE, SOUP_NAME_COL));

        // Create soup_names table
        // The table name for the soup will simply be table_<soupId>
        sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(SOUP_NAMES_TABLE).append(" (")
                    .append(ID_COL).append(" INTEGER PRIMARY KEY AUTOINCREMENT")
                    .append(",").append(SOUP_NAME_COL).append(" TEXT")
                      .append(")");
        db.execSQL(sb.toString());
        // Add index on soup_name column
        db.execSQL(String.format("CREATE INDEX %s on %s ( %s )", SOUP_NAMES_TABLE + "_0", SOUP_NAMES_TABLE, SOUP_NAME_COL));
    }

    /**
     * @param db
     */
    public SmartStore(SQLiteDatabase db) {
        this.db = db;
    }

    /**
     * Start transaction
     */
    public void beginTransaction() {
        db.beginTransaction();
    }

    /**
     * End transaction (commit or rollback)
     */
    public void endTransaction() {
        db.endTransaction();
    }

    /**
     * Mark transaction as successful (next call to endTransaction will be a commit)
     */
    public void setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    /**
     * Register a soup
     *
     * Create table for soupName with a column for the soup itself and columns for paths specified in indexSpecs
     * Create indexes on the new table to make lookup faster
     * Create rows in soup index map table for indexSpecs
     * @param soupName
     * @param indexSpecs
     */
    public void registerSoup(String soupName, IndexSpec[] indexSpecs) {
        if (soupName == null) throw new SmartStoreException("Bogus soup name:" + soupName);
        if (indexSpecs.length == 0) throw new SmartStoreException("No indexSpecs specified for soup: " + soupName);
        if (hasSoup(soupName)) return; // soup already exist - do nothing

        // First get a table name
        String soupTableName = null;
        ContentValues soupMapValues = new ContentValues();
        soupMapValues.put(SOUP_NAME_COL, soupName);
        try {
            db.beginTransaction();
            long soupId = DBHelper.INSTANCE.insert(db, SOUP_NAMES_TABLE, soupMapValues);
            soupTableName = getSoupTableName(soupId);
            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
        }

        // Prepare SQL for creating soup table and its indices
        StringBuilder createTableStmt = new StringBuilder();          // to create new soup table
        List<String> createIndexStmts = new ArrayList<String>();      // to create indices on new soup table
        List<ContentValues> soupIndexMapInserts = new ArrayList<ContentValues>();  // to be inserted in soup index map table

        createTableStmt.append("CREATE TABLE ").append(soupTableName).append(" (")
                        .append(ID_COL).append(" INTEGER PRIMARY KEY AUTOINCREMENT")
                        .append(", ").append(SOUP_COL).append(" TEXT")
                        .append(", ").append(CREATED_COL).append(" INTEGER")
                        .append(", ").append(LAST_MODIFIED_COL).append(" INTEGER");

        int i = 0;
        IndexSpec[] indexSpecsToCache = new IndexSpec[indexSpecs.length];
        for (IndexSpec indexSpec : indexSpecs) {
            // for create table
            String columnName = soupTableName + "_" + i;
            String columnType = indexSpec.type.getColumnType();
            createTableStmt.append(", ").append(columnName).append(" ").append(columnType);

            // for insert
            ContentValues values = new ContentValues();
            values.put(SOUP_NAME_COL, soupName);
            values.put(PATH_COL, indexSpec.path);
            values.put(COLUMN_NAME_COL, columnName);
            values.put(COLUMN_TYPE_COL, indexSpec.type.toString());
            soupIndexMapInserts.add(values);

            // for create index
            String indexName = soupTableName + "_" + i + "_idx";
            createIndexStmts.add(String.format("CREATE INDEX %s on %s ( %s )", indexName, soupTableName, columnName));;

            // for the cache
            indexSpecsToCache[i] = new IndexSpec(indexSpec.path, indexSpec.type, columnName);

            i++;
        }
        createTableStmt.append(")");

        // Run SQL for creating soup table and its indices
        db.execSQL(createTableStmt.toString());
        for (String createIndexStmt : createIndexStmts) {
            db.execSQL(createIndexStmt.toString());
        }

        try {
            db.beginTransaction();
            for (ContentValues values : soupIndexMapInserts) {
                DBHelper.INSTANCE.insert(db, SOUP_INDEX_MAP_TABLE, values);
            }
            db.setTransactionSuccessful();

            // Add to soupNameToTableNamesMap
            DBHelper.INSTANCE.cacheTableName(soupName, soupTableName);

            // Add to soupNameToIndexSpecsMap
            DBHelper.INSTANCE.cacheIndexSpecs(soupName, indexSpecsToCache);
        }
        finally {
            db.endTransaction();
        }
    }

    /**
     * Check if soup exists
     *
     * @param soupName
     * @return true if soup exists, false otherwise
     */
    public boolean hasSoup(String soupName) {
        return DBHelper.INSTANCE.getSoupTableName(db, soupName) != null;
    }

    /**
     * Destroy a soup
     *
     * Drop table for soupName
     * Cleanup entries in soup index map table
     * @param soupName
     */
    public void dropSoup(String soupName) {
        String soupTableName = DBHelper.INSTANCE.getSoupTableName(db, soupName);
        if (soupTableName != null) {
            db.execSQL("DROP TABLE IF EXISTS " + soupTableName);
            try {
                db.beginTransaction();
                DBHelper.INSTANCE.delete(db, SOUP_NAMES_TABLE, SOUP_NAME_PREDICATE, soupName);
                DBHelper.INSTANCE.delete(db, SOUP_INDEX_MAP_TABLE, SOUP_NAME_PREDICATE, soupName);
                db.setTransactionSuccessful();

                // Remove from cache
                DBHelper.INSTANCE.removeFromCache(soupName);
            }
            finally {
                db.endTransaction();
            }
        }
    }

    /**
     * Destroy all the soups in the smartstore
     */
    public void dropAllSoups() {
        List<String> soupNames = getAllSoupNames();
        for(String soupName : soupNames) {
            dropSoup(soupName);
        }
    }

    /**
     * @return all soup names in the smartstore
     */
    public List<String> getAllSoupNames() {
        List<String> soupNames = new ArrayList<String>();
        Cursor cursor = null;
        try {
            cursor = DBHelper.INSTANCE.query(db, SOUP_NAMES_TABLE, new String[] {SOUP_NAME_COL}, null, null, null);
            if (cursor.moveToFirst()) {
                do {
                    soupNames.add(cursor.getString(0));
                }
                while (cursor.moveToNext());
            }
        }
        finally {
            safeClose(cursor);
        }
        return soupNames;
    }

    /**
	 * Run a query given by its query Spec, only returned results from selected page
	 * @param querySpec
	 * @param pageIndex
     * @throws JSONException 
	 */
	public JSONArray query(QuerySpec querySpec, int pageIndex) throws JSONException {
		QueryType qt = querySpec.queryType;
    	String sql = convertSmartSql(querySpec.smartSql);

        // Page
        int offsetRows = querySpec.pageSize * pageIndex;
        int numberRows = querySpec.pageSize;
        String limit = offsetRows + "," + numberRows;
    	
    	
    	Cursor cursor = null;
    	try {

    		cursor = DBHelper.INSTANCE.limitRawQuery(db, sql, limit, querySpec.getArgs());

            JSONArray results = new JSONArray();
            if (cursor.moveToFirst()) {
                do {
                	// Smart queries
                	if (qt == QueryType.smart) {
                		results.put(getDataFromRow(cursor));	
                	}
            		// Exact/like/range queries
                	else {
                		results.put(new JSONObject(cursor.getString(0)));
                	}
                }
                while (cursor.moveToNext());
            }

            return results;
    	}
    	finally {
    		safeClose(cursor);
    	}
	}

	/**
	 * Return JSONArray for one row of data from cursor
	 * @param cursor
	 * @return
	 * @throws JSONException
	 */
	private JSONArray getDataFromRow(Cursor cursor) throws JSONException {
		JSONArray row = new JSONArray();
		int columnCount = cursor.getColumnCount();
		for (int i=0; i<columnCount; i++) {
			String raw = cursor.getString(i);

			// Is this column holding a serialized json object?
			if (cursor.getColumnName(i).endsWith(SOUP_COL)) {
				row.put(new JSONObject(raw));
				// Note: we could end up returning a string if you aliased the column
			}
			else {
	    		// Is it holding a integer
	    		try {
	    			Long n = Long.parseLong(raw);
	    			row.put(n);
	    			// Note: we could end up returning an integer for a string column if you have a string value that contains just an integer
	    		}
	    		// It must be holding a string then
	    		catch (NumberFormatException e) {
	    			row.put(raw);
	    		}
	    		// cursor.getType is API 11 and above
			}
		}
		return row;
	}
	
	/**
	 * @param querySpec
	 * @return count of results for a "smart" query
	 */
	public int countQuery(QuerySpec querySpec) {
		String sql = convertSmartSql(querySpec.smartSql);
		return DBHelper.INSTANCE.countRawQuery(db, sql, querySpec.getArgs());
	}

	/**
	 * @param smartSql
	 * @return
	 */
	public String convertSmartSql(String smartSql) {
		return SmartSqlHelper.INSTANCE.convertSmartSql(db, smartSql);
	}


    /**
     * Create (and commits)
     * Note: Passed soupElt is modified (last modified date and soup entry id fields)
     * @param soupName
     * @param soupElt
     * @return soupElt created or null if creation failed
     * @throws JSONException
     */
    public JSONObject create(String soupName, JSONObject soupElt) throws JSONException {
        return create(soupName, soupElt, true);
    }

    /**
     * Create
     * Note: Passed soupElt is modified (last modified date and soup entry id fields)
     * @param soupName
     * @param soupElt
     * @return
     * @throws JSONException
     */
    public JSONObject create(String soupName, JSONObject soupElt, boolean handleTx) throws JSONException {
        String soupTableName = DBHelper.INSTANCE.getSoupTableName(db, soupName);
        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
        IndexSpec[] indexSpecs = DBHelper.INSTANCE.getIndexSpecs(db, soupName);

        try {
            if (handleTx) {
                db.beginTransaction();
            }

            long now = System.currentTimeMillis();
            long soupEntryId = DBHelper.INSTANCE.getNextId(db, soupTableName);

            // Adding fields to soup element
            soupElt.put(SOUP_ENTRY_ID, soupEntryId);
            soupElt.put(SOUP_LAST_MODIFIED_DATE, now);
            ContentValues contentValues = new ContentValues();
            contentValues.put(ID_COL, soupEntryId);
            contentValues.put(SOUP_COL, "");
            contentValues.put(CREATED_COL, now);
            contentValues.put(LAST_MODIFIED_COL, now);
            contentValues.put(SOUP_COL, soupElt.toString());
            for (IndexSpec indexSpec : indexSpecs) {
                projectIndexedPaths(soupElt, contentValues, indexSpec);
            }

            // Inserting into database
            boolean success = DBHelper.INSTANCE.insert(db, soupTableName, contentValues) == soupEntryId;

            // Commit if successful
            if (success) {
                if (handleTx) {
                    db.setTransactionSuccessful();
                }
                return soupElt;
            }
            else {
                return null;
            }
        }
        finally {
            if (handleTx) {
                db.endTransaction();
            }
        }
    }

    /**
     * @param soupElt
     * @param contentValues
     * @param indexSpec
     */
    private void projectIndexedPaths(JSONObject soupElt, ContentValues contentValues, IndexSpec indexSpec) {
        Object value = project(soupElt, indexSpec.path);
        switch (indexSpec.type) {
        case integer:
            contentValues.put(indexSpec.columnName, (Integer) value); break;
        case string:
            contentValues.put(indexSpec.columnName, value != null ? value.toString() : null); break;
        case floating:
        	contentValues.put(indexSpec.columnName, (Double) value); break;
        }
    }

    /**
     * Retrieve
     * @param soupName
     * @param soupEntryIds
     * @return JSONArray of JSONObject's with the given soupEntryIds
     * @throws JSONException
     */
    public JSONArray retrieve(String soupName, Long... soupEntryIds) throws JSONException {
        String soupTableName = DBHelper.INSTANCE.getSoupTableName(db, soupName);
        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
        Cursor cursor = null;
        try {
            JSONArray result = new JSONArray();
            cursor = DBHelper.INSTANCE.query(db, soupTableName, new String[] {SOUP_COL}, null, null, getSoupEntryIdsPredicate(soupEntryIds), (String[]) null);
            if (!cursor.moveToFirst()) {
                return result;
            }
            do {
                String raw = cursor.getString(cursor.getColumnIndex(SOUP_COL));
                result.put(new JSONObject(raw));
            }
            while (cursor.moveToNext());

            return result;
        }
        finally {
            safeClose(cursor);
        }
    }


    /**
     * Update (and commits)
     * Note: Passed soupElt is modified (last modified date and soup entry id fields)
     * @param soupName
     * @param soupElt
     * @param soupEntryId
     * @return soupElt updated or null if update failed
     * @throws JSONException
     */
    public JSONObject update(String soupName, JSONObject soupElt, long soupEntryId) throws JSONException {
        return update(soupName, soupElt, soupEntryId, true);
    }

    /**
     * Update
     * Note: Passed soupElt is modified (last modified date and soup entry id fields)
     * @param soupName
     * @param soupElt
     * @param soupEntryId
     * @return
     * @throws JSONException
     */
    public JSONObject update(String soupName, JSONObject soupElt, long soupEntryId, boolean handleTx) throws JSONException {
        String soupTableName = DBHelper.INSTANCE.getSoupTableName(db, soupName);
        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
        IndexSpec[] indexSpecs = DBHelper.INSTANCE.getIndexSpecs(db, soupName);

        long now = System.currentTimeMillis();

        // In the case of an upsert with external id, _soupEntryId won't be in soupElt
        soupElt.put(SOUP_ENTRY_ID, soupEntryId);
        // Updating last modified field in soup element
        soupElt.put(SOUP_LAST_MODIFIED_DATE, now);

        // Preparing data for row
        ContentValues contentValues = new ContentValues();
        contentValues.put(SOUP_COL, soupElt.toString());
        contentValues.put(LAST_MODIFIED_COL, now);
        for (IndexSpec indexSpec : indexSpecs) {
            projectIndexedPaths(soupElt, contentValues, indexSpec);
        }

        try {
            if (handleTx) {
                db.beginTransaction();
            }
            boolean success = DBHelper.INSTANCE.update(db, soupTableName, contentValues, SOUP_ENTRY_ID_PREDICATE, soupEntryId + "") == 1;
            if (success) {
                if (handleTx) {
                    db.setTransactionSuccessful();
                }
                return soupElt;
            }
            else {
                return null;
            }
        }
        finally {
            if (handleTx) {
                db.endTransaction();
            }
        }
    }

    /**
     * Upsert (and commits)
     * @param soupName
     * @param soupElt
     * @param externalIdPath
     * @return soupElt upserted or null if upsert failed
     * @throws JSONException
     */
    public JSONObject upsert(String soupName, JSONObject soupElt, String externalIdPath) throws JSONException {
        return upsert(soupName, soupElt, externalIdPath, true);
    }

    /**
     * Upsert (and commits) expecting _soupEntryId in soupElt for updates
     * @param soupName
     * @param soupElt
     * @return
     * @throws JSONException
     */
    public JSONObject upsert(String soupName, JSONObject soupElt) throws JSONException {
        return upsert(soupName, soupElt, SOUP_ENTRY_ID);
    }

    /**
     * Upsert
     * @param soupName
     * @param soupElt
     * @param externalIdPath
     * @param handleTx
     * @return
     * @throws JSONException
     */
    public JSONObject upsert(String soupName, JSONObject soupElt, String externalIdPath, boolean handleTx) throws JSONException {
        long entryId = -1;
        if (externalIdPath.equals(SOUP_ENTRY_ID)) {
            if (soupElt.has(SOUP_ENTRY_ID)) {
                entryId = soupElt.getLong(SOUP_ENTRY_ID);
            }
        }
        else {
            Object externalIdObj = project(soupElt, externalIdPath);
            if (externalIdObj != null) {
                entryId = lookupSoupEntryId(soupName, externalIdPath, externalIdObj + "");
            }
        }

        // If we have an entryId, let's do an update, otherwise let's do a create
        if (entryId != -1) {
            return update(soupName, soupElt, entryId, handleTx);
        }
        else {
            return create(soupName, soupElt, handleTx);
        }
    }

    /**
     * Look for a soup element where fieldPath's value is fieldValue
     * Return its soupEntryId
     * Return -1 if not found
     * Throw an exception if fieldName is not indexed
     * Throw an exception if more than one soup element are found
     *
     * @param soupName
     * @param fieldPath
     * @param fieldValue
     */
    public long lookupSoupEntryId(String soupName, String fieldPath, String fieldValue) {
        String soupTableName = DBHelper.INSTANCE.getSoupTableName(db, soupName);
        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
        String columnName = DBHelper.INSTANCE.getColumnNameForPath(db, soupName, fieldPath);

        Cursor cursor = null;
        try {
            cursor = db.query(soupTableName, new String[] {ID_COL}, columnName + " = ?", new String[] { fieldValue }, null, null, null);
            if (cursor.getCount() > 1) {
                throw new SmartStoreException(String.format("There are more than one soup elements where %s is %s", fieldPath, fieldValue));
            }
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
            else {
                return -1; // not found
            }
        }
        finally {
            safeClose(cursor);
        }
    }

    /**
     * Delete (and commits)
     * @param soupName
     * @param soupEntryIds
     */
    public void delete(String soupName, Long... soupEntryIds) {
        delete(soupName, soupEntryIds, true);
    }

    /**
     * Delete
     * @param soupName
     * @param soupEntryId
     * @param handleTx
     */
    public void delete(String soupName, Long[] soupEntryIds, boolean handleTx) {
        String soupTableName = DBHelper.INSTANCE.getSoupTableName(db, soupName);
        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");

        if (handleTx) {
            db.beginTransaction();
        }

        try {
            db.delete(soupTableName, getSoupEntryIdsPredicate(soupEntryIds), (String []) null);
            if (handleTx) {
                db.setTransactionSuccessful();
            }
        }
        finally {
            if (handleTx) {
                db.endTransaction();
            }
        }
    }

    /**
     * @return predicate to match soup entries by id
     */
    protected String getSoupEntryIdsPredicate(Long[] soupEntryIds) {
        return ID_COL + " IN (" + TextUtils.join(",", soupEntryIds)+ ")";
    }


    /**
     * @param soupId
     * @return
     */
    public static String getSoupTableName(long soupId) {
        return "TABLE_" + soupId;
    }

    /**
     * @param cursor
     */
    protected void safeClose(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
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
        if (path == null || path.equals("")) {
            return soup;
        }

        String[] pathElements = path.split("[.]");
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
        string("TEXT"), integer("INTEGER"), floating("REAL");

        private String columnType;

        private Type(String columnType) {
            this.columnType = columnType;
        }

        public String getColumnType() {
            return columnType;
        }
    }

    /**
     * Exception thrown by smart store
     *
     */
    public static class SmartStoreException extends RuntimeException {

        public SmartStoreException(String message) {
            super(message);
        }

        private static final long serialVersionUID = -6369452803270075464L;

    }
}

/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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

import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.smartstore.store.LongOperation.LongOperationType;
import com.salesforce.androidsdk.smartstore.store.QuerySpec.QueryType;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Smart store
 *
 * Provides a secure means for SalesforceMobileSDK Container-based applications to store objects in a persistent
 * and searchable manner. Similar in some ways to CouchDB, SmartStore stores documents as JSON values.
 * SmartStore is inspired by the Apple Newton OS Soup/Store model.
 * The main challenge here is how to effectively store documents with dynamic fields, and still allow indexing and searching.
 */
public class SmartStore  {

	private static final String TAG = "SmartStore";

	// Table to keep track of soup names and attributes.
	public static final String SOUP_ATTRS_TABLE = "soup_attrs";

	// Fts table suffix
	public static final String FTS_SUFFIX = "_fts";

	// Table to keep track of soup's index specs
    public static final String SOUP_INDEX_MAP_TABLE = "soup_index_map";

    // Table to keep track of status of long operations in flight
    protected static final String LONG_OPERATIONS_STATUS_TABLE = "long_operations_status";

    // Columns of the soup index map table
    public static final String SOUP_NAME_COL = "soupName";
    public static final String PATH_COL = "path";
    protected static final String COLUMN_NAME_COL = "columnName";
    public static final String COLUMN_TYPE_COL = "columnType";

    // Columns of a soup table
    protected static final String ID_COL = "id";
    protected static final String CREATED_COL = "created";
    protected static final String LAST_MODIFIED_COL = "lastModified";
    protected static final String SOUP_COL = "soup";

	// Column of a fts soup table
	protected static final String ROWID_COL = "rowid";

    // Columns of long operations status table
	protected static final String TYPE_COL = "type";
    protected static final String DETAILS_COL = "details";
	protected static final String STATUS_COL = "status";

    // JSON fields added to soup element on insert/update
    public static final String SOUP_ENTRY_ID = "_soupEntryId";
    public static final String SOUP_LAST_MODIFIED_DATE = "_soupLastModifiedDate";
	public static final String SOUP_CREATED_DATE = "_soupCreatedDate";

    // Predicates
    protected static final String SOUP_NAME_PREDICATE = SOUP_NAME_COL + " = ?";
	protected static final String ID_PREDICATE = ID_COL + " = ?";
	protected static final String ROWID_PREDICATE = ROWID_COL + " =?";

	// Backing database
	protected SQLiteDatabase dbLocal;
	protected SQLiteOpenHelper dbOpenHelper;
	protected String encryptionKey;

	// FTS extension to use
	protected FtsExtension ftsExtension = FtsExtension.fts5;

	// background executor
	private final ExecutorService threadPool = Executors.newFixedThreadPool(1);

	/**
     * Changes the encryption key on the smartstore.
     *
     * @param db Database object.
     * @param oldKey Old encryption key.
     * @param newKey New encryption key.
     */
    public static synchronized void changeKey(SQLiteDatabase db, String oldKey, String newKey) {
    	synchronized(db) {
	        if (newKey != null && !newKey.trim().equals("")) {
	            db.execSQL("PRAGMA rekey = '" + newKey + "'");
	            DBOpenHelper.reEncryptAllFiles(db, oldKey, newKey);
	        }
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
    	synchronized(db) {
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
	        sb.append("CREATE TABLE ").append(SOUP_ATTRS_TABLE).append(" (")
	                    .append(ID_COL).append(" INTEGER PRIMARY KEY AUTOINCREMENT")
	                    .append(",").append(SOUP_NAME_COL).append(" TEXT");

	        // Create columns for all possible soup features
	        for (String feature : SoupSpec.ALL_FEATURES) {
		        sb.append(",").append(feature).append(" INTEGER DEFAULT 0");
	        }

	        sb.append(")");
	        db.execSQL(sb.toString());
	        // Add index on soup_name column
	        db.execSQL(String.format("CREATE INDEX %s on %s ( %s )", SOUP_ATTRS_TABLE + "_0", SOUP_ATTRS_TABLE, SOUP_NAME_COL));

	        // Create alter_soup_status table
	        createLongOperationsStatusTable(db);
    	}
    }

    /**
     * Create long_operations_status table
     * @param db
     */
    public static void createLongOperationsStatusTable(SQLiteDatabase db) {
    	synchronized(db) {
    		StringBuilder sb = new StringBuilder();
	    	sb.append("CREATE TABLE IF NOT EXISTS ").append(LONG_OPERATIONS_STATUS_TABLE).append(" (")
	        .append(ID_COL).append(" INTEGER PRIMARY KEY AUTOINCREMENT")
	        .append(",").append(TYPE_COL).append(" TEXT")
	        .append(",").append(DETAILS_COL).append(" TEXT")
	        .append(",").append(STATUS_COL).append(" TEXT")
	        .append(", ").append(CREATED_COL).append(" INTEGER")
	        .append(", ").append(LAST_MODIFIED_COL).append(" INTEGER")
	        .append(")");
	        db.execSQL(sb.toString());
    	}
    }

    /**
     * Relies on SQLiteOpenHelper for database handling.
     *
     * @param dbOpenHelper DB open helper.
     * @param encryptionKey Encryption key.
     */
    public SmartStore(SQLiteOpenHelper dbOpenHelper, String encryptionKey) {
    	this.dbOpenHelper = dbOpenHelper;
        this.encryptionKey = encryptionKey;
    }

	/**
	 * Package-level constructor. Should be used in tests only.
	 *
	 * @param db Database.
	 */
	SmartStore(SQLiteDatabase db) {
		this.dbLocal = db;
	}

    /**
     * Return db
     */
    public SQLiteDatabase getDatabase() {
    	if (dbLocal != null) {
            return dbLocal;
        } else {
            return this.dbOpenHelper.getWritableDatabase(encryptionKey);
        }
    }

	/**
	 * If turned on, explain query plan is run before executing a query and stored in lastExplainQueryPlan
	 * and also get logged
	 * @param captureExplainQueryPlan true to turn capture on and false to turn off
	 */
	public void setCaptureExplainQueryPlan(boolean captureExplainQueryPlan) {
		DBHelper.getInstance(getDatabase()).setCaptureExplainQueryPlan(captureExplainQueryPlan);
	}

	/**
	 * @return explain query plan for last query run (if captureExplainQueryPlan is true)
	 */
	public JSONObject getLastExplainQueryPlan() {
		return DBHelper.getInstance(getDatabase()).getLastExplainQueryPlan();
	}

	/**
     * Get database size
     */
    public int getDatabaseSize() {
    	int size =  (int) (new File(getDatabase().getPath()).length()); // XXX That cast will be trouble if the file is more than 2GB
    	if (dbOpenHelper instanceof DBOpenHelper) {
    		size += ((DBOpenHelper) dbOpenHelper).getSizeOfDir(null);
    	}
    	return size;
    }

    /**
     * Start transaction
	 * NB: to avoid deadlock, caller should have synchronized(store.getDatabase()) around the whole transaction
     */
    public void beginTransaction() {
    	getDatabase().beginTransaction();
    }

    /**
     * End transaction (commit or rollback)
     */
    public void endTransaction() {
    	getDatabase().endTransaction();
    }

    /**
     * Mark transaction as successful (next call to endTransaction will be a commit)
     */
    public void setTransactionSuccessful() {
    	getDatabase().setTransactionSuccessful();
    }

    /**
     * Register a soup without any features. Use {@link #registerSoupWithSpec(SoupSpec, IndexSpec[])} to enable features such as external storage, etc.
     *
     * Create table for soupName with a column for the soup itself and columns for paths specified in indexSpecs
     * Create indexes on the new table to make lookup faster
     * Create rows in soup index map table for indexSpecs
     * @param soupName
     * @param indexSpecs
     */
    public void registerSoup(String soupName, IndexSpec[] indexSpecs) {
		registerSoupWithSpec(new SoupSpec(soupName), indexSpecs);
    }

	/**
	 * Register a soup using the given soup specifications. This allows the soup to use extra features such as external storage.
	 *
	 * Create table for soupName with a column for the soup itself and columns for paths specified in indexSpecs
	 * Create indexes on the new table to make lookup faster
	 * Create rows in soup index map table for indexSpecs
	 * @param soupSpec
	 * @param indexSpecs
	 */
	public void registerSoupWithSpec(final SoupSpec soupSpec, final IndexSpec[] indexSpecs) {
		final SQLiteDatabase db = getDatabase();
		synchronized (db) {
			String soupName = soupSpec.getSoupName();
			if (soupName == null) throw new SmartStoreException("Bogus soup name:" + soupName);
			if (indexSpecs.length == 0)
				throw new SmartStoreException("No indexSpecs specified for soup: " + soupName);
			if (IndexSpec.hasJSON1(indexSpecs) && soupSpec.getFeatures().contains(SoupSpec.FEATURE_EXTERNAL_STORAGE))
				throw new SmartStoreException("Can't have JSON1 index specs in externally stored soup:" + soupName);
			if (hasSoup(soupName)) return; // soup already exist - do nothing

			// First get a table name
			String soupTableName = null;
			ContentValues soupMapValues = new ContentValues();
			soupMapValues.put(SOUP_NAME_COL, soupName);

			// Register features from soup spec
			for (String feature : soupSpec.getFeatures()) {
				soupMapValues.put(feature, 1);
			}

			try {
				db.beginTransaction();
				long soupId = DBHelper.getInstance(db).insert(db, SOUP_ATTRS_TABLE, soupMapValues);
				soupTableName = getSoupTableName(soupId);

				// Do the rest - create table / indexes
				registerSoupUsingTableName(soupSpec, indexSpecs, soupTableName);

				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
			if (SalesforceSDKManager.getInstance().getIsTestRun()) {
				logRegisterSoupEvent(soupSpec, indexSpecs);
			} else {
				threadPool.execute(new Runnable() {
					@Override
					public void run() {
						logRegisterSoupEvent(soupSpec, indexSpecs);
					}
				});
			}
		}
	}

	/**
	 * Log the soup event.
	 * @param soupSpec
	 * @param indexSpecs
	 */
	private void logRegisterSoupEvent(final SoupSpec soupSpec, final IndexSpec[] indexSpecs) {
		final JSONArray features = new JSONArray();
		if (IndexSpec.hasJSON1(indexSpecs)) {
			features.put("JSON1");
		}
		if (IndexSpec.hasFTS(indexSpecs)) {
			features.put("FTS");
		}
		if (soupSpec.getFeatures().contains(SoupSpec.FEATURE_EXTERNAL_STORAGE)) {
			features.put("ExternalStorage");
		}
		final JSONObject attributes = new JSONObject();
		try {
			attributes.put("features", features);
		} catch (JSONException e) {
            SmartStoreLogger.e(TAG, "Exception thrown while building page object", e);
		}
		EventBuilderHelper.createAndStoreEventSync("registerSoup", null, TAG, attributes);
	}

	/**
	 * Helper method for registerSoup using soup spec
	 *
	 * @param soupSpec
	 * @param indexSpecs
	 * @param soupTableName
	 */
	protected void registerSoupUsingTableName(SoupSpec soupSpec, IndexSpec[] indexSpecs, String soupTableName) {
        // Prepare SQL for creating soup table and its indices
        StringBuilder createTableStmt = new StringBuilder();          // to create new soup table
		StringBuilder createFtsStmt = new StringBuilder();            // to create fts table
        List<String> createIndexStmts = new ArrayList<String>();      // to create indices on new soup table
        List<ContentValues> soupIndexMapInserts = new ArrayList<ContentValues>();  // to be inserted in soup index map table
        IndexSpec[] indexSpecsToCache = new IndexSpec[indexSpecs.length];
        List<String> columnsForFts = new ArrayList<String>();

        String soupName = soupSpec.getSoupName();

        createTableStmt.append("CREATE TABLE ").append(soupTableName).append(" (")
                        .append(ID_COL).append(" INTEGER PRIMARY KEY AUTOINCREMENT");

        if (!usesExternalStorage(soupName)) {
	        // If external storage is used, do not add column for soup in the db since it will be empty.
	        createTableStmt.append(", ").append(SOUP_COL).append(" TEXT");
        }

        createTableStmt.append(", ").append(CREATED_COL).append(" INTEGER")
                        .append(", ").append(LAST_MODIFIED_COL).append(" INTEGER");

        final String createIndexFormat = "CREATE INDEX %s_%s_idx on %s ( %s )";

        for (String col : new String[]{CREATED_COL, LAST_MODIFIED_COL}) {
            createIndexStmts.add(String.format(createIndexFormat, soupTableName, col, soupTableName, col));
        }

        int i = 0;
        for (IndexSpec indexSpec : indexSpecs) {
            // Column name or expression the db index is on
            String columnName = soupTableName + "_" + i;
            if (TypeGroup.value_indexed_with_json_extract.isMember(indexSpec.type)) {
                columnName = "json_extract(" + SOUP_COL + ", '$." + indexSpec.path + "')";
            }

            // for create table
            if (TypeGroup.value_extracted_to_column.isMember(indexSpec.type)) {
                String columnType = indexSpec.type.getColumnType();
                createTableStmt.append(", ").append(columnName).append(" ").append(columnType);
            }

			// for fts
			if (indexSpec.type == Type.full_text) {
				columnsForFts.add(columnName);
			}

            // for insert
            ContentValues values = new ContentValues();
            values.put(SOUP_NAME_COL, soupName);
            values.put(PATH_COL, indexSpec.path);
            values.put(COLUMN_NAME_COL, columnName);
            values.put(COLUMN_TYPE_COL, indexSpec.type.toString());
            soupIndexMapInserts.add(values);

            // for create index
			createIndexStmts.add(String.format(createIndexFormat, soupTableName, "" + i, soupTableName, columnName));;

            // for the cache
            indexSpecsToCache[i] = new IndexSpec(indexSpec.path, indexSpec.type, columnName);

            i++;
        }
        createTableStmt.append(")");

		// fts
		if (columnsForFts.size() > 0) {
			createFtsStmt.append(String.format("CREATE VIRTUAL TABLE %s%s USING %s(%s)", soupTableName, FTS_SUFFIX, ftsExtension, TextUtils.join(",", columnsForFts)));
		}

        // Run SQL for creating soup table and its indices
		final SQLiteDatabase db = getDatabase();
        db.execSQL(createTableStmt.toString());

		if (columnsForFts.size() > 0) {
			db.execSQL(createFtsStmt.toString());
		}

        for (String createIndexStmt : createIndexStmts) {
            db.execSQL(createIndexStmt.toString());
        }

        try {
            db.beginTransaction();
            for (ContentValues values : soupIndexMapInserts) {
                DBHelper.getInstance(db).insert(db, SOUP_INDEX_MAP_TABLE, values);
            }

            if (usesExternalStorage(soupName) && dbOpenHelper instanceof DBOpenHelper) {
                ((DBOpenHelper) dbOpenHelper).createExternalBlobsDirectory(soupTableName);
            }

            db.setTransactionSuccessful();

            // Add to soupNameToTableNamesMap
            DBHelper.getInstance(db).cacheTableName(soupName, soupTableName);

            // Add to soupNameToIndexSpecsMap
            DBHelper.getInstance(db).cacheIndexSpecs(soupName, indexSpecsToCache);
        } finally {
            db.endTransaction();
        }
    }

	/**
	 * Finish long operations that were interrupted
	 */
	public void resumeLongOperations() {
		final SQLiteDatabase db = getDatabase();
		synchronized(db) {
			for (LongOperation longOperation :  getLongOperations()) {
				try {
					longOperation.run();
				} catch (Exception e) {
					SmartStoreLogger.e(TAG, "Unexpected error", e);
				}
			}
		}
	}

	/**
	 * @return unfinished long operations
	 */
	public LongOperation[] getLongOperations() {
		final SQLiteDatabase db = getDatabase();
		List<LongOperation> longOperations = new ArrayList<LongOperation>();
		synchronized(db) {
			Cursor cursor = null;
			try {
				cursor = DBHelper.getInstance(db).query(db,
						LONG_OPERATIONS_STATUS_TABLE, new String[] {ID_COL, TYPE_COL, DETAILS_COL, STATUS_COL},
						null, null, null);
			    if (cursor.moveToFirst()) {
			        do {
			        	try {
				        	long rowId = cursor.getLong(0);
				        	LongOperationType operationType = LongOperationType.valueOf(cursor.getString(1));
				        	JSONObject details = new JSONObject(cursor.getString(2));
				        	String statusStr = cursor.getString(3);

				        	longOperations.add(operationType.getOperation(this, rowId, details, statusStr));
			        	}
			        	catch (Exception e) {
                            SmartStoreLogger.e(TAG, "Unexpected error", e);
			        	}
			        }
			        while (cursor.moveToNext());
			    }
			} finally {
			    safeClose(cursor);
			}
		}
		return longOperations.toArray(new LongOperation[0]);
	}

	/**
	 * Alter soup using only soup name without extra soup features.
	 *
	 * @param soupName
	 * @param indexSpecs array of index specs
	 * @param reIndexData
	 * @throws JSONException
	 */
	public void alterSoup(String soupName, IndexSpec[] indexSpecs,
			boolean reIndexData) throws JSONException {
		alterSoup(soupName, new SoupSpec(soupName, new String[0]), indexSpecs, reIndexData);
	}

	/**
	 * Alter soup with new soup spec.
	 *
	 * @param soupName name of soup to alter
	 * @param soupSpec
	 * @param indexSpecs array of index specs
	 * @param reIndexData
	 * @throws JSONException
	 */
	public void alterSoup(String soupName, SoupSpec soupSpec, IndexSpec[] indexSpecs,
			boolean reIndexData) throws JSONException {
		AlterSoupLongOperation operation = new AlterSoupLongOperation(this, soupName, soupSpec, indexSpecs, reIndexData);
		operation.run();
	}

	/**
	 * Re-index all soup elements for passed indexPaths
	 * NB: only indexPath that have IndexSpec on them will be indexed
	 *
	 * @param soupName
	 * @param indexPaths
	 * @param handleTx
	 */
	public void reIndexSoup(String soupName, String[] indexPaths, boolean handleTx) {
		final SQLiteDatabase db = getDatabase();
		synchronized(db) {
	        String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
	        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");

	        // Getting index specs from indexPaths skipping json1 index specs
			Map<String, IndexSpec> mapAllSpecs = IndexSpec.mapForIndexSpecs(getSoupIndexSpecs(soupName));
			List<IndexSpec> indexSpecsList = new ArrayList<IndexSpec>();
			for (String indexPath : indexPaths) {
				if (mapAllSpecs.containsKey(indexPath)) {
					IndexSpec indexSpec = mapAllSpecs.get(indexPath);
					if (TypeGroup.value_extracted_to_column.isMember(indexSpec.type)) {
						indexSpecsList.add(indexSpec);
					}
				}
				else {
                    SmartStoreLogger.w(TAG, "Can not re-index " + indexPath + " - it does not have an index");
				}
			}
			IndexSpec[] indexSpecs = indexSpecsList.toArray(new IndexSpec[0]);
			if (indexSpecs.length == 0) {
				// Nothing to do
				return;
			}

			boolean hasFts = IndexSpec.hasFTS(indexSpecs);

			if (handleTx) {
				db.beginTransaction();
			}
			Cursor cursor = null;
			try {
			    String[] projection;
			    if (usesExternalStorage(soupName)) {
			        projection = new String[] {ID_COL};
			    } else {
			        projection = new String[] {ID_COL, SOUP_COL};
			    }
			    cursor = DBHelper.getInstance(db).query(db, soupTableName, projection, null, null, null);
			    if (cursor.moveToFirst()) {
			        do {
			        	String soupEntryId = cursor.getString(0);
			        	try {
			                JSONObject soupElt;
			                if (usesExternalStorage(soupName) && dbOpenHelper instanceof DBOpenHelper) {
			                	soupElt = ((DBOpenHelper) dbOpenHelper).loadSoupBlob(soupTableName, Long.parseLong(soupEntryId), encryptionKey);
			                } else {
			                	String soupRaw = cursor.getString(1);
			                	soupElt = new JSONObject(soupRaw);
			                }
			                ContentValues contentValues = new ContentValues();
			                projectIndexedPaths(soupElt, contentValues, indexSpecs, TypeGroup.value_extracted_to_column);
			                DBHelper.getInstance(db).update(db, soupTableName, contentValues, ID_PREDICATE, soupEntryId + "");

							// Fts
							if (hasFts) {
								String soupTableNameFts = soupTableName + FTS_SUFFIX;
								ContentValues contentValuesFts = new ContentValues();
								projectIndexedPaths(soupElt, contentValuesFts, indexSpecs, TypeGroup.value_extracted_to_fts_column);
								DBHelper.getInstance(db).update(db, soupTableNameFts, contentValuesFts, ROWID_PREDICATE, soupEntryId + "");
							}
			        	}
			        	catch (JSONException e) {
                            SmartStoreLogger.w(TAG, "Could not parse soup element " + soupEntryId, e);
			        		// Should not have happen - just keep going
			        	}
			        }
			        while (cursor.moveToNext());
			    }
			} finally {
				if (handleTx) {
					db.setTransactionSuccessful();
					db.endTransaction();
				}
			    safeClose(cursor);
			}
		}
	}

	/**
	 * Return indexSpecs of soup
	 *
	 * @param soupName
	 * @return
	 */
	public IndexSpec[] getSoupIndexSpecs(String soupName) {
		final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
	        String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
	        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
	        return DBHelper.getInstance(db).getIndexSpecs(db, soupName);
    	}
	}

	/**
	 * Clear all rows from a soup
	 * @param soupName
	 */
	public void clearSoup(String soupName) {
		final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
	        String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
	        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
			db.beginTransaction();
			try {
				DBHelper.getInstance(db).delete(db, soupTableName, null);
				if (hasFTS(soupName)) {
					DBHelper.getInstance(db).delete(db, soupTableName + FTS_SUFFIX, null);
				}
				if (dbOpenHelper instanceof DBOpenHelper) {
					((DBOpenHelper) dbOpenHelper).removeExternalBlobsDirectory(soupTableName);
				}
			} finally {
				db.setTransactionSuccessful();
				db.endTransaction();
			}
    	}
	}

    /**
     * Check if soup exists
     *
     * @param soupName
     * @return true if soup exists, false otherwise
     */
    public boolean hasSoup(String soupName) {
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
    		return DBHelper.getInstance(db).getSoupTableName(db, soupName) != null;
    	}
    }

    /**
     * Destroy a soup
     *
     * Drop table for soupName
     * Cleanup entries in soup index map table
     * @param soupName
     */
    public void dropSoup(String soupName) {
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
			String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
	        if (soupTableName != null) {
	            db.execSQL("DROP TABLE IF EXISTS " + soupTableName);
				if (hasFTS(soupName)) {
					db.execSQL("DROP TABLE IF EXISTS " + soupTableName + FTS_SUFFIX);
				}

	            try {
	                db.beginTransaction();
	                DBHelper.getInstance(db).delete(db, SOUP_ATTRS_TABLE, SOUP_NAME_PREDICATE, soupName);
	                DBHelper.getInstance(db).delete(db, SOUP_INDEX_MAP_TABLE, SOUP_NAME_PREDICATE, soupName);
	                if (dbOpenHelper instanceof DBOpenHelper) {
						((DBOpenHelper) dbOpenHelper).removeExternalBlobsDirectory(soupTableName);
	                }
	                db.setTransactionSuccessful();

	                // Remove from cache
	                DBHelper.getInstance(db).removeFromCache(soupName);
	            } finally {
	                db.endTransaction();
	            }
	        }
    	}
    }

    /**
     * Destroy all the soups in the smartstore
     */
    public void dropAllSoups() {
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
	    	List<String> soupNames = getAllSoupNames();
	        for(String soupName : soupNames) {
	            dropSoup(soupName);
	        }
    	}
    }

    /**
     * @return all soup names in the smartstore
     */
    public List<String> getAllSoupNames() {
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
	    	List<String> soupNames = new ArrayList<String>();
	        Cursor cursor = null;
			try {
				cursor = DBHelper.getInstance(db).query(db, SOUP_ATTRS_TABLE, new String[]{SOUP_NAME_COL}, SOUP_NAME_COL, null, null);
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
    }

	/**
	 * Returns the entire SoupSpec of the given soup.
	 * @param soupName
	 * @return SoupSpec for given soup name.
	 */
	public SoupSpec getSoupSpec(String soupName) {
		final SQLiteDatabase db = getDatabase();
		List<String> features = DBHelper.getInstance(db).getFeatures(db, soupName);
		return new SoupSpec(soupName, features.toArray(new String[features.size()]));
	}

    /**
	 * Run a query given by its query Spec, only returned results from selected page
	 * @param querySpec
	 * @param pageIndex
     * @throws JSONException
	 */
	public JSONArray query(QuerySpec querySpec, int pageIndex) throws JSONException {
		JSONArray resultAsArray = new JSONArray();
		runQuery(resultAsArray, null, querySpec, pageIndex);
		return resultAsArray;
	}
	/**
	 * Run a query given by its query Spec, only returned results from selected page
	 * without deserializing any JSON
	 *
	 * @param resultBuilder string builder to which results are appended
	 * @param querySpec
	 * @param pageIndex
	 */
	public void queryAsString(StringBuilder resultBuilder, QuerySpec querySpec, int pageIndex) {
		try {
			runQuery(null, resultBuilder, querySpec, pageIndex);
		}
		catch (JSONException e) {
			// shouldn't happen since we call runQuery with a string builder
			throw new SmartStoreException("Unexpected json exception", e);
		}
	}

	private void runQuery(JSONArray resultAsArray, StringBuilder resultAsStringBuilder, QuerySpec querySpec, int pageIndex) throws JSONException {
		boolean computeResultAsString = resultAsStringBuilder != null;

		final SQLiteDatabase db = getDatabase();
		synchronized(db) {
			QueryType qt = querySpec.queryType;
			String sql = convertSmartSql(querySpec.smartSql);

			// Page
			int offsetRows = querySpec.pageSize * pageIndex;
			int numberRows = querySpec.pageSize;
			String limit = offsetRows + "," + numberRows;
			Cursor cursor = null;
			try {
				cursor = DBHelper.getInstance(db).limitRawQuery(db, sql, limit, querySpec.getArgs());

				if (computeResultAsString) {
					resultAsStringBuilder.append("[");
				}

				int currentRow = 0;
				if (cursor.moveToFirst()) {
					do {
						if (computeResultAsString && currentRow > 0) {
							resultAsStringBuilder.append(", ");
						}
						currentRow++;

						// Smart queries
						if (qt == QueryType.smart || querySpec.selectPaths != null) {
							if (computeResultAsString) {
								getDataFromRow(null, resultAsStringBuilder, cursor);
							}
							else {
								JSONArray rowArray = new JSONArray();
								getDataFromRow(rowArray, null, cursor);
								resultAsArray.put(rowArray);
							}
						}
						// Exact/like/range queries
						else {
							String rowAsString = null;
							if (cursor.getColumnIndex(SoupSpec.FEATURE_EXTERNAL_STORAGE) >= 0) {
								// Presence of external storage column implies we must fetch from storage. Soup name and entry id values can be extracted
								String soupTableName = cursor.getString(cursor.getColumnIndex(SoupSpec.FEATURE_EXTERNAL_STORAGE));
								Long soupEntryId = cursor.getLong(cursor.getColumnIndex(SmartStore.SOUP_ENTRY_ID));
								rowAsString = ((DBOpenHelper) dbOpenHelper).loadSoupBlobAsString(soupTableName, soupEntryId, encryptionKey);
							} else {
								rowAsString = cursor.getString(0);
							}

							if (computeResultAsString) {
								resultAsStringBuilder.append(rowAsString);
							}
							else {
								resultAsArray.put(new JSONObject(rowAsString));
							}
						}
					} while (cursor.moveToNext());
				}
				if (computeResultAsString) {
					resultAsStringBuilder.append("]");
				}

			} finally {
				safeClose(cursor);
			}
		}
	}

	private void getDataFromRow(JSONArray resultAsArray, StringBuilder resultAsStringBuilder, Cursor cursor) throws JSONException {
		boolean computeResultAsString = resultAsStringBuilder != null;
		int columnCount = cursor.getColumnCount();
		if (computeResultAsString) {
			resultAsStringBuilder.append("[");
		}
		for (int i=0; i<columnCount; i++) {
			if (computeResultAsString && i > 0) {
				resultAsStringBuilder.append(",");
			}
			int valueType = cursor.getType(i);
			String columnName = cursor.getColumnName(i);
			if (valueType == Cursor.FIELD_TYPE_NULL) {
				if (computeResultAsString) {
					resultAsStringBuilder.append("null");
				} else {
					resultAsArray.put(null);
				}
			}
			else if (valueType == Cursor.FIELD_TYPE_STRING) {
				String raw = cursor.getString(i);
				if (columnName.equals(SoupSpec.FEATURE_EXTERNAL_STORAGE)) {
					// Presence of external storage column implies we must fetch from storage. Soup name and entry id values can be extracted
					String soupTableName = cursor.getString(i);
					Long soupEntryId = cursor.getLong(i + 1);
					if (computeResultAsString) {
						resultAsStringBuilder.append(((DBOpenHelper) dbOpenHelper).loadSoupBlobAsString(soupTableName, soupEntryId, encryptionKey));
					} else {
						resultAsArray.put(((DBOpenHelper) dbOpenHelper).loadSoupBlob(soupTableName, soupEntryId, encryptionKey));
					}
					i++; // skip next column (_soupEntryId)
				} else if (columnName.equals(SOUP_COL) || columnName.startsWith(SOUP_COL + ":") /* :num is appended to column name when result set has more than one column with same name */) {
					if (computeResultAsString) {
						resultAsStringBuilder.append(raw);
					} else {
						resultAsArray.put(new JSONObject(raw));
					}
					// Note: we could end up returning a string if you aliased the column
				}
				else {
					if (computeResultAsString) {
						raw = escapeStringValue(raw);
						resultAsStringBuilder.append("\"").append(raw).append("\"");
					} else {
						resultAsArray.put(raw);
					}
				}
			}
			else if (valueType == Cursor.FIELD_TYPE_INTEGER) {
				if (computeResultAsString) {
					resultAsStringBuilder.append(cursor.getLong(i));
				} else {
					resultAsArray.put(cursor.getLong(i));
				}
			}
			else if (valueType == Cursor.FIELD_TYPE_FLOAT) {
				if (computeResultAsString) {
					resultAsStringBuilder.append(cursor.getDouble(i));
				} else {
					resultAsArray.put(cursor.getDouble(i));
				}
			}
		}
		if (computeResultAsString) {
			resultAsStringBuilder.append("]");
		}
	}

	private String escapeStringValue(String raw) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < raw.length(); i ++) {
			char c = raw.charAt(i);
			switch (c) {
				case '\\':
				case '"':
					sb.append('\\');
					sb.append(c);
					break;
				case '/':
					sb.append('\\');
					sb.append(c);
					break;
				case '\b':
					sb.append("\\b");
					break;
				case '\t':
					sb.append("\\t");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\f':
					sb.append("\\f");
					break;
				case '\r':
					sb.append("\\r");
					break;
				default:
					if (c < ' ') {
						String t = "000" + Integer.toHexString(c);
						sb.append("\\u" + t.substring(t.length() - 4));
					} else {
						sb.append(c);
					}
			}
		}
		return sb.toString();
	}

	/**
	 * @param querySpec
	 * @return count of results for a query
	 */
	public int countQuery(QuerySpec querySpec) {
		final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
			String countSql = convertSmartSql(querySpec.countSmartSql);
			return DBHelper.getInstance(db).countRawCountQuery(db, countSql, querySpec.getArgs());
    	}
	}

	/**
	 * @param smartSql
	 * @return
	 */
	public String convertSmartSql(String smartSql) {
		final SQLiteDatabase db = getDatabase();
    	synchronized (db) {
			return SmartSqlHelper.getInstance(db).convertSmartSql(db, smartSql);
    	}
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
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
    		return create(soupName, soupElt, true);
    	}
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
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
	        String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
	        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
	        IndexSpec[] indexSpecs = DBHelper.getInstance(db).getIndexSpecs(db, soupName);

	        try {
	            if (handleTx) {
	                db.beginTransaction();
	            }
	            long now = System.currentTimeMillis();
	            long soupEntryId = DBHelper.getInstance(db).getNextId(db, soupTableName);

	            // Adding fields to soup element
	            soupElt.put(SOUP_ENTRY_ID, soupEntryId);
	            soupElt.put(SOUP_LAST_MODIFIED_DATE, now);
	            ContentValues contentValues = new ContentValues();
	            contentValues.put(ID_COL, soupEntryId);
	            contentValues.put(CREATED_COL, now);
	            contentValues.put(LAST_MODIFIED_COL, now);
	            if (!usesExternalStorage(soupName)) {
	                contentValues.put(SOUP_COL, soupElt.toString());
	            }
	            projectIndexedPaths(soupElt, contentValues, indexSpecs, TypeGroup.value_extracted_to_column);

	            // Inserting into database
	            boolean success = DBHelper.getInstance(db).insert(db, soupTableName, contentValues) == soupEntryId;

				// Fts
				if (success && hasFTS(soupName)) {
					String soupTableNameFts = soupTableName + FTS_SUFFIX;
					ContentValues contentValuesFts = new ContentValues();
					contentValuesFts.put(ROWID_COL, soupEntryId);
					projectIndexedPaths(soupElt, contentValuesFts, indexSpecs, TypeGroup.value_extracted_to_fts_column);
					// InsertHelper not working against virtual fts table
					db.insert(soupTableNameFts, null, contentValuesFts);
				}

	            // Add to external storage if applicable
	            if (success && usesExternalStorage(soupName) && dbOpenHelper instanceof DBOpenHelper) {
					success = ((DBOpenHelper) dbOpenHelper).saveSoupBlob(soupTableName, soupEntryId, soupElt, encryptionKey);
	            }

	            // Commit if successful
	            if (success) {
	                if (handleTx) {
	                    db.setTransactionSuccessful();
	                }
	                return soupElt;
	            } else {
	                return null;
	            }
	        }
			finally {
	            if (handleTx) {
	                db.endTransaction();
	            }
	        }
    	}
    }

	/**
	 * @soupName
	 * @return true if soup has at least one full-text search index
	 */
	private boolean hasFTS(String soupName) {
		SQLiteDatabase db = getDatabase();
		synchronized (db) {
			return DBHelper.getInstance(db).hasFTS(db, soupName);
		}
	}

	/**
	 * Populate content values by projecting index specs that have a type in typeGroup
	 * @param soupElt
	 * @param contentValues
	 * @param indexSpecs
	 * @param typeGroup
	 */
	private void projectIndexedPaths(JSONObject soupElt, ContentValues contentValues, IndexSpec[] indexSpecs, TypeGroup typeGroup) {
		for (IndexSpec indexSpec : indexSpecs) {
			if (typeGroup.isMember(indexSpec.type)) {
				projectIndexedPath(soupElt, contentValues, indexSpec);
			}
		}
	}

    /**
     * @param soupElt
     * @param contentValues
     * @param indexSpec
     */
    private void projectIndexedPath(JSONObject soupElt, ContentValues contentValues, IndexSpec indexSpec) {
        Object value = project(soupElt, indexSpec.path);

		contentValues.put(indexSpec.columnName, (String) null); // fall back
		if (value != null) {
			try {
				switch (indexSpec.type) {
					case integer:
						contentValues.put(indexSpec.columnName, ((Number) value).longValue());
						break;
					case string:
					case full_text:
						contentValues.put(indexSpec.columnName, value.toString());
						break;
					case floating:
						contentValues.put(indexSpec.columnName, ((Number) value).doubleValue());
						break;
				}
			} catch (Exception e) {
				// Ignore (will use the null value)
				SmartStoreLogger.e(TAG, "Unexpected error", e);
			}
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
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
	        String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
	        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");

	        JSONArray result = new JSONArray();
	        if (usesExternalStorage(soupName) && dbOpenHelper instanceof DBOpenHelper) {
		        for (long soupEntryId : soupEntryIds) {
			        JSONObject raw = ((DBOpenHelper) dbOpenHelper).loadSoupBlob(soupTableName, soupEntryId, encryptionKey);
			        if (raw != null) {
				        result.put(raw);
			        }
		        }
	        } else {
		        Cursor cursor = null;
		        try {
			        cursor = DBHelper.getInstance(db).query(db, soupTableName, new String[] { SOUP_COL }, null, null, getSoupEntryIdsPredicate(soupEntryIds), (String[]) null);
			        if (!cursor.moveToFirst()) {
				        return result;
			        }
			        do {
				        String raw = cursor.getString(cursor.getColumnIndex(SOUP_COL));
				        result.put(new JSONObject(raw));
			        }
			        while (cursor.moveToNext());
		        } finally {
			        safeClose(cursor);
		        }
	        }
	        return result;
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
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
    		return update(soupName, soupElt, soupEntryId, true);
    	}
    }

    /**
     * Update
     * Note: Passed soupElt is modified (last modified date and soup entry id fields)
     * @param soupName
     * @param soupElt
     * @param soupEntryId
	 * @param handleTx
     * @return
     * @throws JSONException
     */
    public JSONObject update(String soupName, JSONObject soupElt, long soupEntryId, boolean handleTx) throws JSONException {
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
			try {
				if (handleTx) {
					db.beginTransaction();
				}

				String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
				if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
				IndexSpec[] indexSpecs = DBHelper.getInstance(db).getIndexSpecs(db, soupName);

				long now = System.currentTimeMillis();

				// In the case of an upsert with external id, _soupEntryId won't be in soupElt
				soupElt.put(SOUP_ENTRY_ID, soupEntryId);
				// Updating last modified field in soup element
				soupElt.put(SOUP_LAST_MODIFIED_DATE, now);

				// Preparing data for row
				ContentValues contentValues = new ContentValues();
				contentValues.put(LAST_MODIFIED_COL, now);
				projectIndexedPaths(soupElt, contentValues, indexSpecs, TypeGroup.value_extracted_to_column);
				if (!usesExternalStorage(soupName)) {
					contentValues.put(SOUP_COL, soupElt.toString());
				}

				// Updating database
				boolean success = DBHelper.getInstance(db).update(db, soupTableName, contentValues, ID_PREDICATE, soupEntryId + "") == 1;

				// Fts
				if (success && hasFTS(soupName)) {
					String soupTableNameFts = soupTableName + FTS_SUFFIX;
					ContentValues contentValuesFts = new ContentValues();
					projectIndexedPaths(soupElt, contentValuesFts, indexSpecs, TypeGroup.value_extracted_to_fts_column);
					success = DBHelper.getInstance(db).update(db, soupTableNameFts, contentValuesFts, ROWID_PREDICATE, soupEntryId + "") == 1;
				}

				// Add to external storage if applicable
				if (success && usesExternalStorage(soupName) && dbOpenHelper instanceof DBOpenHelper) {
					success = ((DBOpenHelper) dbOpenHelper).saveSoupBlob(soupTableName, soupEntryId, soupElt, encryptionKey);
				}

				if (success) {
					if (handleTx) {
						db.setTransactionSuccessful();
					}
					return soupElt;
				} else {
					return null;
				}
			} finally {
				if (handleTx) {
					db.endTransaction();
				}
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
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
    		return upsert(soupName, soupElt, externalIdPath, true);
    	}
    }

    /**
     * Upsert (and commits) expecting _soupEntryId in soupElt for updates
     * @param soupName
     * @param soupElt
     * @return
     * @throws JSONException
     */
    public JSONObject upsert(String soupName, JSONObject soupElt) throws JSONException {
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
    		return upsert(soupName, soupElt, SOUP_ENTRY_ID);
    	}
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
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
	        long entryId = -1;
	        if (externalIdPath.equals(SOUP_ENTRY_ID)) {
	            if (soupElt.has(SOUP_ENTRY_ID)) {
	                entryId = soupElt.getLong(SOUP_ENTRY_ID);
	            }
	        } else {
	            Object externalIdObj = project(soupElt, externalIdPath);
	            if (externalIdObj != null) {
	                entryId = lookupSoupEntryId(soupName, externalIdPath, externalIdObj + "");
	            }
	            else {
					// Cannot have empty values for user-defined external ID upsert.
					throw new SmartStoreException(String.format("For upsert with external ID path '%s', value cannot be empty for any entries.", externalIdPath));
				}
	        }

	        // If we have an entryId, let's do an update, otherwise let's do a create
	        if (entryId != -1) {
	            return update(soupName, soupElt, entryId, handleTx);
	        } else {
	            return create(soupName, soupElt, handleTx);
	        }
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
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
	        String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
	        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
	        String columnName = DBHelper.getInstance(db).getColumnNameForPath(db, soupName, fieldPath);

	        Cursor cursor = null;
	        try {
	            cursor = db.query(soupTableName, new String[] {ID_COL}, columnName + " = ?", new String[] { fieldValue }, null, null, null);
	            if (cursor.getCount() > 1) {
	                throw new SmartStoreException(String.format("There are more than one soup elements where %s is %s", fieldPath, fieldValue));
	            }
	            if (cursor.moveToFirst()) {
	                return cursor.getLong(0);
	            } else {
	                return -1; // not found
	            }
	        } finally {
	            safeClose(cursor);
	        }
    	}
    }

    /**
     * Delete soup elements given by their ids (and commits)
     * @param soupName
     * @param soupEntryIds
     */
    public void delete(String soupName, Long... soupEntryIds) {
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
    		delete(soupName, soupEntryIds, true);
    	}
    }

    /**
     * Delete soup elements given by their ids
     * @param soupName
     * @param soupEntryIds
     * @param handleTx
     */
    public void delete(String soupName, Long[] soupEntryIds, boolean handleTx) {
    	final SQLiteDatabase db = getDatabase();
    	synchronized(db) {
	        String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
	        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
	        if (handleTx) {
	            db.beginTransaction();
	        }
	        try {
	            db.delete(soupTableName, getSoupEntryIdsPredicate(soupEntryIds), (String []) null);

				if (hasFTS(soupName)) {
					db.delete(soupTableName + FTS_SUFFIX, getRowIdsPredicate(soupEntryIds), (String[]) null);
				}

				if (usesExternalStorage(soupName) && dbOpenHelper instanceof DBOpenHelper) {
					((DBOpenHelper) dbOpenHelper).removeSoupBlob(soupTableName, soupEntryIds);
				}

	            if (handleTx) {
	                db.setTransactionSuccessful();
	            }
	        } finally {
	            if (handleTx) {
	                db.endTransaction();
	            }
	        }
    	}
    }

	/**
	 * Delete soup elements selected by querySpec (and commits)
	 * @param soupName
	 * @param querySpec Query returning entries to delete (if querySpec uses smartSQL, it must select soup entry ids)
	 */
	public void deleteByQuery(String soupName, QuerySpec querySpec) {
		final SQLiteDatabase db = getDatabase();
		synchronized(db) {
			deleteByQuery(soupName, querySpec, true);
		}
	}

	/**
	 * Delete soup elements selected by querySpec
	 * @param soupName
	 * @param querySpec
	 * @param handleTx
	 */
	public void deleteByQuery(String soupName, QuerySpec querySpec, boolean handleTx) {
		final SQLiteDatabase db = getDatabase();
		synchronized(db) {
			String soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
			if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
			if (handleTx) {
				db.beginTransaction();
			}
			try {
                String subQuerySql = String.format("SELECT %s FROM (%s) LIMIT %d", ID_COL, convertSmartSql(querySpec.idsSmartSql), querySpec.pageSize);
                String[] args = querySpec.getArgs();

                if (usesExternalStorage(soupName) && dbOpenHelper instanceof DBOpenHelper) {
					// Query list of ids and remove them from external storage
					Cursor c = null;
					try {
						c = db.query(soupTableName, new String[] { ID_COL }, buildInStatement(ID_COL, subQuerySql), args, null, null, null);
						if (c.moveToFirst()) {
							Long[] ids = new Long[c.getCount()];
							int counter = 0;
							do {
								ids[counter++] = c.getLong(0);
							} while (c.moveToNext());
							((DBOpenHelper) dbOpenHelper).removeSoupBlob(soupTableName, ids);
						}
					} finally {
						if (c != null) {
							c.close();
						}
					}
                }

                db.delete(soupTableName, buildInStatement(ID_COL, subQuerySql), args);

				if (hasFTS(soupName)) {
                    db.delete(soupTableName + FTS_SUFFIX, buildInStatement(ROWID_COL, subQuerySql), args);
				}

				if (handleTx) {
					db.setTransactionSuccessful();
				}
			} finally {
				if (handleTx) {
					db.endTransaction();
				}
			}
		}
	}

    /**
     * @return predicate to match soup entries by id
     */
    private String getSoupEntryIdsPredicate(Long[] soupEntryIds) {
        return buildInStatement(ID_COL, TextUtils.join(",", soupEntryIds));
    }


	/**
	 * @return predicate to match entries by rowid
	 */
	private String getRowIdsPredicate(Long[] rowids) {
        return buildInStatement(ROWID_COL, TextUtils.join(",", rowids));
	}

    /**
     * @param col
     * @param inPredicate
     * @return in statement
     */
    private String buildInStatement(String col, String inPredicate) {
        return String.format("%s IN (%s)", col, inPredicate);
    }

	/**
	 * @return ftsX to be used when creating the virtual table to support full_text queries
     */
	public FtsExtension getFtsExtension() {
		return ftsExtension;
	}

	/**
	 * Sets the ftsX to be used when creating the virtual table to support full_text queries
	 * NB: only used in tests
	 * @param ftsExtension
     */
	public void setFtsExtension(FtsExtension ftsExtension) {
		this.ftsExtension = ftsExtension;
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
    private void safeClose(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }

    /**
     * @param soup
     * @param path
     * @return object at path in soup
	 *
	 * Examples (in pseudo code):
	 *
	 * json = {"a": {"b": [{"c":"xx"}, {"c":"xy"}, {"d": [{"e":1}, {"e":2}]}, {"d": [{"e":3}, {"e":4}]}] }}
	 * projectIntoJson(jsonObj, "a") = {"b": [{"c":"xx"}, {"c":"xy"}, {"d": [{"e":1}, {"e":2}]}, {"d": [{"e":3}, {"e":4}]} ]}
	 * projectIntoJson(json, "a.b") = [{c:"xx"}, {c:"xy"}, {"d": [{"e":1}, {"e":2}]}, {"d": [{"e":3}, {"e":4}]}]
	 * projectIntoJson(json, "a.b.c") = ["xx", "xy"]                                     // new in 4.1
	 * projectIntoJson(json, "a.b.d") = [[{"e":1}, {"e":2}], [{"e":3}, {"e":4}]]         // new in 4.1
	 * projectIntoJson(json, "a.b.d.e") = [[1, 2], [3, 4]]                               // new in 4.1
	 *
     */
    public static Object project(JSONObject soup, String path) {
        if (soup == null) {
            return null;
        }
        if (path == null || path.equals("")) {
            return soup;
        }
        String[] pathElements = path.split("[.]");
		return project(soup, pathElements, 0);
    }

	private static Object project(Object jsonObj, String[] pathElements, int index) {
		Object result = null;
		if (index == pathElements.length) {
			return jsonObj;
		}

		if (null != jsonObj) {
			String pathElement = pathElements[index];

			if (jsonObj instanceof JSONObject) {
				JSONObject jsonDict = (JSONObject) jsonObj;
				Object dictVal = JSONObjectHelper.opt(jsonDict, pathElement);
				result = project(dictVal, pathElements, index+1);
			}
			else if (jsonObj instanceof JSONArray) {
				JSONArray jsonArr = (JSONArray) jsonObj;
				result = new JSONArray();
				for (int i=0; i<jsonArr.length(); i++) {
					Object arrayElt = JSONObjectHelper.opt(jsonArr, i);
					Object resultPart = project(arrayElt, pathElements, index);
					if (resultPart != null) {
						((JSONArray) result).put(resultPart);
					}
				}
				if (((JSONArray) result).length() == 0) {
					result = null;
				}
			}
		}

		return result;
	}

    /**
     * Enum for column type
     */
    public enum Type {
		string("TEXT"),
        integer("INTEGER"),
        floating("REAL"),
        full_text("TEXT"),
        json1(null);

        private String columnType;

        private Type(String columnType) {
            this.columnType = columnType;
        }

        public String getColumnType() {
            return columnType;
        }
    }

    /**
      * Enum for type groups
      */
    public enum TypeGroup {
        value_extracted_to_column {
            @Override
            public boolean isMember(Type type) {
                return type == Type.string || type == Type.integer || type == Type.floating || type == Type.full_text;
            }
        },
        value_extracted_to_fts_column {
            @Override
            public boolean isMember(Type type) {
                return type == Type.full_text;
            }
        },
        value_indexed_with_json_extract {
            @Override
            public boolean isMember(Type type) {
                return type == Type.json1;
            }
        };

        public abstract boolean isMember(Type type);
    }

	/**
	 * Enum for fts extensions
	 */
	public enum FtsExtension {
		fts4,
		fts5
	}

    /**
     * Exception thrown by smart store
     *
     */
    public static class SmartStoreException extends RuntimeException {

        public SmartStoreException(String message) {
            super(message);
        }

        public SmartStoreException(String message, Throwable t) { super(message, t); }

        private static final long serialVersionUID = -6369452803270075464L;

    }

	/**
	 * Updates the given table with a new name and adds columns if any.
	 *
	 * @param db Database to update
	 * @param oldName Old name of the table to be renamed, null if table should not be renamed.
	 * @param newName New name of the table to be renamed, null if table should not be renamed.
	 * @param columns Columns to add. Null if no new columns should be added.
	 */
	public static void updateTableNameAndAddColumns(SQLiteDatabase db, String oldName, String newName, String[] columns) {
		synchronized(SmartStore.class) {
			StringBuilder sb = new StringBuilder();
			if (columns != null && columns.length > 0) {
				for (String column : columns) {
					sb.append("ALTER TABLE ").append(oldName).append(" ADD COLUMN ").append(column).append(" INTEGER DEFAULT 0;");
				}
				db.execSQL(sb.toString());
			}
			if (oldName != null && newName != null) {
				sb = new StringBuilder();
				sb.append("ALTER TABLE ").append(oldName).append(" RENAME TO ").append(newName).append(';');
				db.execSQL(sb.toString());
			}
		}
	}

	/**
	 * Determines if the given soup features external storage.
	 *
	 * @param soupName Name of the soup to determine external storage enablement.
	 *
	 * @return  True if soup uses external storage; false otherwise.
	 */
	public boolean usesExternalStorage(String soupName) {
		final SQLiteDatabase db = getDatabase();
		synchronized (db) {
			return DBHelper.getInstance(db).getFeatures(db, soupName).contains(SoupSpec.FEATURE_EXTERNAL_STORAGE);
		}
	}

	/**
	 * Get SQLCipher runtime settings
	 *
	 * @return list of SQLCipher runtime settings
	 */
	public List<String> getRuntimeSettings() {
		return queryPragma("cipher_settings");
	}

	/**
	 * Get SQLCipher compile options
	 *
	 * @return list of SQLCipher compile options
	 */
	public List<String> getCompileOptions() {
		return queryPragma("compile_options");
	}

	/**
	 * Get SQLCipher version
	 *
	 * @return SQLCipher version
	 */
	public String getSQLCipherVersion() {
		return TextUtils.join(" ", queryPragma("cipher_version"));
	}

	@NonNull
	private List<String> queryPragma(String pragma) {
		final SQLiteDatabase db = getDatabase();
		ArrayList<String> results = new ArrayList<>();
		Cursor c = null;
		try {
			c = db.rawQuery("PRAGMA " + pragma, null);
			while (c.moveToNext()) {
				results.add(c.getString(0));
			}
		} finally {
			safeClose(c);
		}
		return results;
	}

}

/*
 * Copyright (c) 2014, salesforce.com, inc.
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
import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;

import net.sqlcipher.database.SQLiteDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class taking care of alter soup
 * Two entry points:
 * - new AlterSoupLongOperation(...) + run() => when asked to alterSoup in SmartStore
 * - LongOperation.getOperation(...) + run() => when completing interrupted long operations when opening the database
 * 
 */
public class AlterSoupLongOperation extends LongOperation {

	// Fields of details for alter soup long operation row in long_operations_status table
	private static final String SOUP_NAME = "soupName";
	private static final String SOUP_TABLE_NAME = "soupTableName";
	private static final String OLD_INDEX_SPECS = "oldIndexSpecs";
	private static final String NEW_INDEX_SPECS = "newIndexSpecs";
	private static final String RE_INDEX_DATA = "reIndexData";
	
    /**
     * Enum for alter steps
     */
    public enum AlterSoupStep {
		STARTING,
		RENAME_OLD_SOUP_TABLE,
		DROP_OLD_INDEXES,
		REGISTER_SOUP_USING_TABLE_NAME,
		COPY_TABLE,
		RE_INDEX_SOUP,
        DROP_OLD_TABLE;

        public static final AlterSoupStep LAST = DROP_OLD_TABLE;
    }

    // Soup being altered
	protected String soupName;

	// Backing table for soup being altered
	private String soupTableName;
	
	// Last step completed
	private AlterSoupStep afterStep;
	
	// New index specs
	private IndexSpec[] newIndexSpecs;
	
	// Old index specs
	private IndexSpec[] oldIndexSpecs;
	
	// True if soup elements should be brought to memory to be re-indexed
	private boolean reIndexData;
	
	// Instance of smartstore
	private SmartStore store;
	
	// Underlying database
	private SQLiteDatabase db;
	
	// Row id for long_operations_status
	private long rowId;
	
	/**
	 * Default constructor when reading back from long operations status table
	 * Should be followed by a call to: initFromDbRow
	 */
	public AlterSoupLongOperation() {
		
	}

	/**
	 * Constructor
	 * 
	 * @param store
	 * @param soupName
	 * @param newIndexSpecs
	 * @param reIndexData
	 * @throws JSONException 
	 */
	public AlterSoupLongOperation(SmartStore store, String soupName, IndexSpec[] newIndexSpecs,
			boolean reIndexData) throws JSONException {
		
    	synchronized(SmartStore.class) {
    		// Setting store field
    		this.store = store;
    		
    		// Setting db field
    		this.db = store.getDatabase();
    		
    		// Setting soupName field
    		this.soupName = soupName;
    		
			// Get backing table for soup
	        this.soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName);
	        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
	        
	        // Setting newIndexSpecs field
	        this.newIndexSpecs = newIndexSpecs;
	        
	        // Setting reIndexData field
	        this.reIndexData = reIndexData;
	        
	        // Get old indexSpecs
	        this.oldIndexSpecs = DBHelper.getInstance(db).getIndexSpecs(db, soupName);

    		// Create row in alter status table - auto commit
    		this.rowId = createLongOperationDbRow();
	        
    		// Last step completed
    		this.afterStep = AlterSoupStep.STARTING;
    	}
	}
	
	/* (non-Javadoc)
	 * @see com.salesforce.androidsdk.smartstore.store.LongOperation#run()
	 */
	@Override
	public void run() {
		run(AlterSoupStep.LAST);
	}
	
	/**
	 * Used by test only
	 * @param toStep
	 */
	public void run(AlterSoupStep toStep) {
		alterSoupInternal(toStep);
	}
	
	/**
	 * @return last step completed
	 */
	public AlterSoupStep getLastStepCompleted() {
		return afterStep;
	}
	
	/* (non-Javadoc)
	 * @see com.salesforce.androidsdk.smartstore.store.LongOperation#initFromDbRow(com.salesforce.androidsdk.smartstore.store.SmartStore, long, org.json.JSONObject, java.lang.String)
	 */
	@Override
	protected void initFromDbRow(SmartStore store, long rowId, JSONObject details, String statusStr) throws JSONException {
		this.store = store;
		this.db = store.getDatabase();
		this.rowId = rowId;
		this.afterStep = AlterSoupStep.valueOf(statusStr);
		this.soupName = details.getString(SOUP_NAME);
		this.newIndexSpecs = IndexSpec.fromJSON(details.getJSONArray(NEW_INDEX_SPECS));
		this.oldIndexSpecs = IndexSpec.fromJSON(details.getJSONArray(OLD_INDEX_SPECS));
		this.reIndexData = details.getBoolean(RE_INDEX_DATA);
		this.soupTableName = details.getString(SOUP_TABLE_NAME);
	}


	/**
	 * Helper method for alterSoup
	 * @param toStep 
	 */
	private void alterSoupInternal(AlterSoupStep toStep)  {
		
		switch(afterStep) {
		case STARTING:
			renameOldSoupTable();
			if (toStep == AlterSoupStep.RENAME_OLD_SOUP_TABLE) break;
		case RENAME_OLD_SOUP_TABLE:
			dropOldIndexes();
			if (toStep == AlterSoupStep.DROP_OLD_INDEXES) break;
		case DROP_OLD_INDEXES:
			registerSoupUsingTableName();
			if (toStep == AlterSoupStep.REGISTER_SOUP_USING_TABLE_NAME) break;
		case REGISTER_SOUP_USING_TABLE_NAME:
			copyTable();
			if (toStep == AlterSoupStep.COPY_TABLE) break;
		case COPY_TABLE:
			// Re-index soup (if requested)
			if (reIndexData)
				reIndexSoup();
			if (toStep == AlterSoupStep.RE_INDEX_SOUP) break;
		case RE_INDEX_SOUP:
			dropOldTable();		
			if (toStep == AlterSoupStep.DROP_OLD_TABLE) break;
		case DROP_OLD_TABLE:
			// Nothing left to do
			break;
		}
	}


	/**
	 * Step 1: rename old table
	 */
	protected void renameOldSoupTable() {
		// Rename backing table for soup
		db.execSQL("ALTER TABLE " + soupTableName + " RENAME TO " + getOldSoupTableName());

		// Renaming fts table if any
		if (IndexSpec.hasFTS(oldIndexSpecs)) {
			db.execSQL("ALTER TABLE " + soupTableName + SmartStore.FTS_SUFFIX + " RENAME TO " + getOldSoupTableName() + SmartStore.FTS_SUFFIX);
		}
	
		// Update row in alter status table - auto commit
		updateLongOperationDbRow(AlterSoupStep.RENAME_OLD_SOUP_TABLE);
	}

	/**
	 * Step 2: drop old indexes / remove entries in soup_index_map / cleaanup cache
	 */
	protected void dropOldIndexes() {
		// Removing db indexes on table (otherwise registerSoup will fail to create indexes with the same name)
		for (int i=0; i<oldIndexSpecs.length; i++) {
		    String indexName = soupTableName + "_" + i + "_idx";
		    db.execSQL("DROP INDEX IF EXISTS "  + indexName);
		}

		// Cleaning up soup index map table and cache
        try {
            db.beginTransaction();
            DBHelper.getInstance(db).delete(db, SmartStore.SOUP_INDEX_MAP_TABLE, SmartStore.SOUP_NAME_PREDICATE, soupName);

            // Remove from cache
            DBHelper.getInstance(db).removeFromCache(soupName);

    		// Update row in alter status table - auto commit
    		updateLongOperationDbRow(AlterSoupStep.DROP_OLD_INDEXES);

            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
        }
	}

	/**
	 * Step 3: register soup with new indexes
	 */
	protected void registerSoupUsingTableName() {
		// Create new table for soup
		store.registerSoupUsingTableName(soupName, newIndexSpecs, soupTableName);
		
		// Update row in alter status table -auto commit
		updateLongOperationDbRow(AlterSoupStep.REGISTER_SOUP_USING_TABLE_NAME);
	}


	/**
	 * Step 4: copy data from old soup table to new soup table
	 */
	protected void copyTable() {
		db.beginTransaction();
		try {
			// We need column names in the index specs
			this.newIndexSpecs = store.getSoupIndexSpecs(soupName);
		
			// Move data (core columns + indexed paths that we are still indexing)
			copyOldData();

			// Update row in alter status table 
			updateLongOperationDbRow(AlterSoupStep.COPY_TABLE);
		}
		finally {
			db.setTransactionSuccessful();
			db.endTransaction();
		}
	}


	/**
	 * Step 5: re-index soup for new indexes (optional step)
	 */
	protected void reIndexSoup() {
		// Putting path--type of old index specs in a set
		Set<String> oldPathTypeSet = new HashSet<String>();
		for (IndexSpec oldIndexSpec : oldIndexSpecs) {
			oldPathTypeSet.add(oldIndexSpec.getPathType());
		}
		
		// Filtering out the ones that do not have their path--type in oldPathTypeSet
		List<String> indexPaths = new ArrayList<String>();
		for (IndexSpec indexSpec : newIndexSpecs) {
			if (!oldPathTypeSet.contains(indexSpec.getPathType())) {
				indexPaths.add(indexSpec.path);
			}
		}
		
		db.beginTransaction();
        try {
            store.reIndexSoup(soupName, indexPaths.toArray(new String[0]), false);
            updateLongOperationDbRow(AlterSoupStep.RE_INDEX_SOUP);
        }
        finally {
            db.setTransactionSuccessful();
            db.endTransaction();
        }
	}


	/**
	 * Step 6: drop old soup table
	 */
	protected void dropOldTable() {
		// Drop old table
		db.execSQL("DROP TABLE " + getOldSoupTableName());

		// Dropping FTS table if any
		if (IndexSpec.hasFTS(oldIndexSpecs)) {
			db.execSQL("DROP TABLE IF EXISTS " + getOldSoupTableName() + SmartStore.FTS_SUFFIX);
		}

		// Update status row - auto commit
		updateLongOperationDbRow(AlterSoupStep.DROP_OLD_TABLE);
	}


	/**
	 * Create row in long operations status table for a new alter soup operation 
	 * @return
	 * @throws JSONException
	 */
	protected long createLongOperationDbRow() throws JSONException {
		AlterSoupStep status = AlterSoupStep.STARTING;
    	JSONObject details = getDetails();
		
		Long now = System.currentTimeMillis();
		ContentValues contentValues = new ContentValues();
    	contentValues.put(SmartStore.TYPE_COL, LongOperationType.alterSoup.toString());
    	contentValues.put(SmartStore.STATUS_COL, status.toString());
    	contentValues.put(SmartStore.DETAILS_COL, details.toString());
    	contentValues.put(SmartStore.CREATED_COL, now);
    	contentValues.put(SmartStore.LAST_MODIFIED_COL, now);
    	Log.i("SmartStore.trackAlterStatus", soupName + " " + status);
		return DBHelper.getInstance(db).insert(db, SmartStore.LONG_OPERATIONS_STATUS_TABLE, contentValues);
	}

	/* (non-Javadoc)
	 * @see com.salesforce.androidsdk.smartstore.store.LongOperation#getDetails()
	 */
	@Override
	public JSONObject getDetails() throws JSONException {
		JSONObject details = new JSONObject();
    	details.put(SOUP_NAME, soupName);
    	details.put(SOUP_TABLE_NAME, soupTableName);
    	details.put(OLD_INDEX_SPECS, IndexSpec.toJSON(oldIndexSpecs));
    	details.put(NEW_INDEX_SPECS, IndexSpec.toJSON(newIndexSpecs));
    	details.put(RE_INDEX_DATA, reIndexData);
		return details;
	}
	
	/**
	 * Update row in long operations status table for on-going alter soup operation
	 * Delete row if newStatus is AlterStatus.LAST
	 * @param newStatus
	 * 
	 * @return
	 */
	protected void updateLongOperationDbRow(AlterSoupStep newStatus) {
		if (newStatus == AlterSoupStep.LAST) {
	    	DBHelper.getInstance(db).delete(db, SmartStore.LONG_OPERATIONS_STATUS_TABLE, SmartStore.ID_PREDICATE, rowId + "");
		}
		else {
	    	Long now = System.currentTimeMillis();
			ContentValues contentValues = new ContentValues();
	    	contentValues.put(SmartStore.STATUS_COL, newStatus.toString());
	    	contentValues.put(SmartStore.LAST_MODIFIED_COL, now);
	    	DBHelper.getInstance(db).update(db, SmartStore.LONG_OPERATIONS_STATUS_TABLE, contentValues, SmartStore.ID_PREDICATE, rowId + "");
		}
    	Log.i("SmartStore.trackAlterStatus", soupName + " " + newStatus);
	}
	
	/**
	 * Helper method
	 * @return insert statement to copy data from soup old backing table to soup new backing table
	 */
	private void copyOldData() {
		Map<String, IndexSpec> mapOldSpecs = IndexSpec.mapForIndexSpecs(oldIndexSpecs);
		Map<String, IndexSpec> mapNewSpecs = IndexSpec.mapForIndexSpecs(newIndexSpecs);

		// Figuring out paths we are keeping
		Set<String> oldPaths = mapOldSpecs.keySet();
		Set<String> keptPaths = mapNewSpecs.keySet(); 
		keptPaths.retainAll(oldPaths);

		// Compute list of columns to copy from / list of columns to copy into
		List<String> oldColumns = new ArrayList<String>(); 
		List<String> newColumns = new ArrayList<String>();

		// Adding core columns
		for (String column : new String[] {SmartStore.ID_COL, SmartStore.SOUP_COL, SmartStore.CREATED_COL, SmartStore.LAST_MODIFIED_COL}) {
			oldColumns.add(column);
			newColumns.add(column);
		}

		// Adding indexed path columns that we are keeping 
		for (String keptPath : keptPaths) {
			IndexSpec oldIndexSpec = mapOldSpecs.get(keptPath);
			IndexSpec newIndexSpec = mapNewSpecs.get(keptPath);
			if (oldIndexSpec.type.getColumnType().equals(newIndexSpec.type.getColumnType())) {
				oldColumns.add(oldIndexSpec.columnName);
				newColumns.add(newIndexSpec.columnName);
			}
		}

		// Compute copy statement
		String copyToSoupTable = String.format("INSERT INTO %s (%s) SELECT %s FROM %s",
							soupTableName, TextUtils.join(",", newColumns),
							TextUtils.join(",", oldColumns), getOldSoupTableName());

		// Execute copy
		db.execSQL(copyToSoupTable);

		// Fts
		if (IndexSpec.hasFTS(newIndexSpecs)) {

			// Compute list of columns to copy from / list of columns to copy into for the fts table
			List<String> oldColumnsFts = new ArrayList<String>();
			List<String> newColumnsFts = new ArrayList<String>();

			// Adding docid column
			oldColumnsFts.add(SmartStore.ID_COL);
			newColumnsFts.add(SmartStore.DOCID_COL);

			// Adding indexed path columns that we are keeping
			for (String keptPath : keptPaths) {
				IndexSpec oldIndexSpec = mapOldSpecs.get(keptPath);
				IndexSpec newIndexSpec = mapNewSpecs.get(keptPath);
				if (oldIndexSpec.type.getColumnType().equals(newIndexSpec.type.getColumnType())
					&& newIndexSpec.type == SmartStore.Type.full_text) {
					oldColumnsFts.add(oldIndexSpec.columnName);
					newColumnsFts.add(newIndexSpec.columnName);
				}
			}

			// Compute copy statement for fts table
			String copyToFtsTable = String.format("INSERT INTO %s%s (%s) SELECT %s FROM %s",
					soupTableName, SmartStore.FTS_SUFFIX, TextUtils.join(",", newColumnsFts),
					TextUtils.join(",", oldColumnsFts), getOldSoupTableName());

			// Execute copy
			db.execSQL(copyToFtsTable);
		}
	}
	
	/**
	 * Return name old backing table should be renamed to
	 */
	private String getOldSoupTableName() {
		return this.soupTableName + "_old";
	}
}

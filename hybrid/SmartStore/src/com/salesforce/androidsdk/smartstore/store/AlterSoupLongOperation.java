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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.text.TextUtils;
import android.util.Log;

import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;

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
		initiated,
		oldIndexesDropped,
		oldSoupRemovedFromCache,
		oldSoupTableRenamed,
		registerSoupUsingTableNameCompleted,
		copyTableCompleted,
		reIndexSoupCompleted,
        dropOldTable;

        private static final AlterSoupStep LAST = dropOldTable;
    }
	
	/**
	 * Alter soup
	 * 
	 * @param soupName
	 * @param array of index specs
	 * @param reIndexData
	 * @throws JSONException 
	 */
	public void alterSoup(String soupName, IndexSpec[] indexSpecs,
			boolean reIndexData) throws JSONException {
		
    	synchronized(SmartStore.class) {
			// Get backing table for soup
	        String soupTableName = DBHelper.INSTANCE.getSoupTableName(db, soupName);
	        if (soupTableName == null) throw new SmartStoreException("Soup: " + soupName + " does not exist");
	
	        // Get old indexSpecs
	        IndexSpec[] oldIndexSpecs = DBHelper.INSTANCE.getIndexSpecs(db, soupName);

    		// Create row in alter status table - auto commit
    		long rowId = trackAlterStatus(soupName, soupTableName, oldIndexSpecs, indexSpecs, reIndexData);
	        
	        alterSoup(AlterSoupStep.initiated, AlterSoupStep.LAST, soupName, indexSpecs, reIndexData, soupTableName, oldIndexSpecs, rowId);
    	}
	}

    
	/* (non-Javadoc)
	 * @see com.salesforce.androidsdk.smartstore.store.LongOperation#resume(long, org.json.JSONObject, java.lang.String, java.lang.String)
	 */
	@Override
	protected void resume(long rowId, JSONObject details, String afterStepStr, String toStepStr) throws JSONException {
		AlterSoupStep afterStep = AlterSoupStep.valueOf(afterStepStr);
		AlterSoupStep toStep = (toStepStr == null ? AlterSoupStep.LAST : AlterSoupStep.valueOf(toStepStr));
		String soupName = details.getString(SOUP_NAME);
		IndexSpec[] indexSpecs = IndexSpec.fromJSON(new JSONArray(details.getString(NEW_INDEX_SPECS)));
		IndexSpec[] oldIndexSpecs = IndexSpec.fromJSON(new JSONArray(details.getString(OLD_INDEX_SPECS)));
		boolean reIndexData = details.getBoolean(RE_INDEX_DATA);
		String soupTableName = details.getString(SOUP_TABLE_NAME);
		
		alterSoup(afterStep, toStep, soupName, indexSpecs, reIndexData, soupTableName, oldIndexSpecs, rowId);
	}
	
	/**
	 * Helper method for alterSoup
	 * Does all the steps after afterStep to toStep included
	 * 
	 * @param afterStep
	 * @param toStep
	 * @param soupName
	 * @param indexSpecs
	 * @param reIndexData
	 * @param soupTableName
	 * @param oldIndexSpecs
	 * @param rowId
	 */
	private void alterSoup(AlterSoupStep afterStep, AlterSoupStep toStep, String soupName,
			IndexSpec[] indexSpecs, boolean reIndexData, String soupTableName,
			IndexSpec[] oldIndexSpecs, long rowId) {
		
		String soupTableNameOld = soupTableName + "_old";		

		
		switch(afterStep) {
		case initiated:
			if (toStep == AlterSoupStep.initiated) break;
			dropOldIndexes(soupName, soupTableName, oldIndexSpecs, rowId);
		case oldIndexesDropped:
			if (toStep == AlterSoupStep.oldIndexesDropped) break;
			removeOldSoupFromCache(soupName, rowId);
		case oldSoupRemovedFromCache:
			if (toStep == AlterSoupStep.oldSoupRemovedFromCache) break;
			renameOldSoupTable(soupName, soupTableName, rowId, soupTableNameOld);
		case oldSoupTableRenamed:
			if (toStep == AlterSoupStep.oldSoupTableRenamed) break;
			registerSoupUsingTableName(soupName, indexSpecs, soupTableName, rowId);
		case registerSoupUsingTableNameCompleted:
			if (toStep == AlterSoupStep.registerSoupUsingTableNameCompleted) break;
		case copyTableCompleted:
			if (toStep == AlterSoupStep.copyTableCompleted) break;
			copyTable(soupName, oldIndexSpecs, rowId, soupTableNameOld);
		case reIndexSoupCompleted:
			if (toStep == AlterSoupStep.reIndexSoupCompleted) break;
			reIndexSoup(soupName, reIndexData, soupTableName, oldIndexSpecs, rowId);
		case dropOldTable:
			if (toStep == AlterSoupStep.dropOldTable) break;
			dropOldTable(soupTableNameOld, rowId, soupName);		
		}
	}


	/**
	 * Step 1: drop old indexes since we are about to create indexes with the same names
	 * @param soupName
	 * @param soupTableName
	 * @param oldIndexSpecs
	 * @param rowId
	 */
	private void dropOldIndexes(String soupName, String soupTableName,
			IndexSpec[] oldIndexSpecs, long rowId) {
		// Removing db indexes on table (otherwise registerSoup will fail to create indexes with the same name)
		for (int i=0; i<oldIndexSpecs.length; i++) {
		    String indexName = soupTableName + "_" + i + "_idx";
		    db.execSQL("DROP INDEX "  + indexName);
		}
	
		// Update row in alter status table - auto commit
		trackAlterStatus(rowId, soupName, AlterSoupStep.oldIndexesDropped);
	}


	/**
	 * Step 2: rename old table
	 * @param soupName
	 * @param soupTableName
	 * @param rowId
	 * @param soupTableNameOld
	 */
	private void renameOldSoupTable(String soupName, String soupTableName,
			long rowId, String soupTableNameOld) {
		// Rename backing table for soup
		db.execSQL("ALTER TABLE " + soupTableName + " RENAME TO " + soupTableNameOld);
	
		// Update row in alter status table - auto commit
		trackAlterStatus(rowId, soupName, AlterSoupStep.oldSoupTableRenamed);
	}


	/**
	 * Step 3: remove old soup from cache since we about to register a soup with the same name
	 * @param soupName
	 * @param rowId
	 */
	private void removeOldSoupFromCache(String soupName, long rowId) {
		// Remove soup from cache
		DBHelper.INSTANCE.removeFromCache(soupName);
	
		// Update row in alter status table - auto commit
		trackAlterStatus(rowId, soupName, AlterSoupStep.oldSoupRemovedFromCache);
	}


	/**
	 * Step 4: register soup with new indexes
	 * @param soupName
	 * @param indexSpecs
	 * @param soupTableName
	 * @param rowId
	 */
	private void registerSoupUsingTableName(String soupName,
			IndexSpec[] indexSpecs, String soupTableName, long rowId) {
		// Create new table for soup
		store.registerSoupUsingTableName(soupName, indexSpecs, soupTableName);
	
		// Update row in alter status table -auto commit
		trackAlterStatus(rowId, soupName, AlterSoupStep.registerSoupUsingTableNameCompleted);
	}


	/**
	 * Step 5: copy data from old soup table to new soup table
	 * @param soupName
	 * @param oldIndexSpecs
	 * @param rowId
	 * @param soupTableNameOld
	 */
	private void copyTable(String soupName, IndexSpec[] oldIndexSpecs,
			long rowId, String soupTableNameOld) {
		// Get new backing table for soup
		String soupTableNameNew = DBHelper.INSTANCE.getSoupTableName(db, soupName);
	
		// Get new indexSpecs (we need db column names)
		IndexSpec[] newIndexSpecs = DBHelper.INSTANCE.getIndexSpecs(db, soupName);
		
		
		db.beginTransaction();
		try {
			// Move data (core columns + indexed paths that we are still indexing)
			db.execSQL(computeCopyTableStatement(soupTableNameOld, soupTableNameNew, oldIndexSpecs, newIndexSpecs));
	
			// Update row in alter status table 
			trackAlterStatus(rowId, soupName, AlterSoupStep.copyTableCompleted);
		}
		finally {
			db.setTransactionSuccessful();
			db.endTransaction();
		}
	}


	/**
	 * Step 6: re-index soup for new indexes (optional step)
	 * @param soupName
	 * @param reIndexData
	 * @param soupTableName
	 * @param oldIndexSpecs
	 * @param rowId
	 */
	private void reIndexSoup(String soupName, boolean reIndexData,
			String soupTableName, IndexSpec[] oldIndexSpecs, long rowId) {
		IndexSpec[] newIndexSpecs = DBHelper.INSTANCE.getIndexSpecs(db, soupName);
	
		// Re-index soup (if requested)
		if (reIndexData) {
            db.beginTransaction();
            try {
                store.reIndexSoup(soupTableName, IndexSpec.getChangedOrNewIndexSpecs(oldIndexSpecs, newIndexSpecs), false);
                trackAlterStatus(rowId, soupName, AlterSoupStep.reIndexSoupCompleted);
            }
            finally {
                db.setTransactionSuccessful();
                db.endTransaction();
            }
		}
	}


	/**
	 * Step 7: drop old soup table
	 * @param soupTableNameOld
	 * @param rowId
	 * @param soupName
	 */
	private void dropOldTable(String soupTableNameOld, long rowId, String soupName) {
		// Drop old table
		db.execSQL("DROP TABLE " + soupTableNameOld);
		
		// Update status row - auto commit
		trackAlterStatus(rowId, soupName, AlterSoupStep.dropOldTable);
	}


	/**
	 * Create row in alter status table for a new alter soup operation 
	 * @param soupName
	 * @param soupTableName
	 * @param oldIndexSpecs
	 * @param newIndexSpecs
	 * @param reIndexData
	 * @return
	 * @throws JSONException
	 */
	private long trackAlterStatus(String soupName, String soupTableName, IndexSpec[] oldIndexSpecs, IndexSpec[] newIndexSpecs, boolean reIndexData) throws JSONException {
		AlterSoupStep status = AlterSoupStep.initiated;
    	JSONObject details = new JSONObject();
    	details.put(SOUP_NAME, soupName);
    	details.put(SOUP_TABLE_NAME, soupTableName);
    	details.put(OLD_INDEX_SPECS, IndexSpec.toJSON(oldIndexSpecs).toString());
    	details.put(NEW_INDEX_SPECS, IndexSpec.toJSON(newIndexSpecs).toString());
    	details.put(RE_INDEX_DATA, reIndexData);
		
		Long now = System.currentTimeMillis();
		ContentValues contentValues = new ContentValues();
    	contentValues.put(SmartStore.TYPE_COL, LongOperationType.alterSoup.toString());
    	contentValues.put(SmartStore.STATUS_COL, status.toString());
    	contentValues.put(SmartStore.DETAILS_COL, details.toString());
    	contentValues.put(SmartStore.CREATED_COL, now);
    	contentValues.put(SmartStore.LAST_MODIFIED_COL, now);
    	Log.i("SmartStore.trackAlterStatus", soupName + " " + status);
		return DBHelper.INSTANCE.insert(db, SmartStore.LONG_OPERATIONS_STATUS_TABLE, contentValues);
	}
	
	/**
	 * Update row in alter status table for on-going alter soup operation
	 * Delete row if newStatus is AlterStatus.LAST
	 * 
	 * @param rowId
	 * @param soupName
	 * @param newStatus
	 * @return
	 */
	private void trackAlterStatus(long rowId, String soupName, AlterSoupStep newStatus) {
		if (newStatus == AlterSoupStep.LAST) {
	    	DBHelper.INSTANCE.delete(db, SmartStore.LONG_OPERATIONS_STATUS_TABLE, SmartStore.ID_PREDICATE, rowId + "");
		}
		else {
	    	Long now = System.currentTimeMillis();
			ContentValues contentValues = new ContentValues();
	    	contentValues.put(SmartStore.STATUS_COL, newStatus.toString());
	    	contentValues.put(SmartStore.LAST_MODIFIED_COL, now);
	    	DBHelper.INSTANCE.update(db, SmartStore.LONG_OPERATIONS_STATUS_TABLE, contentValues, SmartStore.ID_PREDICATE, rowId + "");
		}
    	Log.i("SmartStore.trackAlterStatus", soupName + " " + newStatus);
	}
	
	/**
	 * Helper method
	 * @param soupTableNameOld
	 * @param soupTableNameNew
	 * @param oldIndexSpecs
	 * @param newIndexSpecs
	 * @return insert statement to copy data from soup old backing table to soup new backing table
	 */
	private String computeCopyTableStatement(String soupTableNameOld, String soupTableNameNew, IndexSpec[] oldIndexSpecs, IndexSpec[] newIndexSpecs) {
		Map<String, IndexSpec> mapOldSpecs = mapForIndexSpecs(oldIndexSpecs);
		Map<String, IndexSpec> mapNewSpecs = mapForIndexSpecs(newIndexSpecs);

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
			if (oldIndexSpec.type == newIndexSpec.type) {
				oldColumns.add(oldIndexSpec.columnName);
				newColumns.add(newIndexSpec.columnName);
			}
		}

		// Compute and return statement
		return String.format("INSERT INTO %s (%s) SELECT %s FROM %s",
							soupTableNameNew, TextUtils.join(",", newColumns),
							TextUtils.join(",", oldColumns), soupTableNameOld);
	}
	
	/**
	 * Helper method
	 * @param indexSpecs
	 * @return map index spec path to index spec
	 */
	private Map<String, IndexSpec> mapForIndexSpecs(IndexSpec[] indexSpecs) {
		Map<String, IndexSpec> map = new HashMap<String, IndexSpec>();
		for (IndexSpec indexSpec : indexSpecs) {
			map.put(indexSpec.path, indexSpec);
		}
		return map;
	}
		
}

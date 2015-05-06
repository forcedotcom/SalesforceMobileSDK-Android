/*
 * Copyright (c) 2012-2014, salesforce.com, inc.
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
import java.util.Map.Entry;

import net.sqlcipher.DatabaseUtils.InsertHelper;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDoneException;
import net.sqlcipher.database.SQLiteStatement;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.app.SalesforceSDKManagerWithSmartStore;
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException;
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type;

/**
 * SmartStore Database Helper
 * 
 * Singleton class that provides helpful methods for accessing the database underneath the SmartStore
 * It also caches a number of of things to speed things up (e.g. soup table name, index specs, insert helpers etc)
 */
public class DBHelper {

	private static Map<SQLiteDatabase, DBHelper> INSTANCES;

	/**
	 * Returns the instance of this class associated with the database specified.
	 *
	 * @param db Database.
	 * @return Instance of this class.
	 */
	public static synchronized DBHelper getInstance(SQLiteDatabase db) {
		if (INSTANCES == null) {
			INSTANCES = new HashMap<SQLiteDatabase, DBHelper>();
		}
		DBHelper instance = INSTANCES.get(db);
		if (instance == null) {
			instance = new DBHelper();
			INSTANCES.put(db, instance);
		}
		return instance;
	}

	// Some queries
	private static final String COUNT_SELECT = "SELECT count(*) FROM %s %s";
	private static final String SEQ_SELECT = "SELECT seq FROM SQLITE_SEQUENCE WHERE name = ?";
	private static final String LIMIT_SELECT = "SELECT * FROM (%s) LIMIT %s";

	// Cache of soup name to soup table names
	private Map<String, String> soupNameToTableNamesMap = new HashMap<String, String>();

	// Cache of soup name to index specs
	private Map<String, IndexSpec[]> soupNameToIndexSpecsMap = new HashMap<String, IndexSpec[]>();

	// Cache of soup name to boolean indicating if soup uses FTS
	private Map<String, Boolean> soupNameToHasFTS = new HashMap<String, Boolean>();

	// Cache of table name to get-next-id compiled statements
	private Map<String, SQLiteStatement> tableNameToNextIdStatementsMap = new HashMap<String, SQLiteStatement>();

	// Cache of table name to insert helpers
	private Map<String, InsertHelper> tableNameToInsertHelpersMap = new HashMap<String, InsertHelper>();

	// Cache of raw count sql to compiled statements
	private Map<String, SQLiteStatement> rawCountSqlToStatementsMap = new HashMap<String, SQLiteStatement>();

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
	 * @param indexSpecs
	 */
	public void cacheIndexSpecs(String soupName, IndexSpec[] indexSpecs) {
		soupNameToIndexSpecsMap.put(soupName, indexSpecs.clone());
		soupNameToHasFTS.put(soupName, IndexSpec.hasFTS(indexSpecs));
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
	 * @return
	 */
	public Boolean getCachedHasFTS(String soupName) {
		return soupNameToHasFTS.get(soupName);
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
			
			cleanupRawCountSqlToStatementMaps(tableName);
		}
		soupNameToTableNamesMap.remove(soupName);
		soupNameToIndexSpecsMap.remove(soupName);
		soupNameToHasFTS.remove(soupName);
	}

	private void cleanupRawCountSqlToStatementMaps(String tableName) {
		List<String> countSqlToRemove = new ArrayList<String>();
		for (Entry<String, SQLiteStatement>  entry : rawCountSqlToStatementsMap.entrySet()) {
			String countSql = entry.getKey();
			if (countSql.contains(tableName)) {
				SQLiteStatement countProg = entry.getValue();
				if (countProg != null)
					countProg.close();
				countSqlToRemove.add(countSql);
			}
		}
		for (String countSql : countSqlToRemove) {
			rawCountSqlToStatementsMap.remove(countSql);
		}
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
	 * Does a limit for a raw query
	 * @param db
	 * @param sql
	 * @param limit
	 * @param whereArgs
	 * @return
	 */
	public Cursor limitRawQuery(SQLiteDatabase db, String sql, String limit, String... whereArgs) {
		String limitSql = String.format(LIMIT_SELECT, sql, limit);
		return db.rawQuery(limitSql, whereArgs);
	}

	/**
	 * Does a count for a raw count query
	 * @param db
	 * @param countSql
	 * @param whereArgs
	 * @return
	 */
	public int countRawCountQuery(SQLiteDatabase db, String countSql, String... whereArgs) {
		SQLiteStatement prog = rawCountSqlToStatementsMap.get(countSql);
		if (prog == null) {
			prog = db.compileStatement(countSql);
			rawCountSqlToStatementsMap.put(countSql, prog);
		}
		if (whereArgs != null) {
			for (int i=0; i<whereArgs.length; i++) {
				prog.bindString(i+1, whereArgs[i]);
			}
		}
		try {
			int count =  (int) prog.simpleQueryForLong();
			prog.clearBindings();
			return count;
		} catch (SQLiteDoneException e) {
			return -1;
		}
	}

	/**
	 * Does a count for a raw query
	 * @param db
	 * @param sql
	 * @param whereArgs
	 * @return
	 */
	public int countRawQuery(SQLiteDatabase db, String sql, String... whereArgs) {
		String countSql = String.format(COUNT_SELECT, "", "(" + sql + ")");
		return countRawCountQuery(db, countSql, whereArgs);
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
	 * Resets all cached data and deletes the database for all users.
	 *
	 * @param ctx Context.
	 */
	public synchronized void reset(Context ctx) {
		clearMemoryCache();
		final List<UserAccount> accounts = SalesforceSDKManagerWithSmartStore.getInstance().getUserAccountManager().getAuthenticatedUsers();
		if (accounts != null) {
			for (final UserAccount account : accounts) {
				reset(ctx, account);
			}
		}
	}

	/**
	 * Resets all cached data and deletes the database for the specified user.
	 *
	 * @param ctx Context.
	 * @param account User account.
	 */
	public synchronized void reset(Context ctx, UserAccount account) {
		clearMemoryCache();
		DBOpenHelper.deleteDatabase(ctx, account);
	}

	/**
	 * Resets all cached data from memory.
	 */
	public synchronized void clearMemoryCache() {

		// Closes all statements.
		for (final InsertHelper  ih : tableNameToInsertHelpersMap.values()) {
			ih.close();
		}
		for (final SQLiteStatement prog : tableNameToNextIdStatementsMap.values()) {
			prog.close();
		}
		for (final SQLiteStatement rawCountSql : rawCountSqlToStatementsMap.values()) {
			rawCountSql.close();
		}

		// Clears all maps.
		soupNameToTableNamesMap.clear();
		soupNameToIndexSpecsMap.clear();
		tableNameToInsertHelpersMap.clear();
		tableNameToNextIdStatementsMap.clear();
		rawCountSqlToStatementsMap.clear();
	}

    /**
     * Return column name in soup table that holds the soup projection for path
     * @param soupName
     * @param path
     * @return
     */
    public String getColumnNameForPath(SQLiteDatabase db, String soupName, String path) {
        IndexSpec[] indexSpecs = getIndexSpecs(db, soupName);
        for (IndexSpec indexSpec : indexSpecs) {
            if (indexSpec.path.equals(path)) {
                return indexSpec.columnName;
            }
        }
        throw new SmartStoreException(String.format("%s does not have an index on %s", soupName, path));
    }

    /**
     * Read index specs back from the soup index map table
     * @param db
     * @param soupName
     * @return
     */
    public IndexSpec[] getIndexSpecs(SQLiteDatabase db, String soupName) {
        IndexSpec[] indexSpecs = getCachedIndexSpecs(soupName);
        if (indexSpecs == null) {
            indexSpecs = getIndexSpecsFromDb(db, soupName);
            cacheIndexSpecs(soupName, indexSpecs);
        }
        return indexSpecs;
    }

    protected IndexSpec[] getIndexSpecsFromDb(SQLiteDatabase db, String soupName) {
        Cursor cursor = null;
        try {
            cursor = query(db, SmartStore.SOUP_INDEX_MAP_TABLE, new String[] {SmartStore.PATH_COL, SmartStore.COLUMN_NAME_COL, SmartStore.COLUMN_TYPE_COL}, null,
                    null, SmartStore.SOUP_NAME_PREDICATE, soupName);

            if (!cursor.moveToFirst()) {
                throw new SmartStoreException(String.format("%s does not have any indices", soupName));
            }
            List<IndexSpec> indexSpecs = new ArrayList<IndexSpec>();
            do {
                String path = cursor.getString(cursor.getColumnIndex(SmartStore.PATH_COL));
                String columnName = cursor.getString(cursor.getColumnIndex(SmartStore.COLUMN_NAME_COL));
                Type columnType = Type.valueOf(cursor.getString(cursor.getColumnIndex(SmartStore.COLUMN_TYPE_COL)));
                indexSpecs.add(new IndexSpec(path, columnType, columnName));
            } while (cursor.moveToNext());
            return indexSpecs.toArray(new IndexSpec[0]);
        }
        finally {
            safeClose(cursor);
        }
    }

	/**
	 * @param db
	 * @param soupName
	 * @return true if soup has full-text-search index
	 */
	public boolean hasFTS(SQLiteDatabase db, String soupName) {
		getIndexSpecs(db, soupName); // will populate cache if needed
		return getCachedHasFTS(soupName);
	}

	/**
     * Return table name for a given soup or null if the soup doesn't exist
     * @param db
     * @param soupName
     * @return 
    */
   public String getSoupTableName(SQLiteDatabase db, String soupName) {
       String soupTableName = getCachedTableName(soupName);
       if (soupTableName == null) {
           soupTableName = getSoupTableNameFromDb(db, soupName);
           if (soupTableName != null) {
               cacheTableName(soupName, soupTableName);
           }
           // Note: if you ask twice about a non-existing soup, we go to the database both times
           //       we could optimize for that scenario but it doesn't seem very important
       }
       return soupTableName;
   }

   protected String getSoupTableNameFromDb(SQLiteDatabase db, String soupName) {
       Cursor cursor = null;
       try {
           cursor = query(db, SmartStore.SOUP_NAMES_TABLE, new String[] {SmartStore.ID_COL}, null, null, SmartStore.SOUP_NAME_PREDICATE, soupName);
           if (!cursor.moveToFirst()) {
               return null;
           }
           return SmartStore.getSoupTableName(cursor.getLong(cursor.getColumnIndex(SmartStore.ID_COL)));
       	}
       	finally {
           safeClose(cursor);
       	}
   	}

    /**
     * @param cursor
     */
    protected void safeClose(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }
}

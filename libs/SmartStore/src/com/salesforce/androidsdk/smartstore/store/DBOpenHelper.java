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

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;
import android.content.Context;
import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;

/**
 * Helper class to manage SmartStore's database creation and version management.
 */
public class DBOpenHelper extends SQLiteOpenHelper {

	// 1 --> up until 2.3
	// 2 --> starting at 2.3 (new meta data table long_operations_status)
	public static final int DB_VERSION = 2;

	public static final String DB_NAME = "smartstore%s.db";

	private static Map<String, DBOpenHelper> openHelpers;
	private static DBOpenHelper defaultHelper;

	/**
	 * Returns the DBOpenHelper instance associated with this user account.
	 *
	 * @param ctx Context.
	 * @param account User account.
	 * @return DBOpenHelper instance.
	 */
	public static synchronized DBOpenHelper getOpenHelper(Context ctx,
			UserAccount account) {
		return getOpenHelper(ctx, account, null);
	}

	/**
	 * Returns the DBOpenHelper instance associated with this user and community.
	 *
	 * @param ctx Context.
	 * @param account User account.
	 * @param communityId Community ID.
	 * @return DBOpenHelper instance.
	 */
	public static synchronized DBOpenHelper getOpenHelper(Context ctx,
			UserAccount account, String communityId) {
		String dbName = String.format(DB_NAME, "");

		/*
		 * If this method is called before authentication, we will simply
		 * return the default smart store DB, which is not associated with
		 * any user account. Otherwise, we will return a unique database
		 * at the community level.
		 */
		if (account != null) {

			// Default user path for a user is 'internal', if community ID is null.
			final String dbPath = account.getCommunityLevelFilenameSuffix(communityId);
			if (!TextUtils.isEmpty(dbPath)) {
				dbName = String.format(DB_NAME, dbPath);
			}
			String uniqueId = account.getUserId();
			if (!TextUtils.isEmpty(communityId)) {
				uniqueId = uniqueId + communityId;
			}
			DBOpenHelper helper = null;
			if (openHelpers == null) {
				openHelpers = new HashMap<String, DBOpenHelper>();
				helper = new DBOpenHelper(ctx, dbName);
				openHelpers.put(uniqueId, helper);
			} else {
				helper = openHelpers.get(uniqueId);
			}
			if (helper == null) {
				helper = new DBOpenHelper(ctx, dbName);
				openHelpers.put(uniqueId, helper);
			}
			return helper;
		} else {
			if (defaultHelper == null) {
				defaultHelper = new DBOpenHelper(ctx, dbName);
			}
			return defaultHelper;
		}
	}

	private DBOpenHelper(Context context, String dbName) {
		super(context, dbName, null, DB_VERSION, new DBHook());
		SQLiteDatabase.loadLibs(context);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		SmartStore.createMetaTables(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion == 1) {
			SmartStore.createLongOperationsStatusTable(db);
		}
	}
	
	@Override
	public void onOpen(SQLiteDatabase db) {
		(new SmartStore(db)).resumeLongOperations();
	}

	/**
	 * Deletes the underlying database for the specified user account.
	 *
	 * @param ctx Context.
	 * @param account User account.
	 */
	public static void deleteDatabase(Context ctx, UserAccount account) {
		deleteDatabase(ctx, account, null);
	}

	/**
	 * Deletes the underlying database for the specified user and community.
	 *
	 * @param ctx Context.
	 * @param account User account.
	 * @param communityId Community ID.
	 */
	public static void deleteDatabase(Context ctx, UserAccount account,
			String communityId) {
		if (account != null) {
			String uniqueId = account.getUserId();
			if (!TextUtils.isEmpty(communityId)) {
				uniqueId = uniqueId + communityId;
			}
			if (openHelpers != null) {
				final DBOpenHelper helper = openHelpers.get(uniqueId);
				if (helper != null) {
					helper.close();
					openHelpers.remove(uniqueId);
				}
			}
		} else if (defaultHelper != null) {
			defaultHelper.close();
			defaultHelper = null;
		}
		String dbName = String.format(DB_NAME, "");
		if (account != null) {
			final String dbPath = account.getCommunityLevelFilenameSuffix(communityId);
			if (!TextUtils.isEmpty(dbPath)) {
				dbName = String.format(DB_NAME, dbPath);
			}
		}
		ctx.deleteDatabase(dbName);

    	// Deletes the community databases associated with this user account.
    	final String dbPath = ctx.getApplicationInfo().dataDir + "/databases";
    	final File dir = new File(dbPath);
    	if (dir != null) {
        	final SmartStoreFileFilter fileFilter = new SmartStoreFileFilter(dbName);
        	final File[] fileList = dir.listFiles();
        	if (fileList != null) {
            	for (final File file : fileList) {
            		if (file != null && fileFilter.accept(dir, file.getName())) {
            			file.delete();
            		}
            	}
        	}
    	}
	}

	static class DBHook implements SQLiteDatabaseHook {
		public void preKey(SQLiteDatabase database) {
			database.execSQL("PRAGMA cipher_default_kdf_iter = '4000'"); 
			// the new default for sqlcipher 3.x (64000) is too slow
            // also that way we can open 2.x databases without any migration
		}

		public void postKey(SQLiteDatabase database) {
		}
	};

    /**
     * This class acts as a filter to identify only the relevant SmartStore files.
     *
     * @author bhariharan
     */
    private static class SmartStoreFileFilter implements FilenameFilter {

    	private String dbName;

    	/**
    	 * Parameterized constructor.
    	 *
    	 * @param dbName Database name.
    	 */
    	public SmartStoreFileFilter(String dbName) {
    		this.dbName = dbName;
    	}

		@Override
		public boolean accept(File dir, String filename) {
			final String subString = dbName.substring(0,
					dbName.length() - 3);
			if (filename != null && filename.startsWith(subString)) {
				return true;
			}
			return false;
		}
    }
}

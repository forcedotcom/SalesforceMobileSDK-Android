/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import android.content.Context;
import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.analytics.EventBuilderHelper;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;
import com.salesforce.androidsdk.util.ManagedFilesHelper;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to manage SmartStore's database creation and version management.
 */
public class DBOpenHelper extends SQLiteOpenHelper {

	// 1 --> up until 2.3
	// 2 --> starting at 2.3 (new meta data table long_operations_status)
	// 3 --> starting at 4.3 (soup_names table changes to soup_attr)
	public static final int DB_VERSION = 3;
	public static final String DEFAULT_DB_NAME = "smartstore";
	public static final String SOUP_ELEMENT_PREFIX = "soupelt_";
	private static final String TAG = "DBOpenHelper";
	private static final String DB_NAME_SUFFIX = ".db";
	private static final String ORG_KEY_PREFIX = "00D";
	private static final String EXTERNAL_BLOBS_SUFFIX = "_external_soup_blobs/";
	public static final String DATABASES = "databases";
	private static String dataDir;
	private String dbName;

	/*
	 * Cache for the helper instances
	 */
	private static Map<String, DBOpenHelper> openHelpers = new HashMap<>();

	/**
	 * Returns a map of all DBOpenHelper instances created. The key is the
	 * database name and the value is the instance itself.
	 *
	 * @return Map of DBOpenHelper instances.
	 */
	public static synchronized Map<String, DBOpenHelper> getOpenHelpers() {
		return openHelpers;
	}

	/**
	 * Returns a list of all prefixes for  user databases.
	 *
	 * @return List of Database names(prefixes).
	 */
	public static synchronized List<String> getUserDatabasePrefixList(Context ctx,
			UserAccount account, String communityId) {
		return ManagedFilesHelper.getPrefixList(ctx, DATABASES, account.getCommunityLevelFilenameSuffix(communityId), DB_NAME_SUFFIX, null);
	}

	/**
	 * Returns a list of all prefixes for  user databases.
	 *
	 * @return List of Database names(prefixes).
	 */
	public static synchronized List<String> getGlobalDatabasePrefixList(Context ctx,
			UserAccount account, String communityId) {
		return ManagedFilesHelper.getPrefixList(ctx, DATABASES, "", DB_NAME_SUFFIX, ORG_KEY_PREFIX);
	}

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
		return getOpenHelper(ctx, DEFAULT_DB_NAME, account, communityId);
	}

	/**
	 * Returns the DBOpenHelper instance for the given database name.
	 *
	 * @param ctx Context.
	 * @param dbNamePrefix The database name. This must be a valid file name without a
	 *                     filename extension such as ".db".
	 * @param account User account. If this method is called before authentication,
	 * 				we will simply return the smart store DB, which is not associated
	 * 				with any user account. Otherwise, we will return a unique
	 * 				database at the community level.
	 * @param communityId Community ID.
	 * @return DBOpenHelper instance.
	 */
	public static DBOpenHelper getOpenHelper(Context ctx, String dbNamePrefix,
			UserAccount account, String communityId) {
		final StringBuilder dbName = new StringBuilder(dbNamePrefix);

		// If we have account information, we will use it to create a database suffix for the user.
		if (account != null) {

			// Default user path for a user is 'internal', if community ID is null.
			final String accountSuffix = account.getCommunityLevelFilenameSuffix(communityId);
			dbName.append(accountSuffix);
		}
		dbName.append(DB_NAME_SUFFIX);
		final String fullDBName = dbName.toString();
		DBOpenHelper helper = openHelpers.get(fullDBName);
		if (helper == null) {
			List<String> numDbs = null;
			String key = "numGlobalStores";
			String eventName = "globalSmartStoreInit";
			if (account == null) {
				numDbs = getGlobalDatabasePrefixList(ctx, null, communityId);
			} else {
				key = "numUserStores";
				eventName = "userSmartStoreInit";
				numDbs = getUserDatabasePrefixList(ctx, account, communityId);
			}
			int numStores = numDbs.size();
			final JSONObject storeAttributes = new JSONObject();
			try {
				storeAttributes.put(key, numStores);
			} catch (JSONException e) {
                SmartStoreLogger.e(TAG, "Error occurred while creating JSON", e);
			}
			EventBuilderHelper.createAndStoreEvent(eventName, account, TAG, storeAttributes);
			helper = new DBOpenHelper(ctx, fullDBName);
			openHelpers.put(fullDBName, helper);
		}
		return helper;
	}

	protected DBOpenHelper(Context context, String dbName) {
		super(context, dbName, null, DB_VERSION, new DBHook());
		this.loadLibs(context);
		this.dbName = dbName;
		dataDir = context.getApplicationInfo().dataDir;
	}

	protected void loadLibs(Context context) {
		SQLiteDatabase.loadLibs(context);
	}

	@Override
	public void onConfigure(final SQLiteDatabase db) {
		db.enableWriteAheadLogging();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {

		/*
		 * SQLCipher manages locking on the DB at a low level. However,
		 * we explicitly lock on the DB as well, for all SmartStore
		 * operations. This can lead to deadlocks or ReentrantLock
		 * exceptions where a thread is waiting for itself. Hence, we
		 * set the default SQLCipher locking to 'false', since we
		 * manage locking at our level anyway.
		 */
		db.setLockingEnabled(false);
		SmartStore.createMetaTables(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		/*
		 * SQLCipher manages locking on the DB at a low level. However,
		 * we explicitly lock on the DB as well, for all SmartStore
		 * operations. This can lead to deadlocks or ReentrantLock
		 * exceptions where a thread is waiting for itself. Hence, we
		 * set the default SQLCipher locking to 'false', since we
		 * manage locking at our level anyway.
		 */
		db.setLockingEnabled(false);
	}

	/**
	 * Deletes the underlying database for the specified user account.
	 *
	 * @param ctx Context.
	 * @param account User account.
	 */
	public static synchronized void deleteDatabase(Context ctx, UserAccount account) {
		deleteDatabase(ctx, account, null);
	}

	/**
	 * Deletes the underlying database for the specified user and community.
	 *
	 * @param ctx Context.
	 * @param account User account.
	 * @param communityId Community ID.
	 */
	public static synchronized void deleteDatabase(Context ctx, UserAccount account,
			String communityId) {
		deleteDatabase(ctx, DEFAULT_DB_NAME, account, communityId);
	}

	/**
	 * Deletes the underlying database for the specified user and community.
	 *
	 * @param ctx Context.
	 * @param dbNamePrefix The database name. This must be a valid file name without a
	 *                     filename extension such as ".db".
	 * @param account User account.
	 * @param communityId Community ID.
	 */
	public static synchronized void deleteDatabase(Context ctx, String dbNamePrefix,
			UserAccount account, String communityId) {
		try {
			final StringBuffer dbName = new StringBuffer(dbNamePrefix);

			// If we have account information, we will use it to create a database suffix for the user.
			if (account != null) {

				// Default user path for a user is 'internal', if community ID is null.
				final String accountSuffix = account.getCommunityLevelFilenameSuffix(communityId);
				dbName.append(accountSuffix);
			}
			dbName.append(DB_NAME_SUFFIX);
			final String fullDBName = dbName.toString();

			// Close and remove the helper from the cache if it exists.
			final DBOpenHelper helper = openHelpers.get(fullDBName);
			if (helper != null) {
				helper.close();
				openHelpers.remove(fullDBName);
			}

			// Physically delete the database from disk.
			ctx.deleteDatabase(fullDBName);

			// If community id was not passed in, then we remove ALL databases for the account.
			if (account != null && TextUtils.isEmpty(communityId)) {
				String accountSuffix = account.getUserLevelFilenameSuffix();
				File[] files = ManagedFilesHelper
						.getFiles(ctx, DATABASES, dbNamePrefix + accountSuffix, DB_NAME_SUFFIX, null);
				for (File file : files) {
					openHelpers.remove(file.getName());
				}
				ManagedFilesHelper.deleteFiles(files);
			}
		} catch (Exception e) {
            SmartStoreLogger.e(TAG, "Exception occurred while attemption to delete database", e);
		}
	}

	/**
	 * Deletes all remaining authenticated databases. We pass in the key prefix
	 * for an organization here, because all authenticated DBs will have to
	 * go against an org, which means the org ID will be a part of the DB name.
	 * This prevents the global DBs from being removed.
	 *
	 * @param ctx Context.
	 */
	public static synchronized void deleteAllUserDatabases(Context ctx) {
		File[] files = ManagedFilesHelper.getFiles(ctx, DATABASES, ORG_KEY_PREFIX, DB_NAME_SUFFIX, null);
		for (File file : files) {
			openHelpers.remove(file.getName());
		}
		ManagedFilesHelper.deleteFiles(files);
	}

	/**
	 * Deletes all databases of given user.
	 *
	 * @param ctx Context.
	 * @param userAccount User account.
	 */
	public static synchronized void deleteAllDatabases(Context ctx, UserAccount userAccount) {
		if (userAccount != null) {
			File[] files = ManagedFilesHelper
				.getFiles(ctx, DATABASES, userAccount.getUserLevelFilenameSuffix(), DB_NAME_SUFFIX,
					null);
			for (File file : files) {
				openHelpers.remove(file.getName());
			}
			ManagedFilesHelper.deleteFiles(files);
		}
	}

	/**
	 * Determines if a smart store currently exists for the given account and/or community id.
	 *
	 * @param ctx Context.
	 * @param account User account.
	 * @param communityId Community ID.
	 * @return boolean indicating if a smartstore already exists.
	 */
	public static boolean smartStoreExists(Context ctx, UserAccount account,
			String communityId) {
		return smartStoreExists(ctx, DEFAULT_DB_NAME, account, communityId);
	}

	/**
	 * Determines if a smart store currently exists for the given database name, account
	 * and/or community id.
	 *
	 * @param ctx Context.
	 * @param dbNamePrefix The database name. This must be a valid file name without a
	 *                     filename extension such as ".db".
	 * @param account User account.
	 * @param communityId Community ID.
	 * @return boolean indicating if a smartstore already exists.
	 */
	public static boolean smartStoreExists(Context ctx, String dbNamePrefix,
			UserAccount account, String communityId) {
		final StringBuilder dbName = new StringBuilder(dbNamePrefix);
		if (account != null) {
			final String dbSuffix = account.getCommunityLevelFilenameSuffix(communityId);
			dbName.append(dbSuffix);
		}
		dbName.append(DB_NAME_SUFFIX);
		return ctx.getDatabasePath(dbName.toString()).exists();
	}

	static class DBHook implements SQLiteDatabaseHook {
		public void preKey(SQLiteDatabase database) {
		}

		/**
		 * Need to migrate for SqlCipher 4.x
		 * @param database db being processed
		 */
		public void postKey(SQLiteDatabase database) {
			// Using sqlcipher 2.x kdf iter because 3.x default (64000) and 4.x default (256000) are too slow
			// => should open 2.x databases without any migration
			database.rawExecSQL("PRAGMA kdf_iter = 4000");
		}
	}

	/**
	 * Removes all files and folders in the given directory recursively as well as removes itself.
	 *
	 * @param dir Directory to remove all files and folders recursively.
	 * @return True if all delete operations were successful. False otherwise.
	 */
	public static boolean removeAllFiles(File dir) {
		if (dir != null && dir.exists()) {
			boolean success = true;
			File[] files = dir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						success &= file.delete();
					} else {
						success &= removeAllFiles(file);
					}
				}
			}
			success &= dir.delete();
			return success;
		} else {
			return false;
		}
	}

	/**
	 * Changes the encryption key on the database.
	 *
	 * @param db Database object.
	 * @param oldKey Old encryption key.
	 * @param newKey New encryption key.
	 */
	public static synchronized void changeKey(SQLiteDatabase db, String oldKey, String newKey) {
		db.query("PRAGMA rekey = '" + newKey + "'");
	}
}

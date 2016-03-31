/*
 * Copyright (c) 2014-2015, salesforce.com, inc.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.accounts.UserAccount;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

/**
 * Helper class to manage SmartStore's database creation and version management.
 */
public class DBOpenHelper extends SQLiteOpenHelper {

	// 1 --> up until 2.3
	// 2 --> starting at 2.3 (new meta data table long_operations_status)
	public static final int DB_VERSION = 2;
	public static final String DEFAULT_DB_NAME = "smartstore";
	private static final String DB_NAME_SUFFIX = ".db";
	private static final String ORG_KEY_PREFIX = "00D";

	/*
	 * Cache for the helper instances
	 */
	private static Map<String, DBOpenHelper> openHelpers = new HashMap<String, DBOpenHelper>();

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
		final StringBuffer dbName = new StringBuffer(dbNamePrefix);

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
			helper = new DBOpenHelper(ctx, fullDBName);
			openHelpers.put(fullDBName, helper);
		}
		return helper;
	}

	protected DBOpenHelper(Context context, String dbName) {
		super(context, dbName, null, DB_VERSION, new DBHook());
		this.loadLibs(context);
	}

	 protected void loadLibs(Context context) {
        SQLiteDatabase.loadLibs(context);
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
		if (oldVersion == 1) {
			SmartStore.createLongOperationsStatusTable(db);
		}
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public void onOpen(SQLiteDatabase db) {
		(new SmartStore(db)).resumeLongOperations();
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
				StringBuffer communityDBNamePrefix = new StringBuffer(dbNamePrefix);
				String accountSuffix = account.getUserLevelFilenameSuffix();
				communityDBNamePrefix.append(accountSuffix);
		    	deleteFiles(ctx, communityDBNamePrefix.toString());
			}
		} catch (Exception e) {
			Log.e("DBOpenHelper:deleteDatabase", "Exception occurred while attempting to delete database.", e);
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
		deleteFiles(ctx, ORG_KEY_PREFIX);
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
		final StringBuffer dbName = new StringBuffer(dbNamePrefix);
		if (account != null) {
			final String dbSuffix = account.getCommunityLevelFilenameSuffix(communityId);
			dbName.append(dbSuffix);
		}
		dbName.append(DB_NAME_SUFFIX);
		return ctx.getDatabasePath(dbName.toString()).exists();
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

	private static void deleteFiles(Context ctx, String prefix) {
		final String dbPath = ctx.getApplicationInfo().dataDir + "/databases";
    	final File dir = new File(dbPath);
    	if (dir != null) {
        	final SmartStoreFileFilter fileFilter = new SmartStoreFileFilter(prefix);
        	final File[] fileList = dir.listFiles();
        	if (fileList != null) {
            	for (final File file : fileList) {
            		if (file != null && fileFilter.accept(dir, file.getName())) {
            			file.delete();
            			openHelpers.remove(file.getName());
            		}
            	}
        	}
    	}
	}

    /**
     * This class acts as a filter to identify only the relevant SmartStore files.
     *
     * @author bhariharan
     */
    private static class SmartStoreFileFilter implements FilenameFilter {

    	private String dbNamePrefix;

    	/**
    	 * Parameterized constructor.
    	 *
    	 * @param dbNamePrefix Database name prefix pattern.
    	 */
    	public SmartStoreFileFilter(String dbNamePrefix) {
    		this.dbNamePrefix = dbNamePrefix;
    	}

		@Override
		public boolean accept(File dir, String filename) {
			if (filename != null && filename.contains(dbNamePrefix)) {
				return true;
			}
			return false;
		}
    }

	/**
	 * Returns the path to external blobs folder for the given soup in this db.
	 *
	 * @param soupName Name of the soup for which to get external blobs folder
	 *
	 * @return Path to external blobs folder for the given soup
	 */
	public String getExternalSoupBlobsPath(String soupName) {
		StringBuilder path = new StringBuilder(context.getApplicationInfo().dataDir);
		path.append("/databases/").append(dbName).append("_external_soup_blobs/").append(soupName).append('/');
		return path.toString();
	}

	/**
	 * Creates the folder for external blobs for the given soup name.
	 *
	 * @param soupName Soup for which to create the external blobs folder
	 */
	public void createExternalBlobsDirectory(String soupName) {
		File blobsDirectory = new File(getExternalSoupBlobsPath(soupName));
		blobsDirectory.mkdirs();
	}

	/**
	 * Places the soup blob on file storage. The name and folder are determined by the soup and soup entry id.
	 *
	 * @param soupName Name of the soup that the blob belongs to.
	 * @param soupEntryId Entry id for the soup blob.
	 * @param soupElt Blob to store on file storage in JSON format.
	 *
	 * @return True if operation was successful, false otherwise.
	 */
	public boolean packSoup(String soupName, long soupEntryId, JSONObject soupElt) {
		FileOutputStream outputStream;
		File file = new File(getExternalSoupBlobsPath(soupName), "soupelt_" + soupEntryId);

		try {
			outputStream = new FileOutputStream(file, false);
			outputStream.write(soupElt.toString().getBytes());
			outputStream.close();
			return true;
		} catch (IOException ex) {
			Log.e("DBOpenHelper:packSoup", "Exception occurred while attempting to write external soup blob.", ex);
		}
		return false;
	}

	/**
	 * Retrieves the soup blob for the given soup entry id from file storage.
	 *
	 * @param soupName Soup name to which the blob belongs.
	 * @param soupEntryId Entry id for the requested soup blob.
	 *
	 * @return The blob from file storage represented as JSON. Returns null if there was an error.
	 */
	public JSONObject unpackSoup(String soupName, long soupEntryId) {
		File file = new File(getExternalSoupBlobsPath(soupName), "soupelt_" + soupEntryId);
		StringBuilder json = new StringBuilder();
		JSONObject result = null;
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line;

			while ((line = br.readLine()) != null) {
				json.append(line).append('\n');
			}

			br.close();
			result = new JSONObject(json.toString());

		} catch (JSONException | IOException ex) {
			Log.e("DBOpenHelper:unpackSoup", "Exception occurred while attempting to read external soup blob.", ex);
		}
		return result;
	}
}

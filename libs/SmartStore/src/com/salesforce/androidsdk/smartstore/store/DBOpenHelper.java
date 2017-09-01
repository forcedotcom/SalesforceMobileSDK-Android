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
import com.salesforce.androidsdk.analytics.security.Encryptor;
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
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
	private static final String UTF8 = "UTF-8";
	private static String dataDir;
	private String dbName;

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
	 * Returns a list of all prefixes for  user databases.
	 *
	 * @return List of Database names(prefixes).
	 */
	public static synchronized List<String> getUserDatabasePrefixList(Context ctx,
			UserAccount account, String communityId) {
		List<String> result = new ArrayList<>();
		if(account==null) return  result;

		final String accountSuffix = account.getCommunityLevelFilenameSuffix(communityId);
		SmartStoreFileFilter userFileFilter = new SmartStoreFileFilter(accountSuffix);
		final String dbPath = ctx.getApplicationInfo().dataDir + "/databases";
		final File dir = new File(dbPath);
		String[] fileNames = dir.list(userFileFilter);
		if (fileNames != null && fileNames.length > 0) {
			for (String fileName : fileNames) {
				int dbFileIndx = fileName.indexOf(".db");
				if (dbFileIndx >- 1) {
					result.add(fileName.substring(0, fileName.indexOf(accountSuffix)));
				}
			}
		}
		return result;
	}

	/**
	 * Returns a list of all prefixes for  user databases.
	 *
	 * @return List of Database names(prefixes).
	 */
	public static synchronized List<String> getGlobalDatabasePrefixList(Context ctx,
			UserAccount account, String communityId) {
		List<String> result = new ArrayList<>();
		String accountSuffix = null;
		String orgId = null;
		if (account != null) {
			accountSuffix = account.getCommunityLevelFilenameSuffix(communityId);
			orgId = account.getOrgId();
		}
		SmartStoreGlobalFileFilter globalFileFilter = new SmartStoreGlobalFileFilter(accountSuffix,
																					 orgId);
		final String dbPath = ctx.getApplicationInfo().dataDir + "/databases";
		final File dir = new File(dbPath);
		String[] fileNames = dir.list(globalFileFilter);
		if (fileNames != null && fileNames.length > 0) {
			for (String fileName : fileNames) {
				int dbFileIndx = fileName.indexOf(".db");
				if (dbFileIndx > -1)
					result.add(fileName.substring(0, dbFileIndx));
			}
		}
		return result;
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
			List<String> numDbs = null;
			String key = "numGlobalStores";
			String eventName = "globalSmartStoreInit";
			if (account == null) {
				numDbs = getGlobalDatabasePrefixList(ctx, account, communityId);
			} else {
				key = "numUserStores";
				eventName = "userSmartStoreInit";
				numDbs = getUserDatabasePrefixList(ctx, account, communityId);
			}
			int numStores = (numDbs == null) ? 0 : numDbs.size();
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
		SqliteLibraryLoader.loadSqlCipher(context);
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

		if (oldVersion < 3) {
			// DB versions before 3 used soup_names, which has changed to soup_attrs
			SmartStore.updateTableNameAndAddColumns(db, SmartStore.SOUP_NAMES_TABLE,
													SmartStore.SOUP_ATTRS_TABLE, new String[] { SoupSpec.FEATURE_EXTERNAL_STORAGE });
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

			// Delete external blobs directory
			StringBuilder blobsDbPath = new StringBuilder(ctx.getApplicationInfo().dataDir);
			blobsDbPath.append("/databases/").append(fullDBName).append(EXTERNAL_BLOBS_SUFFIX);
			removeAllFiles(new File(blobsDbPath.toString()));
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

		String getDbNamePrefix(){
			return  dbNamePrefix;
		}
	}

	private static class SmartStoreGlobalFileFilter extends SmartStoreFileFilter {

		String orgId;

		public SmartStoreGlobalFileFilter(String dbNamePrefix, String orgId) {
			super(dbNamePrefix);
			this.orgId = orgId;
		}

		@Override
		public boolean accept(File dir, String filename) {
			// if there isn't a prefix   OR
			// ( IS NOT A USER's DB)  AND does not have an orgid (belong to  another user)
			// then it is a global file
			return (this.getDbNamePrefix()==null) || (!super.accept(dir, filename) && !filename.contains(this.orgId));
		}
	}

	/**
	 * Returns the path to external blobs folder for the given soup in this db. If no soup is provided, the db folder is returned.
	 *
	 * @param soupTableName Name of the soup for which to get external blobs folder.
	 *
	 * @return Path to external blobs folder for the given soup. If no soup is provided, the parent directory is returned.
	 */
	public String getExternalSoupBlobsPath(String soupTableName) {
		StringBuilder path = new StringBuilder(dataDir);
		path.append("/databases/").append(dbName).append(EXTERNAL_BLOBS_SUFFIX);
		if (soupTableName != null) {
			path.append(soupTableName).append('/');
		}
		return path.toString();
	}

	/**
	 * Recursively determines size of all files in the given subdirectory of the soup storage.
	 *
	 * @param subDir Subdirectory to determine size of. Use null for top-level directory.
	 *
	 * @return Size of all files in all subdirectories.
	 */
	public int getSizeOfDir(File subDir) {
		int size = 0;
		if (subDir == null) {
			// Top level directory
			subDir = new File(getExternalSoupBlobsPath(null));
		}
		if (subDir.exists()) {
			File[] files = subDir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						size += file.length();
					} else {
						size += getSizeOfDir(file);
					}
				}
			}
		}
		return size;
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
	 * Creates the folder for external blobs for the given soup name.
	 *
	 * @param soupTableName Soup for which to create the external blobs folder
	 *
	 * @return True if directory was created, false otherwise.
	 */
	public boolean createExternalBlobsDirectory(String soupTableName) {
		File blobsDirectory = new File(getExternalSoupBlobsPath(soupTableName));
		return blobsDirectory.mkdirs();
	}

	/**
	 * Removes the folder for external blobs for the given soup name.
	 *
	 * @param soupTableName Soup for which to remove the external blobs folder
	 *
	 * @return True if directory was removed, false otherwise.
	 */
	public boolean removeExternalBlobsDirectory(String soupTableName) {
		if (dataDir != null) {
			return removeAllFiles(new File(getExternalSoupBlobsPath(soupTableName)));
		} else {
			return false;
		}
	}

	/**
	 * Re-encrypts the files on external storage with the new key. If external storage is not enabled for any table in the db, this operation is ignored.
	 *
	 * @param db DB containing external storage (if applicable).
	 * @param oldKey Old key with which to decrypt the existing data.
	 * @param newKey New key with which to encrypt the existing data.
	 */
	public static void reEncryptAllFiles(SQLiteDatabase db, String oldKey, String newKey) {
		StringBuilder path = new StringBuilder(db.getPath()).append(EXTERNAL_BLOBS_SUFFIX);
		File dir = new File(path.toString());
		if (dir.exists()) {
			File[] tables = dir.listFiles();
			if (tables != null) {
				for (File table : tables) {
					File[] blobs = table.listFiles();
					if (blobs != null) {
						for (File blob : blobs) {
							StringBuilder json = new StringBuilder();
							String result = null;
							try {
								BufferedReader br = new BufferedReader(new FileReader(blob));
								String line;
								while ((line = br.readLine()) != null) {
									json.append(line).append('\n');
								}
								br.close();
								result = Encryptor.decrypt(json.toString(), oldKey);
								blob.delete();
								FileOutputStream outputStream = new FileOutputStream(blob, false);
								outputStream.write(Encryptor.encrypt(result, newKey).getBytes());
								outputStream.close();
							} catch (IOException ex) {
                                SmartStoreLogger.e(TAG, "Exception occurred while rekeying external files", ex);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Places the soup blob on file storage. The name and folder are determined by the soup and soup entry id.
	 *
	 * @param soupTableName Name of the soup that the blob belongs to.
	 * @param soupEntryId Entry id for the soup blob.
	 * @param soupElt Blob to store on file storage in JSON format.
	 * @param passcode Key with which to encrypt the data.
	 *
	 * @return True if operation was successful, false otherwise.
	 */
	public boolean saveSoupBlob(String soupTableName, long soupEntryId, JSONObject soupElt, String passcode) {
		return saveSoupBlobFromString(soupTableName, soupEntryId, soupElt.toString(), passcode);
	}

	/**
	 * Places the soup blob on file storage. The name and folder are determined by the soup and soup entry id.
	 *
	 * @param soupTableName Name of the soup that the blob belongs to.
	 * @param soupEntryId Entry id for the soup blob.
	 * @param soupEltStr Blob to store on file storage as a String.
	 * @param passcode Key with which to encrypt the data.
	 *
	 * @return True if operation was successful, false otherwise.
	 */
	public boolean saveSoupBlobFromString(String soupTableName, long soupEntryId, String soupEltStr, String passcode) {
		File file = getSoupBlobFile(soupTableName, soupEntryId);
		try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
			byte[] data = Encryptor.encryptBytes(soupEltStr, passcode);
			if (data != null) {
				outputStream.write(data);
				return true;
			}
		} catch (IOException ex) {
            SmartStoreLogger.e(TAG, "Exception occurred while attempting to write external soup blob", ex);
		}
		return false;
	}

	/**
	 * Retrieves the soup blob for the given soup entry id from file storage.
	 *
	 * @param soupTableName Soup name to which the blob belongs.
	 * @param soupEntryId Entry id for the requested soup blob.
	 * @param passcode Key with which to decrypt the data.
	 *
	 * @return The blob from file storage represented as JSON. Returns null if there was an error.
	 */
	public JSONObject loadSoupBlob(String soupTableName, long soupEntryId, String passcode) {
		JSONObject result = null;
		try {
			final String soupBlobString = loadSoupBlobAsString(soupTableName, soupEntryId, passcode);
			if (soupBlobString != null) {
				result = new JSONObject(soupBlobString);
			}
		} catch (JSONException ex) {
            SmartStoreLogger.e(TAG, "Exception occurred while attempting to read external soup blob", ex);
		}
		return result;
	}

	/**
	 * Retrieves the soup blob for the given soup entry id from file storage.
	 *
	 * @param soupTableName Soup name to which the blob belongs.
	 * @param soupEntryId Entry id for the requested soup blob.
	 * @param passcode Key with which to decrypt the data.
	 *
	 * @return The blob from file storage represented as String. Returns null if there was an error.
	 */
	public String loadSoupBlobAsString(String soupTableName, long soupEntryId, String passcode) {
		File file = getSoupBlobFile(soupTableName, soupEntryId);
		try (FileInputStream f = new FileInputStream(file)) {
			DataInputStream data = new DataInputStream(f);
			byte[] bytes = new byte[(int) file.length()];
			data.readFully(bytes);
			return Encryptor.decrypt(bytes, passcode);
		} catch (IOException ex) {
            SmartStoreLogger.e(TAG, "Exception occurred while attempting to read external soup blob", ex);
		}
		return null;
	}

	/**
	 * Removes the blobs represented by the given list of soup entry ids from external storage.
	 *
	 * @param soupTableName Soup name to which the blobs belong.
	 * @param soupEntryIds List of soup entry ids to delete.
	 *
	 * @return True if all soup entry ids were deleted, false if blob could not be found or had an error.
	 */
	public boolean removeSoupBlob(String soupTableName, Long[] soupEntryIds) {
		File file;
		boolean success = true;
		for (long soupEntryId : soupEntryIds) {
			file = getSoupBlobFile(soupTableName, soupEntryId);
			success &= file.delete();
		}
		return success;
	}

	/**
	 * Returns a file that the soup data is stored in for the given soup name and entry id.
	 *
	 * @param soupTableName Soup name to which the blob belongs.
	 * @param soupEntryId Entry id for the requested soup blob.
	 *
	 * @return A File representing the soup blob in external storage.
	 */
	public File getSoupBlobFile(String soupTableName, long soupEntryId) {
		return new File(getExternalSoupBlobsPath(soupTableName), SOUP_ELEMENT_PREFIX + soupEntryId);
	}
}

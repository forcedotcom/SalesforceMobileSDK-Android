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
package com.salesforce.androidsdk.store;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.test.InstrumentationTestCase;

import com.salesforce.androidsdk.smartstore.store.DBHelper;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;
import com.salesforce.androidsdk.smartstore.store.SmartStore;

/**
 * Abstract super class for smart store tests
 *
 */
public abstract class SmartStoreTestCase extends InstrumentationTestCase {

	protected Context targetContext;
	protected SQLiteOpenHelper dbOpenHelper;
	protected SmartStore store;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		targetContext = getInstrumentation().getTargetContext();
		dbOpenHelper = DBOpenHelper.getOpenHelper(targetContext, null);
		DBHelper.getInstance(dbOpenHelper.getWritableDatabase(getPasscode())).reset(targetContext, null);
		store = new SmartStore(dbOpenHelper, getPasscode());
		store.dropAllSoups();
	}

	protected abstract String getPasscode();

	@Override
	protected void tearDown() throws Exception {
		dbOpenHelper.close();
		store.dropAllSoups();
		DBOpenHelper.deleteDatabase(targetContext, null);
		super.tearDown();
	}

	/**
	 * Helper method to check that a table exists in the database
	 * @param tableName
	 * @return
	 */
	protected boolean hasTable(String tableName) {
		Cursor c = null;
		final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());
		try {
			c = DBHelper.getInstance(db).query(db, "sqlite_master", null, null, null, "type = ? and name = ?", "table", tableName);
			return c.getCount() == 1;
		}
		finally {
			safeClose(c);
		}
	}
	
	/**
	 * Close cursor if not null
	 * @param c
	 */
	protected void safeClose(Cursor c) {
		if (c != null) {
			c.close();
		}
	}

	/**
	 * @param soupName
	 * @return table name for soup
	 */
	protected String getSoupTableName(String soupName) {
		final SQLiteDatabase db = dbOpenHelper.getWritableDatabase(getPasscode());
		return DBHelper.getInstance(db).getSoupTableName(db, soupName);
	}

	/**
	 * @param soupElt
	 * @return _soupEntryId field value
	 * @throws JSONException
	 */
	public static long idOf(JSONObject soupElt) throws JSONException {
		return soupElt.getLong(SmartStore.SOUP_ENTRY_ID);
	}	
}
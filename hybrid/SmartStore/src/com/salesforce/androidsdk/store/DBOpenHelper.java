/*
 * Copyright (c) 2011, salesforce.com, inc.
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

import info.guardianproject.database.sqlcipher.SQLiteDatabase;
import info.guardianproject.database.sqlcipher.SQLiteOpenHelper;
import android.content.Context;
import android.util.Log;


/**
 * Helper class to manage SmartStore's database creation and version management.
 */
public class DBOpenHelper extends SQLiteOpenHelper {
	public static final String DB_NAME = "smartstore.db";
	public static final int DB_VERSION = 1;

	private static DBOpenHelper openHelper;
	
	public static synchronized DBOpenHelper getOpenHelper(Context ctx) {
		if (openHelper == null) {
			openHelper = new DBOpenHelper(ctx);
		}
		return openHelper;
	}
	
	private DBOpenHelper(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
		SQLiteDatabase.loadLibs(context);
		Log.i("DBOpenHelper:DBOpenHelper", DB_NAME + "/" + DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.i("DBOpenHelper:onCreate", DB_NAME + "/" + DB_VERSION);
		SmartStore.createMetaTables(db);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.i("DBOpenHelper:onUpgrade", DB_NAME + "/" + DB_VERSION);
		// do the needful if DB_VERSION has changed 
	}

	public static void deleteDatabase(Context ctx) {
		Log.i("DBOpenHelper:deleteDatabase", DB_NAME + "/" + DB_VERSION);
		if (openHelper != null) {
			openHelper.close();
			openHelper =  null;
		}
		ctx.deleteDatabase(DB_NAME);
	}
}

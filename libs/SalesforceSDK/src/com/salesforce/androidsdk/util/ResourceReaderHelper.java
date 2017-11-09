/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.util;

import android.content.Context;

import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Scanner;

/**
 * Helper class for reading resource files
 *
 */

public class ResourceReaderHelper {

    private static final String TAG = "ResourceReaderHelper";

    /**
     * Reads the content of a resource file
     *
     * @param ctx        Context.
     * @param resourceId The id of the resource to read.
     * @return
     */
    public static String readResourceFile(Context ctx, int resourceId) {
        InputStream resourceReader = ctx.getResources().openRawResource(resourceId);
        Writer writer = new StringWriter();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(resourceReader, "UTF-8"));
            String line = reader.readLine();
            while (line != null) {
                writer.write(line);
                line = reader.readLine();
            }
        } catch (Exception e) {
            SalesforceSDKLogger.e(TAG, "Unhandled exception reading resource", e);
        } finally {
            try {
                resourceReader.close();
            } catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Unhandled exception closing reader", e);
            }
        }

        return writer.toString();
    }

    /**
     * Reads the contents of an asset file at the specified path.
     *
     * @param ctx            Context.
     * @param assetsFilePath The path to the file, relative to the assets/ folder of the context.
     * @return String content of the file.
     */
    public static String readAssetFile(Context ctx, String assetsFilePath) {
        Scanner scanner = null;
        try {
            scanner = new Scanner(ctx.getAssets().open(assetsFilePath));

            // Good trick to get a string from a stream (http://weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html).
            return scanner.useDelimiter("\\A").next();
        } catch (IOException e) {
            SalesforceSDKLogger.e(TAG, "Unhandled exception reading resource", e);
            return null;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }
}

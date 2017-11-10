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
import android.content.res.Resources;

import com.salesforce.androidsdk.config.BootConfig;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
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
        try {
            return readStream(ctx.getResources().openRawResource(resourceId));
        }
        catch (Resources.NotFoundException e) {
            SalesforceSDKLogger.d(TAG, "Resource not found: " + resourceId);
            return null;
        }
        catch (IOException e) {
            SalesforceSDKLogger.e(TAG, "Unhandled exception reading resource " + resourceId, e);
            return null;
        }
    }

    /**
     * Reads the contents of an asset file at the specified path.
     *
     * @param ctx            Context.
     * @param assetFilePath The path to the file, relative to the assets/ folder of the context.
     * @return String content of the file.
     */
    public static String readAssetFile(Context ctx, String assetFilePath) {
        try {
            return readStream(ctx.getAssets().open(assetFilePath));
        }
        catch (FileNotFoundException e) {
            SalesforceSDKLogger.d(TAG, "Asset not found: " + assetFilePath);
            return null;
        }
        catch (IOException e) {
            SalesforceSDKLogger.e(TAG, "Unhandled exception reading asset " + assetFilePath, e);
            return null;
        }
    }

    protected static String readStream(InputStream inputStream) throws IOException {
        Writer writer = new StringWriter();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            String line = reader.readLine();
            while (line != null) {
                writer.write(line);
                line = reader.readLine();
            }
        }
        finally {
            try {
                inputStream.close();
            } catch (Exception e) {
                SalesforceSDKLogger.e(TAG, "Unhandled exception closing stream", e);
            }
        }

        return writer.toString();
    }


}

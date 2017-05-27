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
package com.salesforce.androidsdk.analytics.logger;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.squareup.tape.ObjectQueue;
import com.squareup.tape.QueueFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A simple file logger that works with SalesforceLogger to write log entries to a file.
 *
 * @author bhariharan
 */
public class FileLogger {

    private static final String LOG_SUFFIX = "_log";
    private static final String UTF8 = "UTF-8";
    private static final String TAG = "FileLogger";
    private static final int MAX_SIZE = 1000;

    private QueueFile file;
    private ObjectQueue<String> queue;
    private int maxSize;

    /**
     * Parameterized constructor.
     *
     * @param context Context.
     * @param componentName Component name.
     * @throws IOException If file initialization was unsuccessful.
     */
    public FileLogger(Context context, String componentName) throws IOException {
        final File filename = new File(context.getFilesDir(), componentName + LOG_SUFFIX);
        file = new QueueFile(filename);
        maxSize = MAX_SIZE;
    }

    /**
     * Flushes the log file and resets it to its original state.
     *
     * @throws IOException If the operation failed.
     */
    public void flushLog() throws IOException {
        try {
            file.clear();
        } catch (IOException e) {
            Log.e(TAG, "Failed to flush log file", e);
        }
    }

    /**
     * Returns the number of log lines stored in this file.
     *
     * @return Number of stored log lines.
     */
    public int getSize() {
        return file.size();
    }

    /**
     * Returns the maximum number of log lines that can be stored in this file.
     *
     * @return Maximum number of log lines.
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Sets the maximum number of log lines that can be stored in this file.
     *
     * @param size Maximum number of log lines.
     */
    public synchronized void setMaxSize(int size) {
        maxSize = size;
    }

    /**
     * Writes a log line to the file.
     *
     * @param logLine Log line.
     */
    public void addLogLine(String logLine) {
        if (TextUtils.isEmpty(logLine)) {
            return;
        }
        try {
            while (getSize() >= maxSize) {
                file.remove();
            }
            file.add(logLine.getBytes(UTF8));
        } catch (Exception e) {
            Log.e(TAG, "Failed to write log line", e);
        }
    }

    /**
     * Writes a list of log lines to the file.
     *
     * @param logLines Log lines.
     */
    public void addLogLines(List<String> logLines) {
        if (logLines == null || logLines.size() == 0) {
            return;
        }
        for (final String logLine : logLines) {
            addLogLine(logLine);
        }
    }

    /**
     * Writes an array of log lines to the file.
     *
     * @param logLines Log lines.
     */
    public void addLogLines(String[] logLines) {
        if (logLines == null || logLines.length == 0) {
            return;
        }
        for (final String logLine : logLines) {
            addLogLine(logLine);
        }
    }
}

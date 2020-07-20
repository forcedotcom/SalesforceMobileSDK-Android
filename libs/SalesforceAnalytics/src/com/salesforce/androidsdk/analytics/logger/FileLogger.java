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
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.squareup.tape.QueueFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple file logger that works with SalesforceLogger to write log entries to a file.
 *
 * @author bhariharan
 */
public class FileLogger {

    private static final String LOG_SUFFIX = "_log";
    private static final String FILE_LOGGER_PREFS = "sf_file_logger_prefs";
    private static final String TAG = "FileLogger";
    private static final int MAX_SIZE = 10000;

    private Context context;
    private String componentName;
    private QueueFile file;
    private int maxSize;

    /**
     * Parameterized constructor.
     *
     * @param context Context.
     * @param componentName Component name.
     * @throws IOException If file initialization was unsuccessful.
     */
    public FileLogger(Context context, String componentName) throws IOException {
        this.context = context;
        this.componentName = componentName;
        readFileLoggerPrefs();
        final File filename = new File(context.getFilesDir(), componentName + LOG_SUFFIX);
        file = new QueueFile(filename);
    }

    /**
     * Flushes the log file and resets it to its original state.
     */
    public void flushLog() {
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
    public void setMaxSize(int size) {
        if (size < 0) {
            size = 0;
        }
        storeFileLoggerPrefs(size);
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
            if (maxSize > 0) {
                file.add(logLine.getBytes(StandardCharsets.UTF_8));
            }
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
        String[] logLinesArray = new String[logLines.size()];
        logLines.toArray(logLinesArray);
        addLogLines(logLinesArray);
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

    /**
     * Returns a single log line in FIFO.
     *
     * @return Log line.
     */
    public String readLogLine() {
        String logLine = null;
        try {
            byte[] logLineBytes = file.peek();
            if (logLineBytes != null && logLineBytes.length > 0) {
                logLine = new String(logLineBytes, StandardCharsets.US_ASCII);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read log line", e);
        }
        return logLine;
    }

    /**
     * Returns a list of log lines in FIFO and removes them as they are being read.
     *
     * @param numLines Number of lines to be read.
     * @return List of log lines.
     */
    public List<String> readAndRemoveLogLinesAsList(int numLines) {
        List<String> logLines = new ArrayList<>();
        int linesToRead = Math.min(getSize(), numLines);
        for (int i = 0; i < linesToRead; i++) {
            final String logLine = readLogLine();
            removeLogLine();
            if (logLine != null) {
                logLines.add(logLine);
            }
        }
        if (logLines.size() == 0) {
            logLines = null;
        }
        return logLines;
    }

    /**
     * Returns an array of log lines in FIFO and removes them as they are being read.
     *
     * @param numLines Number of lines to be read.
     * @return Array of log lines.
     */
    public String[] readAndRemoveLogLinesAsArray(int numLines) {
        String[] logLinesArray = null;
        final List<String> logLines = readAndRemoveLogLinesAsList(numLines);
        if (logLines != null && logLines.size() > 0) {
            logLinesArray = new String[logLines.size()];
            logLines.toArray(logLinesArray);
        }
        return logLinesArray;
    }

    /**
     * Returns all log lines stored in FIFO and removes them as they are being read.
     *
     * @return List of all log lines stored in this file.
     */
    public List<String> readAndRemoveFileAsList() {
        return readAndRemoveLogLinesAsList(getSize());
    }

    /**
     * Returns all log lines stored in FIFO and removes them as they are being read.
     *
     * @return Array of all log lines stored in this file.
     */
    public String[] readAndRemoveFileAsArray() {
        return readAndRemoveLogLinesAsArray(getSize());
    }

    /**
     * Removes the first log line in the file.
     */
    public void removeLogLine() {
        try {
            file.remove();
        } catch (IOException e) {
            Log.e(TAG, "Failed to remove log line", e);
        }
    }

    /**
     * Removes the specified number of log lines from the file in FIFO.
     *
     * @param numLines Number of log lines.
     */
    public void removeLogLines(int numLines) {
        int linesToRemove = Math.min(getSize(), numLines);
        for (int i = 0; i < linesToRemove; i++) {
            removeLogLine();
        }
    }

    private synchronized void storeFileLoggerPrefs(int maxSize) {
        final SharedPreferences sp = context.getSharedPreferences(FILE_LOGGER_PREFS, Context.MODE_PRIVATE);
        final SharedPreferences.Editor e = sp.edit();
        e.putInt(componentName, maxSize);
        e.commit();
        this.maxSize = maxSize;
    }

    private void readFileLoggerPrefs() {
        final SharedPreferences sp = context.getSharedPreferences(FILE_LOGGER_PREFS, Context.MODE_PRIVATE);
        if (!sp.contains(componentName)) {
            storeFileLoggerPrefs(MAX_SIZE);
        }
        maxSize = sp.getInt(componentName, MAX_SIZE);
    }

    /**
     * Resets the stored file logger prefs. Should be used ONLY by tests.
     *
     * @param context Context.
     */
    public synchronized static void resetFileLoggerPrefs(Context context) {
        final SharedPreferences sp = context.getSharedPreferences(FILE_LOGGER_PREFS, Context.MODE_PRIVATE);
        final SharedPreferences.Editor e = sp.edit();
        e.clear();
        e.commit();
    }
}

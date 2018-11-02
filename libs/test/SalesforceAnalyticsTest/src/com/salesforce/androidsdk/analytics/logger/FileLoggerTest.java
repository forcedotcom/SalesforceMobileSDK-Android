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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for FileLogger.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class FileLoggerTest {

    private static final String COMPONENT_NAME = "FileLoggerTest";
    private static final String TEST_LOG_LINE_1 = "This is test log line 1!";
    private static final String TEST_LOG_LINE_2 = "This is test log line 2!";
    private static final String TEST_LOG_LINE_3 = "This is test log line 3!";
    private static final String TEST_LOG_LINE_4 = "This is test log line 4!";
    private static final int DEFAULT_MAX_SIZE = 10000;

    private Context targetContext;
    private FileLogger fileLogger;

    @Before
    public void setUp() throws Exception {
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        FileLogger.resetFileLoggerPrefs(targetContext);
        fileLogger = new FileLogger(targetContext, COMPONENT_NAME);
        fileLogger.flushLog();
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should be empty", 0, size);
    }

    @After
    public void tearDown() throws Exception {
        FileLogger.resetFileLoggerPrefs(targetContext);
        fileLogger.flushLog();
    }

    /**
     * Test for setting maximum number of log lines.
     */
    @Test
    public void testSetMaxSize() {
        int maxSize = fileLogger.getMaxSize();
        Assert.assertEquals("Max size didn't match expected max size", DEFAULT_MAX_SIZE, maxSize);
        int newMaxSize = 2000;
        fileLogger.setMaxSize(newMaxSize);
        maxSize = fileLogger.getMaxSize();
        Assert.assertEquals("Max size didn't match expected max size", newMaxSize, maxSize);
    }

    /**
     * Test for flushing the log file.
     *
     * @throws Exception
     */
    @Test
    public void testFlushLogFile() throws Exception {
        fileLogger.addLogLine(TEST_LOG_LINE_1);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 1 entry", 1, size);
        fileLogger.flushLog();
        size = fileLogger.getSize();
        Assert.assertEquals("Log file should be empty", 0, size);
    }

    /**
     * Test for adding a log line.
     */
    @Test
    public void testAddLogLine() {
        fileLogger.addLogLine(TEST_LOG_LINE_1);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 1 entry", 1, size);
    }

    /**
     * Test for adding a list of log lines.
     */
    @Test
    public void testAddListOfLogLines() {
        final List<String> logLines = new ArrayList<>();
        logLines.add(TEST_LOG_LINE_1);
        logLines.add(TEST_LOG_LINE_2);
        logLines.add(TEST_LOG_LINE_3);
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
    }

    /**
     * Test for adding an array of log lines.
     */
    @Test
    public void testAddArrayOfLogLines() {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
    }

    /**
     * Test for removing a log line in FIFO.
     */
    @Test
    public void testRemoveLogLine() {
        fileLogger.addLogLine(TEST_LOG_LINE_1);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 1 entry", 1, size);
        fileLogger.removeLogLine();
        size = fileLogger.getSize();
        Assert.assertEquals("Log file should be empty", 0, size);
    }

    /**
     * Test for removing a specified number of log lines in FIFO.
     */
    @Test
    public void testRemoveNumLogLines() {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
        fileLogger.removeLogLines(2);
        size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 1 entry", 1, size);
    }

    /**
     * Test for the order of removal of log lines.
     */
    @Test
    public void testOrderOfRemoval() {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
        fileLogger.removeLogLines(2);
        size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 1 entry", 1, size);
        final String logLineRead = fileLogger.readLogLine();
        Assert.assertEquals("Incorrect log lines were removed", TEST_LOG_LINE_3, logLineRead);
    }

    /**
     * Test for reading a log line in FIFO.
     */
    @Test
    public void testReadLogLine() {
        fileLogger.addLogLine(TEST_LOG_LINE_1);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 1 entry", 1, size);
        final String logLineRead = fileLogger.readLogLine();
        Assert.assertEquals("Incorrect log line read", TEST_LOG_LINE_1, logLineRead);
    }

    /**
     * Test for reading and removing a specified number of log lines as a list in FIFO.
     */
    @Test
    public void testReadAndRemoveLogLinesAsList() {
        final List<String> logLines = new ArrayList<>();
        logLines.add(TEST_LOG_LINE_1);
        logLines.add(TEST_LOG_LINE_2);
        logLines.add(TEST_LOG_LINE_3);
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
        final List<String> logLinesRead = fileLogger.readAndRemoveLogLinesAsList(3);
        Assert.assertEquals("Number of log lines read should be 3", 3, logLinesRead.size());
        Assert.assertEquals("Log lines read are different from expected log lines", logLines, logLinesRead);
    }

    /**
     * Test for reading and removing a specified number of log lines as an array in FIFO.
     */
    @Test
    public void testReadAndRemoveLogLinesAsArray() {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
        final String[] logLinesRead = fileLogger.readAndRemoveLogLinesAsArray(3);
        Assert.assertEquals("Number of log lines read should be 3", 3, logLinesRead.length);
        Assert.assertEquals("Log line read is different from expected log line", TEST_LOG_LINE_1, logLinesRead[0]);
        Assert.assertEquals("Log line read is different from expected log line", TEST_LOG_LINE_2, logLinesRead[1]);
        Assert.assertEquals("Log line read is different from expected log line", TEST_LOG_LINE_3, logLinesRead[2]);
    }

    /**
     * Test for reading and removing all log lines as a list in FIFO.
     */
    @Test
    public void testReadAndRemoveFileAsList() {
        final List<String> logLines = new ArrayList<>();
        logLines.add(TEST_LOG_LINE_1);
        logLines.add(TEST_LOG_LINE_2);
        logLines.add(TEST_LOG_LINE_3);
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
        final List<String> logLinesRead = fileLogger.readAndRemoveFileAsList();
        Assert.assertEquals("Number of log lines read should be 3", 3, logLinesRead.size());
        Assert.assertEquals("Log lines read are different from expected log lines", logLines, logLinesRead);
        size = fileLogger.getSize();
        Assert.assertEquals("Log file should have no entries", 0, size);
    }

    /**
     * Test for reading and removing all log lines as an array in FIFO.
     */
    @Test
    public void testReadAndRemoveFileAsArray() {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
        final String[] logLinesRead = fileLogger.readAndRemoveFileAsArray();
        Assert.assertEquals("Number of log lines read should be 3", 3, logLinesRead.length);
        Assert.assertEquals("Log line read is different from expected log line", TEST_LOG_LINE_1, logLinesRead[0]);
        Assert.assertEquals("Log line read is different from expected log line", TEST_LOG_LINE_2, logLinesRead[1]);
        Assert.assertEquals("Log line read is different from expected log line", TEST_LOG_LINE_3, logLinesRead[2]);
        size = fileLogger.getSize();
        Assert.assertEquals("Log file should have no entries", 0, size);
    }

    /**
     * Test for the order of reading of log lines.
     */
    @Test
    public void testOrderOfReading() {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
        final String logLineRead = fileLogger.readLogLine();
        Assert.assertEquals("Incorrect log line was read", TEST_LOG_LINE_1, logLineRead);
    }

    /**
     * Test for writing a log line after max size has been reached.
     */
    @Test
    public void testWriteAfterMaxSizeReached() {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
        fileLogger.setMaxSize(1);
        int maxSize = fileLogger.getMaxSize();
        Assert.assertEquals("Max size should be 1", 1, maxSize);
        fileLogger.addLogLine(TEST_LOG_LINE_4);
        size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 1 entry", 1, size);
        final String logLineRead = fileLogger.readLogLine();
        Assert.assertEquals("Incorrect log line read", TEST_LOG_LINE_4, logLineRead);
    }

    /**
     * Test for writing a log line if max size has been set to 0.
     */
    @Test
    public void testWriteForMaxSizeZero() {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        Assert.assertEquals("Log file should have 3 entries", 3, size);
        fileLogger.setMaxSize(0);
        int maxSize = fileLogger.getMaxSize();
        Assert.assertEquals("Max size should be 0", 0, maxSize);
        fileLogger.addLogLine(TEST_LOG_LINE_4);
        size = fileLogger.getSize();
        Assert.assertEquals("Log file should have no entries", 0, size);
    }
}

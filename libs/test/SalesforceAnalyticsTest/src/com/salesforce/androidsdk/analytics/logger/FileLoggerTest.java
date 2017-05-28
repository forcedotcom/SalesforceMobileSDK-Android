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
import android.test.InstrumentationTestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for FileLogger.
 *
 * @author bhariharan
 */
public class FileLoggerTest extends InstrumentationTestCase {

    private static final String COMPONENT_NAME = "FileLoggerTest";
    private static final String TEST_LOG_LINE_1 = "This is test log line 1!";
    private static final String TEST_LOG_LINE_2 = "This is test log line 2!";
    private static final String TEST_LOG_LINE_3 = "This is test log line 3!";
    private static final int DEFAULT_MAX_SIZE = 1000;

    private Context targetContext;
    private FileLogger fileLogger;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        targetContext = getInstrumentation().getTargetContext();
        fileLogger = new FileLogger(targetContext, COMPONENT_NAME);
        fileLogger.flushLog();
        int size = fileLogger.getSize();
        assertEquals("Log file should be empty", 0, size);
    }

    @Override
    public void tearDown() throws Exception {
        fileLogger.flushLog();
        super.tearDown();
    }

    /**
     * Test for setting maximum number of log lines.
     *
     * @throws Exception
     */
    public void testSetMaxSize() throws Exception {
        int maxSize = fileLogger.getMaxSize();
        assertEquals("Max size didn't match expected max size", DEFAULT_MAX_SIZE, maxSize);
        int newMaxSize = 2000;
        fileLogger.setMaxSize(newMaxSize);
        maxSize = fileLogger.getMaxSize();
        assertEquals("Max size didn't match expected max size", newMaxSize, maxSize);
    }

    /**
     * Test for flushing the log file.
     *
     * @throws Exception
     */
    public void testFlushLogFile() throws Exception {
        fileLogger.addLogLine(TEST_LOG_LINE_1);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 1 entry", 1, size);
        fileLogger.flushLog();
        size = fileLogger.getSize();
        assertEquals("Log file should be empty", 0, size);
    }

    /**
     * Test for adding a log line.
     *
     * @throws Exception
     */
    public void testAddLogLine() throws Exception {
        fileLogger.addLogLine(TEST_LOG_LINE_1);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 1 entry", 1, size);
    }

    /**
     * Test for adding a list of log lines.
     *
     * @throws Exception
     */
    public void testAddListOfLogLines() throws Exception {
        final List<String> logLines = new ArrayList<>();
        logLines.add(TEST_LOG_LINE_1);
        logLines.add(TEST_LOG_LINE_2);
        logLines.add(TEST_LOG_LINE_3);
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 3 entries", 3, size);
    }

    /**
     * Test for adding an array of log lines.
     *
     * @throws Exception
     */
    public void testAddArrayOfLogLines() throws Exception {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 3 entries", 3, size);
    }

    /**
     * Test for removing a log line in FIFO.
     *
     * @throws Exception
     */
    public void testRemoveLogLine() throws Exception {
        fileLogger.addLogLine(TEST_LOG_LINE_1);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 1 entry", 1, size);
        fileLogger.removeLogLine();
        size = fileLogger.getSize();
        assertEquals("Log file should be empty", 0, size);
    }

    /**
     * Test for removing a specified number of log lines in FIFO.
     *
     * @throws Exception
     */
    public void testRemoveNumLogLines() throws Exception {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 3 entries", 3, size);
        fileLogger.removeLogLines(2);
        size = fileLogger.getSize();
        assertEquals("Log file should have 1 entry", 1, size);
    }

    /**
     * Test for the order of removal of log lines.
     *
     * @throws Exception
     */
    public void testOrderOfRemoval() throws Exception {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 3 entries", 3, size);
        fileLogger.removeLogLines(2);
        size = fileLogger.getSize();
        assertEquals("Log file should have 1 entry", 1, size);
        final String logLineRead = fileLogger.readLogLine();
        assertEquals("Incorrect log lines were removed", TEST_LOG_LINE_3, logLineRead);
    }

    /**
     * Test for reading a log line in FIFO.
     *
     * @throws Exception
     */
    public void testReadLogLine() throws Exception {
        fileLogger.addLogLine(TEST_LOG_LINE_1);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 1 entry", 1, size);
        final String logLineRead = fileLogger.readLogLine();
        assertEquals("Incorrect log line read", TEST_LOG_LINE_1, logLineRead);
    }

    /**
     * Test for reading and removing a specified number of log lines as a list in FIFO.
     *
     * @throws Exception
     */
    public void testReadAndRemoveLogLinesAsList() throws Exception {
        final List<String> logLines = new ArrayList<>();
        logLines.add(TEST_LOG_LINE_1);
        logLines.add(TEST_LOG_LINE_2);
        logLines.add(TEST_LOG_LINE_3);
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 3 entries", 3, size);
        final List<String> logLinesRead = fileLogger.readAndRemoveLogLinesAsList(3);
        assertEquals("Number of log lines read should be 3", 3, logLinesRead.size());
        assertEquals("Log lines read are different from expected log lines", logLines, logLinesRead);
    }

    /**
     * Test for reading and removing a specified number of log lines as an array in FIFO.
     *
     * @throws Exception
     */
    public void testReadAndRemoveLogLinesAsArray() throws Exception {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 3 entries", 3, size);
        final String[] logLinesRead = fileLogger.readAndRemoveLogLinesAsArray(3);
        assertEquals("Number of log lines read should be 3", 3, logLinesRead.length);
        assertEquals("Log line read is different from expected log line", TEST_LOG_LINE_1, logLinesRead[0]);
        assertEquals("Log line read is different from expected log line", TEST_LOG_LINE_2, logLinesRead[1]);
        assertEquals("Log line read is different from expected log line", TEST_LOG_LINE_3, logLinesRead[2]);
    }

    /**
     * Test for the order of reading of log lines.
     *
     * @throws Exception
     */
    public void testOrderOfReading() throws Exception {
        final String[] logLines = new String[] {TEST_LOG_LINE_1, TEST_LOG_LINE_2, TEST_LOG_LINE_3};
        fileLogger.addLogLines(logLines);
        int size = fileLogger.getSize();
        assertEquals("Log file should have 3 entries", 3, size);
        final String logLineRead = fileLogger.readLogLine();
        assertEquals("Incorrect log line was read", TEST_LOG_LINE_1, logLineRead);
    }
}

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
package com.salesforce.androidsdk.reactnative.util;

import com.salesforce.androidsdk.analytics.logger.SalesforceLogger;
import com.salesforce.androidsdk.app.SalesforceSDKManager;

/**
 * A simple logger util class for the SalesforceReact library. This class simply acts
 * as a wrapper around SalesforceLogger specific to the SalesforceReact library.
 *
 * @author bhariharan
 */
public class SalesforceReactLogger {

    private static final String COMPONENT_NAME = "SalesforceReact";

    /**
     * Logs an error log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void e(String tag, String message) {
        getLogger().e(tag, message);
    }

    /**
     * Logs an error log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void e(String tag, String message, Throwable e) {
        getLogger().e(tag, message, e);
    }

    /**
     * Logs a warning log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void w(String tag, String message) {
        getLogger().w(tag, message);
    }

    /**
     * Logs a warning log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void w(String tag, String message, Throwable e) {
        getLogger().w(tag, message, e);
    }

    /**
     * Logs an info log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void i(String tag, String message) {
        getLogger().i(tag, message);
    }

    /**
     * Logs an info log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void i(String tag, String message, Throwable e) {
        getLogger().i(tag, message, e);
    }

    /**
     * Logs a debug log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void d(String tag, String message) {
        getLogger().d(tag, message);
    }

    /**
     * Logs a debug log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void d(String tag, String message, Throwable e) {
        getLogger().d(tag, message, e);
    }

    /**
     * Logs a verbose log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void v(String tag, String message) {
        getLogger().v(tag, message);
    }

    /**
     * Logs a verbose log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void v(String tag, String message, Throwable e) {
        getLogger().v(tag, message, e);
    }

    /**
     * Sets the log level to be used.
     *
     * @param level Log level.
     */
    public static void setLogLevel(SalesforceLogger.Level level) {
        getLogger().setLogLevel(level);
    }

    private static SalesforceLogger getLogger() {
        return SalesforceLogger.getLogger(COMPONENT_NAME,
                SalesforceSDKManager.getInstance().getAppContext());
    }
}

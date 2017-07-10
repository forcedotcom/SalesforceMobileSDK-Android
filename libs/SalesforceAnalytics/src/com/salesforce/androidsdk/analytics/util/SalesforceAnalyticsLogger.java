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
package com.salesforce.androidsdk.analytics.util;

import android.content.Context;
import android.util.Log;

import com.salesforce.androidsdk.analytics.logger.SalesforceLogger;

/**
 * A simple logger util class for the SalesforceAnalytics library. This class simply acts
 * as a wrapper around SalesforceLogger specific to the SalesforceAnalytics library.
 *
 * @author bhariharan
 */
public class SalesforceAnalyticsLogger {

    private static final String COMPONENT_NAME = "SalesforceAnalytics";

    /**
     * Logs an error log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void e(Context context, String tag, String message) {
        log(context, SalesforceLogger.Level.ERROR, tag, message);
    }

    /**
     * Logs an error log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void e(Context context, String tag, String message, Throwable e) {
        log(context, SalesforceLogger.Level.ERROR, tag, message, e);
    }

    /**
     * Logs a warning log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void w(Context context, String tag, String message) {
        log(context, SalesforceLogger.Level.WARN, tag, message);
    }

    /**
     * Logs a warning log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void w(Context context, String tag, String message, Throwable e) {
        log(context, SalesforceLogger.Level.WARN, tag, message, e);
    }

    /**
     * Logs an info log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void i(Context context, String tag, String message) {
        log(context, SalesforceLogger.Level.INFO, tag, message);
    }

    /**
     * Logs an info log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void i(Context context, String tag, String message, Throwable e) {
        log(context, SalesforceLogger.Level.INFO, tag, message, e);
    }

    /**
     * Logs a debug log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void d(Context context, String tag, String message) {
        log(context, SalesforceLogger.Level.DEBUG, tag, message);
    }

    /**
     * Logs a debug log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void d(Context context, String tag, String message, Throwable e) {
        log(context, SalesforceLogger.Level.DEBUG, tag, message, e);
    }

    /**
     * Logs a verbose log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     */
    public static void v(Context context, String tag, String message) {
        log(context, SalesforceLogger.Level.VERBOSE, tag, message);
    }

    /**
     * Logs a verbose log line.
     *
     * @param context Context.
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    public static void v(Context context, String tag, String message, Throwable e) {
        log(context, SalesforceLogger.Level.VERBOSE, tag, message, e);
    }

    private static void log(Context context, SalesforceLogger.Level level, String tag, String message) {
        if (context != null) {
            SalesforceLogger.getLogger(COMPONENT_NAME, context).log(level, tag, message);
        } else {
            logToLogcat(level, tag, message);
        }
    }

    private static void log(Context context, SalesforceLogger.Level level, String tag, String message, Throwable e) {
        if (context != null) {
            SalesforceLogger.getLogger(COMPONENT_NAME, context).log(level, tag, message, e);
        } else {
            logToLogcat(level, tag, message, e);
        }
    }

    private static void logToLogcat(SalesforceLogger.Level level, String tag, String message) {
        switch (level) {
            case OFF:
                break;
            case ERROR:
                Log.e(tag, message);
                break;
            case WARN:
                Log.w(tag, message);
                break;
            case INFO:
                Log.i(tag, message);
                break;
            case DEBUG:
                Log.d(tag, message);
                break;
            case VERBOSE:
                Log.v(tag, message);
                break;
            default:
                Log.d(tag, message);
        }
    }

    private static void logToLogcat(SalesforceLogger.Level level, String tag, String message, Throwable e) {
        switch (level) {
            case OFF:
                break;
            case ERROR:
                Log.e(tag, message, e);
                break;
            case WARN:
                Log.w(tag, message, e);
                break;
            case INFO:
                Log.i(tag, message, e);
                break;
            case DEBUG:
                Log.d(tag, message, e);
                break;
            case VERBOSE:
                Log.v(tag, message, e);
                break;
            default:
                Log.d(tag, message, e);
        }
    }
}

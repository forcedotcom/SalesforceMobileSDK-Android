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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple logger that allows components to log statements of different log levels. This class
 * also provides the ability to break down logs at the component level and set different log
 * levels for different components. The available options for log output are console and file.
 *
 * @author bhariharan
 */
public class SalesforceLogger {

    private static Map<String, SalesforceLogger> LOGGERS;

    /**
     * An enumeration of log levels.
     */
    public enum Level {
        OFF,
        ERROR,
        WARN,
        INFO,
        DEBUG,
        VERBOSE
    }

    private String componentName;
    private Context context;
    private Level logLevel;

    /**
     * Returns a logger instance associated with a named component.
     *
     * @param componentName Component name.
     * @param context Context.
     * @return Logger instance.
     */
    public synchronized static SalesforceLogger getLogger(String componentName, Context context) {
        if (LOGGERS == null) {
            LOGGERS = new HashMap<>();
        }
        if (!LOGGERS.containsKey(componentName)) {
            final SalesforceLogger logger = new SalesforceLogger(componentName, context);
            LOGGERS.put(componentName, logger);
        }
        return LOGGERS.get(componentName);
    }

    private SalesforceLogger(String componentName, Context context) {
        this.componentName = componentName;
        this.context = context;
        if (isDebugMode()) {
            logLevel = Level.DEBUG;
        } else {
            logLevel = Level.ERROR;
        }
    }

    private boolean isDebugMode() {
        boolean debugMode = true;
        try {
            final PackageManager pm = context.getPackageManager();
            if (pm != null) {
                final PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
                if (pi != null) {
                    final ApplicationInfo ai = pi.applicationInfo;
                    if (ai != null && ((ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0)) {
                        debugMode = false;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            debugMode = true;
        }
        return debugMode;
    }

    /**
     * Returns the log level currently being used.
     *
     * @return Log level.
     */
    public Level getLogLevel() {
        return logLevel;
    }

    /**
     * Sets the log level to be used.
     *
     * @param level Log level.
     */
    public void setLogLevel(Level level) {
        this.logLevel = level;
    }

    /**
     * Logs the message passed in at the level specified.
     *
     * @param level Log level.
     * @param tag Log tag.
     * @param message Log message.
     */
    public void log(Level level, String tag, String message) {
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
        logToFile(level, tag, message, null);
    }

    /**
     * Logs the message and throwable passed in at the level specified.
     *
     * @param level Log level.
     * @param tag Log tag.
     * @param message Log message.
     * @param e Throwable to be logged.
     */
    public void log(Level level, String tag, String message, Throwable e) {
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
        logToFile(level, tag, message, e);
    }

    private void logToFile(Level level, String tag, String message, Throwable e) {
        // TODO:
    }
}

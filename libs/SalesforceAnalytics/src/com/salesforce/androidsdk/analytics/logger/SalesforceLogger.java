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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A simple logger that allows components to log statements of different log levels. This class
 * also provides the ability to break down logs at the component level and set different log
 * levels for different components. The available options for log output are console and file.
 *
 * @author bhariharan
 */
public class SalesforceLogger {

    /**
     * The factory for Salesforce log receivers.  Defaults to null
     */
    private static SalesforceLogReceiverFactory logReceiverFactory;

    private static final String TAG = "SalesforceLogger";
    private static final String LOG_LINE_FORMAT = "TIME: %s, LEVEL: %s, TAG: %s, MESSAGE: %s";
    private static final String LOG_LINE_FORMAT_WITH_EXCEPTION = "TIME: %s, LEVEL: %s, TAG: %s, MESSAGE: %s, EXCEPTION: %s";
    private static final String US_DATE_FORMAT = "MM-dd HH:mm:ss.SSS";
    private static final String SF_LOGGER_PREFS = "sf_logger_prefs";
    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(1);
    private static Map<String, SalesforceLogger> LOGGERS;

    /**
     * An enumeration of log levels.
     */
    public enum Level {
        OFF(6),
        ERROR(5),
        WARN(4),
        INFO(3),
        DEBUG(2),
        VERBOSE(1);

        private final Integer severity;

        Level(int severity) {
            this.severity = severity;
        }
    }

    private FileLogger fileLogger;
    private final Context context;
    private final String componentName;

    /**
     * The Salesforce log receiver to receive all logs issued by this Salesforce logger
     */
    private final SalesforceLogReceiver logReceiver;

    private Level logLevel;

    /**
     * Sets the factory for Salesforce log receivers.
     *
     * @param logReceiverFactory The factory for Salesforce log receivers
     * @noinspection unused
     */
    public synchronized static void setLogReceiverFactory(
            SalesforceLogReceiverFactory logReceiverFactory
    ) {
        SalesforceLogger.logReceiverFactory = logReceiverFactory;
    }

    /**
     * Returns the Salesforce logger instance associated with the named component.  The logger will
     * be created if needed and without any additional Salesforce log receiver.
     *
     * @param componentName The component name
     * @param context       The Android context
     * @return Either a new or existing Salesforce logger for the named component
     */
    public synchronized static SalesforceLogger getLogger(String componentName,
                                                          Context context) {
        return getLogger(componentName, context, null);
    }

    /**
     * Returns the Salesforce logger instance associated with the named component.  The logger will
     * be created if needed and with the specified Salesforce log receiver.
     *
     * @param componentName         The component name
     * @param context               The Android context
     * @param salesforceLogReceiver The Salesforce log receiver to receive all logs issued by the
     *                              Salesforce logger
     * @return Either a new or existing Salesforce logger for the named component
     */
    public synchronized static SalesforceLogger getLogger(String componentName,
                                                          Context context,
                                                          SalesforceLogReceiver salesforceLogReceiver) {
        if (LOGGERS == null) {
            LOGGERS = new ConcurrentHashMap<>();
        }

        // Resolve the log receiver from parameters or the factory.
        SalesforceLogReceiver salesforceLogReceiverResolved = salesforceLogReceiver;
        if (salesforceLogReceiverResolved == null && logReceiverFactory != null) {
            salesforceLogReceiverResolved = logReceiverFactory.create(componentName);
        }

        if (!LOGGERS.containsKey(componentName)) {
            final SalesforceLogger logger = new SalesforceLogger(
                    componentName,
                    context,
                    salesforceLogReceiverResolved);
            LOGGERS.put(componentName, logger);
        }
        return LOGGERS.get(componentName);
    }

    /**
     * Returns the set of components that have loggers associated with them.
     *
     * @return Set of components being logged.
     */
    public synchronized static Set<String> getComponents() {
        if (LOGGERS == null || LOGGERS.isEmpty()) {
            return null;
        }
        Set<String> components = LOGGERS.keySet();
        if (components.isEmpty()) {
            components = null;
        }
        return components;
    }

    /**
     * Wipes all components currently being logged. Used only by tests.
     */
    public synchronized static void flushComponents() {
        LOGGERS = null;
    }

    /**
     * Creates a new Salesforce logger.
     *
     * @param componentName         The named component the logger will be associated with
     * @param context               The Android context
     * @param salesforceLogReceiver The Salesforce log receiver to receive all logs issued by the
     *                              Salesforce logger
     */
    private SalesforceLogger(
            String componentName,
            Context context,
            SalesforceLogReceiver salesforceLogReceiver) {
        this.context = context;
        this.componentName = componentName;
        this.logReceiver = salesforceLogReceiver;
        readLoggerPrefs();
        try {
            fileLogger = new FileLogger(context, componentName);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't create file logger", e);
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
        } catch (NameNotFoundException e) {
            /* Intentionally blank */
        }
        return debugMode;
    }

    /**
     * Returns the instance of FileLogger associated with this component.
     *
     * @return FileLogger instance.
     * @noinspection unused
     */
    public FileLogger getFileLogger() {
        return fileLogger;
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
        storeLoggerPrefs(level);
    }

    /**
     * Disables file logging.
     *
     * @noinspection unused
     */
    public synchronized void disableFileLogging() {
        if (fileLogger != null) {
            fileLogger.setMaxSize(0);
        }
    }

    /**
     * Enables file logging.
     *
     * @param maxSize Maximum number of log lines allowed to be stored at a time.
     * @noinspection unused
     */
    public synchronized void enableFileLogging(int maxSize) {
        if (fileLogger != null) {
            fileLogger.setMaxSize(maxSize);
        }
    }

    /**
     * Returns if file logging is enabled or not.
     *
     * @return True - if enabled, False - otherwise.
     * @noinspection unused
     */
    public boolean isFileLoggingEnabled() {
        int maxSize = 0;
        if (fileLogger != null) {
            maxSize = fileLogger.getMaxSize();
        }
        return (maxSize > 0);
    }

    /**
     * Logs an error log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     */
    public void e(String tag, String message) {
        log(Level.ERROR, tag, message);
    }

    /**
     * Logs an error log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     * @param e       Exception to be logged.
     */
    public void e(String tag, String message, Throwable e) {
        log(Level.ERROR, tag, message, e);
    }

    /**
     * Logs a warning log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     */
    public void w(String tag, String message) {
        log(Level.WARN, tag, message);
    }

    /**
     * Logs a warning log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     * @param e       Exception to be logged.
     */
    public void w(String tag, String message, Throwable e) {
        log(Level.WARN, tag, message, e);
    }

    /**
     * Logs an info log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     */
    public void i(String tag, String message) {
        log(Level.INFO, tag, message);
    }

    /**
     * Logs an info log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     * @param e       Exception to be logged.
     */
    public void i(String tag, String message, Throwable e) {
        log(Level.INFO, tag, message, e);
    }

    /**
     * Logs a debug log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     */
    public void d(String tag, String message) {
        log(Level.DEBUG, tag, message);
    }

    /**
     * Logs a debug log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     * @param e       Exception to be logged.
     */
    public void d(String tag, String message, Throwable e) {
        log(Level.DEBUG, tag, message, e);
    }

    /**
     * Logs a verbose log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     */
    public void v(String tag, String message) {
        log(Level.VERBOSE, tag, message);
    }

    /**
     * Logs a verbose log line.
     *
     * @param tag     Log tag.
     * @param message Log message.
     * @param e       Exception to be logged.
     */
    public void v(String tag, String message, Throwable e) {
        log(Level.VERBOSE, tag, message, e);
    }

    /**
     * Logs a log line of the specified level.
     *
     * @param level   Log level.
     * @param tag     Log tag.
     * @param message Log message.
     */
    public void log(Level level, String tag, String message) {
        if (level.severity >= logLevel.severity) {
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
            }
            logToFile(getTimeFromUTC(), level, tag, message, null);

            if (logReceiver != null) logReceiver.receive(level, tag, message);
        }
    }

    /**
     * Logs a log line of the specified level.
     *
     * @param level   Log level.
     * @param tag     Log tag.
     * @param message Log message.
     * @param e       Exception to be logged.
     */
    public void log(Level level, String tag, String message, Throwable e) {
        if (level.severity >= logLevel.severity) {
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
            }
            logToFile(getTimeFromUTC(), level, tag, message, e);

            if (logReceiver != null) logReceiver.receive(level, tag, message, e);
        }
    }

    private void logToFile(final String curTime, final Level level, final String tag,
                           final String message, final Throwable e) {
        THREAD_POOL.execute(() -> {
            if (fileLogger != null) {
                String logLine;
                if (e != null) {
                    logLine = String.format(LOG_LINE_FORMAT_WITH_EXCEPTION, curTime, level,
                            tag, message, Log.getStackTraceString(e));
                } else {
                    logLine = String.format(LOG_LINE_FORMAT, curTime, level, tag, message);
                }
                fileLogger.addLogLine(logLine);
            }
        });
    }

    private String getTimeFromUTC() {
        long curTime = System.currentTimeMillis();
        final Date date = new Date(curTime);
        final SimpleDateFormat dateFormat = new SimpleDateFormat(US_DATE_FORMAT, Locale.US);
        return dateFormat.format(date);
    }

    private synchronized void storeLoggerPrefs(Level level) {
        final SharedPreferences sp = context.getSharedPreferences(SF_LOGGER_PREFS, Context.MODE_PRIVATE);
        final SharedPreferences.Editor e = sp.edit();
        e.putString(componentName, level.toString());
        e.apply();
        logLevel = level;
    }

    private void readLoggerPrefs() {
        final SharedPreferences sp = context.getSharedPreferences(SF_LOGGER_PREFS, Context.MODE_PRIVATE);
        Level level = Level.DEBUG;
        if (!isDebugMode()) {
            level = Level.ERROR;
        }
        if (!sp.contains(componentName)) {
            storeLoggerPrefs(level);
        }
        final String logLevelString = sp.getString(componentName, level.toString());
        logLevel = Level.valueOf(logLevelString);
    }

    /**
     * Resets the stored logger prefs. Should be used ONLY by tests.
     *
     * @param context Context.
     */
    public synchronized static void resetLoggerPrefs(Context context) {
        final SharedPreferences sp = context.getSharedPreferences(SF_LOGGER_PREFS, Context.MODE_PRIVATE);
        final SharedPreferences.Editor e = sp.edit();
        e.clear();
        e.apply();
    }
}

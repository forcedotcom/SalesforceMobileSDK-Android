import 'dart:developer' as developer;
import 'package:logging/logging.dart';
import 'log_sanitizer.dart';

/// Logger component for the Salesforce SDK.
///
/// Provides a unified logging interface with configurable log levels,
/// component-based logging, and automatic sensitive data sanitization.
class SalesforceLogger {
  static final Map<String, SalesforceLogger> _instances = {};
  final String _componentName;
  final Logger _logger;
  Level _logLevel;

  /// Whether to sanitize log messages (redact tokens, etc.).
  /// Enabled by default. Disable only in debug/test environments.
  static bool sanitizeLogs = true;

  SalesforceLogger._(this._componentName)
      : _logger = Logger(_componentName),
        _logLevel = Level.INFO;

  /// Gets or creates a logger instance for the given component.
  factory SalesforceLogger.getLogger(String componentName) {
    return _instances.putIfAbsent(
      componentName,
      () => SalesforceLogger._(componentName),
    );
  }

  /// Sets the log level for this logger.
  set logLevel(Level level) {
    _logLevel = level;
    _logger.level = level;
  }

  /// Gets the current log level.
  Level get logLevel => _logLevel;

  /// Gets the component name.
  String get componentName => _componentName;

  /// Log an error message.
  void e(String tag, String message, [Object? error, StackTrace? stackTrace]) {
    _log(Level.SEVERE, tag, message, error, stackTrace);
  }

  /// Log a warning message.
  void w(String tag, String message, [Object? error, StackTrace? stackTrace]) {
    _log(Level.WARNING, tag, message, error, stackTrace);
  }

  /// Log an info message.
  void i(String tag, String message, [Object? error, StackTrace? stackTrace]) {
    _log(Level.INFO, tag, message, error, stackTrace);
  }

  /// Log a debug message.
  void d(String tag, String message, [Object? error, StackTrace? stackTrace]) {
    _log(Level.FINE, tag, message, error, stackTrace);
  }

  /// Log a verbose message.
  void v(String tag, String message, [Object? error, StackTrace? stackTrace]) {
    _log(Level.FINEST, tag, message, error, stackTrace);
  }

  void _log(Level level, String tag, String message,
      [Object? error, StackTrace? stackTrace]) {
    if (level.value >= _logLevel.value) {
      var formattedMessage = '[$_componentName] $tag: $message';
      if (sanitizeLogs) {
        formattedMessage = LogSanitizer.sanitize(formattedMessage);
      }
      _logger.log(level, formattedMessage, error, stackTrace);
      developer.log(
        formattedMessage,
        level: level.value,
        name: _componentName,
        error: sanitizeLogs && error != null
            ? LogSanitizer.sanitize(error.toString())
            : error,
        stackTrace: stackTrace,
      );
    }
  }

  /// Resets all logger instances.
  static void resetAll() {
    _instances.clear();
  }
}

/// Convenience logger for the SDK itself.
class SalesforceSDKLogger {
  static final SalesforceLogger _logger =
      SalesforceLogger.getLogger('SalesforceSDK');

  static void e(String tag, String message,
          [Object? error, StackTrace? stackTrace]) =>
      _logger.e(tag, message, error, stackTrace);

  static void w(String tag, String message,
          [Object? error, StackTrace? stackTrace]) =>
      _logger.w(tag, message, error, stackTrace);

  static void i(String tag, String message,
          [Object? error, StackTrace? stackTrace]) =>
      _logger.i(tag, message, error, stackTrace);

  static void d(String tag, String message,
          [Object? error, StackTrace? stackTrace]) =>
      _logger.d(tag, message, error, stackTrace);

  static void v(String tag, String message,
          [Object? error, StackTrace? stackTrace]) =>
      _logger.v(tag, message, error, stackTrace);
}

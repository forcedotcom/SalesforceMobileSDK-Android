import 'dart:async';

/// Log entry containing all log information.
class LogEntry {
  final String level;
  final String tag;
  final String message;
  final Object? error;
  final StackTrace? stackTrace;
  final DateTime timestamp;

  LogEntry({
    required this.level,
    required this.tag,
    required this.message,
    this.error,
    this.stackTrace,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();

  @override
  String toString() =>
      '${timestamp.toIso8601String()} [$level] $tag: $message';
}

/// Callback type for receiving log entries.
typedef LogReceiverCallback = void Function(LogEntry entry);

/// Receives and distributes log entries to registered listeners.
///
/// Acts as a central log bus where components can register to receive
/// log entries for custom handling (remote logging, file logging,
/// analytics, etc.).
///
/// Mirrors Android SalesforceLogReceiver for feature parity.
class LogReceiver {
  static LogReceiver? _instance;

  final StreamController<LogEntry> _controller =
      StreamController<LogEntry>.broadcast();
  final List<LogReceiverCallback> _callbacks = [];

  LogReceiver._();

  /// Gets the singleton instance.
  static LogReceiver get instance {
    _instance ??= LogReceiver._();
    return _instance!;
  }

  /// Stream of log entries.
  Stream<LogEntry> get logStream => _controller.stream;

  /// Registers a callback to receive log entries.
  void addListener(LogReceiverCallback callback) {
    _callbacks.add(callback);
  }

  /// Removes a registered callback.
  void removeListener(LogReceiverCallback callback) {
    _callbacks.remove(callback);
  }

  /// Publishes a log entry to all listeners.
  void publish(LogEntry entry) {
    _controller.add(entry);
    for (final callback in _callbacks) {
      callback(entry);
    }
  }

  /// Convenience method to create and publish a log entry.
  void log(String level, String tag, String message,
      [Object? error, StackTrace? stackTrace]) {
    publish(LogEntry(
      level: level,
      tag: tag,
      message: message,
      error: error,
      stackTrace: stackTrace,
    ));
  }

  /// Disposes resources.
  void dispose() {
    _controller.close();
    _callbacks.clear();
  }

  /// Resets the singleton (for testing).
  static void reset() {
    _instance?.dispose();
    _instance = null;
  }
}

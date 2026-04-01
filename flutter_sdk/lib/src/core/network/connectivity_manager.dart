import 'dart:async';
import 'package:connectivity_plus/connectivity_plus.dart';
import '../../analytics/logger/salesforce_logger.dart';

/// Exception thrown when a network request is attempted while offline.
class NetworkOfflineException implements Exception {
  final String message;
  const NetworkOfflineException([this.message = 'No network connectivity']);
  @override
  String toString() => 'NetworkOfflineException: $message';
}

/// Manages network connectivity state for the SDK.
///
/// Provides:
/// - Real-time online/offline status
/// - Stream of connectivity changes
/// - Awaitable future for when connectivity returns
class ConnectivityManager {
  static const String _tag = 'ConnectivityManager';
  static ConnectivityManager? _instance;

  final Connectivity _connectivity;
  final SalesforceLogger _logger = SalesforceLogger.getLogger('Connectivity');

  StreamSubscription<ConnectivityResult>? _subscription;
  final StreamController<bool> _statusController =
      StreamController<bool>.broadcast();

  bool _isOnline = true;
  Completer<void>? _onlineCompleter;

  ConnectivityManager._({Connectivity? connectivity})
      : _connectivity = connectivity ?? Connectivity();

  /// Gets the singleton instance.
  static ConnectivityManager get instance {
    _instance ??= ConnectivityManager._();
    return _instance!;
  }

  /// Whether the device currently has network connectivity.
  bool get isOnline => _isOnline;

  /// Stream of connectivity status changes (true = online, false = offline).
  Stream<bool> get onlineStatusStream => _statusController.stream;

  /// Initializes connectivity monitoring.
  Future<void> initialize() async {
    final result = await _connectivity.checkConnectivity();
    _updateStatus(result);

    _subscription = _connectivity.onConnectivityChanged.listen((result) {
      _updateStatus(result);
    });
  }

  /// Returns a future that completes when the device comes online.
  ///
  /// Completes immediately if already online.
  Future<void> whenOnline() {
    if (_isOnline) return Future.value();
    _onlineCompleter ??= Completer<void>();
    return _onlineCompleter!.future;
  }

  /// Throws [NetworkOfflineException] if the device is offline.
  void requireOnline() {
    if (!_isOnline) {
      throw const NetworkOfflineException();
    }
  }

  void _updateStatus(ConnectivityResult result) {
    final wasOnline = _isOnline;
    _isOnline = result != ConnectivityResult.none;

    if (_isOnline != wasOnline) {
      _statusController.add(_isOnline);
      _logger.i(_tag, 'Connectivity changed: ${_isOnline ? "online" : "offline"}');

      if (_isOnline && _onlineCompleter != null && !_onlineCompleter!.isCompleted) {
        _onlineCompleter!.complete();
        _onlineCompleter = null;
      }
    }
  }

  /// Disposes resources.
  void dispose() {
    _subscription?.cancel();
    _statusController.close();
  }

  /// Resets the singleton (for testing).
  static void reset() {
    _instance?.dispose();
    _instance = null;
  }
}

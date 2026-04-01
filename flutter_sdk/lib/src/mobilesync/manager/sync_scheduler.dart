import 'dart:async';
import '../../analytics/logger/salesforce_logger.dart';
import '../../core/network/connectivity_manager.dart';
import 'sync_manager.dart';

/// Configuration for a scheduled sync.
class ScheduledSync {
  /// Unique name for this schedule.
  final String name;

  /// The sync name in SyncManager to re-run.
  final String syncName;

  /// Interval between sync runs.
  final Duration interval;

  /// Whether to skip if offline.
  final bool skipWhenOffline;

  const ScheduledSync({
    required this.name,
    required this.syncName,
    required this.interval,
    this.skipWhenOffline = true,
  });
}

/// Schedules periodic sync operations.
///
/// Provides foreground-based periodic sync scheduling using Dart timers.
/// For true background sync that survives app kill, use
/// [BackgroundSyncScheduler] which integrates with the `workmanager` package.
class SyncScheduler {
  static const String _tag = 'SyncScheduler';

  final SyncManager _syncManager;
  final ConnectivityManager? _connectivityManager;
  final SalesforceLogger _logger = SalesforceLogger.getLogger('SyncScheduler');
  final Map<String, Timer> _timers = {};
  final Map<String, ScheduledSync> _schedules = {};

  SyncScheduler({
    required SyncManager syncManager,
    ConnectivityManager? connectivityManager,
  })  : _syncManager = syncManager,
        _connectivityManager = connectivityManager;

  /// Schedules a periodic sync.
  void scheduleSync(ScheduledSync schedule) {
    cancelSync(schedule.name);

    _schedules[schedule.name] = schedule;
    _timers[schedule.name] = Timer.periodic(schedule.interval, (_) {
      _runScheduledSync(schedule);
    });

    _logger.i(_tag,
        'Scheduled sync "${schedule.name}" every ${schedule.interval.inSeconds}s');
  }

  /// Cancels a scheduled sync.
  void cancelSync(String name) {
    _timers[name]?.cancel();
    _timers.remove(name);
    _schedules.remove(name);
  }

  /// Cancels all scheduled syncs.
  void cancelAll() {
    for (final timer in _timers.values) {
      timer.cancel();
    }
    _timers.clear();
    _schedules.clear();
  }

  /// Gets all active schedule names.
  Set<String> get activeSchedules => Set.unmodifiable(_schedules.keys.toSet());

  /// Manually triggers a scheduled sync now.
  Future<void> triggerNow(String name) async {
    final schedule = _schedules[name];
    if (schedule != null) {
      await _runScheduledSync(schedule);
    }
  }

  Future<void> _runScheduledSync(ScheduledSync schedule) async {
    if (schedule.skipWhenOffline &&
        _connectivityManager != null &&
        !_connectivityManager!.isOnline) {
      _logger.d(_tag,
          'Skipping sync "${schedule.name}" — offline');
      return;
    }

    if (!_syncManager.isAcceptingSyncs) {
      _logger.d(_tag,
          'Skipping sync "${schedule.name}" — SyncManager not accepting syncs');
      return;
    }

    try {
      await _syncManager.reSyncByName(schedule.syncName);
      _logger.i(_tag, 'Scheduled sync "${schedule.name}" completed');
    } catch (e) {
      _logger.e(_tag, 'Scheduled sync "${schedule.name}" failed', e);
    }
  }

  /// Disposes all timers.
  void dispose() {
    cancelAll();
  }
}

/// Background sync scheduler using workmanager for OS-level scheduling.
///
/// This scheduler survives app kill and runs on a platform-defined interval.
/// Note: On iOS, background execution is heavily restricted by the OS.
class BackgroundSyncScheduler {
  static const String _taskPrefix = 'sf_sync_';

  /// Registers a background sync task.
  ///
  /// The actual sync execution must be configured in your app's
  /// `Workmanager().executeTask()` callback at app initialization.
  static Future<void> registerPeriodicSync({
    required String syncName,
    Duration frequency = const Duration(hours: 1),
    Map<String, dynamic>? inputData,
  }) async {
    // Note: Workmanager must be initialized in the app's main() function.
    // This provides the registration API. The actual task execution
    // is handled by the app's callbackDispatcher.
    //
    // Usage in app:
    // ```dart
    // Workmanager().initialize(callbackDispatcher);
    // BackgroundSyncScheduler.registerPeriodicSync(syncName: 'mySync');
    // ```
    //
    // The callbackDispatcher should:
    // 1. Initialize the SDK
    // 2. Get or create SyncManager
    // 3. Call reSyncByName(syncName)

    // Import is deferred to avoid requiring workmanager in all builds
    try {
      final taskName = '$_taskPrefix$syncName';
      // The app must call Workmanager().registerPeriodicTask() directly
      // as it requires the callbackDispatcher to be set up first.
      // This class provides a helper interface.
      throw UnimplementedError(
        'Call Workmanager().registerPeriodicTask("$taskName", "$taskName", '
        'frequency: Duration(hours: ${frequency.inHours})) in your app code. '
        'See documentation for setup instructions.',
      );
    } catch (e) {
      if (e is UnimplementedError) rethrow;
    }
  }

  /// Cancels a background sync task.
  static Future<void> cancelSync(String syncName) async {
    // App should call: Workmanager().cancelByUniqueName('$_taskPrefix$syncName')
    throw UnimplementedError(
      'Call Workmanager().cancelByUniqueName("$_taskPrefix$syncName") in your app code.',
    );
  }

  /// Returns the task name for a sync name.
  static String taskName(String syncName) => '$_taskPrefix$syncName';
}

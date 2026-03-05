import 'dart:async';
import '../logger/salesforce_logger.dart';
import '../model/device_app_attributes.dart';
import '../model/instrumentation_event.dart';
import '../store/event_store_manager.dart';
import '../transform/transform.dart';

/// Publishing types for analytics events.
enum AnalyticsPublishingType {
  /// Publishing is disabled.
  publishDisabled,

  /// Publish when the app goes to background.
  publishOnAppBackground,

  /// Publish periodically.
  publishPeriodically,
}

/// Callback for analytics publishing.
typedef AnalyticsPublisher = Future<bool> Function(
    List<Map<String, dynamic>> events);

/// Manages analytics event logging, storage, and publishing.
///
/// Mirrors the Android SalesforceAnalyticsManager functionality,
/// providing event recording, batched publishing, and configurable
/// publish strategies.
class SalesforceAnalyticsManager {
  static const String _tag = 'SalesforceAnalyticsManager';
  static const int defaultBatchSize = 100;
  static const Duration defaultPublishFrequency = Duration(hours: 8);

  static final Map<String, SalesforceAnalyticsManager> _instances = {};

  final String _uniqueId;
  final EventStoreManager _eventStoreManager;
  final DeviceAppAttributes _deviceAppAttributes;
  final SalesforceLogger _logger = SalesforceLogger.getLogger('Analytics');

  bool _loggingEnabled = true;
  int _batchSize = defaultBatchSize;
  AnalyticsPublishingType _publishingType =
      AnalyticsPublishingType.publishDisabled;
  Duration _publishFrequency = defaultPublishFrequency;
  Timer? _publishTimer;

  final List<_PublisherEntry> _remotePublishers = [];

  SalesforceAnalyticsManager._({
    required String uniqueId,
    required EventStoreManager eventStoreManager,
    required DeviceAppAttributes deviceAppAttributes,
  })  : _uniqueId = uniqueId,
        _eventStoreManager = eventStoreManager,
        _deviceAppAttributes = deviceAppAttributes;

  /// Gets or creates an instance for the given unique ID.
  factory SalesforceAnalyticsManager.getInstance({
    required String uniqueId,
    required String encryptionKey,
    required DeviceAppAttributes deviceAppAttributes,
  }) {
    return _instances.putIfAbsent(
      uniqueId,
      () => SalesforceAnalyticsManager._(
        uniqueId: uniqueId,
        eventStoreManager: EventStoreManager(
          storeId: uniqueId,
          encryptionKey: encryptionKey,
        ),
        deviceAppAttributes: deviceAppAttributes,
      ),
    );
  }

  /// Gets the event store manager.
  EventStoreManager get eventStoreManager => _eventStoreManager;

  /// Gets the device app attributes.
  DeviceAppAttributes get deviceAppAttributes => _deviceAppAttributes;

  /// Whether logging is enabled.
  bool get isLoggingEnabled => _loggingEnabled;

  /// Enable or disable logging.
  set loggingEnabled(bool enabled) {
    _loggingEnabled = enabled;
  }

  /// Gets the batch size for publishing.
  int get batchSize => _batchSize;

  /// Sets the batch size for publishing.
  set batchSize(int size) {
    _batchSize = size;
  }

  /// Gets the publishing type.
  AnalyticsPublishingType get publishingType => _publishingType;

  /// Sets the publishing type and configures timers accordingly.
  set publishingType(AnalyticsPublishingType type) {
    _publishingType = type;
    _publishTimer?.cancel();
    _publishTimer = null;

    if (type == AnalyticsPublishingType.publishPeriodically) {
      _publishTimer = Timer.periodic(_publishFrequency, (_) {
        publishAllEvents();
      });
    }
  }

  /// Gets the publish frequency for periodic publishing.
  Duration get publishFrequency => _publishFrequency;

  /// Sets the publish frequency.
  set publishFrequency(Duration frequency) {
    _publishFrequency = frequency;
    if (_publishingType == AnalyticsPublishingType.publishPeriodically) {
      publishingType = _publishingType; // Restart timer
    }
  }

  /// Adds a remote publisher with a transform.
  void addRemotePublisher(EventTransform transform, AnalyticsPublisher publisher) {
    _remotePublishers.add(_PublisherEntry(transform, publisher));
  }

  /// Stores an event if logging is enabled.
  Future<void> storeEvent(InstrumentationEvent event) async {
    if (!_loggingEnabled) return;
    await _eventStoreManager.storeEvent(event);
  }

  /// Publishes all stored events to all registered publishers.
  Future<void> publishAllEvents() async {
    if (_remotePublishers.isEmpty) {
      _logger.w(_tag, 'No publishers registered');
      return;
    }

    final events = await _eventStoreManager.fetchAllEvents();
    if (events.isEmpty) return;

    // Process in batches
    for (var i = 0; i < events.length; i += _batchSize) {
      final batch = events.skip(i).take(_batchSize).toList();

      for (final entry in _remotePublishers) {
        final transformed =
            batch.map((e) => entry.transform.transform(e)).toList();
        try {
          final success = await entry.publisher(transformed);
          if (success) {
            await _eventStoreManager
                .deleteEvents(batch.map((e) => e.eventId).toList());
          }
        } catch (e) {
          _logger.e(_tag, 'Failed to publish events', e);
        }
      }
    }
  }

  /// Publishes a single event.
  Future<void> publishEvent(InstrumentationEvent event) async {
    for (final entry in _remotePublishers) {
      final transformed = entry.transform.transform(event);
      try {
        await entry.publisher([transformed]);
      } catch (e) {
        _logger.e(_tag, 'Failed to publish event', e);
      }
    }
  }

  /// Called when app goes to background.
  void onAppBackground() {
    if (_publishingType == AnalyticsPublishingType.publishOnAppBackground) {
      publishAllEvents();
    }
  }

  /// Resets all instances.
  static void reset() {
    for (final instance in _instances.values) {
      instance._publishTimer?.cancel();
    }
    _instances.clear();
  }

  /// Resets a specific instance.
  static void resetInstance(String uniqueId) {
    final instance = _instances.remove(uniqueId);
    instance?._publishTimer?.cancel();
  }

  /// Disposes this instance.
  void dispose() {
    _publishTimer?.cancel();
    _publishTimer = null;
  }
}

class _PublisherEntry {
  final EventTransform transform;
  final AnalyticsPublisher publisher;
  _PublisherEntry(this.transform, this.publisher);
}

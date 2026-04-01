import 'dart:async';
import '../../core/rest/rest_client.dart';
import '../../core/rest/rest_request.dart';
import '../manager/analytics_manager.dart';
import '../model/instrumentation_event.dart';
import '../store/event_store_manager.dart';
import '../transform/transform.dart';

/// Interface for publishing analytics events to an endpoint.
abstract class AnalyticsPublisher {
  /// Publishes a list of events. Returns true if successful.
  Future<bool> publish(List<InstrumentationEvent> events);
}

/// AILTN (Analytics Instrumentation Library for the Network) publisher.
///
/// Transforms events using [AILTNTransform] and publishes them to the
/// Salesforce analytics endpoint via the REST API.
class AILTNPublisher implements AnalyticsPublisher {
  final RestClient _restClient;
  final AILTNTransform _transform = AILTNTransform();

  AILTNPublisher(this._restClient);

  @override
  Future<bool> publish(List<InstrumentationEvent> events) async {
    if (events.isEmpty) return true;

    final transformedEvents =
        events.map((e) => _transform.transform(e)).toList();

    final request = RestRequest(
      method: RestMethod.post,
      path: '/services/data/v62.0/connect/proxy/app-analytics-logging',
      requestBody: {
        'logLines': transformedEvents,
      },
    );

    try {
      final response = await _restClient.sendRequest(request);
      return response.isSuccess;
    } catch (_) {
      return false;
    }
  }
}

/// Manages periodic publishing of analytics events.
///
/// Collects events from the [EventStoreManager] and publishes them
/// using the configured [AnalyticsPublisher]. Supports both manual
/// and scheduled publishing.
class AnalyticsPublishingManager {
  final EventStoreManager _eventStoreManager;
  final List<AnalyticsPublisher> _publishers = [];
  Timer? _publishTimer;
  bool _isPublishing = false;

  /// The interval between automatic publish cycles.
  Duration publishInterval;

  AnalyticsPublishingManager(
    this._eventStoreManager, {
    this.publishInterval = const Duration(minutes: 15),
  });

  /// Registers a publisher to receive events.
  void addPublisher(AnalyticsPublisher publisher) {
    _publishers.add(publisher);
  }

  /// Removes a publisher.
  void removePublisher(AnalyticsPublisher publisher) {
    _publishers.remove(publisher);
  }

  /// Starts the automatic publishing timer.
  void startPublishing() {
    _publishTimer?.cancel();
    _publishTimer = Timer.periodic(publishInterval, (_) => publishAllEvents());
  }

  /// Stops the automatic publishing timer.
  void stopPublishing() {
    _publishTimer?.cancel();
    _publishTimer = null;
  }

  /// Publishes all stored events to all registered publishers.
  ///
  /// Returns the number of events successfully published.
  Future<int> publishAllEvents() async {
    if (_isPublishing || _publishers.isEmpty) return 0;
    _isPublishing = true;

    try {
      final events = _eventStoreManager.getAllEvents();
      if (events.isEmpty) return 0;

      int published = 0;
      for (final publisher in _publishers) {
        final success = await publisher.publish(events);
        if (success) published = events.length;
      }

      if (published > 0) {
        _eventStoreManager.deleteAllEvents();
      }

      return published;
    } finally {
      _isPublishing = false;
    }
  }

  /// Releases resources.
  void dispose() {
    stopPublishing();
    _publishers.clear();
  }
}

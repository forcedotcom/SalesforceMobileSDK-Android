import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import '../model/instrumentation_event.dart';

/// Manages persistent storage of instrumentation events.
///
/// Events are stored encrypted using flutter_secure_storage and
/// can be retrieved, deleted, or published in batches.
class EventStoreManager {
  static const String _eventPrefix = 'sf_analytics_event_';
  static const String _eventListKey = 'sf_analytics_event_ids';

  final FlutterSecureStorage _secureStorage;
  final String _storeId;

  EventStoreManager({
    required String storeId,
    FlutterSecureStorage? secureStorage,
  })  : _storeId = storeId,
        _secureStorage = secureStorage ?? const FlutterSecureStorage();

  String _eventKey(String eventId) => '${_eventPrefix}${_storeId}_$eventId';
  String get _listKey => '${_eventListKey}_$_storeId';

  /// Stores an instrumentation event.
  Future<void> storeEvent(InstrumentationEvent event) async {
    final eventJson = jsonEncode(event.toJson());
    await _secureStorage.write(
      key: _eventKey(event.eventId),
      value: eventJson,
    );

    // Track event ID in list
    final ids = await _getEventIds();
    ids.add(event.eventId);
    await _saveEventIds(ids);
  }

  /// Stores multiple events.
  Future<void> storeEvents(List<InstrumentationEvent> events) async {
    for (final event in events) {
      await storeEvent(event);
    }
  }

  /// Retrieves a single event by ID.
  Future<InstrumentationEvent?> fetchEvent(String eventId) async {
    final eventJson = await _secureStorage.read(key: _eventKey(eventId));
    if (eventJson == null) return null;
    return InstrumentationEvent.fromJson(jsonDecode(eventJson));
  }

  /// Fetches all stored events.
  Future<List<InstrumentationEvent>> fetchAllEvents() async {
    final ids = await _getEventIds();
    final events = <InstrumentationEvent>[];
    for (final id in ids) {
      final event = await fetchEvent(id);
      if (event != null) events.add(event);
    }
    return events;
  }

  /// Fetches events in batches.
  Future<List<InstrumentationEvent>> fetchEvents({
    int limit = 100,
    int offset = 0,
  }) async {
    final ids = await _getEventIds();
    final subset =
        ids.skip(offset).take(limit).toList();
    final events = <InstrumentationEvent>[];
    for (final id in subset) {
      final event = await fetchEvent(id);
      if (event != null) events.add(event);
    }
    return events;
  }

  /// Deletes a single event.
  Future<void> deleteEvent(String eventId) async {
    await _secureStorage.delete(key: _eventKey(eventId));
    final ids = await _getEventIds();
    ids.remove(eventId);
    await _saveEventIds(ids);
  }

  /// Deletes multiple events.
  Future<void> deleteEvents(List<String> eventIds) async {
    for (final id in eventIds) {
      await _secureStorage.delete(key: _eventKey(id));
    }
    final ids = await _getEventIds();
    ids.removeAll(eventIds);
    await _saveEventIds(ids);
  }

  /// Deletes all stored events.
  Future<void> deleteAllEvents() async {
    final ids = await _getEventIds();
    for (final id in ids) {
      await _secureStorage.delete(key: _eventKey(id));
    }
    await _saveEventIds(<String>{});
  }

  /// Returns the number of stored events.
  Future<int> getNumStoredEvents() async {
    final ids = await _getEventIds();
    return ids.length;
  }

  /// Returns whether the store is empty.
  Future<bool> isEmpty() async {
    return (await getNumStoredEvents()) == 0;
  }

  Future<Set<String>> _getEventIds() async {
    final json = await _secureStorage.read(key: _listKey);
    if (json == null) return {};
    try {
      final list = (jsonDecode(json) as List<dynamic>).cast<String>();
      return list.toSet();
    } catch (_) {
      return {};
    }
  }

  Future<void> _saveEventIds(Set<String> ids) async {
    await _secureStorage.write(
        key: _listKey, value: jsonEncode(ids.toList()));
  }
}

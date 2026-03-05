import 'dart:convert';
import 'package:uuid/uuid.dart';
import 'device_app_attributes.dart';

/// Types of instrumentation events.
enum EventType {
  user,
  system,
  error,
  crud,
  performance,
}

/// Categories for instrumentation events.
enum EventCategory {
  server,
  client,
}

/// Schema types for events.
enum SchemaType {
  LightningInteraction,
  LightningPageView,
  LightningPerformance,
  LightningError,
}

/// Represents a single instrumentation event for analytics tracking.
class InstrumentationEvent {
  /// Unique event ID.
  final String eventId;

  /// Start time in milliseconds.
  final double startTime;

  /// End time in milliseconds.
  final double endTime;

  /// Event name.
  final String name;

  /// Event attributes as JSON.
  final Map<String, dynamic> attributes;

  /// Session ID.
  final String? sessionId;

  /// Sequence ID for ordering.
  final int sequenceId;

  /// Sender's context.
  final Map<String, dynamic>? senderContext;

  /// Sender parent context.
  final Map<String, dynamic>? senderParentContext;

  /// Event type.
  final EventType eventType;

  /// Event category.
  final EventCategory eventCategory;

  /// Schema type.
  final SchemaType schemaType;

  /// Device app attributes.
  final DeviceAppAttributes deviceAppAttributes;

  /// Connection type.
  final String? connectionType;

  /// Sender ID.
  final String? senderId;

  /// Page context.
  final Map<String, dynamic>? page;

  /// Previous page context.
  final Map<String, dynamic>? previousPage;

  /// Error type (for error events).
  final String? errorType;

  InstrumentationEvent({
    String? eventId,
    required this.startTime,
    double? endTime,
    required this.name,
    Map<String, dynamic>? attributes,
    this.sessionId,
    this.sequenceId = 0,
    this.senderContext,
    this.senderParentContext,
    this.eventType = EventType.system,
    this.eventCategory = EventCategory.client,
    this.schemaType = SchemaType.LightningInteraction,
    required this.deviceAppAttributes,
    this.connectionType,
    this.senderId,
    this.page,
    this.previousPage,
    this.errorType,
  })  : eventId = eventId ?? const Uuid().v4(),
        endTime = endTime ?? startTime,
        attributes = attributes ?? {};

  Map<String, dynamic> toJson() => {
        'eventId': eventId,
        'startTime': startTime,
        'endTime': endTime,
        'name': name,
        'attributes': attributes,
        'sessionId': sessionId,
        'sequenceId': sequenceId,
        'senderContext': senderContext,
        'senderParentContext': senderParentContext,
        'eventType': eventType.name,
        'eventCategory': eventCategory.name,
        'schemaType': schemaType.name,
        'deviceAppAttributes': deviceAppAttributes.toJson(),
        'connectionType': connectionType,
        'senderId': senderId,
        'page': page,
        'previousPage': previousPage,
        'errorType': errorType,
      };

  factory InstrumentationEvent.fromJson(Map<String, dynamic> json) {
    return InstrumentationEvent(
      eventId: json['eventId'],
      startTime: (json['startTime'] as num).toDouble(),
      endTime: (json['endTime'] as num?)?.toDouble(),
      name: json['name'] ?? '',
      attributes: json['attributes'] != null
          ? Map<String, dynamic>.from(json['attributes'])
          : null,
      sessionId: json['sessionId'],
      sequenceId: json['sequenceId'] ?? 0,
      senderContext: json['senderContext'] != null
          ? Map<String, dynamic>.from(json['senderContext'])
          : null,
      senderParentContext: json['senderParentContext'] != null
          ? Map<String, dynamic>.from(json['senderParentContext'])
          : null,
      eventType: EventType.values.firstWhere(
        (e) => e.name == json['eventType'],
        orElse: () => EventType.system,
      ),
      eventCategory: EventCategory.values.firstWhere(
        (e) => e.name == json['eventCategory'],
        orElse: () => EventCategory.client,
      ),
      schemaType: SchemaType.values.firstWhere(
        (e) => e.name == json['schemaType'],
        orElse: () => SchemaType.LightningInteraction,
      ),
      deviceAppAttributes: DeviceAppAttributes.fromJson(
        json['deviceAppAttributes'] ?? {},
      ),
      connectionType: json['connectionType'],
      senderId: json['senderId'],
      page: json['page'] != null
          ? Map<String, dynamic>.from(json['page'])
          : null,
      previousPage: json['previousPage'] != null
          ? Map<String, dynamic>.from(json['previousPage'])
          : null,
      errorType: json['errorType'],
    );
  }

  @override
  String toString() => jsonEncode(toJson());
}

/// Builder for creating InstrumentationEvent instances.
class InstrumentationEventBuilder {
  double? _startTime;
  double? _endTime;
  String? _name;
  Map<String, dynamic>? _attributes;
  String? _sessionId;
  int _sequenceId = 0;
  Map<String, dynamic>? _senderContext;
  Map<String, dynamic>? _senderParentContext;
  EventType _eventType = EventType.system;
  EventCategory _eventCategory = EventCategory.client;
  SchemaType _schemaType = SchemaType.LightningInteraction;
  DeviceAppAttributes? _deviceAppAttributes;
  String? _connectionType;
  String? _senderId;
  Map<String, dynamic>? _page;
  Map<String, dynamic>? _previousPage;
  String? _errorType;

  InstrumentationEventBuilder startTime(double time) {
    _startTime = time;
    return this;
  }

  InstrumentationEventBuilder endTime(double time) {
    _endTime = time;
    return this;
  }

  InstrumentationEventBuilder name(String name) {
    _name = name;
    return this;
  }

  InstrumentationEventBuilder attributes(Map<String, dynamic> attrs) {
    _attributes = attrs;
    return this;
  }

  InstrumentationEventBuilder sessionId(String id) {
    _sessionId = id;
    return this;
  }

  InstrumentationEventBuilder sequenceId(int id) {
    _sequenceId = id;
    return this;
  }

  InstrumentationEventBuilder senderContext(Map<String, dynamic> ctx) {
    _senderContext = ctx;
    return this;
  }

  InstrumentationEventBuilder senderParentContext(Map<String, dynamic> ctx) {
    _senderParentContext = ctx;
    return this;
  }

  InstrumentationEventBuilder eventType(EventType type) {
    _eventType = type;
    return this;
  }

  InstrumentationEventBuilder eventCategory(EventCategory cat) {
    _eventCategory = cat;
    return this;
  }

  InstrumentationEventBuilder schemaType(SchemaType type) {
    _schemaType = type;
    return this;
  }

  InstrumentationEventBuilder deviceAppAttributes(DeviceAppAttributes attrs) {
    _deviceAppAttributes = attrs;
    return this;
  }

  InstrumentationEventBuilder connectionType(String type) {
    _connectionType = type;
    return this;
  }

  InstrumentationEventBuilder senderId(String id) {
    _senderId = id;
    return this;
  }

  InstrumentationEventBuilder page(Map<String, dynamic> p) {
    _page = p;
    return this;
  }

  InstrumentationEventBuilder previousPage(Map<String, dynamic> p) {
    _previousPage = p;
    return this;
  }

  InstrumentationEventBuilder errorType(String type) {
    _errorType = type;
    return this;
  }

  InstrumentationEvent build() {
    if (_name == null) throw StateError('Event name is required');
    if (_startTime == null) throw StateError('Start time is required');
    if (_deviceAppAttributes == null) {
      throw StateError('Device app attributes are required');
    }
    return InstrumentationEvent(
      startTime: _startTime!,
      endTime: _endTime,
      name: _name!,
      attributes: _attributes,
      sessionId: _sessionId,
      sequenceId: _sequenceId,
      senderContext: _senderContext,
      senderParentContext: _senderParentContext,
      eventType: _eventType,
      eventCategory: _eventCategory,
      schemaType: _schemaType,
      deviceAppAttributes: _deviceAppAttributes!,
      connectionType: _connectionType,
      senderId: _senderId,
      page: _page,
      previousPage: _previousPage,
      errorType: _errorType,
    );
  }
}

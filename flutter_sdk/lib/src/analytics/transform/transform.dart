import '../model/instrumentation_event.dart';

/// Abstract interface for transforming instrumentation events
/// into a format suitable for publishing.
abstract class EventTransform {
  /// Transforms an event into a publishable JSON format.
  Map<String, dynamic> transform(InstrumentationEvent event);
}

/// AILTN (Analytics Instrumentation Library for the Network) transform.
///
/// Transforms events into the Salesforce AILTN format for publishing
/// to the Salesforce analytics endpoint.
class AILTNTransform implements EventTransform {
  static const String _schemaTypeKey = 'schemaType';
  static const String _idKey = 'id';
  static const String _eventSourceKey = 'eventSource';
  static const String _tsKey = 'ts';
  static const String _durationKey = 'duration';
  static const String _pageKey = 'page';
  static const String _previousPageKey = 'previousPage';
  static const String _marksKey = 'marks';
  static const String _locatorKey = 'locator';
  static const String _clientSessionIdKey = 'clientSessionId';
  static const String _sequenceKey = 'sequence';
  static const String _attributesKey = 'attributes';
  static const String _connectionTypeKey = 'connectionType';
  static const String _deviceAttributesKey = 'deviceAttributes';
  static const String _errorTypeKey = 'errorType';

  @override
  Map<String, dynamic> transform(InstrumentationEvent event) {
    final transformed = <String, dynamic>{
      _schemaTypeKey: event.schemaType.name,
      _idKey: event.eventId,
      _eventSourceKey: event.eventCategory.name,
      _tsKey: event.startTime.toInt(),
      _durationKey: (event.endTime - event.startTime).toInt(),
      _sequenceKey: event.sequenceId,
      _attributesKey: event.attributes,
      _deviceAttributesKey: event.deviceAppAttributes.toJson(),
    };

    if (event.page != null) transformed[_pageKey] = event.page;
    if (event.previousPage != null) {
      transformed[_previousPageKey] = event.previousPage;
    }
    if (event.sessionId != null) {
      transformed[_clientSessionIdKey] = event.sessionId;
    }
    if (event.connectionType != null) {
      transformed[_connectionTypeKey] = event.connectionType;
    }
    if (event.senderContext != null) {
      transformed[_locatorKey] = event.senderContext;
    }
    if (event.errorType != null) {
      transformed[_errorTypeKey] = event.errorType;
    }

    return transformed;
  }
}

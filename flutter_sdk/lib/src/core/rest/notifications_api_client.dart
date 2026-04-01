import 'dart:convert';
import 'rest_client.dart';
import 'rest_request.dart';
import 'rest_response.dart';

/// Exception for Notifications API errors.
class NotificationsApiException implements Exception {
  final String? errorCode;
  final String? message;
  final String? messageCode;
  final String? source;

  NotificationsApiException({
    this.errorCode,
    this.message,
    this.messageCode,
    this.source,
  });

  factory NotificationsApiException.fromResponse(RestResponse response) {
    try {
      final errors = jsonDecode(response.asString);
      if (errors is List && errors.isNotEmpty) {
        final first = errors[0] as Map<String, dynamic>;
        return NotificationsApiException(
          errorCode: first['errorCode'] as String?,
          message: first['message'] as String?,
          messageCode: first['messageCode'] as String?,
          source: response.asString,
        );
      }
    } catch (_) {}
    return NotificationsApiException(
      message: 'HTTP ${response.statusCode}',
      source: response.asString,
    );
  }

  @override
  String toString() => 'NotificationsApiException: $message ($errorCode)';
}

/// Action within a notification action group.
class NotificationAction {
  final String? actionKey;
  final String? label;
  final String? name;
  final String? type;

  NotificationAction({this.actionKey, this.label, this.name, this.type});

  factory NotificationAction.fromJson(Map<String, dynamic> json) {
    return NotificationAction(
      actionKey: json['actionKey'],
      label: json['label'],
      name: json['name'],
      type: json['type'],
    );
  }

  Map<String, dynamic> toJson() => {
        'actionKey': actionKey,
        'label': label,
        'name': name,
        'type': type,
      };
}

/// Group of actions available for a notification type.
class NotificationActionGroup {
  final String? name;
  final List<NotificationAction> actions;

  NotificationActionGroup({this.name, this.actions = const []});

  factory NotificationActionGroup.fromJson(Map<String, dynamic> json) {
    return NotificationActionGroup(
      name: json['name'],
      actions: (json['actions'] as List<dynamic>?)
              ?.map((a) =>
                  NotificationAction.fromJson(a as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }

  Map<String, dynamic> toJson() => {
        'name': name,
        'actions': actions.map((a) => a.toJson()).toList(),
      };
}

/// A notification type available for the current user.
class NotificationType {
  final String? apiName;
  final String? label;
  final String? type;
  final List<NotificationActionGroup> actionGroups;

  NotificationType({
    this.apiName,
    this.label,
    this.type,
    this.actionGroups = const [],
  });

  factory NotificationType.fromJson(Map<String, dynamic> json) {
    return NotificationType(
      apiName: json['apiName'],
      label: json['label'],
      type: json['type'],
      actionGroups: (json['actionGroups'] as List<dynamic>?)
              ?.map((g) =>
                  NotificationActionGroup.fromJson(g as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }

  Map<String, dynamic> toJson() => {
        'apiName': apiName,
        'label': label,
        'type': type,
        'actionGroups': actionGroups.map((g) => g.toJson()).toList(),
      };
}

/// Response from the notifications types endpoint.
class NotificationsTypesResponse {
  final List<NotificationType> notificationTypes;

  NotificationsTypesResponse({this.notificationTypes = const []});

  factory NotificationsTypesResponse.fromJson(Map<String, dynamic> json) {
    return NotificationsTypesResponse(
      notificationTypes: (json['notificationTypes'] as List<dynamic>?)
              ?.map((t) =>
                  NotificationType.fromJson(t as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }

  Map<String, dynamic> toJson() => {
        'notificationTypes':
            notificationTypes.map((t) => t.toJson()).toList(),
      };
}

/// Response from the notifications actions endpoint.
class NotificationsActionsResponse {
  final String? message;

  NotificationsActionsResponse({this.message});

  factory NotificationsActionsResponse.fromJson(Map<String, dynamic> json) {
    return NotificationsActionsResponse(message: json['message']);
  }
}

/// REST client for Salesforce Notifications API endpoints (v64.0+).
///
/// Provides methods to fetch notification types and submit notification actions.
/// Mirrors Android NotificationsApiClient for feature parity.
class NotificationsApiClient {
  static const String _minApiVersion = '64.0';
  final RestClient _restClient;

  NotificationsApiClient(this._restClient);

  /// Fetches the notification types available for the current user.
  ///
  /// Returns null if API version < v64.0.
  /// Throws [NotificationsApiException] on error.
  Future<NotificationsTypesResponse?> fetchNotificationsTypes(
      String apiVersion) async {
    if (!_isVersionSupported(apiVersion)) return null;

    final request = RestRequest(
      method: RestMethod.get,
      path:
          '${RestRequest.servicesData}/v$apiVersion/connect/notifications/types',
    );

    final response = await _restClient.sendAsync(request);
    if (!response.isSuccess) {
      throw NotificationsApiException.fromResponse(response);
    }
    return NotificationsTypesResponse.fromJson(response.asJsonObject());
  }

  /// Submits an action for a notification.
  ///
  /// Returns null if API version < v64.0.
  /// Throws [NotificationsApiException] on error.
  Future<NotificationsActionsResponse?> submitNotificationAction(
    String apiVersion,
    String notificationId,
    String actionKey,
  ) async {
    if (!_isVersionSupported(apiVersion)) return null;

    final request = RestRequest(
      method: RestMethod.post,
      path:
          '${RestRequest.servicesData}/v$apiVersion/connect/notifications/$notificationId/actions/$actionKey',
    );

    final response = await _restClient.sendAsync(request);
    if (!response.isSuccess) {
      throw NotificationsApiException.fromResponse(response);
    }
    return NotificationsActionsResponse.fromJson(response.asJsonObject());
  }

  bool _isVersionSupported(String apiVersion) {
    final version = double.tryParse(apiVersion) ?? 0;
    final minVersion = double.tryParse(_minApiVersion) ?? 64.0;
    return version >= minVersion;
  }
}

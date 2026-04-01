import '../../analytics/logger/salesforce_logger.dart';
import '../rest/rest_client.dart';
import '../rest/rest_request.dart';

/// Manages push notification device registration with Salesforce.
///
/// Handles:
/// - Registering the device token for push notifications
/// - Unregistering on logout
/// - Re-registering on token refresh
class PushNotificationManager {
  static const String _tag = 'PushNotificationManager';
  static PushNotificationManager? _instance;

  final SalesforceLogger _logger =
      SalesforceLogger.getLogger('PushNotification');

  String? _deviceToken;
  String? _registrationId;

  PushNotificationManager._();

  /// Gets the singleton instance.
  static PushNotificationManager get instance {
    _instance ??= PushNotificationManager._();
    return _instance!;
  }

  /// The current device token.
  String? get deviceToken => _deviceToken;

  /// Whether the device is registered for push notifications.
  bool get isRegistered => _registrationId != null;

  /// Sets the device token (typically from Firebase or APNs).
  void setDeviceToken(String token) {
    _deviceToken = token;
  }

  /// Registers the device for push notifications with Salesforce.
  ///
  /// The [deviceToken] should be obtained from your push notification
  /// provider (e.g., Firebase Cloud Messaging). The [serviceName] and
  /// [serviceType] identify the notification service.
  Future<void> registerForPushNotifications({
    required RestClient restClient,
    required String apiVersion,
    required String deviceToken,
    String serviceType = 'Android',
    String? applicationBundle,
  }) async {
    _deviceToken = deviceToken;

    final fields = <String, Object>{
      'ConnectionToken': deviceToken,
      'ServiceType': serviceType,
    };
    if (applicationBundle != null) {
      fields['ApplicationBundle'] = applicationBundle;
    }

    try {
      final request = RestRequest.getRequestForCreate(
        apiVersion,
        'MobilePushServiceDevice',
        fields,
      );

      final response = await restClient.sendAsync(request);
      if (response.isSuccess) {
        _registrationId = response.asJsonObject()['id'] as String?;
        _logger.i(_tag, 'Push notification registration successful');
      } else {
        _logger.e(_tag,
            'Push notification registration failed: ${response.statusCode}');
      }
    } catch (e) {
      _logger.e(_tag, 'Push notification registration error', e);
    }
  }

  /// Unregisters the device from push notifications.
  Future<void> unregisterPushNotifications({
    required RestClient restClient,
    required String apiVersion,
  }) async {
    if (_registrationId == null) return;

    try {
      final request = RestRequest.getRequestForDelete(
        apiVersion,
        'MobilePushServiceDevice',
        _registrationId!,
      );

      await restClient.sendAsync(request);
      _registrationId = null;
      _deviceToken = null;
      _logger.i(_tag, 'Push notification unregistration successful');
    } catch (e) {
      _logger.e(_tag, 'Push notification unregistration error', e);
    }
  }

  /// Resets the manager (for testing).
  static void reset() {
    _instance = null;
  }
}

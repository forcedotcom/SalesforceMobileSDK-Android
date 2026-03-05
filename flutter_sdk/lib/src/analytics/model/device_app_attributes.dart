/// Represents device and application attributes for analytics instrumentation.
class DeviceAppAttributes {
  /// Application version.
  final String appVersion;

  /// Application name.
  final String appName;

  /// OS version.
  final String osVersion;

  /// OS name (e.g., 'Android', 'iOS').
  final String osName;

  /// Native app type (e.g., 'Native', 'Hybrid', 'ReactNative', 'Flutter').
  final String nativeAppType;

  /// Mobile SDK version.
  final String mobileSdkVersion;

  /// Device model.
  final String deviceModel;

  /// Device ID.
  final String deviceId;

  /// Client ID (connected app consumer key).
  final String clientId;

  const DeviceAppAttributes({
    required this.appVersion,
    required this.appName,
    required this.osVersion,
    required this.osName,
    required this.nativeAppType,
    required this.mobileSdkVersion,
    required this.deviceModel,
    required this.deviceId,
    required this.clientId,
  });

  Map<String, dynamic> toJson() => {
        'appVersion': appVersion,
        'appName': appName,
        'osVersion': osVersion,
        'osName': osName,
        'nativeAppType': nativeAppType,
        'mobileSdkVersion': mobileSdkVersion,
        'deviceModel': deviceModel,
        'deviceId': deviceId,
        'clientId': clientId,
      };

  factory DeviceAppAttributes.fromJson(Map<String, dynamic> json) {
    return DeviceAppAttributes(
      appVersion: json['appVersion'] ?? '',
      appName: json['appName'] ?? '',
      osVersion: json['osVersion'] ?? '',
      osName: json['osName'] ?? '',
      nativeAppType: json['nativeAppType'] ?? '',
      mobileSdkVersion: json['mobileSdkVersion'] ?? '',
      deviceModel: json['deviceModel'] ?? '',
      deviceId: json['deviceId'] ?? '',
      clientId: json['clientId'] ?? '',
    );
  }
}

/// Feature flags for the ftr_ field in the user agent string.
///
/// These constants are used to track which SDK features are being
/// used by applications, enabling analytics and support.
class Features {
  static const String featureAiltnEnabled = 'AI';
  static const String featureAppIsIdp = 'IP';
  static const String featureAppIsSp = 'SP';
  static const String featureBrowserLogin = 'BW';
  static const String featureCertAuth = 'CT';
  static const String featureLocalhost = 'LH';
  static const String featureMdm = 'MM';
  static const String featureMultiUsers = 'MU';
  static const String featurePushNotifications = 'PN';
  static const String featureUserAuth = 'UA';
  static const String featureScreenLock = 'SL';
  static const String featureBiometricAuth = 'BA';
  static const String featureNativeLogin = 'NL';
  static const String featureQrCodeLogin = 'QR';
  static const String featureWelcomeDiscoveryLogin = 'WD';
  static const String featureSmartStore = 'SS';
  static const String featureMobileSync = 'MS';
  static const String featureBriefcase = 'BR';
  static const String featureRelatedRecords = 'RR';

  /// The set of currently registered features.
  static final Set<String> _registeredFeatures = {};

  /// Registers a feature as being used.
  static void registerFeature(String feature) {
    _registeredFeatures.add(feature);
  }

  /// Unregisters a feature.
  static void unregisterFeature(String feature) {
    _registeredFeatures.remove(feature);
  }

  /// Returns all registered features as a sorted comma-separated string.
  static String getRegisteredFeatures() {
    final sorted = _registeredFeatures.toList()..sort();
    return sorted.join(',');
  }

  /// Returns the set of registered feature flags.
  static Set<String> get registeredFeatures => Set.unmodifiable(_registeredFeatures);

  /// Clears all registered features.
  static void clearAll() {
    _registeredFeatures.clear();
  }
}

/// Runtime configuration keys for the Salesforce SDK.
///
/// These settings can be configured at runtime (e.g., via MDM/EMM)
/// and override defaults from boot config.
///
/// Mirrors Android RuntimeConfig for feature parity.
enum RuntimeConfigKey {
  /// Whether to require certificate pinning.
  requireCertificatePinning('requireCertificatePinning'),

  /// Whether to block unmanaged apps.
  blockUnmanagedApps('blockUnmanagedApps'),

  /// Custom login server URL.
  loginUrl('loginUrl'),

  /// Whether to only allow enterprise login (no direct credentials).
  onlyShowAuthorizedHosts('onlyShowAuthorizedHosts'),

  /// App service hosts.
  appServiceHosts('appServiceHosts'),

  /// App service scheme overrides.
  appServiceSchemes('appServiceSchemes'),

  /// Whether to clear data on logout.
  clearDataOnLogout('clearDataOnLogout'),

  /// Whether to clear clipboard on lock.
  clearClipboardOnLock('clearClipboardOnLock'),

  /// Whether to disable screenshot capture.
  disableScreenCapture('disableScreenCapture'),

  /// Whether to disable copy/paste.
  disableCopyPaste('disableCopyPaste'),

  /// Whether to require device encryption.
  requireDeviceEncryption('requireDeviceEncryption'),

  /// Managed app callback URL.
  managedAppCallbackUrl('managedAppCallbackUrl'),

  /// Managed app OAuth ID.
  managedAppOAuthId('managedAppOAuthId'),

  /// Custom certificate pin hashes (comma-separated).
  certificatePinHashes('certificatePinHashes');

  final String key;
  const RuntimeConfigKey(this.key);
}

/// Manages runtime configuration values.
///
/// Values can be set from MDM/EMM policies, admin configuration,
/// or programmatically. Runtime values override boot config defaults.
///
/// Mirrors Android RuntimeConfig for feature parity.
class RuntimeConfig {
  static RuntimeConfig? _instance;
  final Map<String, dynamic> _values = {};

  RuntimeConfig._();

  /// Gets the singleton instance.
  static RuntimeConfig get instance {
    _instance ??= RuntimeConfig._();
    return _instance!;
  }

  /// Gets a string value.
  String? getString(RuntimeConfigKey key) =>
      _values[key.key] as String?;

  /// Gets a bool value.
  bool getBool(RuntimeConfigKey key, {bool defaultValue = false}) =>
      _values[key.key] as bool? ?? defaultValue;

  /// Gets an int value.
  int? getInt(RuntimeConfigKey key) => _values[key.key] as int?;

  /// Gets a list of strings (from comma-separated value).
  List<String> getStringList(RuntimeConfigKey key) {
    final value = _values[key.key];
    if (value is List) return value.cast<String>();
    if (value is String) {
      return value.split(',').map((s) => s.trim()).toList();
    }
    return [];
  }

  /// Sets a value.
  void setValue(RuntimeConfigKey key, dynamic value) {
    _values[key.key] = value;
  }

  /// Sets multiple values.
  void setValues(Map<RuntimeConfigKey, dynamic> values) {
    for (final entry in values.entries) {
      _values[entry.key.key] = entry.value;
    }
  }

  /// Removes a value.
  void removeValue(RuntimeConfigKey key) {
    _values.remove(key.key);
  }

  /// Whether a key has a value set.
  bool hasValue(RuntimeConfigKey key) => _values.containsKey(key.key);

  /// Loads values from a JSON map (e.g., from MDM policy).
  void loadFromJson(Map<String, dynamic> json) {
    _values.addAll(json);
  }

  /// Gets all current values.
  Map<String, dynamic> toJson() => Map.unmodifiable(_values);

  /// Clears all values.
  void clear() => _values.clear();

  /// Resets the singleton (for testing).
  static void reset() {
    _instance = null;
  }
}

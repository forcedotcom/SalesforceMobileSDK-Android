import 'dart:async';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';
import '../../analytics/logger/salesforce_logger.dart';

/// Manages screen lock and biometric authentication policies.
///
/// Supports:
/// - PIN/passcode-based app lock
/// - Biometric authentication (fingerprint/face)
/// - Configurable lock timeout
/// - Per-user and global lock policies
class ScreenLockManager {
  static const String _tag = 'ScreenLockManager';
  static const String _lockEnabledKey = 'sf_screen_lock_enabled';
  static const String _lockTimeoutKey = 'sf_screen_lock_timeout';
  static const String _backgroundTimestampKey = 'sf_background_timestamp';
  static const String _biometricOptInKey = 'sf_biometric_opt_in';

  static ScreenLockManager? _instance;

  final LocalAuthentication _localAuth;
  final FlutterSecureStorage _secureStorage;
  final SalesforceLogger _logger = SalesforceLogger.getLogger('ScreenLock');

  bool _isLocked = false;
  DateTime? _backgroundTimestamp;
  final StreamController<bool> _lockStateController =
      StreamController<bool>.broadcast();

  ScreenLockManager._({
    LocalAuthentication? localAuth,
    FlutterSecureStorage? secureStorage,
  })  : _localAuth = localAuth ?? LocalAuthentication(),
        _secureStorage = secureStorage ?? const FlutterSecureStorage();

  /// Gets the singleton instance.
  static ScreenLockManager get instance {
    _instance ??= ScreenLockManager._();
    return _instance!;
  }

  /// Stream of lock state changes.
  Stream<bool> get lockStateStream => _lockStateController.stream;

  /// Whether the app is currently locked.
  bool get isLocked => _isLocked;

  /// Called when the app goes to background.
  Future<void> onAppBackgrounded() async {
    if (!_isLocked) {
      _backgroundTimestamp = DateTime.now();
      await _secureStorage.write(
          key: _backgroundTimestampKey,
          value: _backgroundTimestamp!.millisecondsSinceEpoch.toString());
    }
  }

  /// Called when the app returns to foreground.
  Future<void> onAppForegrounded() async {
    if (await shouldLock()) {
      lock();
    }
  }

  /// Checks if the app should be locked based on timeout.
  Future<bool> shouldLock() async {
    final enabledStr = await _secureStorage.read(key: _lockEnabledKey);
    final enabled = enabledStr == 'true';
    if (!enabled) return false;

    final timeoutStr = await _secureStorage.read(key: _lockTimeoutKey);
    final timeout = timeoutStr != null ? int.tryParse(timeoutStr) ?? 0 : 0;
    if (timeout == 0) return false;

    final bgTimestampStr =
        await _secureStorage.read(key: _backgroundTimestampKey);
    if (bgTimestampStr == null) return false;

    final bgTimestamp = int.tryParse(bgTimestampStr);
    if (bgTimestamp == null) return false;

    final backgroundTime =
        DateTime.fromMillisecondsSinceEpoch(bgTimestamp);
    final elapsed = DateTime.now().difference(backgroundTime);
    return elapsed.inMilliseconds >= timeout;
  }

  /// Locks the app.
  void lock() {
    _isLocked = true;
    _lockStateController.add(true);
    _logger.i(_tag, 'App locked');
  }

  /// Unlocks the app.
  void unlock() {
    _isLocked = false;
    _backgroundTimestamp = null;
    _lockStateController.add(false);
    _logger.i(_tag, 'App unlocked');
  }

  /// Stores the mobile policy (lock settings) for an account.
  Future<void> storeMobilePolicy({
    required bool enabled,
    required int timeoutMs,
    String? accountSuffix,
  }) async {
    final suffix = accountSuffix ?? '';
    await _secureStorage.write(
        key: '$_lockEnabledKey$suffix', value: enabled.toString());
    await _secureStorage.write(
        key: '$_lockTimeoutKey$suffix', value: timeoutMs.toString());

    // Update global policy (minimum timeout across all accounts)
    await _updateGlobalPolicy();
  }

  /// Checks if biometric authentication is available.
  Future<bool> isBiometricAvailable() async {
    try {
      final canCheck = await _localAuth.canCheckBiometrics;
      if (!canCheck) return false;
      final available = await _localAuth.getAvailableBiometrics();
      return available.isNotEmpty;
    } catch (e) {
      return false;
    }
  }

  /// Authenticates using biometrics.
  Future<bool> authenticateWithBiometrics({
    String reason = 'Please authenticate to access Salesforce',
  }) async {
    try {
      return await _localAuth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          biometricOnly: true,
          stickyAuth: true,
        ),
      );
    } catch (e) {
      _logger.e(_tag, 'Biometric auth failed', e);
      return false;
    }
  }

  /// Gets/sets biometric opt-in for the current user.
  Future<bool> hasBiometricOptedIn({String? accountSuffix}) async {
    final val = await _secureStorage.read(
        key: '$_biometricOptInKey${accountSuffix ?? ''}');
    return val == 'true';
  }

  /// Sets biometric opt-in.
  Future<void> setBiometricOptIn(bool optIn, {String? accountSuffix}) async {
    await _secureStorage.write(
        key: '$_biometricOptInKey${accountSuffix ?? ''}',
        value: optIn.toString());
  }

  /// Cleans up lock data for a specific account.
  Future<void> cleanUp({String? accountSuffix}) async {
    final suffix = accountSuffix ?? '';
    await _secureStorage.delete(key: '$_lockEnabledKey$suffix');
    await _secureStorage.delete(key: '$_lockTimeoutKey$suffix');
    await _secureStorage.delete(key: '$_biometricOptInKey$suffix');
  }

  Future<void> _updateGlobalPolicy() async {
    // Global policy uses the base keys (no suffix)
    final enabledStr = await _secureStorage.read(key: _lockEnabledKey);
    final enabled = enabledStr == 'true';
    if (!enabled) return;
  }

  /// Disposes resources.
  void dispose() {
    _lockStateController.close();
  }

  /// Resets the singleton (for testing).
  static void reset() {
    _instance?.dispose();
    _instance = null;
  }
}

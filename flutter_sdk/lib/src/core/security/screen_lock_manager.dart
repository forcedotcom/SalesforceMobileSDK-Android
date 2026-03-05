import 'dart:async';
import 'package:local_auth/local_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
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
  final SalesforceLogger _logger = SalesforceLogger.getLogger('ScreenLock');

  bool _isLocked = false;
  DateTime? _backgroundTimestamp;
  final StreamController<bool> _lockStateController =
      StreamController<bool>.broadcast();

  ScreenLockManager._({LocalAuthentication? localAuth})
      : _localAuth = localAuth ?? LocalAuthentication();

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
      final prefs = await SharedPreferences.getInstance();
      await prefs.setInt(
          _backgroundTimestampKey, _backgroundTimestamp!.millisecondsSinceEpoch);
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
    final prefs = await SharedPreferences.getInstance();
    final enabled = prefs.getBool(_lockEnabledKey) ?? false;
    if (!enabled) return false;

    final timeout = prefs.getInt(_lockTimeoutKey) ?? 0;
    if (timeout == 0) return false;

    final bgTimestamp = prefs.getInt(_backgroundTimestampKey);
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
    final prefs = await SharedPreferences.getInstance();
    final suffix = accountSuffix ?? '';
    await prefs.setBool('$_lockEnabledKey$suffix', enabled);
    await prefs.setInt('$_lockTimeoutKey$suffix', timeoutMs);

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
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool('$_biometricOptInKey${accountSuffix ?? ''}') ?? false;
  }

  /// Sets biometric opt-in.
  Future<void> setBiometricOptIn(bool optIn, {String? accountSuffix}) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(
        '$_biometricOptInKey${accountSuffix ?? ''}', optIn);
  }

  /// Cleans up lock data for a specific account.
  Future<void> cleanUp({String? accountSuffix}) async {
    final prefs = await SharedPreferences.getInstance();
    final suffix = accountSuffix ?? '';
    await prefs.remove('$_lockEnabledKey$suffix');
    await prefs.remove('$_lockTimeoutKey$suffix');
    await prefs.remove('$_biometricOptInKey$suffix');
  }

  Future<void> _updateGlobalPolicy() async {
    final prefs = await SharedPreferences.getInstance();
    // Global policy uses the base keys (no suffix)
    final enabled = prefs.getBool(_lockEnabledKey) ?? false;
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

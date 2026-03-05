import 'dart:async';
import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import '../../analytics/logger/salesforce_logger.dart';
import '../../analytics/manager/analytics_manager.dart';
import '../../analytics/model/device_app_attributes.dart';
import '../accounts/user_account.dart';
import '../accounts/user_account_manager.dart';
import '../auth/auth_helper.dart';
import '../auth/oauth2.dart';
import '../config/boot_config.dart';
import '../config/login_server_manager.dart';
import '../rest/rest_client.dart';
import '../security/screen_lock_manager.dart';

/// SDK version string.
const String sdkVersion = '1.0.0';

/// Feature flags for tracking enabled features.
class Features {
  static const String featureAiltnEnabled = 'AI';
  static const String featurePushNotifications = 'PN';
  static const String featureMultiUsers = 'MU';
  static const String featureScreenLock = 'SL';
  static const String featureBiometricAuth = 'BA';
  static const String featureBrowserLogin = 'BL';
}

/// SDK events that can be observed.
enum SdkEvent {
  appCreateComplete,
  appLocked,
  appUnlocked,
  loginComplete,
  logoutComplete,
  userSwitchComplete,
  tokenRefreshed,
  tokenRevoked,
}

/// Callback for SDK events.
typedef SdkEventCallback = void Function(SdkEvent event, dynamic data);

/// Main entry point and manager for the Salesforce Flutter SDK.
///
/// This is the central singleton that coordinates all SDK functionality:
/// - Initialization and configuration
/// - User authentication lifecycle
/// - Account management
/// - Analytics
/// - Screen lock/biometric policies
/// - Login server management
///
/// Usage:
/// ```dart
/// await SalesforceSDKManager.instance.init(
///   config: BootConfig(
///     clientId: 'your_consumer_key',
///     callbackUrl: 'your_callback_url',
///     scopes: ['api', 'web', 'refresh_token'],
///   ),
/// );
/// ```
class SalesforceSDKManager with WidgetsBindingObserver {
  static const String _tag = 'SalesforceSDKManager';
  static SalesforceSDKManager? _instance;

  final SalesforceLogger _logger = SalesforceLogger.getLogger('SDKManager');
  final List<SdkEventCallback> _eventCallbacks = [];
  final Set<String> _enabledFeatures = {};

  late BootConfig _bootConfig;
  late LoginManager _loginManager;
  late LoginServerManager _loginServerManager;
  late UserAccountManager _userAccountManager;
  late ScreenLockManager _screenLockManager;

  bool _isInitialized = false;
  DeviceAppAttributes? _deviceAppAttributes;

  SalesforceSDKManager._();

  /// Gets the singleton instance.
  static SalesforceSDKManager get instance {
    _instance ??= SalesforceSDKManager._();
    return _instance!;
  }

  /// Whether the SDK has been initialized.
  bool get isInitialized => _isInitialized;

  /// The boot configuration.
  BootConfig get bootConfig => _bootConfig;

  /// The login server manager.
  LoginServerManager get loginServerManager => _loginServerManager;

  /// The user account manager.
  UserAccountManager get userAccountManager => _userAccountManager;

  /// The screen lock manager.
  ScreenLockManager get screenLockManager => _screenLockManager;

  /// The device app attributes (for analytics).
  DeviceAppAttributes? get deviceAppAttributes => _deviceAppAttributes;

  /// Set of currently enabled features.
  Set<String> get enabledFeatures => Set.unmodifiable(_enabledFeatures);

  /// Initializes the SDK with the given configuration.
  Future<void> init({
    required BootConfig config,
    DeviceAppAttributes? deviceAppAttributes,
  }) async {
    if (_isInitialized) {
      _logger.w(_tag, 'SDK already initialized');
      return;
    }

    _bootConfig = config;
    _deviceAppAttributes = deviceAppAttributes;

    // Initialize sub-managers
    _loginServerManager = LoginServerManager();
    await _loginServerManager.load();

    _userAccountManager = UserAccountManager.instance;
    await _userAccountManager.initialize();

    _screenLockManager = ScreenLockManager.instance;

    _loginManager = LoginManager(
      config: OAuthConfig(
        loginServer: _loginServerManager.selectedServer.url,
        clientId: config.clientId,
        callbackUrl: config.callbackUrl,
        scopes: config.scopes,
        useWebServerAuthentication: config.useWebServerAuthentication,
      ),
    );

    // Register lifecycle observer
    WidgetsBinding.instance.addObserver(this);

    _isInitialized = true;
    _notifyEvent(SdkEvent.appCreateComplete, null);
    _logger.i(_tag, 'Salesforce SDK initialized (v$sdkVersion)');
  }

  /// Starts the OAuth login flow.
  Future<UserAccount> login(BuildContext context) async {
    _ensureInitialized();

    final tokenResponse = await _loginManager.login(context);

    // Call identity service
    IdServiceResponse? idResponse;
    if (tokenResponse.idUrl != null && tokenResponse.authToken != null) {
      try {
        idResponse = await OAuth2.callIdentityService(
          httpClient: _createHttpClient(),
          identityUrl: tokenResponse.idUrl!,
          authToken: tokenResponse.authToken!,
        );
      } catch (e) {
        _logger.w(_tag, 'Failed to call identity service', e);
      }
    }

    final account = UserAccount(
      authToken: tokenResponse.authToken ?? '',
      refreshToken: tokenResponse.refreshToken ?? '',
      loginServer: _loginServerManager.selectedServer.url,
      instanceUrl: Uri.parse(tokenResponse.instanceUrl ?? ''),
      idUrl: tokenResponse.idUrl != null
          ? Uri.parse(tokenResponse.idUrl!)
          : null,
      userId: tokenResponse.userId ?? '',
      orgId: tokenResponse.orgId ?? '',
      username: idResponse?.username,
      firstName: idResponse?.firstName,
      lastName: idResponse?.lastName,
      displayName: idResponse?.displayName,
      email: idResponse?.email,
      photoUrl: idResponse?.pictureUrl,
      thumbnailUrl: idResponse?.thumbnailUrl,
      lightningDomain: tokenResponse.lightningDomain,
      lightningSid: tokenResponse.lightningSid,
      vfDomain: tokenResponse.vfDomain,
      vfSid: tokenResponse.vfSid,
      contentDomain: tokenResponse.contentDomain,
      contentSid: tokenResponse.contentSid,
      csrfToken: tokenResponse.csrfToken,
    );

    await _userAccountManager.addAccount(account);

    // Apply mobile policies
    if (idResponse != null) {
      await _screenLockManager.storeMobilePolicy(
        enabled: idResponse.screenLock,
        timeoutMs: idResponse.screenLockTimeout * 60000,
        accountSuffix: account.userLevelFilenameSuffix,
      );
    }

    _notifyEvent(SdkEvent.loginComplete, account);
    return account;
  }

  /// Logs out the current user.
  Future<void> logout() async {
    _ensureInitialized();
    final currentUser = _userAccountManager.currentUser;
    if (currentUser == null) return;

    try {
      await _loginManager.revokeToken(currentUser.refreshToken);
    } catch (e) {
      _logger.w(_tag, 'Failed to revoke token', e);
    }

    await _screenLockManager.cleanUp(
        accountSuffix: currentUser.userLevelFilenameSuffix);
    await _userAccountManager.logout(currentUser);
    _notifyEvent(SdkEvent.logoutComplete, currentUser);
  }

  /// Logs out all users.
  Future<void> logoutAll() async {
    _ensureInitialized();
    final users = await _userAccountManager.getAuthenticatedUsers();
    for (final user in users) {
      try {
        await _loginManager.revokeToken(user.refreshToken);
      } catch (e) {
        _logger.w(_tag, 'Failed to revoke token for ${user.username}', e);
      }
    }
    await _userAccountManager.logoutAll();
    _notifyEvent(SdkEvent.logoutComplete, null);
  }

  /// Switches to a different user account.
  Future<void> switchToUser(UserAccount account) async {
    _ensureInitialized();
    await _userAccountManager.switchToUser(account);
    _notifyEvent(SdkEvent.userSwitchComplete, account);
  }

  /// Gets a RestClient for the current user.
  RestClient? getRestClient() {
    return _userAccountManager.buildCurrentRestClient();
  }

  /// Gets a RestClient for a specific user.
  RestClient getRestClientForUser(UserAccount account) {
    return _userAccountManager.buildRestClient(account);
  }

  /// Registers a feature flag.
  void registerFeature(String feature) {
    _enabledFeatures.add(feature);
  }

  /// Unregisters a feature flag.
  void unregisterFeature(String feature) {
    _enabledFeatures.remove(feature);
  }

  /// Registers an SDK event callback.
  void addEventListener(SdkEventCallback callback) {
    _eventCallbacks.add(callback);
  }

  /// Removes an SDK event callback.
  void removeEventListener(SdkEventCallback callback) {
    _eventCallbacks.remove(callback);
  }

  /// Builds the user agent string for API requests.
  String get userAgent {
    final features = _enabledFeatures.toList()..sort();
    final featureStr =
        features.isNotEmpty ? ' [${features.join(',')}]' : '';
    return 'SalesforceMobileSDK/Flutter/$sdkVersion$featureStr';
  }

  // WidgetsBindingObserver overrides

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.paused:
      case AppLifecycleState.inactive:
        _screenLockManager.onAppBackgrounded();
        break;
      case AppLifecycleState.resumed:
        _screenLockManager.onAppForegrounded();
        break;
      default:
        break;
    }
  }

  void _ensureInitialized() {
    if (!_isInitialized) {
      throw StateError('SalesforceSDKManager has not been initialized. '
          'Call init() first.');
    }
  }

  void _notifyEvent(SdkEvent event, dynamic data) {
    for (final callback in _eventCallbacks) {
      callback(event, data);
    }
  }

  static http.Client _createHttpClient() => http.Client();

  /// Disposes the SDK manager and cleans up resources.
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _screenLockManager.dispose();
    _isInitialized = false;
  }

  /// Resets the singleton (for testing).
  static void reset() {
    _instance?.dispose();
    _instance = null;
  }
}

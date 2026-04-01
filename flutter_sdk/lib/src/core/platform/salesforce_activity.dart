import 'package:flutter/widgets.dart';
import '../accounts/user_account.dart';
import '../rest/rest_client.dart';

/// Mixin that provides Salesforce-managed lifecycle for Flutter widgets.
///
/// Equivalent to Android's SalesforceActivity / SalesforceActivityDelegate.
/// Apply this mixin to your StatefulWidget's State class to get automatic
/// REST client initialization, logout handling, and user switch handling.
///
/// ```dart
/// class _MyPageState extends State<MyPage>
///     with WidgetsBindingObserver, SalesforceActivityMixin {
///   @override
///   Widget build(BuildContext context) { ... }
/// }
/// ```
mixin SalesforceActivityMixin<T extends StatefulWidget> on State<T> {
  RestClient? _restClient;

  /// The REST client for the current user. Available after [onResume].
  RestClient? get restClient => _restClient;

  /// Whether the activity has been initialized.
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(_LifecycleObserver(this));
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(_LifecycleObserver(this));
    super.dispose();
  }

  /// Called when the app resumes from background.
  /// Override to perform initialization that requires a REST client.
  void onResume() {
    // Subclasses can override
  }

  /// Called when the app is paused (goes to background).
  void onPause() {
    // Subclasses can override
  }

  /// Called when logout completes.
  void onLogoutComplete() {
    _restClient = null;
    _initialized = false;
  }

  /// Called when the user switches to a different account.
  void onUserSwitched() {
    _restClient = null;
    _initialized = false;
    onResume();
  }

  /// Sets the REST client. Call this from your initialization logic.
  void setRestClient(RestClient client) {
    _restClient = client;
    _initialized = true;
  }

  void _handleLifecycleChange(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        onResume();
        break;
      case AppLifecycleState.paused:
        onPause();
        break;
      default:
        break;
    }
  }
}

class _LifecycleObserver extends WidgetsBindingObserver {
  final SalesforceActivityMixin _mixin;

  _LifecycleObserver(this._mixin);

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _mixin._handleLifecycleChange(state);
  }
}

/// Interface for classes that delegate Salesforce activity behavior.
///
/// Equivalent to Android's SalesforceActivityInterface.
abstract class SalesforceActivityInterface {
  /// Called when the app resumes.
  void onResume();

  /// Called when the app pauses.
  void onPause();

  /// Called when logout completes.
  void onLogoutComplete();

  /// Called when the user switches accounts.
  void onUserSwitched();
}

/// A delegate that handles Salesforce activity lifecycle events.
///
/// Equivalent to Android's SalesforceActivityDelegate. Use this when
/// you can't use the mixin (e.g., when extending a different base class).
class SalesforceActivityDelegate implements SalesforceActivityInterface {
  RestClient? restClient;
  final VoidCallback? _onResumeCallback;
  final VoidCallback? _onLogoutCallback;
  final VoidCallback? _onUserSwitchedCallback;

  SalesforceActivityDelegate({
    VoidCallback? onResume,
    VoidCallback? onLogout,
    VoidCallback? onUserSwitched,
  })  : _onResumeCallback = onResume,
        _onLogoutCallback = onLogout,
        _onUserSwitchedCallback = onUserSwitched;

  @override
  void onResume() {
    _onResumeCallback?.call();
  }

  @override
  void onPause() {
    // Default: no-op
  }

  @override
  void onLogoutComplete() {
    restClient = null;
    _onLogoutCallback?.call();
  }

  @override
  void onUserSwitched() {
    restClient = null;
    _onUserSwitchedCallback?.call();
  }
}

/// A LogoutCompleteReceiver that listens for logout events.
///
/// Register this to clean up resources when the user logs out.
class LogoutCompleteReceiver {
  final VoidCallback _onLogout;

  LogoutCompleteReceiver(this._onLogout);

  /// Called when logout completes.
  void onLogoutComplete() => _onLogout();
}

/// A UserSwitchReceiver that listens for user switch events.
///
/// Register this to update UI/state when the active user changes.
class UserSwitchReceiver {
  final void Function(UserAccount? newAccount) _onSwitch;

  UserSwitchReceiver(this._onSwitch);

  /// Called when the active user changes.
  void onUserSwitch(UserAccount? newAccount) => _onSwitch(newAccount);
}

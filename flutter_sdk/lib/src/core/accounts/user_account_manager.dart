import 'dart:async';
import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../analytics/logger/salesforce_logger.dart';
import '../rest/rest_client.dart';
import 'user_account.dart';

/// Types of user switch events.
enum UserSwitchType {
  firstLogin,
  login,
  logout,
  switchUser,
}

/// Callback for user switch events.
typedef UserSwitchCallback = void Function(
    UserAccount? from, UserAccount? to, UserSwitchType type);

/// Manages authenticated user accounts, including multi-user support.
///
/// Handles:
/// - Storing and retrieving user accounts securely
/// - Current user tracking
/// - User switching
/// - Logout and account cleanup
/// - Building RestClient instances for specific users
class UserAccountManager {
  static const String _tag = 'UserAccountManager';
  static const String _accountsKey = 'sf_user_accounts';
  static const String _currentAccountKey = 'sf_current_account';

  static UserAccountManager? _instance;

  final FlutterSecureStorage _secureStorage;
  final SalesforceLogger _logger = SalesforceLogger.getLogger('AccountManager');
  final List<UserSwitchCallback> _switchCallbacks = [];

  UserAccount? _currentUser;
  List<UserAccount>? _cachedAccounts;

  UserAccountManager._({FlutterSecureStorage? secureStorage})
      : _secureStorage = secureStorage ?? const FlutterSecureStorage();

  /// Gets the singleton instance.
  static UserAccountManager get instance {
    _instance ??= UserAccountManager._();
    return _instance!;
  }

  /// Resets the singleton (for testing).
  static void reset() {
    _instance = null;
  }

  /// Gets the current authenticated user, or null if not logged in.
  UserAccount? get currentUser => _currentUser;

  /// Whether there is an authenticated user.
  bool get isLoggedIn => _currentUser != null;

  /// Registers a callback for user switch events.
  void addUserSwitchCallback(UserSwitchCallback callback) {
    _switchCallbacks.add(callback);
  }

  /// Removes a user switch callback.
  void removeUserSwitchCallback(UserSwitchCallback callback) {
    _switchCallbacks.remove(callback);
  }

  /// Initializes the account manager and loads persisted accounts.
  Future<void> initialize() async {
    await _loadAccounts();
    await _loadCurrentUser();
  }

  /// Gets all authenticated user accounts.
  Future<List<UserAccount>> getAuthenticatedUsers() async {
    _cachedAccounts ??= await _loadAccountsFromStorage();
    return List.unmodifiable(_cachedAccounts!);
  }

  /// Adds a new authenticated user or updates existing.
  Future<void> addAccount(UserAccount account) async {
    final accounts = await getAuthenticatedUsers();
    final mutableAccounts = List<UserAccount>.from(accounts);

    // Replace existing or add new
    final index = mutableAccounts.indexWhere((a) => a.uniqueId == account.uniqueId);
    if (index >= 0) {
      mutableAccounts[index] = account;
    } else {
      mutableAccounts.add(account);
    }

    _cachedAccounts = mutableAccounts;
    await _saveAccountsToStorage(mutableAccounts);

    // Set as current if first login
    final isFirstLogin = _currentUser == null;
    _currentUser = account;
    await _saveCurrentUser(account);

    _notifyUserSwitch(
      null,
      account,
      isFirstLogin ? UserSwitchType.firstLogin : UserSwitchType.login,
    );
  }

  /// Switches to a different authenticated user.
  Future<void> switchToUser(UserAccount account) async {
    final previousUser = _currentUser;
    _currentUser = account;
    await _saveCurrentUser(account);
    _notifyUserSwitch(previousUser, account, UserSwitchType.switchUser);
  }

  /// Logs out the specified user.
  Future<void> logout(UserAccount account) async {
    final accounts = await getAuthenticatedUsers();
    final mutableAccounts = List<UserAccount>.from(accounts);
    mutableAccounts.removeWhere((a) => a.uniqueId == account.uniqueId);

    _cachedAccounts = mutableAccounts;
    await _saveAccountsToStorage(mutableAccounts);

    if (_currentUser?.uniqueId == account.uniqueId) {
      final previousUser = _currentUser;
      _currentUser = mutableAccounts.isNotEmpty ? mutableAccounts.first : null;
      if (_currentUser != null) {
        await _saveCurrentUser(_currentUser!);
      } else {
        await _clearCurrentUser();
      }
      _notifyUserSwitch(previousUser, _currentUser, UserSwitchType.logout);
    }
  }

  /// Logs out all users.
  Future<void> logoutAll() async {
    final previousUser = _currentUser;
    _cachedAccounts = [];
    _currentUser = null;
    await _saveAccountsToStorage([]);
    await _clearCurrentUser();
    _notifyUserSwitch(previousUser, null, UserSwitchType.logout);
  }

  /// Creates a RestClient for the given user account.
  RestClient buildRestClient(UserAccount account) {
    return RestClient(
      clientInfo: account.toClientInfo(),
      authToken: account.authToken,
    );
  }

  /// Creates a RestClient for the current user.
  RestClient? buildCurrentRestClient() {
    if (_currentUser == null) return null;
    return buildRestClient(_currentUser!);
  }

  void _notifyUserSwitch(
      UserAccount? from, UserAccount? to, UserSwitchType type) {
    for (final callback in _switchCallbacks) {
      callback(from, to, type);
    }
  }

  Future<void> _loadAccounts() async {
    _cachedAccounts = await _loadAccountsFromStorage();
  }

  Future<void> _loadCurrentUser() async {
    final prefs = await SharedPreferences.getInstance();
    final currentId = prefs.getString(_currentAccountKey);
    if (currentId != null && _cachedAccounts != null) {
      _currentUser = _cachedAccounts!.cast<UserAccount?>().firstWhere(
            (a) => a?.uniqueId == currentId,
            orElse: () => _cachedAccounts!.isNotEmpty
                ? _cachedAccounts!.first
                : null,
          );
    }
  }

  Future<List<UserAccount>> _loadAccountsFromStorage() async {
    final json = await _secureStorage.read(key: _accountsKey);
    if (json == null) return [];
    try {
      final list = jsonDecode(json) as List<dynamic>;
      return list
          .map((e) => UserAccount.fromJson(e as Map<String, dynamic>))
          .toList();
    } catch (e) {
      _logger.e(_tag, 'Failed to load accounts', e);
      return [];
    }
  }

  Future<void> _saveAccountsToStorage(List<UserAccount> accounts) async {
    final json = jsonEncode(accounts.map((a) => a.toJson()).toList());
    await _secureStorage.write(key: _accountsKey, value: json);
  }

  Future<void> _saveCurrentUser(UserAccount account) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_currentAccountKey, account.uniqueId);
  }

  Future<void> _clearCurrentUser() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_currentAccountKey);
  }
}

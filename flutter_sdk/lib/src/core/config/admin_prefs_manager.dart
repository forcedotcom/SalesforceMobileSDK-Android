import '../accounts/user_account.dart';

/// Abstract base class for managing admin preferences (settings or permissions)
/// stored per user account.
///
/// Preferences are stored as key-value pairs scoped to either the org level
/// or user level, depending on the subclass.
abstract class AbstractPrefsManager {
  final Map<String, Map<String, String>> _cache = {};

  /// Returns the filename root for preference storage.
  String get filenameRoot;

  /// Whether preferences are stored at the org level (true) or user level (false).
  bool get isOrgLevel;

  String _getPrefsKey(UserAccount? account) {
    if (account == null) return filenameRoot;
    return isOrgLevel
        ? '${filenameRoot}_${account.orgId}'
        : '${filenameRoot}_${account.orgId}_${account.userId}';
  }

  /// Sets preferences for the specified user account.
  void setPrefs(Map<String, String> attribs, UserAccount? account) {
    final key = _getPrefsKey(account);
    _cache.putIfAbsent(key, () => {});
    _cache[key]!.addAll(attribs);
  }

  /// Sets preferences from a JSON object for the specified user account.
  void setPrefsFromJson(Map<String, dynamic> attribs, UserAccount? account) {
    final stringMap = attribs.map((k, v) => MapEntry(k, v.toString()));
    setPrefs(stringMap, account);
  }

  /// Returns a specific preference value for the given key and account.
  String? getPref(String key, UserAccount? account) {
    final prefsKey = _getPrefsKey(account);
    return _cache[prefsKey]?[key];
  }

  /// Returns all preferences for the specified user account.
  Map<String, String> getPrefs(UserAccount? account) {
    final key = _getPrefsKey(account);
    return Map.unmodifiable(_cache[key] ?? {});
  }

  /// Clears stored preferences for the specified user.
  void reset(UserAccount? account) {
    final key = _getPrefsKey(account);
    _cache.remove(key);
  }

  /// Clears all stored preferences for all users.
  void resetAll() {
    _cache.removeWhere((key, _) => key.startsWith(filenameRoot));
  }
}

/// Manages custom settings for a connected app set by the org admin.
///
/// Settings are stored at the user level (not org level).
class AdminSettingsManager extends AbstractPrefsManager {
  @override
  String get filenameRoot => 'admin_prefs';

  @override
  bool get isOrgLevel => false;
}

/// Manages custom permissions for a connected app set by the org admin.
///
/// Permissions are stored at the user level (not org level).
class AdminPermsManager extends AbstractPrefsManager {
  @override
  String get filenameRoot => 'admin_perms';

  @override
  bool get isOrgLevel => false;
}

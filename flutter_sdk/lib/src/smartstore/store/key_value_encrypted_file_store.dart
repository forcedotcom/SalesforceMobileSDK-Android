import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path_lib;
import '../../core/security/encryptor.dart';
import '../../analytics/logger/salesforce_logger.dart';

/// Encrypted file-based key-value store.
///
/// Provides a secure key-value store where each entry is stored
/// as an encrypted file on disk. Suitable for storing sensitive
/// configuration data, tokens, and small data items.
class KeyValueEncryptedFileStore {
  static const String _tag = 'KeyValueEncryptedFileStore';
  static const String _kvStoresDir = 'key_value_stores';
  static const int maxStoreNameLength = 96;
  static const int storeVersion = 2;

  static final SalesforceLogger _logger =
      SalesforceLogger.getLogger('KVStore');
  static final Map<String, KeyValueEncryptedFileStore> _instances = {};

  final String storeName;
  String _encryptionKey;
  late Directory _storeDir;
  bool _initialized = false;

  KeyValueEncryptedFileStore._({
    required this.storeName,
    required String encryptionKey,
  }) : _encryptionKey = encryptionKey;

  /// Gets or creates a KeyValueEncryptedFileStore instance.
  static Future<KeyValueEncryptedFileStore> getInstance({
    required String storeName,
    required String encryptionKey,
  }) async {
    if (!isValidStoreName(storeName)) {
      throw ArgumentError('Invalid store name: $storeName');
    }
    if (_instances.containsKey(storeName)) {
      return _instances[storeName]!;
    }
    final store = KeyValueEncryptedFileStore._(
      storeName: storeName,
      encryptionKey: encryptionKey,
    );
    await store._initialize();
    _instances[storeName] = store;
    return store;
  }

  /// Validates a store name.
  static bool isValidStoreName(String name) {
    if (name.isEmpty || name.length > maxStoreNameLength) return false;
    return RegExp(r'^[a-zA-Z0-9_\-]+$').hasMatch(name);
  }

  /// Checks if a store exists on disk.
  static Future<bool> hasKeyValueStore(String storeName) async {
    final dir = await _getStoreDirectory(storeName);
    return dir.existsSync();
  }

  /// Removes a store and all its data.
  static Future<void> removeKeyValueStore(String storeName) async {
    final dir = await _getStoreDirectory(storeName);
    if (dir.existsSync()) {
      await dir.delete(recursive: true);
    }
    _instances.remove(storeName);
  }

  /// Gets the store directory.
  Directory get storeDir => _storeDir;

  /// Checks if a key exists.
  Future<bool> contains(String key) async {
    final file = _getFile(key);
    return file.existsSync();
  }

  /// Saves a string value.
  Future<void> saveValue(String key, String value) async {
    final file = _getFile(key);
    final encrypted = Encryptor.encryptString(value, _encryptionKey);
    await file.writeAsString(encrypted);
  }

  /// Gets a string value.
  Future<String?> getValue(String key) async {
    final file = _getFile(key);
    if (!file.existsSync()) return null;
    final encrypted = await file.readAsString();
    try {
      return Encryptor.decryptString(encrypted, _encryptionKey);
    } catch (e) {
      _logger.e(_tag, 'Failed to decrypt value for key: $key', e);
      return null;
    }
  }

  /// Deletes a value.
  Future<void> deleteValue(String key) async {
    final file = _getFile(key);
    if (file.existsSync()) {
      await file.delete();
    }
  }

  /// Deletes all values.
  Future<void> deleteAll() async {
    if (_storeDir.existsSync()) {
      final entities = _storeDir.listSync();
      for (final entity in entities) {
        if (entity is File) {
          await entity.delete();
        }
      }
    }
  }

  /// Gets all keys.
  Future<Set<String>> keySet() async {
    if (!_storeDir.existsSync()) return {};
    final files = _storeDir
        .listSync()
        .whereType<File>()
        .map((f) => Uri.decodeComponent(path_lib.basename(f.path)))
        .toSet();
    return files;
  }

  /// Returns the number of entries.
  Future<int> count() async {
    if (!_storeDir.existsSync()) return 0;
    return _storeDir.listSync().whereType<File>().length;
  }

  /// Whether the store is empty.
  Future<bool> isEmpty() async => (await count()) == 0;

  /// Changes the encryption key and re-encrypts all values.
  Future<void> changeEncryptionKey(String newEncryptionKey) async {
    final keys = await keySet();
    final data = <String, String>{};

    // Read all with old key
    for (final key in keys) {
      final value = await getValue(key);
      if (value != null) data[key] = value;
    }

    // Update encryption key
    _encryptionKey = newEncryptionKey;

    // Re-write with new key
    for (final entry in data.entries) {
      await saveValue(entry.key, entry.value);
    }
  }

  Future<void> _initialize() async {
    _storeDir = await _getStoreDirectory(storeName);
    if (!_storeDir.existsSync()) {
      await _storeDir.create(recursive: true);
    }
    _initialized = true;
  }

  File _getFile(String key) {
    final safeKey = Uri.encodeComponent(key);
    return File(path_lib.join(_storeDir.path, safeKey));
  }

  static Future<Directory> _getStoreDirectory(String storeName) async {
    final appDir = await getApplicationDocumentsDirectory();
    return Directory(
        path_lib.join(appDir.path, _kvStoresDir, storeName));
  }
}

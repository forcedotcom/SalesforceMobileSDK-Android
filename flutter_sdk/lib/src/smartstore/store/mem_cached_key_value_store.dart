import 'key_value_encrypted_file_store.dart';

/// In-memory cached wrapper around [KeyValueEncryptedFileStore].
///
/// Provides fast reads from a memory cache with write-through to the
/// underlying encrypted file store. Useful for frequently accessed
/// data like sync state and configuration.
///
/// Mirrors Android MemCachedKeyValueStore for feature parity.
class MemCachedKeyValueStore {
  final KeyValueEncryptedFileStore _backingStore;
  final Map<String, String?> _cache = {};
  bool _isLoaded = false;

  MemCachedKeyValueStore(this._backingStore);

  /// Creates and initializes a MemCachedKeyValueStore.
  static Future<MemCachedKeyValueStore> create(
      KeyValueEncryptedFileStore backingStore) async {
    final store = MemCachedKeyValueStore(backingStore);
    await store._loadAll();
    return store;
  }

  /// Gets a value by key, using cache first.
  Future<String?> getValue(String key) async {
    if (!_isLoaded) await _loadAll();
    if (_cache.containsKey(key)) return _cache[key];

    final value = await _backingStore.getValue(key);
    _cache[key] = value;
    return value;
  }

  /// Sets a value, updating both cache and backing store.
  Future<void> saveValue(String key, String value) async {
    _cache[key] = value;
    await _backingStore.saveValue(key, value);
  }

  /// Deletes a value from both cache and backing store.
  Future<void> deleteValue(String key) async {
    _cache.remove(key);
    await _backingStore.deleteValue(key);
  }

  /// Returns all keys in the store.
  Future<Set<String>> allKeys() async {
    if (!_isLoaded) await _loadAll();
    final storeKeys = await _backingStore.keySet();
    return {..._cache.keys, ...storeKeys};
  }

  /// Checks if a key exists.
  Future<bool> containsKey(String key) async {
    if (_cache.containsKey(key)) return true;
    return _backingStore.contains(key);
  }

  /// Returns the number of entries.
  Future<int> get count async {
    if (!_isLoaded) await _loadAll();
    return (await allKeys()).length;
  }

  /// Whether the store is empty.
  Future<bool> get isEmpty async => (await count) == 0;

  /// Clears all data from cache and backing store.
  Future<void> clear() async {
    _cache.clear();
    await _backingStore.deleteAll();
  }

  /// Invalidates the cache, forcing next read to go to backing store.
  void invalidateCache() {
    _cache.clear();
    _isLoaded = false;
  }

  /// Loads all entries from backing store into cache.
  Future<void> _loadAll() async {
    final keys = await _backingStore.keySet();
    for (final key in keys) {
      _cache[key] = await _backingStore.getValue(key);
    }
    _isLoaded = true;
  }
}

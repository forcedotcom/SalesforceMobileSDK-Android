import 'dart:async';
import '../../core/rest/rest_client.dart';
import '../../core/rest/rest_request.dart';
import '../../smartstore/store/smart_store.dart';
import '../../smartstore/store/index_spec.dart';
import '../../smartstore/store/query_spec.dart';
import '../../analytics/logger/salesforce_logger.dart';

/// Manages caching and retrieval of Salesforce object metadata.
///
/// Syncs object describe results (fields, relationships, picklists)
/// into SmartStore for offline access. Automatically refreshes stale data.
///
/// Mirrors Android MetadataSyncManager for feature parity.
class MetadataSyncManager {
  static const String _tag = 'MetadataSyncManager';
  static const String soupName = 'sfdcMetadata';
  static const Duration _defaultMaxAge = Duration(hours: 24);

  static final _logger = SalesforceLogger.getLogger('MetadataSyncManager');

  final SmartStore _store;
  final RestClient _restClient;
  final String _apiVersion;
  final Duration maxAge;

  MetadataSyncManager({
    required SmartStore store,
    required RestClient restClient,
    required String apiVersion,
    this.maxAge = _defaultMaxAge,
  })  : _store = store,
        _restClient = restClient,
        _apiVersion = apiVersion;

  /// Ensures the metadata soup exists.
  Future<void> ensureSoupExists() async {
    if (!await _store.hasSoup(soupName)) {
      await _store.registerSoup(soupName, [
        IndexSpec(path: 'objectType', type: SmartStoreType.json1),
        IndexSpec(path: 'timestamp', type: SmartStoreType.json1),
      ]);
    }
  }

  /// Fetches object metadata, using cache if available and fresh.
  Future<Map<String, dynamic>> fetchMetadata(String objectType) async {
    await ensureSoupExists();

    final cached = await _getCachedMetadata(objectType);
    if (cached != null && !_isStale(cached)) {
      return cached['metadata'] as Map<String, dynamic>;
    }

    return refreshMetadata(objectType);
  }

  /// Forces a refresh of object metadata from the server.
  Future<Map<String, dynamic>> refreshMetadata(String objectType) async {
    await ensureSoupExists();

    final request =
        RestRequest.getRequestForDescribe(_apiVersion, objectType);
    final response = await _restClient.sendAsync(request);
    response.throwIfError();

    final metadata = response.asJsonObject();
    await _cacheMetadata(objectType, metadata);

    _logger.i(_tag, 'Refreshed metadata for $objectType');
    return metadata;
  }

  /// Fetches metadata for multiple object types.
  Future<Map<String, Map<String, dynamic>>> fetchMetadataForObjects(
      List<String> objectTypes) async {
    final results = <String, Map<String, dynamic>>{};
    for (final objectType in objectTypes) {
      results[objectType] = await fetchMetadata(objectType);
    }
    return results;
  }

  /// Clears cached metadata for an object type.
  Future<void> clearMetadata(String objectType) async {
    if (!await _store.hasSoup(soupName)) return;

    final querySpec = QuerySpec.buildExactQuerySpec(
      soupName, 'objectType', objectType, pageSize: 1,
    );
    final results = await _store.query(querySpec, 0);
    for (final result in results) {
      final soupEntryId = result['_soupEntryId'];
      if (soupEntryId is int) {
        await _store.delete(soupName, soupEntryId);
      }
    }
  }

  /// Clears all cached metadata.
  Future<void> clearAllMetadata() async {
    if (await _store.hasSoup(soupName)) {
      await _store.deleteAll(soupName);
    }
  }

  Future<Map<String, dynamic>?> _getCachedMetadata(String objectType) async {
    final querySpec = QuerySpec.buildExactQuerySpec(
      soupName, 'objectType', objectType, pageSize: 1,
    );
    final results = await _store.query(querySpec, 0);
    if (results.isNotEmpty) {
      return results[0];
    }
    return null;
  }

  bool _isStale(Map<String, dynamic> cached) {
    final timestamp = cached['timestamp'] as String?;
    if (timestamp == null) return true;
    final cachedTime = DateTime.tryParse(timestamp);
    if (cachedTime == null) return true;
    return DateTime.now().difference(cachedTime) > maxAge;
  }

  Future<void> _cacheMetadata(
      String objectType, Map<String, dynamic> metadata) async {
    await clearMetadata(objectType);
    await _store.upsert(soupName, {
      'objectType': objectType,
      'metadata': metadata,
      'timestamp': DateTime.now().toUtc().toIso8601String(),
    });
  }
}

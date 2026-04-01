import 'dart:async';
import '../../core/rest/rest_client.dart';
import '../../core/rest/rest_request.dart';
import '../../smartstore/store/smart_store.dart';
import '../../smartstore/store/index_spec.dart';
import '../../smartstore/store/query_spec.dart';
import '../../analytics/logger/salesforce_logger.dart';

/// Manages caching and retrieval of Salesforce object layouts.
///
/// Syncs page layout definitions into SmartStore for offline access.
/// Supports form factor, layout type, mode, and record type filtering.
///
/// Mirrors Android LayoutSyncManager for feature parity.
class LayoutSyncManager {
  static const String _tag = 'LayoutSyncManager';
  static const String soupName = 'sfdcLayouts';
  static const Duration _defaultMaxAge = Duration(hours: 24);

  static final _logger = SalesforceLogger.getLogger('LayoutSyncManager');

  final SmartStore _store;
  final RestClient _restClient;
  final String _apiVersion;
  final Duration maxAge;

  LayoutSyncManager({
    required SmartStore store,
    required RestClient restClient,
    required String apiVersion,
    this.maxAge = _defaultMaxAge,
  })  : _store = store,
        _restClient = restClient,
        _apiVersion = apiVersion;

  /// Ensures the layout soup exists.
  Future<void> ensureSoupExists() async {
    if (!await _store.hasSoup(soupName)) {
      await _store.registerSoup(soupName, [
        IndexSpec(path: 'layoutKey', type: SmartStoreType.json1),
        IndexSpec(path: 'objectApiName', type: SmartStoreType.json1),
        IndexSpec(path: 'timestamp', type: SmartStoreType.json1),
      ]);
    }
  }

  /// Fetches a layout, using cache if available and fresh.
  Future<Map<String, dynamic>> fetchLayout({
    required String objectApiName,
    String? formFactor,
    String? layoutType,
    String? mode,
    String? recordTypeId,
  }) async {
    await ensureSoupExists();

    final key = _buildLayoutKey(
        objectApiName, formFactor, layoutType, mode, recordTypeId);

    final cached = await _getCachedLayout(key);
    if (cached != null && !_isStale(cached)) {
      return cached['layout'] as Map<String, dynamic>;
    }

    return refreshLayout(
      objectApiName: objectApiName,
      formFactor: formFactor,
      layoutType: layoutType,
      mode: mode,
      recordTypeId: recordTypeId,
    );
  }

  /// Forces a refresh of a layout from the server.
  Future<Map<String, dynamic>> refreshLayout({
    required String objectApiName,
    String? formFactor,
    String? layoutType,
    String? mode,
    String? recordTypeId,
  }) async {
    await ensureSoupExists();

    final request = RestRequest.getRequestForObjectLayout(
      _apiVersion,
      objectApiName,
      formFactor: formFactor,
      layoutType: layoutType,
      mode: mode,
      recordTypeId: recordTypeId,
    );

    final response = await _restClient.sendAsync(request);
    response.throwIfError();

    final layout = response.asJsonObject();
    final key = _buildLayoutKey(
        objectApiName, formFactor, layoutType, mode, recordTypeId);

    await _cacheLayout(key, objectApiName, layout);

    _logger.i(_tag, 'Refreshed layout for $objectApiName ($key)');
    return layout;
  }

  /// Clears cached layouts for an object.
  Future<void> clearLayoutsForObject(String objectApiName) async {
    if (!await _store.hasSoup(soupName)) return;

    final querySpec = QuerySpec.buildExactQuerySpec(
      soupName, 'objectApiName', objectApiName, pageSize: 100,
    );
    final results = await _store.query(querySpec, 0);
    for (final result in results) {
      final id = result['_soupEntryId'];
      if (id is int) await _store.delete(soupName, id);
    }
  }

  /// Clears all cached layouts.
  Future<void> clearAllLayouts() async {
    if (await _store.hasSoup(soupName)) {
      await _store.deleteAll(soupName);
    }
  }

  String _buildLayoutKey(String objectApiName, String? formFactor,
      String? layoutType, String? mode, String? recordTypeId) {
    final parts = [objectApiName];
    if (formFactor != null) parts.add('ff:$formFactor');
    if (layoutType != null) parts.add('lt:$layoutType');
    if (mode != null) parts.add('m:$mode');
    if (recordTypeId != null) parts.add('rt:$recordTypeId');
    return parts.join('_');
  }

  Future<Map<String, dynamic>?> _getCachedLayout(String key) async {
    final querySpec = QuerySpec.buildExactQuerySpec(
      soupName, 'layoutKey', key, pageSize: 1,
    );
    final results = await _store.query(querySpec, 0);
    if (results.isNotEmpty) return results[0];
    return null;
  }

  bool _isStale(Map<String, dynamic> cached) {
    final timestamp = cached['timestamp'] as String?;
    if (timestamp == null) return true;
    final cachedTime = DateTime.tryParse(timestamp);
    if (cachedTime == null) return true;
    return DateTime.now().difference(cachedTime) > maxAge;
  }

  Future<void> _cacheLayout(
      String key, String objectApiName, Map<String, dynamic> layout) async {
    // Remove existing
    final querySpec = QuerySpec.buildExactQuerySpec(
        soupName, 'layoutKey', key, pageSize: 1);
    final existing = await _store.query(querySpec, 0);
    for (final result in existing) {
      final id = result['_soupEntryId'];
      if (id is int) await _store.delete(soupName, id);
    }

    await _store.upsert(soupName, {
      'layoutKey': key,
      'objectApiName': objectApiName,
      'layout': layout,
      'timestamp': DateTime.now().toUtc().toIso8601String(),
    });
  }
}

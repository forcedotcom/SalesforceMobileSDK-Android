import 'dart:async';
import '../../core/rest/rest_client.dart';
import '../../core/rest/rest_request.dart';
import '../../smartstore/store/smart_store.dart';
import '../../smartstore/store/query_spec.dart';
import '../../analytics/logger/salesforce_logger.dart';

/// Cleans up "ghost" records from SmartStore — records that were previously
/// synced down but no longer match the sync's target query on the server.
///
/// Ghost records occur when records are deleted, change ownership, or no
/// longer match a SOQL WHERE clause. This task re-runs the sync's query
/// to get current IDs and removes any local records not in the result set.
///
/// Mirrors Android CleanSyncGhostsTask for feature parity.
class CleanSyncGhostsTask {
  static const String _tag = 'CleanSyncGhosts';
  static final _logger = SalesforceLogger.getLogger('CleanSyncGhosts');

  final SmartStore _store;
  final RestClient _restClient;

  CleanSyncGhostsTask({
    required SmartStore store,
    required RestClient restClient,
  })  : _store = store,
        _restClient = restClient;

  /// Runs ghost cleanup for a given soup and SOQL query.
  ///
  /// [soupName] - the soup to clean
  /// [query] - the original SOQL query used for sync down
  /// [apiVersion] - Salesforce API version
  ///
  /// Returns the number of ghost records removed.
  Future<int> cleanGhosts({
    required String soupName,
    required String query,
    required String apiVersion,
  }) async {
    _logger.i(_tag, 'Starting ghost cleanup for soup $soupName');

    // Get all local IDs from SmartStore
    final localIds = await _getLocalIds(soupName);
    if (localIds.isEmpty) return 0;

    // Get all current server IDs via the sync target's query
    final serverIds = await _getServerIds(query, apiVersion);

    // Find ghosts: local IDs not in server IDs
    final ghostIds = localIds.difference(serverIds);

    if (ghostIds.isEmpty) {
      _logger.i(_tag, 'No ghost records found');
      return 0;
    }

    // Remove ghost records from SmartStore
    int removedCount = 0;
    for (final ghostId in ghostIds) {
      // Don't remove locally created/modified records
      final record = await _findRecordById(soupName, ghostId);
      if (record != null) {
        final isLocallyCreated = record['__locally_created__'] == true;
        final isLocallyUpdated = record['__locally_updated__'] == true;
        final isLocallyDeleted = record['__locally_deleted__'] == true;

        if (!isLocallyCreated && !isLocallyUpdated && !isLocallyDeleted) {
          final soupEntryId = record['_soupEntryId'];
          if (soupEntryId is int) {
            await _store.delete(soupName, soupEntryId);
            removedCount++;
          }
        }
      }
    }

    _logger.i(_tag, 'Removed $removedCount ghost records');
    return removedCount;
  }

  Future<Set<String>> _getLocalIds(String soupName) async {
    final ids = <String>{};
    final querySpec = QuerySpec.buildSmartQuerySpec(
      'SELECT {$soupName:Id} FROM {$soupName}',
      pageSize: 10000,
      soupName: soupName,
    );
    final results = await _store.query(querySpec, 0);
    for (final row in results) {
      final id = row['Id'] as String?;
      if (id != null) ids.add(id);
    }
    return ids;
  }

  Future<Set<String>> _getServerIds(String query, String apiVersion) async {
    final ids = <String>{};

    // Modify query to only select Id for efficiency
    final idQuery = _buildIdOnlyQuery(query);
    String? nextUrl;
    var isFirst = true;

    while (isFirst || (nextUrl != null && nextUrl.isNotEmpty)) {
      isFirst = false;
      final request = (nextUrl != null && nextUrl.isNotEmpty)
          ? RestRequest(method: RestMethod.get, path: nextUrl)
          : RestRequest.getRequestForQuery(apiVersion, idQuery);

      final response = await _restClient.sendAsync(request);
      if (!response.isSuccess) break;

      final responseJson = response.asJsonObject();
      final records =
          responseJson['records'] as List<dynamic>? ?? [];
      for (final record in records) {
        if (record is Map<String, dynamic>) {
          final id = record['Id'] as String?;
          if (id != null) ids.add(id);
        }
      }

      final done = responseJson['done'] as bool? ?? true;
      nextUrl = done ? null : responseJson['nextRecordsUrl'] as String?;
    }

    return ids;
  }

  String _buildIdOnlyQuery(String originalQuery) {
    final upper = originalQuery.toUpperCase();
    final fromIdx = upper.indexOf('FROM');
    if (fromIdx < 0) return originalQuery;
    return 'SELECT Id ${originalQuery.substring(fromIdx)}';
  }

  Future<Map<String, dynamic>?> _findRecordById(
      String soupName, String id) async {
    final querySpec = QuerySpec.buildExactQuerySpec(
      soupName, 'Id', id, pageSize: 1,
    );
    final results = await _store.query(querySpec, 0);
    if (results.isNotEmpty) return results[0];
    return null;
  }
}

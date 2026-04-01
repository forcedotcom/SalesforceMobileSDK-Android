import '../../core/rest/rest_request.dart';
import '../manager/sync_manager.dart';
import 'sync_down_target.dart';

/// Sync-down target that downloads most recently used (MRU) records.
///
/// Fetches the MRU list from object metadata and then queries
/// for the full records using SOQL.
class MruSyncDownTarget extends SyncDownTarget {
  /// The fields to fetch for each record.
  final List<String> fieldlist;

  /// The SObject type to query.
  final String objectType;

  MruSyncDownTarget({
    required this.fieldlist,
    required this.objectType,
    String idFieldName = 'Id',
    String modificationDateFieldName = 'LastModifiedDate',
  }) : super(
          idFieldName: idFieldName,
          modificationDateFieldName: modificationDateFieldName,
        );

  @override
  Future<List<Map<String, dynamic>>?> startFetch(
      SyncManager syncManager, int maxTimeStamp) async {
    // Step 1: Get MRU IDs from metadata
    final metadataRequest = RestRequest.getRequestForMetadata(
        syncManager.apiVersion, objectType);
    final metadataResponse = await syncManager.sendSyncRequest(metadataRequest);
    if (!metadataResponse.isSuccess) return null;

    final metadataJson = metadataResponse.asJsonObject();
    final recentItems = metadataJson['recentItems'] as List<dynamic>? ?? [];
    final recentIds = recentItems
        .map((item) =>
            (item as Map<String, dynamic>)['Id']?.toString() ?? '')
        .where((id) => id.isNotEmpty)
        .toList();

    if (recentIds.isEmpty) {
      totalSize = 0;
      return [];
    }

    // Step 2: Build SOQL query to fetch requested fields
    final fields = fieldlist.join(',');
    final idsList = recentIds.map((id) => "'$id'").join(',');
    final soql =
        'SELECT $fields FROM $objectType WHERE $idFieldName IN ($idsList)';

    final request =
        RestRequest.getRequestForQuery(syncManager.apiVersion, soql);
    final response = await syncManager.sendSyncRequest(request);
    if (!response.isSuccess) return null;

    final json = response.asJsonObject();
    totalSize = json['totalSize'] ?? 0;
    final records = json['records'] as List<dynamic>?;
    return records?.cast<Map<String, dynamic>>();
  }

  @override
  Future<List<Map<String, dynamic>>?> continueFetch(
      SyncManager syncManager) async {
    return null; // MRU doesn't support pagination
  }

  @override
  Future<Set<String>> getRemoteIds(
      SyncManager syncManager, Set<String> localIds) async {
    final idsList = localIds.map((id) => "'$id'").join(',');
    final soql =
        'SELECT $idFieldName FROM $objectType WHERE $idFieldName IN ($idsList)';

    final request =
        RestRequest.getRequestForQuery(syncManager.apiVersion, soql);
    final response = await syncManager.sendSyncRequest(request);
    if (!response.isSuccess) return {};

    final json = response.asJsonObject();
    final records = json['records'] as List<dynamic>? ?? [];
    return records
        .map((r) => (r as Map<String, dynamic>)[idFieldName]?.toString() ?? '')
        .toSet()
      ..remove('');
  }

  @override
  Map<String, dynamic> toJson() => {
        ...super.toJson(),
        'type': 'MruSyncDownTarget',
        'fieldlist': fieldlist,
        'sobjectType': objectType,
      };

  factory MruSyncDownTarget.fromJson(Map<String, dynamic> json) {
    return MruSyncDownTarget(
      fieldlist:
          (json['fieldlist'] as List<dynamic>?)?.cast<String>() ?? [],
      objectType: json['sobjectType'] ?? json['objectType'] ?? '',
      idFieldName: json['idFieldName'] ?? 'Id',
      modificationDateFieldName:
          json['modificationDateFieldName'] ?? 'LastModifiedDate',
    );
  }
}

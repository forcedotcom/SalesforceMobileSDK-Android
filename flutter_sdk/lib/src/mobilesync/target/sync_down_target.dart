import '../../core/rest/rest_request.dart';
import '../../smartstore/store/smart_store.dart';
import '../../smartstore/store/query_spec.dart';
import '../manager/sync_manager.dart';

/// Query types for sync-down targets.
enum SyncDownQueryType {
  mru,
  sosl,
  soql,
  refresh,
  parentChildren,
  custom,
  metadata,
  layout,
  briefcase,
}

/// Abstract base class for sync-down targets.
///
/// Defines what records to download from the server and how to fetch them.
/// Subclasses implement the actual fetching logic for different query types.
abstract class SyncDownTarget {
  /// The ID field name on the server (defaults to 'Id').
  final String idFieldName;

  /// The modification date field name (defaults to 'LastModifiedDate').
  final String modificationDateFieldName;

  /// Total number of records (set after first fetch).
  int totalSize = 0;

  SyncDownTarget({
    this.idFieldName = 'Id',
    this.modificationDateFieldName = 'LastModifiedDate',
  });

  /// Starts fetching records from the server.
  /// Returns the first batch of records, or null if none.
  Future<List<Map<String, dynamic>>?> startFetch(
      SyncManager syncManager, int maxTimeStamp);

  /// Continues fetching the next page of records.
  /// Returns null when no more records.
  Future<List<Map<String, dynamic>>?> continueFetch(
      SyncManager syncManager);

  /// Gets the set of record IDs on the server.
  Future<Set<String>> getRemoteIds(
      SyncManager syncManager, Set<String> localIds);

  /// Gets the latest modification timestamp from records.
  int getLatestModificationTimeStamp(List<Map<String, dynamic>> records) {
    int maxStamp = 0;
    for (final record in records) {
      final dateStr = record[modificationDateFieldName];
      if (dateStr != null) {
        final ts = DateTime.tryParse(dateStr.toString())
                ?.millisecondsSinceEpoch ??
            0;
        if (ts > maxStamp) maxStamp = ts;
      }
    }
    return maxStamp;
  }

  /// Cleans ghost records (records deleted on server but still local).
  Future<int> cleanGhosts(
    SyncManager syncManager,
    String soupName,
    int syncId,
  ) async {
    final store = syncManager.smartStore;
    final localIds = await _getNonDirtyRecordIds(store, soupName, idFieldName);
    final remoteIds = await getRemoteIds(syncManager, localIds);

    // Find ghosts (local but not remote)
    final ghostIds = localIds.difference(remoteIds);
    int cleaned = 0;

    for (final ghostId in ghostIds) {
      final entryId = await store.lookupSoupEntryId(
          soupName, idFieldName, ghostId);
      if (entryId != null) {
        await store.delete(soupName, entryId);
        cleaned++;
      }
    }

    return cleaned;
  }

  /// Gets IDs of records that are not dirty (not locally modified).
  Future<Set<String>> getIdsToSkip(
    SyncManager syncManager,
    String soupName,
  ) async {
    return _getDirtyRecordIds(syncManager.smartStore, soupName);
  }

  /// Serializes to JSON.
  Map<String, dynamic> toJson() => {
        'idFieldName': idFieldName,
        'modificationDateFieldName': modificationDateFieldName,
        'type': runtimeType.toString(),
      };

  /// Deserializes from JSON and instantiates the correct subclass.
  static SyncDownTarget fromJson(Map<String, dynamic> json) {
    final type = json['type'] ?? 'SoqlSyncDownTarget';
    switch (type) {
      case 'SoqlSyncDownTarget':
        return SoqlSyncDownTarget.fromJson(json);
      case 'SoslSyncDownTarget':
        return SoslSyncDownTarget.fromJson(json);
      case 'RefreshSyncDownTarget':
        return RefreshSyncDownTarget.fromJson(json);
      default:
        return SoqlSyncDownTarget.fromJson(json);
    }
  }

  Future<Set<String>> _getNonDirtyRecordIds(
      SmartStore store, String soupName, String idField) async {
    final spec = QuerySpec.buildSmartQuerySpec(
      "SELECT {$soupName:$idField} FROM {$soupName} WHERE {$soupName:__local__} = 'false'",
      soupName: soupName,
      pageSize: 100000,
    );
    final results = await store.query(spec, 0);
    return results.map((r) => r[idField]?.toString() ?? '').toSet()
      ..remove('');
  }

  Future<Set<String>> _getDirtyRecordIds(
      SmartStore store, String soupName) async {
    final spec = QuerySpec.buildSmartQuerySpec(
      "SELECT {$soupName:$idFieldName} FROM {$soupName} WHERE {$soupName:__local__} = 'true'",
      soupName: soupName,
      pageSize: 100000,
    );
    final results = await store.query(spec, 0);
    return results.map((r) => r[idFieldName]?.toString() ?? '').toSet()
      ..remove('');
  }
}

/// Sync-down target that uses SOQL queries.
class SoqlSyncDownTarget extends SyncDownTarget {
  final String query;
  String? _nextRecordsUrl;

  SoqlSyncDownTarget({
    required this.query,
    String idFieldName = 'Id',
    String modificationDateFieldName = 'LastModifiedDate',
  }) : super(
          idFieldName: idFieldName,
          modificationDateFieldName: modificationDateFieldName,
        );

  @override
  Future<List<Map<String, dynamic>>?> startFetch(
      SyncManager syncManager, int maxTimeStamp) async {
    var soqlQuery = query;

    // Add timestamp filter for re-sync
    if (maxTimeStamp > 0) {
      final dateStr = DateTime.fromMillisecondsSinceEpoch(maxTimeStamp)
          .toUtc()
          .toIso8601String();
      if (soqlQuery.toLowerCase().contains('where')) {
        soqlQuery += " AND $modificationDateFieldName > $dateStr";
      } else {
        soqlQuery += " WHERE $modificationDateFieldName > $dateStr";
      }
    }

    final request =
        RestRequest.getRequestForQuery(syncManager.apiVersion, soqlQuery);
    final response = await syncManager.sendSyncRequest(request);

    if (!response.isSuccess) return null;

    final json = response.asJsonObject();
    totalSize = json['totalSize'] ?? 0;
    _nextRecordsUrl = json['nextRecordsUrl'];

    final records = json['records'] as List<dynamic>?;
    return records?.cast<Map<String, dynamic>>();
  }

  @override
  Future<List<Map<String, dynamic>>?> continueFetch(
      SyncManager syncManager) async {
    if (_nextRecordsUrl == null) return null;

    final request = RestRequest(
      method: RestMethod.get,
      path: _nextRecordsUrl!,
    );
    final response = await syncManager.sendSyncRequest(request);

    if (!response.isSuccess) return null;

    final json = response.asJsonObject();
    _nextRecordsUrl = json['nextRecordsUrl'];

    final records = json['records'] as List<dynamic>?;
    return records?.cast<Map<String, dynamic>>();
  }

  @override
  Future<Set<String>> getRemoteIds(
      SyncManager syncManager, Set<String> localIds) async {
    // Query just IDs from server
    final idQuery =
        "SELECT $idFieldName FROM (${query.replaceAll(RegExp(r'SELECT .* FROM', caseSensitive: false), 'SELECT $idFieldName FROM')})";
    final request = RestRequest.getRequestForQuery(
        syncManager.apiVersion, idQuery);
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
        'type': 'SoqlSyncDownTarget',
        'query': query,
      };

  factory SoqlSyncDownTarget.fromJson(Map<String, dynamic> json) {
    return SoqlSyncDownTarget(
      query: json['query'] ?? '',
      idFieldName: json['idFieldName'] ?? 'Id',
      modificationDateFieldName:
          json['modificationDateFieldName'] ?? 'LastModifiedDate',
    );
  }
}

/// Sync-down target that uses SOSL searches.
class SoslSyncDownTarget extends SyncDownTarget {
  final String query;

  SoslSyncDownTarget({
    required this.query,
    String idFieldName = 'Id',
    String modificationDateFieldName = 'LastModifiedDate',
  }) : super(
          idFieldName: idFieldName,
          modificationDateFieldName: modificationDateFieldName,
        );

  @override
  Future<List<Map<String, dynamic>>?> startFetch(
      SyncManager syncManager, int maxTimeStamp) async {
    final request =
        RestRequest.getRequestForSearch(syncManager.apiVersion, query);
    final response = await syncManager.sendSyncRequest(request);

    if (!response.isSuccess) return null;

    final records = response.asJsonArray();
    totalSize = records.length;
    return records.cast<Map<String, dynamic>>();
  }

  @override
  Future<List<Map<String, dynamic>>?> continueFetch(
      SyncManager syncManager) async {
    return null; // SOSL doesn't support pagination
  }

  @override
  Future<Set<String>> getRemoteIds(
      SyncManager syncManager, Set<String> localIds) async {
    final records = await startFetch(syncManager, 0);
    if (records == null) return {};
    return records
        .map((r) => r[idFieldName]?.toString() ?? '')
        .toSet()
      ..remove('');
  }

  @override
  Map<String, dynamic> toJson() => {
        ...super.toJson(),
        'type': 'SoslSyncDownTarget',
        'query': query,
      };

  factory SoslSyncDownTarget.fromJson(Map<String, dynamic> json) {
    return SoslSyncDownTarget(
      query: json['query'] ?? '',
      idFieldName: json['idFieldName'] ?? 'Id',
      modificationDateFieldName:
          json['modificationDateFieldName'] ?? 'LastModifiedDate',
    );
  }
}

/// Sync-down target that re-fetches records already in the soup.
class RefreshSyncDownTarget extends SyncDownTarget {
  final String objectType;
  final List<String> fieldlist;
  final String soupName;

  RefreshSyncDownTarget({
    required this.objectType,
    required this.fieldlist,
    required this.soupName,
    String idFieldName = 'Id',
    String modificationDateFieldName = 'LastModifiedDate',
  }) : super(
          idFieldName: idFieldName,
          modificationDateFieldName: modificationDateFieldName,
        );

  @override
  Future<List<Map<String, dynamic>>?> startFetch(
      SyncManager syncManager, int maxTimeStamp) async {
    // Get all local IDs
    final store = syncManager.smartStore;
    final spec = QuerySpec.buildAllQuerySpec(soupName, pageSize: 100000);
    final localRecords = await store.query(spec, 0);
    final ids = localRecords
        .map((r) => r[idFieldName]?.toString())
        .where((id) => id != null && id.isNotEmpty)
        .toList();

    if (ids.isEmpty) return [];

    // Fetch from server
    final query =
        "SELECT ${fieldlist.join(',')} FROM $objectType WHERE $idFieldName IN ('${ids.join("','")}')";
    final request =
        RestRequest.getRequestForQuery(syncManager.apiVersion, query);
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
    return null;
  }

  @override
  Future<Set<String>> getRemoteIds(
      SyncManager syncManager, Set<String> localIds) async {
    return localIds; // Refresh target only deals with known records
  }

  @override
  Map<String, dynamic> toJson() => {
        ...super.toJson(),
        'type': 'RefreshSyncDownTarget',
        'objectType': objectType,
        'fieldlist': fieldlist,
        'soupName': soupName,
      };

  factory RefreshSyncDownTarget.fromJson(Map<String, dynamic> json) {
    return RefreshSyncDownTarget(
      objectType: json['objectType'] ?? json['sobjectType'] ?? '',
      fieldlist: (json['fieldlist'] as List<dynamic>?)?.cast<String>() ?? [],
      soupName: json['soupName'] ?? '',
      idFieldName: json['idFieldName'] ?? 'Id',
      modificationDateFieldName:
          json['modificationDateFieldName'] ?? 'LastModifiedDate',
    );
  }
}

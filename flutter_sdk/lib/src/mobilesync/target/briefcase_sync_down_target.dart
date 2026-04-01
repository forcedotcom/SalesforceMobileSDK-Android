import 'dart:math';
import '../../core/rest/rest_request.dart';
import '../manager/sync_manager.dart';
import 'sync_down_target.dart';

/// Information about a briefcase object configuration.
class BriefcaseObjectInfo {
  final String sobjectType;
  final String soupName;
  final List<String> fieldlist;
  final String idFieldName;
  final String modificationDateFieldName;

  BriefcaseObjectInfo({
    required this.sobjectType,
    required this.soupName,
    required this.fieldlist,
    this.idFieldName = 'Id',
    this.modificationDateFieldName = 'LastModifiedDate',
  });

  Map<String, dynamic> toJson() => {
        'sobjectType': sobjectType,
        'soupName': soupName,
        'fieldlist': fieldlist,
        'idFieldName': idFieldName,
        'modificationDateFieldName': modificationDateFieldName,
      };

  factory BriefcaseObjectInfo.fromJson(Map<String, dynamic> json) {
    return BriefcaseObjectInfo(
      sobjectType: json['sobjectType'] ?? '',
      soupName: json['soupName'] ?? '',
      fieldlist:
          (json['fieldlist'] as List<dynamic>?)?.cast<String>() ?? [],
      idFieldName: json['idFieldName'] ?? 'Id',
      modificationDateFieldName:
          json['modificationDateFieldName'] ?? 'LastModifiedDate',
    );
  }
}

/// Sync-down target that uses the Briefcase (priming records) API.
///
/// Downloads records based on briefcase deployment configurations,
/// using the priming records API to get IDs and then fetching
/// full records using sObject collection retrieve.
class BriefcaseSyncDownTarget extends SyncDownTarget {
  static const int maxCountIdsPerRetrieve = 200;
  static const String infosKey = 'infos';
  static const String countIdsPerRetrieveKey = 'countIdsPerRetrieve';

  final List<BriefcaseObjectInfo> infos;
  final Map<String, BriefcaseObjectInfo> _infosMap;
  final int countIdsPerRetrieve;

  // Per-sync-run state
  int _maxTimeStamp = 0;
  String? _relayToken;
  final List<_TypedId> _fetchedTypedIds = [];
  int _currentSliceIndex = 0;

  BriefcaseSyncDownTarget({
    required this.infos,
    int? countIdsPerRetrieve,
  })  : countIdsPerRetrieve =
            min(countIdsPerRetrieve ?? maxCountIdsPerRetrieve, maxCountIdsPerRetrieve),
        _infosMap = {for (final info in infos) info.sobjectType: info},
        super();

  @override
  Future<List<Map<String, dynamic>>?> startFetch(
      SyncManager syncManager, int maxTimeStamp) async {
    _maxTimeStamp = maxTimeStamp;
    _relayToken = null;
    totalSize = -1;
    return _getIdsAndFetchFromServer(syncManager);
  }

  @override
  Future<List<Map<String, dynamic>>?> continueFetch(
      SyncManager syncManager) async {
    if (_relayToken == null && _fetchedTypedIds.isEmpty) return null;
    return _getIdsAndFetchFromServer(syncManager);
  }

  @override
  Future<Set<String>> getRemoteIds(
      SyncManager syncManager, Set<String> localIds) async {
    // Not used - cleanGhosts is overridden
    return {};
  }

  Future<List<Map<String, dynamic>>?> _getIdsAndFetchFromServer(
      SyncManager syncManager) async {
    final records = <Map<String, dynamic>>[];

    // Run priming record request if needed
    if (_fetchedTypedIds.isEmpty) {
      _currentSliceIndex = 0;
      _relayToken = await _getIdsFromBriefcases(
          syncManager, _fetchedTypedIds, _relayToken, _maxTimeStamp);
    }

    // Get IDs for current slice
    final sliceEnd =
        min(_fetchedTypedIds.length, (_currentSliceIndex + 1) * countIdsPerRetrieve);
    final sliceStart = _currentSliceIndex * countIdsPerRetrieve;
    if (sliceStart >= _fetchedTypedIds.length) {
      _fetchedTypedIds.clear();
      _currentSliceIndex = 0;
      return records;
    }

    final slice = _fetchedTypedIds.sublist(sliceStart, sliceEnd);
    final objectTypeToIds = <String, List<String>>{};
    for (final typedId in slice) {
      objectTypeToIds.putIfAbsent(typedId.sobjectType, () => []);
      objectTypeToIds[typedId.sobjectType]!.add(typedId.id);
    }

    // Fetch records using collection retrieve
    for (final entry in objectTypeToIds.entries) {
      final info = _infosMap[entry.key];
      if (info == null || entry.value.isEmpty) continue;

      final fieldlistToFetch = List<String>.from(info.fieldlist);
      for (final field in [info.idFieldName, info.modificationDateFieldName]) {
        if (!fieldlistToFetch.contains(field)) fieldlistToFetch.add(field);
      }

      final request = RestRequest.getRequestForCollectionRetrieve(
        syncManager.apiVersion,
        info.sobjectType,
        entry.value,
        fieldlistToFetch,
      );
      final response = await syncManager.sendSyncRequest(request);
      if (response.isSuccess) {
        final fetched = response.asJsonArray();
        for (final r in fetched) {
          if (r is Map<String, dynamic>) records.add(r);
        }
      }
    }

    if (totalSize == -1) totalSize = _fetchedTypedIds.length;

    // Advance slice
    _currentSliceIndex++;
    final totalSlices =
        (_fetchedTypedIds.length / countIdsPerRetrieve).ceil();
    if (_currentSliceIndex >= totalSlices) {
      _fetchedTypedIds.clear();
      _currentSliceIndex = 0;
    }

    return records;
  }

  Future<String?> _getIdsFromBriefcases(
    SyncManager syncManager,
    List<_TypedId> typedIds,
    String? relayToken,
    int maxTimeStamp,
  ) async {
    final request = RestRequest.getRequestForPrimingRecords(
      syncManager.apiVersion,
      relayToken: relayToken,
      changedAfterTime: maxTimeStamp > 0 ? maxTimeStamp : null,
    );
    final response = await syncManager.sendSyncRequest(request);
    if (!response.isSuccess) return null;

    final json = response.asJsonObject();
    final primingRecords =
        json['primingRecords'] as Map<String, dynamic>? ?? {};

    for (final info in infos) {
      final objectRecords =
          primingRecords[info.sobjectType] as Map<String, dynamic>?;
      if (objectRecords == null) continue;
      for (final entry in objectRecords.values) {
        if (entry is List) {
          for (final record in entry) {
            if (record is Map<String, dynamic>) {
              final id = record['id']?.toString();
              if (id != null) typedIds.add(_TypedId(info.sobjectType, id));
            }
          }
        }
      }
    }

    return json['relayToken'] as String?;
  }

  @override
  Map<String, dynamic> toJson() => {
        ...super.toJson(),
        'type': 'BriefcaseSyncDownTarget',
        infosKey: infos.map((i) => i.toJson()).toList(),
        countIdsPerRetrieveKey: countIdsPerRetrieve,
      };

  factory BriefcaseSyncDownTarget.fromJson(Map<String, dynamic> json) {
    final infosJson = json[infosKey] as List<dynamic>? ?? [];
    return BriefcaseSyncDownTarget(
      infos: infosJson
          .map((i) =>
              BriefcaseObjectInfo.fromJson(i as Map<String, dynamic>))
          .toList(),
      countIdsPerRetrieve: json[countIdsPerRetrieveKey] as int?,
    );
  }
}

class _TypedId {
  final String sobjectType;
  final String id;
  _TypedId(this.sobjectType, this.id);
}

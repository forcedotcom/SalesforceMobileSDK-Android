import '../../core/rest/rest_request.dart';
import '../manager/sync_manager.dart';
import 'sync_up_target.dart';

/// Sync up target that uses the SObject Collection API to send multiple
/// create/update/delete operations in a single request.
///
/// More efficient than [BatchSyncUpTarget] for homogeneous operations.
/// Supports up to 200 records per collection call.
///
/// Mirrors Android CollectionSyncUpTarget for feature parity.
class CollectionSyncUpTarget extends SyncUpTarget {
  /// Maximum records per collection API call.
  static const int maxCollectionSize = 200;

  CollectionSyncUpTarget({
    super.createFieldlist,
    super.updateFieldlist,
    super.idFieldName,
    super.modificationDateFieldName,
    super.externalIdFieldName,
  });

  factory CollectionSyncUpTarget.fromJson(Map<String, dynamic> json) {
    return CollectionSyncUpTarget(
      createFieldlist: (json['createFieldlist'] as List<dynamic>?)
          ?.cast<String>(),
      updateFieldlist: (json['updateFieldlist'] as List<dynamic>?)
          ?.cast<String>(),
      idFieldName: json['idFieldName'] as String? ?? 'Id',
      modificationDateFieldName:
          json['modificationDateFieldName'] as String? ?? 'LastModifiedDate',
      externalIdFieldName: json['externalIdFieldName'] as String?,
    );
  }

  @override
  Map<String, dynamic> toJson() => {
        ...super.toJson(),
        'type': 'CollectionSyncUpTarget',
      };

  /// Sends creates using the collection create API.
  Future<List<dynamic>> syncUpCreates(
    SyncManager syncManager,
    List<Map<String, dynamic>> records,
    List<String>? fieldlist,
    String objectType, {
    bool allOrNone = false,
  }) async {
    if (records.isEmpty) return [];

    final collectionRecords = records.map((record) {
      final fields = _buildFieldsMap(
          record, fieldlist ?? createFieldlist ?? []);
      return <String, dynamic>{
        'attributes': {'type': objectType},
        ...fields,
      };
    }).toList();

    final request = RestRequest.getRequestForCollectionCreate(
      syncManager.apiVersion,
      allOrNone,
      collectionRecords,
    );

    final response = await syncManager.sendSyncRequest(request);
    return response.asJsonArray();
  }

  /// Sends updates using the collection update API.
  Future<List<dynamic>> syncUpUpdates(
    SyncManager syncManager,
    List<Map<String, dynamic>> records,
    List<String>? fieldlist,
    String objectType, {
    bool allOrNone = false,
  }) async {
    if (records.isEmpty) return [];

    final collectionRecords = records.map((record) {
      final fields = _buildFieldsMap(
          record, fieldlist ?? updateFieldlist ?? []);
      return <String, dynamic>{
        'attributes': {'type': objectType},
        'Id': record[idFieldName],
        ...fields,
      };
    }).toList();

    final request = RestRequest.getRequestForCollectionUpdate(
      syncManager.apiVersion,
      allOrNone,
      collectionRecords,
    );

    final response = await syncManager.sendSyncRequest(request);
    return response.asJsonArray();
  }

  /// Sends deletes using the collection delete API.
  Future<List<dynamic>> syncUpDeletes(
    SyncManager syncManager,
    List<String> objectIds, {
    bool allOrNone = false,
  }) async {
    if (objectIds.isEmpty) return [];

    final request = RestRequest.getRequestForCollectionDelete(
      syncManager.apiVersion,
      allOrNone,
      objectIds,
    );

    final response = await syncManager.sendSyncRequest(request);
    return response.asJsonArray();
  }

  Map<String, Object> _buildFieldsMap(
      Map<String, dynamic> record, List<String> fieldlist) {
    final fields = <String, Object>{};
    for (final field in fieldlist) {
      if (field != idFieldName &&
          !field.startsWith('__') &&
          record.containsKey(field) &&
          record[field] != null) {
        fields[field] = record[field] as Object;
      }
    }
    return fields;
  }
}

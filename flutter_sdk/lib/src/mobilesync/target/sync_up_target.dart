import 'dart:convert';
import '../../core/rest/rest_client.dart';
import '../../core/rest/rest_request.dart';
import '../../core/rest/rest_response.dart';
import '../../smartstore/store/smart_store.dart';
import '../../smartstore/store/query_spec.dart';
import '../manager/sync_manager.dart';

/// Metadata about a record's modification date.
class RecordModDate {
  final String? id;
  final DateTime? timestamp;
  final bool isDeleted;

  RecordModDate({this.id, this.timestamp, this.isDeleted = false});
}

/// Abstract base class for sync-up targets.
///
/// Defines what records to upload to the server and how to send them.
/// Handles create, update, delete operations and conflict detection.
abstract class SyncUpTarget {
  /// Fields to include when creating new records.
  final List<String>? createFieldlist;

  /// Fields to include when updating existing records.
  final List<String>? updateFieldlist;

  /// The ID field name (defaults to 'Id').
  final String idFieldName;

  /// The modification date field name.
  final String modificationDateFieldName;

  /// External ID field name for upsert operations.
  final String? externalIdFieldName;

  SyncUpTarget({
    this.createFieldlist,
    this.updateFieldlist,
    this.idFieldName = 'Id',
    this.modificationDateFieldName = 'LastModifiedDate',
    this.externalIdFieldName,
  });

  /// Creates a record on the server. Returns the new record ID.
  Future<String?> createOnServer(
    SyncManager syncManager,
    Map<String, dynamic> record,
    List<String>? fieldlist,
  ) async {
    final objectType = _getObjectType(record);
    final fields = _buildFieldsMap(record, fieldlist ?? createFieldlist ?? []);

    final request = RestRequest.getRequestForCreate(
      syncManager.apiVersion,
      objectType,
      fields,
    );
    final response = await syncManager.sendSyncRequest(request);

    if (response.isSuccess) {
      final json = response.asJsonObject();
      return json['id'] as String?;
    }
    return null;
  }

  /// Updates a record on the server. Returns HTTP status code.
  Future<int> updateOnServer(
    SyncManager syncManager,
    Map<String, dynamic> record,
    List<String>? fieldlist,
  ) async {
    final objectType = _getObjectType(record);
    final objectId = record[idFieldName]?.toString();
    if (objectId == null) return 400;

    final fields =
        _buildFieldsMap(record, fieldlist ?? updateFieldlist ?? []);

    final request = RestRequest.getRequestForUpdate(
      syncManager.apiVersion,
      objectType,
      objectId,
      fields,
    );
    final response = await syncManager.sendSyncRequest(request);
    return response.statusCode;
  }

  /// Deletes a record on the server. Returns HTTP status code.
  Future<int> deleteOnServer(
    SyncManager syncManager,
    Map<String, dynamic> record,
  ) async {
    final objectType = _getObjectType(record);
    final objectId = record[idFieldName]?.toString();
    if (objectId == null) return 400;

    final request = RestRequest.getRequestForDelete(
      syncManager.apiVersion,
      objectType,
      objectId,
    );
    final response = await syncManager.sendSyncRequest(request);
    return response.statusCode;
  }

  /// Checks if the local record is newer than the server version.
  Future<bool> isNewerThanServer(
    SyncManager syncManager,
    Map<String, dynamic> record,
  ) async {
    final remoteModDate = await fetchLastModifiedDate(syncManager, record);
    if (remoteModDate == null) return true; // Deleted on server
    if (remoteModDate.isDeleted) return true;

    final localModStr = record[modificationDateFieldName]?.toString();
    if (localModStr == null) return true;
    final localMod = DateTime.tryParse(localModStr);
    if (localMod == null) return true;

    return remoteModDate.timestamp == null ||
        localMod.isAfter(remoteModDate.timestamp!);
  }

  /// Fetches the last modified date of a record from the server.
  Future<RecordModDate?> fetchLastModifiedDate(
    SyncManager syncManager,
    Map<String, dynamic> record,
  ) async {
    final objectType = _getObjectType(record);
    final objectId = record[idFieldName]?.toString();
    if (objectId == null) return null;

    final query =
        "SELECT $modificationDateFieldName, IsDeleted FROM $objectType WHERE $idFieldName = '$objectId'";
    final request = RestRequest.getRequestForQuery(
        syncManager.apiVersion, query);
    final response = await syncManager.sendSyncRequest(request);

    if (!response.isSuccess) return null;

    final json = response.asJsonObject();
    final records = json['records'] as List<dynamic>?;
    if (records == null || records.isEmpty) {
      return RecordModDate(id: objectId, isDeleted: true);
    }

    final serverRecord = records.first as Map<String, dynamic>;
    return RecordModDate(
      id: objectId,
      timestamp: DateTime.tryParse(
          serverRecord[modificationDateFieldName]?.toString() ?? ''),
      isDeleted: serverRecord['IsDeleted'] == true,
    );
  }

  /// Gets IDs of records that need to be synced up (dirty records).
  Future<Set<String>> getIdsOfRecordsToSyncUp(
    SyncManager syncManager,
    String soupName,
  ) async {
    final store = syncManager.smartStore;
    final spec = QuerySpec.buildSmartQuerySpec(
      "SELECT {$soupName:$idFieldName} FROM {$soupName} WHERE {$soupName:__local__} = 'true'",
      soupName: soupName,
      pageSize: 100000,
    );
    final results = await store.query(spec, 0);
    return results
        .map((r) => r[idFieldName]?.toString() ?? '')
        .toSet()
      ..remove('');
  }

  /// Saves a record with error information to local store.
  Future<void> saveRecordToLocalStoreWithLastError(
    SyncManager syncManager,
    String soupName,
    Map<String, dynamic> record, {
    String? errorMessage,
  }) async {
    if (errorMessage != null) {
      record['__lastError__'] = errorMessage;
    }
    await syncManager.smartStore.upsert(soupName, record);
  }

  /// Serializes to JSON.
  Map<String, dynamic> toJson() => {
        'type': runtimeType.toString(),
        'createFieldlist': createFieldlist,
        'updateFieldlist': updateFieldlist,
        'idFieldName': idFieldName,
        'modificationDateFieldName': modificationDateFieldName,
        'externalIdFieldName': externalIdFieldName,
      };

  /// Deserializes from JSON.
  static SyncUpTarget fromJson(Map<String, dynamic> json) {
    return DefaultSyncUpTarget.fromJson(json);
  }

  String _getObjectType(Map<String, dynamic> record) {
    final attributes = record['attributes'] as Map<String, dynamic>?;
    return attributes?['type'] ?? record['__objectType__'] ?? '';
  }

  Map<String, Object> _buildFieldsMap(
      Map<String, dynamic> record, List<String> fieldlist) {
    final fields = <String, Object>{};
    for (final field in fieldlist) {
      if (record.containsKey(field) && record[field] != null) {
        fields[field] = record[field];
      }
    }
    // Remove system fields
    fields.remove(idFieldName);
    fields.remove(modificationDateFieldName);
    fields.remove('attributes');
    fields.remove('__local__');
    fields.remove('__locally_created__');
    fields.remove('__locally_updated__');
    fields.remove('__locally_deleted__');
    fields.remove('__lastError__');
    fields.remove(soupEntryId);
    fields.remove(soupLastModifiedDate);
    return fields;
  }
}

/// Default sync-up target implementation.
class DefaultSyncUpTarget extends SyncUpTarget {
  DefaultSyncUpTarget({
    super.createFieldlist,
    super.updateFieldlist,
    super.idFieldName,
    super.modificationDateFieldName,
    super.externalIdFieldName,
  });

  @override
  Map<String, dynamic> toJson() => {
        ...super.toJson(),
        'type': 'DefaultSyncUpTarget',
      };

  factory DefaultSyncUpTarget.fromJson(Map<String, dynamic> json) {
    return DefaultSyncUpTarget(
      createFieldlist:
          (json['createFieldlist'] as List<dynamic>?)?.cast<String>(),
      updateFieldlist:
          (json['updateFieldlist'] as List<dynamic>?)?.cast<String>(),
      idFieldName: json['idFieldName'] ?? 'Id',
      modificationDateFieldName:
          json['modificationDateFieldName'] ?? 'LastModifiedDate',
      externalIdFieldName: json['externalIdFieldName'],
    );
  }
}

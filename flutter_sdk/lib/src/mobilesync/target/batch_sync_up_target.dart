import '../../core/rest/rest_request.dart';
import '../manager/sync_manager.dart';
import 'sync_up_target.dart';

/// Sync up target that uses the Salesforce Batch API to send multiple
/// create/update/delete requests in a single round-trip.
///
/// Groups dirty records into batches of up to [maxBatchSize] and sends
/// them via the composite/batch endpoint, reducing API call count.
///
/// Mirrors Android BatchSyncUpTarget for feature parity.
class BatchSyncUpTarget extends SyncUpTarget {
  /// Maximum records per batch API call (composite batch limit).
  static const int maxBatchSize = 25;

  BatchSyncUpTarget({
    super.createFieldlist,
    super.updateFieldlist,
    super.idFieldName,
    super.modificationDateFieldName,
    super.externalIdFieldName,
  });

  factory BatchSyncUpTarget.fromJson(Map<String, dynamic> json) {
    return BatchSyncUpTarget(
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
        'type': 'BatchSyncUpTarget',
      };

  /// Sends create/update/delete in a batch request.
  Future<Map<String, dynamic>> syncUpBatch(
    SyncManager syncManager,
    List<Map<String, dynamic>> records,
    List<String>? fieldlist,
  ) async {
    if (records.isEmpty) return {};

    final batchRequests = <RestRequest>[];
    final apiVersion = syncManager.apiVersion;

    for (final record in records) {
      final isDeleted = record['__locally_deleted__'] == true;
      final isCreated = record['__locally_created__'] == true;
      final objectType = _getObjectType(record);
      final objectId = record[idFieldName]?.toString();

      if (isDeleted && objectId != null) {
        batchRequests.add(
            RestRequest.getRequestForDelete(apiVersion, objectType, objectId));
      } else if (isCreated) {
        final fields = _buildFieldsMap(
            record, fieldlist ?? createFieldlist ?? []);
        batchRequests.add(
            RestRequest.getRequestForCreate(apiVersion, objectType, fields));
      } else if (objectId != null) {
        final fields = _buildFieldsMap(
            record, fieldlist ?? updateFieldlist ?? []);
        batchRequests.add(RestRequest.getRequestForUpdate(
            apiVersion, objectType, objectId, fields));
      }
    }

    if (batchRequests.isEmpty) return {};

    final batchRequest = RestRequest.getBatchRequest(
      apiVersion,
      false,
      batchRequests,
    );

    final response = await syncManager.sendSyncRequest(batchRequest);
    return response.asJsonObject();
  }

  String _getObjectType(Map<String, dynamic> record) {
    final attributes = record['attributes'] as Map<String, dynamic>?;
    return attributes?['type']?.toString() ?? '';
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

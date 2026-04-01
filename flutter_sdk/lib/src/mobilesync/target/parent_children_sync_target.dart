import '../../core/rest/rest_request.dart';
import '../../smartstore/store/smart_store.dart';
import '../../smartstore/store/query_spec.dart';
import '../manager/sync_manager.dart';
import 'sync_down_target.dart';
import 'sync_up_target.dart';

/// Relationship type between parent and children records.
enum RelationshipType {
  masterDetail,
  lookup,
}

/// Information about the parent object in a parent-children sync.
class ParentInfo {
  final String sobjectType;
  final String soupName;
  final String idFieldName;
  final String modificationDateFieldName;
  final String? externalIdFieldName;

  ParentInfo({
    required this.sobjectType,
    required this.soupName,
    this.idFieldName = 'Id',
    this.modificationDateFieldName = 'LastModifiedDate',
    this.externalIdFieldName,
  });

  Map<String, dynamic> toJson() => {
        'sobjectType': sobjectType,
        'soupName': soupName,
        'idFieldName': idFieldName,
        'modificationDateFieldName': modificationDateFieldName,
        if (externalIdFieldName != null)
          'externalIdFieldName': externalIdFieldName,
      };

  factory ParentInfo.fromJson(Map<String, dynamic> json) {
    return ParentInfo(
      sobjectType: json['sobjectType'] ?? '',
      soupName: json['soupName'] ?? '',
      idFieldName: json['idFieldName'] ?? 'Id',
      modificationDateFieldName:
          json['modificationDateFieldName'] ?? 'LastModifiedDate',
      externalIdFieldName: json['externalIdFieldName'],
    );
  }
}

/// Information about the children object in a parent-children sync.
class ChildrenInfo {
  final String sobjectType;
  final String sobjectTypePlural;
  final String soupName;
  final String parentIdFieldName;
  final String idFieldName;
  final String modificationDateFieldName;
  final String? externalIdFieldName;

  ChildrenInfo({
    required this.sobjectType,
    required this.sobjectTypePlural,
    required this.soupName,
    required this.parentIdFieldName,
    this.idFieldName = 'Id',
    this.modificationDateFieldName = 'LastModifiedDate',
    this.externalIdFieldName,
  });

  Map<String, dynamic> toJson() => {
        'sobjectType': sobjectType,
        'sobjectTypePlural': sobjectTypePlural,
        'soupName': soupName,
        'parentIdFieldName': parentIdFieldName,
        'idFieldName': idFieldName,
        'modificationDateFieldName': modificationDateFieldName,
        if (externalIdFieldName != null)
          'externalIdFieldName': externalIdFieldName,
      };

  factory ChildrenInfo.fromJson(Map<String, dynamic> json) {
    return ChildrenInfo(
      sobjectType: json['sobjectType'] ?? '',
      sobjectTypePlural: json['sobjectTypePlural'] ?? '',
      soupName: json['soupName'] ?? '',
      parentIdFieldName: json['parentIdFieldName'] ?? '',
      idFieldName: json['idFieldName'] ?? 'Id',
      modificationDateFieldName:
          json['modificationDateFieldName'] ?? 'LastModifiedDate',
      externalIdFieldName: json['externalIdFieldName'],
    );
  }
}

/// Sync-down target for downloading parent records with their children.
///
/// Uses nested SOQL queries to fetch parent records along with
/// their related children in a single API call.
class ParentChildrenSyncDownTarget extends SyncDownTarget {
  final ParentInfo parentInfo;
  final List<String> parentFieldlist;
  final String? parentSoqlFilter;
  final ChildrenInfo childrenInfo;
  final List<String> childrenFieldlist;
  final RelationshipType relationshipType;

  String? _nextRecordsUrl;

  ParentChildrenSyncDownTarget({
    required this.parentInfo,
    required this.parentFieldlist,
    this.parentSoqlFilter,
    required this.childrenInfo,
    required this.childrenFieldlist,
    required this.relationshipType,
  }) : super(
          idFieldName: parentInfo.idFieldName,
          modificationDateFieldName: parentInfo.modificationDateFieldName,
        );

  @override
  Future<List<Map<String, dynamic>>?> startFetch(
      SyncManager syncManager, int maxTimeStamp) async {
    final query = _buildQuery(maxTimeStamp);
    final request =
        RestRequest.getRequestForQuery(syncManager.apiVersion, query);
    final response = await syncManager.sendSyncRequest(request);
    if (!response.isSuccess) return null;

    final json = response.asJsonObject();
    totalSize = json['totalSize'] ?? 0;
    _nextRecordsUrl = json['nextRecordsUrl'];

    return _processRecords(json);
  }

  @override
  Future<List<Map<String, dynamic>>?> continueFetch(
      SyncManager syncManager) async {
    if (_nextRecordsUrl == null) return null;

    final request = RestRequest(method: RestMethod.get, path: _nextRecordsUrl!);
    final response = await syncManager.sendSyncRequest(request);
    if (!response.isSuccess) return null;

    final json = response.asJsonObject();
    _nextRecordsUrl = json['nextRecordsUrl'];
    return _processRecords(json);
  }

  @override
  Future<Set<String>> getRemoteIds(
      SyncManager syncManager, Set<String> localIds) async {
    final soql =
        'SELECT $idFieldName FROM ${parentInfo.sobjectType}'
        '${parentSoqlFilter != null && parentSoqlFilter!.isNotEmpty ? ' WHERE $parentSoqlFilter' : ''}';

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

  List<Map<String, dynamic>>? _processRecords(Map<String, dynamic> json) {
    final records = json['records'] as List<dynamic>?;
    if (records == null) return null;

    // Flatten nested children response
    return records.map((r) {
      final record = Map<String, dynamic>.from(r as Map<String, dynamic>);
      final childrenData = record[childrenInfo.sobjectTypePlural];
      if (childrenData is Map<String, dynamic>) {
        record[childrenInfo.sobjectTypePlural] =
            childrenData['records'] ?? [];
      } else if (childrenData == null) {
        record[childrenInfo.sobjectTypePlural] = [];
      }
      return record;
    }).toList();
  }

  String _buildQuery(int maxTimeStamp) {
    final childrenWhere = StringBuffer();
    final parentWhere = StringBuffer();

    if (maxTimeStamp > 0) {
      final dateStr = DateTime.fromMillisecondsSinceEpoch(maxTimeStamp)
          .toUtc()
          .toIso8601String();
      childrenWhere.write(
          '${childrenInfo.modificationDateFieldName} > $dateStr');
      parentWhere.write('$modificationDateFieldName > $dateStr');
      if (parentSoqlFilter != null && parentSoqlFilter!.isNotEmpty) {
        parentWhere.write(' AND ');
      }
    }
    if (parentSoqlFilter != null && parentSoqlFilter!.isNotEmpty) {
      parentWhere.write(parentSoqlFilter);
    }

    // Build nested children query
    final childFields = List<String>.from(childrenFieldlist);
    if (!childFields.contains(childrenInfo.idFieldName)) {
      childFields.add(childrenInfo.idFieldName);
    }
    if (!childFields.contains(childrenInfo.modificationDateFieldName)) {
      childFields.add(childrenInfo.modificationDateFieldName);
    }

    var nestedQuery =
        'SELECT ${childFields.join(',')} FROM ${childrenInfo.sobjectTypePlural}';
    if (childrenWhere.isNotEmpty) {
      nestedQuery += ' WHERE $childrenWhere';
    }

    // Build parent query
    final parentFields = List<String>.from(parentFieldlist);
    if (!parentFields.contains(idFieldName)) parentFields.add(idFieldName);
    if (!parentFields.contains(modificationDateFieldName)) {
      parentFields.add(modificationDateFieldName);
    }
    parentFields.add('($nestedQuery)');

    var query =
        'SELECT ${parentFields.join(',')} FROM ${parentInfo.sobjectType}';
    if (parentWhere.isNotEmpty) {
      query += ' WHERE $parentWhere';
    }
    query += ' ORDER BY ${parentInfo.modificationDateFieldName}';

    return query;
  }

  @override
  int getLatestModificationTimeStamp(List<Map<String, dynamic>> records) {
    int maxStamp = super.getLatestModificationTimeStamp(records);

    for (final record in records) {
      final children =
          record[childrenInfo.sobjectTypePlural] as List<dynamic>? ?? [];
      for (final child in children) {
        if (child is Map<String, dynamic>) {
          final dateStr =
              child[childrenInfo.modificationDateFieldName]?.toString();
          if (dateStr != null) {
            final ts = DateTime.tryParse(dateStr)?.millisecondsSinceEpoch ?? 0;
            if (ts > maxStamp) maxStamp = ts;
          }
        }
      }
    }
    return maxStamp;
  }

  @override
  Map<String, dynamic> toJson() => {
        ...super.toJson(),
        'type': 'ParentChildrenSyncDownTarget',
        'parent': parentInfo.toJson(),
        'parentFieldlist': parentFieldlist,
        'parentSoqlFilter': parentSoqlFilter,
        'children': childrenInfo.toJson(),
        'childrenFieldlist': childrenFieldlist,
        'relationshipType': relationshipType.name,
      };

  factory ParentChildrenSyncDownTarget.fromJson(Map<String, dynamic> json) {
    return ParentChildrenSyncDownTarget(
      parentInfo:
          ParentInfo.fromJson(json['parent'] as Map<String, dynamic>),
      parentFieldlist:
          (json['parentFieldlist'] as List<dynamic>?)?.cast<String>() ?? [],
      parentSoqlFilter: json['parentSoqlFilter'],
      childrenInfo:
          ChildrenInfo.fromJson(json['children'] as Map<String, dynamic>),
      childrenFieldlist:
          (json['childrenFieldlist'] as List<dynamic>?)?.cast<String>() ?? [],
      relationshipType: RelationshipType.values.firstWhere(
        (e) => e.name == json['relationshipType'],
        orElse: () => RelationshipType.lookup,
      ),
    );
  }
}

/// Interface for advanced sync-up targets where records are not
/// simply created/updated/deleted individually.
///
/// With advanced sync-up targets, the sync manager calls [syncUpRecords]
/// instead of individual create/update/delete methods.
abstract class AdvancedSyncUpTarget {
  /// Maximum number of records that can be passed to [syncUpRecords] at once.
  int get maxBatchSize;

  /// Syncs up a batch of records.
  Future<void> syncUpRecords(
    SyncManager syncManager,
    List<Map<String, dynamic>> records,
    List<String>? fieldlist,
    String mergeMode,
    String syncSoupName,
  );
}

/// Sync-up target for uploading parent records with their children.
///
/// Uses the Composite API to send parent and children operations
/// in a single request, maintaining referential integrity.
class ParentChildrenSyncUpTarget extends SyncUpTarget
    implements AdvancedSyncUpTarget {
  final ParentInfo parentInfo;
  final ChildrenInfo childrenInfo;
  final List<String> childrenCreateFieldlist;
  final List<String> childrenUpdateFieldlist;
  final RelationshipType relationshipType;

  ParentChildrenSyncUpTarget({
    required this.parentInfo,
    List<String>? parentCreateFieldlist,
    List<String>? parentUpdateFieldlist,
    required this.childrenInfo,
    required this.childrenCreateFieldlist,
    required this.childrenUpdateFieldlist,
    required this.relationshipType,
  }) : super(
          createFieldlist: parentCreateFieldlist,
          updateFieldlist: parentUpdateFieldlist,
          idFieldName: parentInfo.idFieldName,
          modificationDateFieldName: parentInfo.modificationDateFieldName,
          externalIdFieldName: parentInfo.externalIdFieldName,
        );

  @override
  int get maxBatchSize => 1;

  @override
  Future<void> syncUpRecords(
    SyncManager syncManager,
    List<Map<String, dynamic>> records,
    List<String>? fieldlist,
    String mergeMode,
    String syncSoupName,
  ) async {
    if (records.length > 1) {
      throw StateError(
          'ParentChildrenSyncUpTarget can handle only 1 record at a time');
    }
    if (records.isNotEmpty) {
      await _syncUpRecord(syncManager, records[0], fieldlist, mergeMode);
    }
  }

  Future<void> _syncUpRecord(
    SyncManager syncManager,
    Map<String, dynamic> record,
    List<String>? fieldlist,
    String mergeMode,
  ) async {
    final isCreate = record['__locally_created__'] == true;
    final isDelete = record['__locally_deleted__'] == true;

    // Get children from local store
    final children = await _getChildren(syncManager, record);

    // Build composite request parts
    final subrequests = <Map<String, RestRequest>>{};
    final parentId = record[idFieldName]?.toString() ?? '';

    // Build parent request
    if (!isDelete) {
      final parentRequest =
          _buildParentRequest(record, fieldlist, isCreate);
      if (parentRequest != null) {
        subrequests[parentId] = parentRequest;
      }
    }

    // Build children requests
    for (final child in children) {
      final childId =
          child[childrenInfo.idFieldName]?.toString() ?? '';
      if (isCreate) {
        child['__local__'] = true;
        child['__locally_updated__'] = true;
      }
      final childRequest = _buildChildRequest(child, isCreate, parentId);
      if (childRequest != null) {
        subrequests[childId] = childRequest;
      }
    }

    // Parent delete goes last
    if (isDelete) {
      final parentRequest =
          _buildParentRequest(record, fieldlist, false);
      if (parentRequest != null) {
        subrequests[parentId] = parentRequest;
      }
    }

    if (subrequests.isNotEmpty) {
      final compositeRequest = RestRequest.getCompositeRequest(
        syncManager.apiVersion,
        false,
        subrequests,
      );
      await syncManager.sendSyncRequest(compositeRequest);
    }
  }

  RestRequest? _buildParentRequest(
    Map<String, dynamic> record,
    List<String>? fieldlist,
    bool isCreate,
  ) {
    final isDirty = record['__local__'] == true;
    if (!isDirty) return null;

    final isDelete = record['__locally_deleted__'] == true;
    final objectType = parentInfo.sobjectType;
    final objectId = record[idFieldName]?.toString();

    if (isDelete) {
      if (record['__locally_created__'] == true) return null;
      return RestRequest.getRequestForDelete(
          'v62.0', objectType, objectId ?? '');
    }

    final fields = <String, Object>{};
    final fieldNames =
        isCreate ? (createFieldlist ?? fieldlist ?? []) : (updateFieldlist ?? fieldlist ?? []);
    for (final field in fieldNames) {
      if (record.containsKey(field) && record[field] != null) {
        fields[field] = record[field];
      }
    }
    fields.remove(idFieldName);
    fields.remove(modificationDateFieldName);

    if (isCreate) {
      return RestRequest.getRequestForCreate('v62.0', objectType, fields);
    } else {
      return RestRequest.getRequestForUpdate(
          'v62.0', objectType, objectId ?? '', fields);
    }
  }

  RestRequest? _buildChildRequest(
    Map<String, dynamic> record,
    bool parentIsCreate,
    String parentId,
  ) {
    final isDirty = record['__local__'] == true;
    if (!isDirty && !parentIsCreate) return null;

    final isDelete = record['__locally_deleted__'] == true;
    final isCreate = record['__locally_created__'] == true;
    final objectType = childrenInfo.sobjectType;
    final objectId = record[childrenInfo.idFieldName]?.toString();

    if (isDelete) {
      if (isCreate) return null;
      return RestRequest.getRequestForDelete(
          'v62.0', objectType, objectId ?? '');
    }

    final fields = <String, Object>{};
    final fieldNames =
        isCreate ? childrenCreateFieldlist : childrenUpdateFieldlist;
    for (final field in fieldNames) {
      if (record.containsKey(field) && record[field] != null) {
        fields[field] = record[field];
      }
    }
    fields.remove(childrenInfo.idFieldName);
    fields.remove(childrenInfo.modificationDateFieldName);

    // Set parent reference
    if (parentIsCreate) {
      fields[childrenInfo.parentIdFieldName] = '@{$parentId.id}';
    } else {
      fields[childrenInfo.parentIdFieldName] = parentId;
    }

    if (isCreate) {
      return RestRequest.getRequestForCreate('v62.0', objectType, fields);
    } else {
      return RestRequest.getRequestForUpdate(
          'v62.0', objectType, objectId ?? '', fields);
    }
  }

  Future<List<Map<String, dynamic>>> _getChildren(
    SyncManager syncManager,
    Map<String, dynamic> parentRecord,
  ) async {
    final store = syncManager.smartStore;
    final parentId = parentRecord[idFieldName]?.toString() ?? '';
    final spec = QuerySpec.buildSmartQuerySpec(
      "SELECT {${childrenInfo.soupName}:_soup} FROM {${childrenInfo.soupName}} "
      "WHERE {${childrenInfo.soupName}:${childrenInfo.parentIdFieldName}} = '$parentId'",
      soupName: childrenInfo.soupName,
      pageSize: 10000,
    );
    final results = await store.query(spec, 0);
    return results.cast<Map<String, dynamic>>();
  }

  @override
  Map<String, dynamic> toJson() => {
        ...super.toJson(),
        'type': 'ParentChildrenSyncUpTarget',
        'parent': parentInfo.toJson(),
        'children': childrenInfo.toJson(),
        'childrenCreateFieldlist': childrenCreateFieldlist,
        'childrenUpdateFieldlist': childrenUpdateFieldlist,
        'relationshipType': relationshipType.name,
      };

  factory ParentChildrenSyncUpTarget.fromJson(Map<String, dynamic> json) {
    return ParentChildrenSyncUpTarget(
      parentInfo:
          ParentInfo.fromJson(json['parent'] as Map<String, dynamic>),
      parentCreateFieldlist:
          (json['createFieldlist'] as List<dynamic>?)?.cast<String>(),
      parentUpdateFieldlist:
          (json['updateFieldlist'] as List<dynamic>?)?.cast<String>(),
      childrenInfo:
          ChildrenInfo.fromJson(json['children'] as Map<String, dynamic>),
      childrenCreateFieldlist:
          (json['childrenCreateFieldlist'] as List<dynamic>?)
                  ?.cast<String>() ??
              [],
      childrenUpdateFieldlist:
          (json['childrenUpdateFieldlist'] as List<dynamic>?)
                  ?.cast<String>() ??
              [],
      relationshipType: RelationshipType.values.firstWhere(
        (e) => e.name == json['relationshipType'],
        orElse: () => RelationshipType.lookup,
      ),
    );
  }
}

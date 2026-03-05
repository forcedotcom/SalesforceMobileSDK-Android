import 'dart:convert';

/// HTTP methods supported by REST requests.
enum RestMethod { get, post, put, delete, head, patch }

/// REST endpoint types.
enum RestEndpoint { login, instance }

/// Represents a REST API request to Salesforce.
///
/// Provides factory methods for all standard Salesforce REST API operations
/// including CRUD, SOQL queries, SOSL searches, composite requests,
/// and collection operations.
class RestRequest {
  /// The HTTP method.
  final RestMethod method;

  /// The endpoint type.
  final RestEndpoint endpoint;

  /// The request path (relative to base URL).
  final String path;

  /// The request body as a JSON-encodable map.
  final Map<String, dynamic>? requestBody;

  /// Additional HTTP headers.
  final Map<String, String>? additionalHttpHeaders;

  /// Raw body string for non-JSON requests.
  final String? rawBody;

  static const String servicesData = '/services/data';

  RestRequest({
    required this.method,
    required this.path,
    this.endpoint = RestEndpoint.instance,
    this.requestBody,
    this.additionalHttpHeaders,
    this.rawBody,
  });

  /// Returns the body as a JSON string.
  String? get bodyAsString {
    if (rawBody != null) return rawBody;
    if (requestBody != null) return jsonEncode(requestBody);
    return null;
  }

  // ===== Factory Methods =====

  /// Request for API versions.
  static RestRequest getRequestForVersions() {
    return RestRequest(method: RestMethod.get, path: '$servicesData/');
  }

  /// Request for available resources.
  static RestRequest getRequestForResources(String apiVersion) {
    return RestRequest(
        method: RestMethod.get, path: '$servicesData/v$apiVersion/');
  }

  /// Request for global describe (list of all objects).
  static RestRequest getRequestForDescribeGlobal(String apiVersion) {
    return RestRequest(
        method: RestMethod.get,
        path: '$servicesData/v$apiVersion/sobjects/');
  }

  /// Request for object metadata.
  static RestRequest getRequestForMetadata(
      String apiVersion, String objectType) {
    return RestRequest(
        method: RestMethod.get,
        path: '$servicesData/v$apiVersion/sobjects/$objectType/');
  }

  /// Request for object describe (detailed metadata).
  static RestRequest getRequestForDescribe(
      String apiVersion, String objectType) {
    return RestRequest(
        method: RestMethod.get,
        path: '$servicesData/v$apiVersion/sobjects/$objectType/describe/');
  }

  /// Request to create a new record.
  static RestRequest getRequestForCreate(
    String apiVersion,
    String objectType,
    Map<String, Object> fields,
  ) {
    return RestRequest(
      method: RestMethod.post,
      path: '$servicesData/v$apiVersion/sobjects/$objectType/',
      requestBody: fields,
    );
  }

  /// Request to retrieve a record by ID.
  static RestRequest getRequestForRetrieve(
    String apiVersion,
    String objectType,
    String objectId,
    List<String> fieldList,
  ) {
    final fields = fieldList.join(',');
    return RestRequest(
      method: RestMethod.get,
      path:
          '$servicesData/v$apiVersion/sobjects/$objectType/$objectId?fields=$fields',
    );
  }

  /// Request to update an existing record.
  static RestRequest getRequestForUpdate(
    String apiVersion,
    String objectType,
    String objectId,
    Map<String, Object> fields, {
    DateTime? ifUnmodifiedSinceDate,
  }) {
    final headers = <String, String>{};
    if (ifUnmodifiedSinceDate != null) {
      headers['If-Unmodified-Since'] =
          ifUnmodifiedSinceDate.toUtc().toIso8601String();
    }
    return RestRequest(
      method: RestMethod.patch,
      path:
          '$servicesData/v$apiVersion/sobjects/$objectType/$objectId',
      requestBody: fields,
      additionalHttpHeaders: headers.isNotEmpty ? headers : null,
    );
  }

  /// Request to upsert a record using an external ID.
  static RestRequest getRequestForUpsert(
    String apiVersion,
    String objectType,
    String externalIdField,
    String externalId,
    Map<String, Object> fields,
  ) {
    return RestRequest(
      method: RestMethod.patch,
      path:
          '$servicesData/v$apiVersion/sobjects/$objectType/$externalIdField/$externalId',
      requestBody: fields,
    );
  }

  /// Request to delete a record.
  static RestRequest getRequestForDelete(
    String apiVersion,
    String objectType,
    String objectId,
  ) {
    return RestRequest(
      method: RestMethod.delete,
      path:
          '$servicesData/v$apiVersion/sobjects/$objectType/$objectId',
    );
  }

  /// Request to execute a SOQL query.
  static RestRequest getRequestForQuery(
    String apiVersion,
    String query, {
    int? batchSize,
  }) {
    final encodedQuery = Uri.encodeComponent(query);
    final headers = <String, String>{};
    if (batchSize != null) {
      headers['Sforce-Query-Options'] = 'batchSize=$batchSize';
    }
    return RestRequest(
      method: RestMethod.get,
      path: '$servicesData/v$apiVersion/query/?q=$encodedQuery',
      additionalHttpHeaders: headers.isNotEmpty ? headers : null,
    );
  }

  /// Request to execute a SOQL queryAll (includes deleted/archived).
  static RestRequest getRequestForQueryAll(String apiVersion, String query) {
    final encodedQuery = Uri.encodeComponent(query);
    return RestRequest(
      method: RestMethod.get,
      path: '$servicesData/v$apiVersion/queryAll/?q=$encodedQuery',
    );
  }

  /// Request to execute a SOSL search.
  static RestRequest getRequestForSearch(String apiVersion, String query) {
    final encodedQuery = Uri.encodeComponent(query);
    return RestRequest(
      method: RestMethod.get,
      path: '$servicesData/v$apiVersion/search/?q=$encodedQuery',
    );
  }

  /// Request for search scope and order.
  static RestRequest getRequestForSearchScopeAndOrder(String apiVersion) {
    return RestRequest(
      method: RestMethod.get,
      path: '$servicesData/v$apiVersion/search/scopeOrder',
    );
  }

  /// Request for search result layout.
  static RestRequest getRequestForSearchResultLayout(
      String apiVersion, List<String> objectList) {
    final objects = objectList.join(',');
    return RestRequest(
      method: RestMethod.get,
      path: '$servicesData/v$apiVersion/search/layout/?q=$objects',
    );
  }

  /// Request for object layout.
  static RestRequest getRequestForObjectLayout(
    String apiVersion,
    String objectApiName, {
    String? formFactor,
    String? layoutType,
    String? mode,
    String? recordTypeId,
  }) {
    var path =
        '$servicesData/v$apiVersion/ui-api/layout/$objectApiName?';
    final params = <String>[];
    if (formFactor != null) params.add('formFactor=$formFactor');
    if (layoutType != null) params.add('layoutType=$layoutType');
    if (mode != null) params.add('mode=$mode');
    if (recordTypeId != null) params.add('recordTypeId=$recordTypeId');
    path += params.join('&');
    return RestRequest(method: RestMethod.get, path: path);
  }

  /// Request for user info.
  static RestRequest getRequestForUserInfo() {
    return RestRequest(
      method: RestMethod.get,
      path: '/services/oauth2/userinfo',
    );
  }

  /// Request for API limits.
  static RestRequest getRequestForLimits(String apiVersion) {
    return RestRequest(
      method: RestMethod.get,
      path: '$servicesData/v$apiVersion/limits/',
    );
  }

  // ===== Collection Operations =====

  /// Request for creating multiple records in a single call.
  static RestRequest getRequestForCollectionCreate(
    String apiVersion,
    bool allOrNone,
    List<Map<String, dynamic>> records,
  ) {
    return RestRequest(
      method: RestMethod.post,
      path: '$servicesData/v$apiVersion/composite/sobjects',
      requestBody: {
        'allOrNone': allOrNone,
        'records': records,
      },
    );
  }

  /// Request for retrieving multiple records by IDs.
  static RestRequest getRequestForCollectionRetrieve(
    String apiVersion,
    String objectType,
    List<String> objectIds,
    List<String> fieldList,
  ) {
    return RestRequest(
      method: RestMethod.post,
      path: '$servicesData/v$apiVersion/composite/sobjects/$objectType',
      requestBody: {
        'ids': objectIds,
        'fields': fieldList,
      },
    );
  }

  /// Request for updating multiple records.
  static RestRequest getRequestForCollectionUpdate(
    String apiVersion,
    bool allOrNone,
    List<Map<String, dynamic>> records,
  ) {
    return RestRequest(
      method: RestMethod.patch,
      path: '$servicesData/v$apiVersion/composite/sobjects',
      requestBody: {
        'allOrNone': allOrNone,
        'records': records,
      },
    );
  }

  /// Request for upserting multiple records.
  static RestRequest getRequestForCollectionUpsert(
    String apiVersion,
    bool allOrNone,
    String objectType,
    String externalIdField,
    List<Map<String, dynamic>> records,
  ) {
    return RestRequest(
      method: RestMethod.patch,
      path:
          '$servicesData/v$apiVersion/composite/sobjects/$objectType/$externalIdField',
      requestBody: {
        'allOrNone': allOrNone,
        'records': records,
      },
    );
  }

  /// Request for deleting multiple records.
  static RestRequest getRequestForCollectionDelete(
    String apiVersion,
    bool allOrNone,
    List<String> objectIds,
  ) {
    final ids = objectIds.join(',');
    return RestRequest(
      method: RestMethod.delete,
      path:
          '$servicesData/v$apiVersion/composite/sobjects?ids=$ids&allOrNone=$allOrNone',
    );
  }

  // ===== Composite / Batch =====

  /// Request for composite API (multiple related requests).
  static RestRequest getCompositeRequest(
    String apiVersion,
    bool allOrNone,
    Map<String, RestRequest> refIdToRequests,
  ) {
    final compositeRequests = refIdToRequests.entries.map((entry) {
      final req = entry.value;
      final subrequest = <String, dynamic>{
        'method': req.method.name.toUpperCase(),
        'url': req.path,
        'referenceId': entry.key,
      };
      if (req.requestBody != null) subrequest['body'] = req.requestBody;
      if (req.additionalHttpHeaders != null) {
        subrequest['httpHeaders'] = req.additionalHttpHeaders;
      }
      return subrequest;
    }).toList();

    return RestRequest(
      method: RestMethod.post,
      path: '$servicesData/v$apiVersion/composite/',
      requestBody: {
        'allOrNone': allOrNone,
        'compositeRequest': compositeRequests,
      },
    );
  }

  /// Request for batch API (multiple independent requests).
  static RestRequest getBatchRequest(
    String apiVersion,
    bool haltOnError,
    List<RestRequest> requests,
  ) {
    final batchRequests = requests.map((req) {
      final subrequest = <String, dynamic>{
        'method': req.method.name.toUpperCase(),
        'url': req.path,
      };
      if (req.requestBody != null) {
        subrequest['richInput'] = req.requestBody;
      }
      return subrequest;
    }).toList();

    return RestRequest(
      method: RestMethod.post,
      path: '$servicesData/v$apiVersion/composite/batch/',
      requestBody: {
        'haltOnError': haltOnError,
        'batchRequests': batchRequests,
      },
    );
  }

  /// Request for SObject tree (create records with relationships).
  static RestRequest getRequestForSObjectTree(
    String apiVersion,
    String objectType,
    List<Map<String, dynamic>> objectTrees,
  ) {
    return RestRequest(
      method: RestMethod.post,
      path: '$servicesData/v$apiVersion/composite/tree/$objectType/',
      requestBody: {'records': objectTrees},
    );
  }

  // ===== Notifications =====

  /// Request for notification status.
  static RestRequest getRequestForNotificationsStatus(String apiVersion) {
    return RestRequest(
      method: RestMethod.get,
      path:
          '$servicesData/v$apiVersion/connect/notifications/status',
    );
  }

  /// Request for a specific notification.
  static RestRequest getRequestForNotification(
      String apiVersion, String notificationId) {
    return RestRequest(
      method: RestMethod.get,
      path:
          '$servicesData/v$apiVersion/connect/notifications/$notificationId',
    );
  }

  /// Request to update a notification.
  static RestRequest getRequestForNotificationUpdate(
    String apiVersion,
    String notificationId, {
    bool? read,
    bool? seen,
  }) {
    final body = <String, dynamic>{};
    if (read != null) body['read'] = read;
    if (seen != null) body['seen'] = seen;
    return RestRequest(
      method: RestMethod.patch,
      path:
          '$servicesData/v$apiVersion/connect/notifications/$notificationId',
      requestBody: body,
    );
  }

  /// Request for listing notifications.
  static RestRequest getRequestForNotifications(
    String apiVersion, {
    int? size,
    DateTime? before,
    DateTime? after,
  }) {
    final params = <String>[];
    if (size != null) params.add('size=$size');
    if (before != null) {
      params.add('before=${before.toUtc().toIso8601String()}');
    }
    if (after != null) {
      params.add('after=${after.toUtc().toIso8601String()}');
    }
    final queryStr = params.isNotEmpty ? '?${params.join('&')}' : '';
    return RestRequest(
      method: RestMethod.get,
      path:
          '$servicesData/v$apiVersion/connect/notifications$queryStr',
    );
  }

  /// A cheap/minimal request for health checks.
  static RestRequest getCheapRequest(String apiVersion) {
    return getRequestForLimits(apiVersion);
  }

  @override
  String toString() =>
      'RestRequest(${method.name.toUpperCase()} $path)';
}

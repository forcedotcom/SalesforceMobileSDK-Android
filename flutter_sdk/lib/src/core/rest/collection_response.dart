/// Error in a collection sub-response.
class CollectionErrorResponse {
  final String? statusCode;
  final String? message;
  final List<String> fields;

  CollectionErrorResponse({
    this.statusCode,
    this.message,
    this.fields = const [],
  });

  factory CollectionErrorResponse.fromJson(Map<String, dynamic> json) {
    return CollectionErrorResponse(
      statusCode: json['statusCode'] as String?,
      message: json['message'] as String?,
      fields: (json['fields'] as List<dynamic>?)?.cast<String>() ?? [],
    );
  }

  Map<String, dynamic> toJson() => {
        'statusCode': statusCode,
        'message': message,
        'fields': fields,
      };

  @override
  String toString() => '$statusCode: $message';
}

/// Individual result from a collection create/update/upsert/delete operation.
class CollectionSubResponse {
  final String? id;
  final bool success;
  final List<CollectionErrorResponse> errors;

  CollectionSubResponse({
    this.id,
    required this.success,
    this.errors = const [],
  });

  factory CollectionSubResponse.fromJson(Map<String, dynamic> json) {
    return CollectionSubResponse(
      id: json['id'] as String?,
      success: json['success'] as bool? ?? false,
      errors: (json['errors'] as List<dynamic>?)
              ?.map((e) =>
                  CollectionErrorResponse.fromJson(e as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'success': success,
        'errors': errors.map((e) => e.toJson()).toList(),
      };
}

/// Response from SObject collection create/update/upsert/delete operations.
///
/// Mirrors Android CollectionResponse for feature parity.
class CollectionResponse {
  final List<CollectionSubResponse> subResponses;

  CollectionResponse({required this.subResponses});

  factory CollectionResponse.fromJson(List<dynamic> jsonArray) {
    return CollectionResponse(
      subResponses: jsonArray
          .map((e) =>
              CollectionSubResponse.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  /// Whether all operations succeeded.
  bool get allSuccess => subResponses.every((r) => r.success);

  /// Whether any operation had errors.
  bool get hasErrors => subResponses.any((r) => !r.success);

  /// Returns only the failed sub-responses.
  List<CollectionSubResponse> get failures =>
      subResponses.where((r) => !r.success).toList();
}

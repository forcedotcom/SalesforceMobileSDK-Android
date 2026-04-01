/// Represents a response from the Salesforce Composite API.
///
/// Parses the composite response JSON into individual sub-responses,
/// each with its own HTTP status, headers, and body.
class CompositeResponse {
  static const String _compositeResponse = 'compositeResponse';

  /// The list of sub-responses from the composite request.
  final List<CompositeSubResponse> subResponses;

  CompositeResponse(Map<String, dynamic> responseJson)
      : subResponses = _parseSubResponses(responseJson);

  static List<CompositeSubResponse> _parseSubResponses(
      Map<String, dynamic> json) {
    final results = json[_compositeResponse] as List<dynamic>? ?? [];
    return results
        .map((r) => CompositeSubResponse(r as Map<String, dynamic>))
        .toList();
  }

  /// Finds a sub-response by its reference ID.
  CompositeSubResponse? getByReferenceId(String referenceId) {
    for (final sub in subResponses) {
      if (sub.referenceId == referenceId) return sub;
    }
    return null;
  }
}

/// Represents a single sub-response within a composite API response.
class CompositeSubResponse {
  static const String _httpHeaders = 'httpHeaders';
  static const String _httpStatusCode = 'httpStatusCode';
  static const String _referenceId = 'referenceId';
  static const String _body = 'body';

  /// The HTTP headers from this sub-response.
  final Map<String, String> httpHeaders;

  /// The HTTP status code of this sub-response.
  final int httpStatusCode;

  /// The reference ID that was assigned to the corresponding sub-request.
  final String referenceId;

  /// The raw JSON of this sub-response.
  final Map<String, dynamic> json;

  CompositeSubResponse(Map<String, dynamic> subResponseJson)
      : json = subResponseJson,
        httpHeaders = _parseHeaders(subResponseJson),
        httpStatusCode = subResponseJson[_httpStatusCode] as int? ?? 0,
        referenceId = subResponseJson[_referenceId] as String? ?? '';

  /// Returns the body as a JSON object (map).
  Map<String, dynamic>? get bodyAsJsonObject {
    final body = json[_body];
    if (body is Map<String, dynamic>) return body;
    return null;
  }

  /// Returns the body as a JSON array (list).
  List<dynamic>? get bodyAsJsonArray {
    final body = json[_body];
    if (body is List<dynamic>) return body;
    return null;
  }

  /// Whether this sub-response indicates success (2xx status).
  bool get isSuccess => httpStatusCode >= 200 && httpStatusCode < 300;

  static Map<String, String> _parseHeaders(Map<String, dynamic> json) {
    final headers = json[_httpHeaders];
    if (headers is Map) {
      return headers.map((k, v) => MapEntry(k.toString(), v.toString()));
    }
    return {};
  }

  @override
  String toString() => 'CompositeSubResponse($referenceId: $httpStatusCode)';
}

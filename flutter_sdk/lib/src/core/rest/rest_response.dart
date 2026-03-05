import 'dart:convert';
import 'package:http/http.dart' as http;

/// Represents a response from a Salesforce REST API call.
///
/// Wraps the HTTP response and provides convenient methods for
/// parsing the response body as JSON, string, or bytes.
class RestResponse {
  /// The underlying HTTP response.
  final http.Response rawResponse;

  /// Cached parsed JSON.
  Map<String, dynamic>? _jsonCache;
  List<dynamic>? _jsonArrayCache;

  RestResponse(this.rawResponse);

  /// All response headers.
  Map<String, String> get headers => rawResponse.headers;

  /// The HTTP status code.
  int get statusCode => rawResponse.statusCode;

  /// Whether the response indicates success (2xx).
  bool get isSuccess => statusCode >= 200 && statusCode < 300;

  /// Static version of success check.
  static bool isSuccessStatus(int statusCode) =>
      statusCode >= 200 && statusCode < 300;

  /// Content type of the response.
  String? get contentType => rawResponse.headers['content-type'];

  /// Response body as string.
  String get asString => rawResponse.body;

  /// Response body as bytes.
  List<int> get asBytes => rawResponse.bodyBytes;

  /// Response body as a JSON object (Map).
  Map<String, dynamic> asJsonObject() {
    _jsonCache ??= jsonDecode(rawResponse.body) as Map<String, dynamic>;
    return _jsonCache!;
  }

  /// Response body as a JSON array (List).
  List<dynamic> asJsonArray() {
    _jsonArrayCache ??= jsonDecode(rawResponse.body) as List<dynamic>;
    return _jsonArrayCache!;
  }

  /// Returns the total number of records for query responses.
  int? get totalSize {
    try {
      return asJsonObject()['totalSize'] as int?;
    } catch (_) {
      return null;
    }
  }

  /// Returns the records array for query responses.
  List<dynamic>? get records {
    try {
      return asJsonObject()['records'] as List<dynamic>?;
    } catch (_) {
      return null;
    }
  }

  /// Returns the next records URL for paginated queries.
  String? get nextRecordsUrl {
    try {
      return asJsonObject()['nextRecordsUrl'] as String?;
    } catch (_) {
      return null;
    }
  }

  /// Whether there are more records to fetch.
  bool get isDone {
    try {
      return asJsonObject()['done'] as bool? ?? true;
    } catch (_) {
      return true;
    }
  }

  @override
  String toString() => 'RestResponse(status=$statusCode, body=${rawResponse.body.length > 200 ? '${rawResponse.body.substring(0, 200)}...' : rawResponse.body})';
}

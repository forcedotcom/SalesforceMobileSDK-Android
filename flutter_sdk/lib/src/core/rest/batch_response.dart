/// Response from a batch REST API request.
///
/// Mirrors Android BatchResponse for feature parity.
class BatchResponse {
  /// Whether any result in the batch contains an error.
  final bool hasErrors;

  /// Individual results from the batch, one per sub-request.
  final List<Map<String, dynamic>> results;

  BatchResponse({
    required this.hasErrors,
    required this.results,
  });

  factory BatchResponse.fromJson(Map<String, dynamic> json) {
    return BatchResponse(
      hasErrors: json['hasErrors'] as bool? ?? false,
      results: (json['results'] as List<dynamic>?)
              ?.cast<Map<String, dynamic>>() ??
          [],
    );
  }

  /// The number of results.
  int get length => results.length;

  /// Gets the status code for a specific result.
  int? statusCodeAt(int index) => results[index]['statusCode'] as int?;

  /// Gets the result body for a specific result.
  dynamic resultAt(int index) => results[index]['result'];

  /// Whether a specific result succeeded (2xx status).
  bool isSuccessAt(int index) {
    final status = statusCodeAt(index);
    return status != null && status >= 200 && status < 300;
  }
}

import 'dart:convert';
import 'rest_response.dart';

/// Known Salesforce API error codes.
enum SalesforceErrorCode {
  malformedQuery,
  invalidSessionId,
  invalidField,
  invalidType,
  entityIsDeleted,
  fieldCustomValidationException,
  insufficientAccess,
  requestLimitExceeded,
  apiDisabledForOrg,
  duplicateValue,
  requiredFieldMissing,
  stringTooLong,
  unknownError,
}

/// A single error from a Salesforce API response.
class SalesforceApiError {
  /// The raw error code string from the API.
  final String errorCode;

  /// Human-readable error message.
  final String message;

  /// Fields associated with this error (if any).
  final List<String> fields;

  const SalesforceApiError({
    required this.errorCode,
    required this.message,
    this.fields = const [],
  });

  /// Parses a single error from JSON.
  factory SalesforceApiError.fromJson(Map<String, dynamic> json) {
    return SalesforceApiError(
      errorCode: json['errorCode'] ?? json['error'] ?? 'UNKNOWN_ERROR',
      message: json['message'] ?? json['error_description'] ?? '',
      fields: (json['fields'] as List<dynamic>?)?.cast<String>() ?? [],
    );
  }

  /// The parsed error code enum.
  SalesforceErrorCode get parsedCode => _parseCode(errorCode);

  /// Whether this is a session/auth error.
  bool get isSessionExpired => parsedCode == SalesforceErrorCode.invalidSessionId;

  /// Whether this is a rate limit error.
  bool get isRateLimited => parsedCode == SalesforceErrorCode.requestLimitExceeded;

  static SalesforceErrorCode _parseCode(String code) {
    switch (code) {
      case 'MALFORMED_QUERY':
        return SalesforceErrorCode.malformedQuery;
      case 'INVALID_SESSION_ID':
        return SalesforceErrorCode.invalidSessionId;
      case 'INVALID_FIELD':
        return SalesforceErrorCode.invalidField;
      case 'INVALID_TYPE':
        return SalesforceErrorCode.invalidType;
      case 'ENTITY_IS_DELETED':
        return SalesforceErrorCode.entityIsDeleted;
      case 'FIELD_CUSTOM_VALIDATION_EXCEPTION':
        return SalesforceErrorCode.fieldCustomValidationException;
      case 'INSUFFICIENT_ACCESS_ON_CROSS_REFERENCE_ENTITY':
      case 'INSUFFICIENT_ACCESS_OR_READONLY':
      case 'INSUFFICIENT_ACCESS':
        return SalesforceErrorCode.insufficientAccess;
      case 'REQUEST_LIMIT_EXCEEDED':
        return SalesforceErrorCode.requestLimitExceeded;
      case 'API_DISABLED_FOR_ORG':
        return SalesforceErrorCode.apiDisabledForOrg;
      case 'DUPLICATE_VALUE':
        return SalesforceErrorCode.duplicateValue;
      case 'REQUIRED_FIELD_MISSING':
        return SalesforceErrorCode.requiredFieldMissing;
      case 'STRING_TOO_LONG':
        return SalesforceErrorCode.stringTooLong;
      default:
        return SalesforceErrorCode.unknownError;
    }
  }

  @override
  String toString() => 'SalesforceApiError($errorCode: $message)';
}

/// Exception thrown when Salesforce API returns an error response.
class SalesforceApiException implements Exception {
  /// The HTTP status code.
  final int httpStatusCode;

  /// Parsed errors from the response body.
  final List<SalesforceApiError> errors;

  /// The raw response body.
  final String? rawBody;

  SalesforceApiException({
    required this.httpStatusCode,
    required this.errors,
    this.rawBody,
  });

  /// Creates from a [RestResponse].
  factory SalesforceApiException.fromResponse(RestResponse response) {
    final errors = <SalesforceApiError>[];
    try {
      final body = response.asString;
      final decoded = jsonDecode(body);
      if (decoded is List) {
        errors.addAll(decoded
            .cast<Map<String, dynamic>>()
            .map(SalesforceApiError.fromJson));
      } else if (decoded is Map<String, dynamic>) {
        errors.add(SalesforceApiError.fromJson(decoded));
      }
    } catch (_) {
      errors.add(SalesforceApiError(
        errorCode: 'HTTP_${response.statusCode}',
        message: response.asString,
      ));
    }

    return SalesforceApiException(
      httpStatusCode: response.statusCode,
      errors: errors,
      rawBody: response.asString,
    );
  }

  /// The first error's code.
  String get errorCode =>
      errors.isNotEmpty ? errors.first.errorCode : 'UNKNOWN_ERROR';

  /// The first error's message.
  String get message =>
      errors.isNotEmpty ? errors.first.message : 'Unknown error';

  /// Whether any error indicates session expiration.
  bool get isSessionExpired => errors.any((e) => e.isSessionExpired);

  /// Whether any error indicates rate limiting.
  bool get isRateLimited => errors.any((e) => e.isRateLimited);

  @override
  String toString() =>
      'SalesforceApiException(HTTP $httpStatusCode: $errorCode - $message)';
}

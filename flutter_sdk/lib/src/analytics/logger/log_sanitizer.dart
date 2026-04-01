/// Sanitizes log messages to prevent sensitive data leakage.
///
/// Redacts OAuth tokens, session IDs, passwords, and other credentials
/// from log output before it reaches system logs or developer console.
class LogSanitizer {
  static const String _redacted = '[REDACTED]';

  static final List<RegExp> _patterns = [
    // Bearer tokens
    RegExp(r'(Bearer\s+)\S+', caseSensitive: false),
    // JSON access_token
    RegExp(r'("access_token"\s*:\s*")[^"]*"'),
    // JSON refresh_token
    RegExp(r'("refresh_token"\s*:\s*")[^"]*"'),
    // JSON password
    RegExp(r'("password"\s*:\s*")[^"]*"'),
    // URL-encoded tokens
    RegExp(r'(access_token=)[^&\s]+'),
    RegExp(r'(refresh_token=)[^&\s]+'),
    // Session IDs
    RegExp(r'(sid=)[^&\s;]+'),
    // Lightning/VF SIDs
    RegExp(r'("lightningSid"\s*:\s*")[^"]*"'),
    RegExp(r'("vfSid"\s*:\s*")[^"]*"'),
    RegExp(r'("contentSid"\s*:\s*")[^"]*"'),
    // Authorization headers
    RegExp(r'(Authorization:\s*)\S+', caseSensitive: false),
  ];

  /// Sanitizes a message by redacting sensitive patterns.
  static String sanitize(String message) {
    var result = message;
    for (final pattern in _patterns) {
      result = result.replaceAllMapped(pattern, (match) {
        // Keep the prefix (group 1), replace the value
        final prefix = match.group(1) ?? '';
        if (match.pattern == _patterns[0]) {
          // Bearer token: keep "Bearer "
          return '$prefix$_redacted';
        }
        if (prefix.endsWith('"')) {
          // JSON field: keep prefix and closing quote
          return '$prefix$_redacted"';
        }
        return '$prefix$_redacted';
      });
    }
    return result;
  }
}

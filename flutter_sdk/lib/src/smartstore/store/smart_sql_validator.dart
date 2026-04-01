/// Validates SmartSQL identifiers to prevent SQL injection.
///
/// All soup names and field paths used in SmartSQL queries must pass
/// validation before being interpolated into SQL strings.
class SmartSqlValidator {
  /// Maximum soup name length.
  static const int maxSoupNameLength = 128;

  /// Maximum field path length.
  static const int maxFieldPathLength = 256;

  /// Valid identifier pattern: alphanumeric, underscores, starting with letter/underscore.
  static final RegExp _validIdentifier = RegExp(r'^[a-zA-Z_][a-zA-Z0-9_]*$');

  /// Valid field path pattern: dot-separated identifiers.
  static final RegExp _validFieldPath =
      RegExp(r'^[a-zA-Z_][a-zA-Z0-9_.]*$');

  /// Validates a soup name.
  ///
  /// Throws [ArgumentError] if the name is invalid.
  static void validateSoupName(String name) {
    if (name.isEmpty) {
      throw ArgumentError.value(name, 'soupName', 'Soup name cannot be empty');
    }
    if (name.length > maxSoupNameLength) {
      throw ArgumentError.value(
          name, 'soupName', 'Soup name exceeds $maxSoupNameLength characters');
    }
    if (!_validIdentifier.hasMatch(name)) {
      throw ArgumentError.value(name, 'soupName',
          'Soup name must be alphanumeric with underscores, starting with a letter or underscore');
    }
  }

  /// Validates a field path (e.g., "Name", "Address.City").
  ///
  /// Throws [ArgumentError] if the path is invalid.
  static void validateFieldPath(String path) {
    if (path.isEmpty) {
      throw ArgumentError.value(path, 'fieldPath', 'Field path cannot be empty');
    }
    if (path.length > maxFieldPathLength) {
      throw ArgumentError.value(path, 'fieldPath',
          'Field path exceeds $maxFieldPathLength characters');
    }
    // Allow special SmartStore internal fields
    if (path == '_soup' || path == '_soupEntryId' || path == '_soupLastModifiedDate') {
      return;
    }
    if (!_validFieldPath.hasMatch(path)) {
      throw ArgumentError.value(path, 'fieldPath',
          'Field path must be alphanumeric with underscores and dots');
    }
    // Ensure no consecutive dots
    if (path.contains('..')) {
      throw ArgumentError.value(
          path, 'fieldPath', 'Field path cannot contain consecutive dots');
    }
  }

  /// Checks if a soup name is valid without throwing.
  static bool isValidSoupName(String name) {
    if (name.isEmpty || name.length > maxSoupNameLength) return false;
    return _validIdentifier.hasMatch(name);
  }

  /// Checks if a field path is valid without throwing.
  static bool isValidFieldPath(String path) {
    if (path.isEmpty || path.length > maxFieldPathLength) return false;
    if (path == '_soup' || path == '_soupEntryId' || path == '_soupLastModifiedDate') {
      return true;
    }
    return _validFieldPath.hasMatch(path) && !path.contains('..');
  }
}

/// Types of indexes for SmartStore soups.
enum SmartStoreType {
  /// JSON1 index type - extracts values from JSON using json_extract.
  json1,

  /// Full-text search index type.
  fullText,
}

/// Specification for an index on a SmartStore soup.
///
/// Defines which JSON path to index and the type of index to create.
/// Used when registering or altering soups.
class IndexSpec {
  /// The JSON path to index (e.g., 'Name', 'Account.Name').
  final String path;

  /// The type of index.
  final SmartStoreType type;

  /// Optional custom column name in the underlying database.
  final String? columnName;

  const IndexSpec({
    required this.path,
    required this.type,
    this.columnName,
  });

  /// Creates a JSON1 index spec.
  factory IndexSpec.json1(String path) =>
      IndexSpec(path: path, type: SmartStoreType.json1);

  /// Creates a full-text search index spec.
  factory IndexSpec.fullText(String path) =>
      IndexSpec(path: path, type: SmartStoreType.fullText);

  /// Returns a string combining path and type.
  String get pathType => '$path|${type.name}';

  Map<String, dynamic> toJson() => {
        'path': path,
        'type': type.name,
        if (columnName != null) 'columnName': columnName,
      };

  factory IndexSpec.fromJson(Map<String, dynamic> json) {
    return IndexSpec(
      path: json['path'],
      type: SmartStoreType.values.firstWhere(
        (t) => t.name == json['type'],
        orElse: () => SmartStoreType.json1,
      ),
      columnName: json['columnName'],
    );
  }

  /// Parses a list of IndexSpec from a JSON array.
  static List<IndexSpec> fromJsonList(List<dynamic> jsonArray) {
    return jsonArray
        .map((e) => IndexSpec.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Checks if any specs use full-text search.
  static bool hasFTS(List<IndexSpec> specs) =>
      specs.any((s) => s.type == SmartStoreType.fullText);

  /// Checks if any specs use JSON1.
  static bool hasJSON1(List<IndexSpec> specs) =>
      specs.any((s) => s.type == SmartStoreType.json1);

  /// Creates a map from path to IndexSpec.
  static Map<String, IndexSpec> mapForIndexSpecs(List<IndexSpec> specs) {
    return {for (final spec in specs) spec.path: spec};
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is IndexSpec && path == other.path && type == other.type;

  @override
  int get hashCode => path.hashCode ^ type.hashCode;

  @override
  String toString() => 'IndexSpec(path=$path, type=${type.name})';
}

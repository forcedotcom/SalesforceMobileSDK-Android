import '../store/index_spec.dart';

/// Configuration for a SmartStore soup.
///
/// Used to declaratively define soups and their indexes, typically
/// loaded from a configuration file or defined in code.
///
/// Mirrors Android StoreConfig for feature parity.
class SoupConfig {
  /// The soup name.
  final String soupName;

  /// Index specifications for this soup.
  final List<IndexSpec> indexes;

  const SoupConfig({
    required this.soupName,
    required this.indexes,
  });

  factory SoupConfig.fromJson(Map<String, dynamic> json) {
    return SoupConfig(
      soupName: json['soupName'] as String,
      indexes: (json['indexes'] as List<dynamic>)
          .map((i) => IndexSpec.fromJson(i as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
        'soupName': soupName,
        'indexes': indexes.map((i) => i.toJson()).toList(),
      };
}

/// Configuration for initializing SmartStore soups.
///
/// Provides a declarative way to define multiple soups and their indexes
/// that should be created when the store is initialized.
///
/// Mirrors Android StoreConfig for feature parity.
class StoreConfig {
  /// Whether this is a global store (shared across users).
  final bool isGlobal;

  /// The store name (null for default store).
  final String? storeName;

  /// Soup configurations to create.
  final List<SoupConfig> soups;

  const StoreConfig({
    this.isGlobal = false,
    this.storeName,
    required this.soups,
  });

  factory StoreConfig.fromJson(Map<String, dynamic> json) {
    return StoreConfig(
      isGlobal: json['isGlobal'] as bool? ?? false,
      storeName: json['storeName'] as String?,
      soups: (json['soups'] as List<dynamic>)
          .map((s) => SoupConfig.fromJson(s as Map<String, dynamic>))
          .toList(),
    );
  }

  Map<String, dynamic> toJson() => {
        'isGlobal': isGlobal,
        'storeName': storeName,
        'soups': soups.map((s) => s.toJson()).toList(),
      };

  /// Creates a StoreConfig from a list of soup JSON definitions.
  factory StoreConfig.fromSoupList(List<Map<String, dynamic>> soupDefs,
      {bool isGlobal = false, String? storeName}) {
    return StoreConfig(
      isGlobal: isGlobal,
      storeName: storeName,
      soups: soupDefs.map((s) => SoupConfig.fromJson(s)).toList(),
    );
  }
}

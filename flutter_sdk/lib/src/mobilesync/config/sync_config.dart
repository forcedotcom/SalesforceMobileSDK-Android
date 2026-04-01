import '../model/sync_state.dart';

/// Configuration for a single sync operation.
///
/// Defines the target, options, soup, and direction for a sync.
class SyncDefinition {
  /// The soup name to sync.
  final String soupName;

  /// The sync target configuration.
  final Map<String, dynamic> target;

  /// The sync options.
  final SyncOptions options;

  /// Whether this is a sync down (true) or sync up (false).
  final bool isSyncDown;

  /// A descriptive name for this sync.
  final String? syncName;

  const SyncDefinition({
    required this.soupName,
    required this.target,
    required this.options,
    required this.isSyncDown,
    this.syncName,
  });

  factory SyncDefinition.fromJson(Map<String, dynamic> json) {
    return SyncDefinition(
      soupName: json['soupName'] as String,
      target: json['target'] as Map<String, dynamic>,
      options: SyncOptions.fromJson(json['options'] as Map<String, dynamic>),
      isSyncDown: json['type'] == 'syncDown',
      syncName: json['syncName'] as String?,
    );
  }

  Map<String, dynamic> toJson() => {
        'soupName': soupName,
        'target': target,
        'options': options.toJson(),
        'type': isSyncDown ? 'syncDown' : 'syncUp',
        if (syncName != null) 'syncName': syncName,
      };
}

/// Configuration for initializing MobileSync operations.
///
/// Provides a declarative way to define syncs that should be created
/// on app initialization.
///
/// Mirrors Android SyncsConfig for feature parity.
class SyncConfig {
  /// Sync definitions for sync down operations.
  final List<SyncDefinition> syncDowns;

  /// Sync definitions for sync up operations.
  final List<SyncDefinition> syncUps;

  const SyncConfig({
    this.syncDowns = const [],
    this.syncUps = const [],
  });

  /// All sync definitions.
  List<SyncDefinition> get allSyncs => [...syncDowns, ...syncUps];

  factory SyncConfig.fromJson(Map<String, dynamic> json) {
    return SyncConfig(
      syncDowns: (json['syncs'] as List<dynamic>?)
              ?.map((s) => SyncDefinition.fromJson(s as Map<String, dynamic>))
              .where((s) => s.isSyncDown)
              .toList() ??
          [],
      syncUps: (json['syncs'] as List<dynamic>?)
              ?.map((s) => SyncDefinition.fromJson(s as Map<String, dynamic>))
              .where((s) => !s.isSyncDown)
              .toList() ??
          [],
    );
  }

  Map<String, dynamic> toJson() => {
        'syncs': allSyncs.map((s) => s.toJson()).toList(),
      };

  /// Creates a SyncConfig from separate sync down and sync up lists.
  factory SyncConfig.fromLists({
    List<Map<String, dynamic>>? syncDownDefs,
    List<Map<String, dynamic>>? syncUpDefs,
  }) {
    return SyncConfig(
      syncDowns: syncDownDefs
              ?.map((s) => SyncDefinition.fromJson({...s, 'type': 'syncDown'}))
              .toList() ??
          [],
      syncUps: syncUpDefs
              ?.map((s) => SyncDefinition.fromJson({...s, 'type': 'syncUp'}))
              .toList() ??
          [],
    );
  }
}

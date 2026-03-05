import 'dart:convert';
import '../../smartstore/store/smart_store.dart';
import '../../smartstore/store/index_spec.dart';
import '../../smartstore/store/query_spec.dart';

/// Type of sync operation.
enum SyncType {
  syncDown,
  syncUp,
}

/// Status of a sync operation.
enum SyncStatus {
  /// Newly created, not yet started.
  newSync,

  /// Stopped/suspended.
  stopped,

  /// Currently running.
  running,

  /// Completed successfully.
  done,

  /// Failed with an error.
  failed,
}

/// Merge mode for sync-down operations.
enum MergeMode {
  /// Server data overwrites local changes.
  overwrite,

  /// Skip records that have local changes.
  leaveIfChanged,
}

/// Represents the current state of a sync operation.
///
/// Tracks progress, status, configuration, and timing for
/// both sync-up and sync-down operations.
class SyncState {
  static const String syncsSoup = 'syncs_soup';
  static const String syncName = 'name';
  static const String syncType = 'type';
  static const String syncTarget = 'target';
  static const String syncOptions = 'options';
  static const String syncSoupName = 'soupName';
  static const String syncStatus = 'status';
  static const String syncProgress = 'progress';
  static const String syncTotalSize = 'totalSize';
  static const String syncMaxTimeStamp = 'maxTimeStamp';
  static const String syncStartTime = 'startTime';
  static const String syncEndTime = 'endTime';
  static const String syncError = 'error';

  /// Unique sync ID.
  final int id;

  /// Sync type (up or down).
  final SyncType type;

  /// Optional sync name for retrieval.
  final String? name;

  /// Target configuration.
  final Map<String, dynamic> target;

  /// Sync options.
  final SyncOptions options;

  /// The soup being synced.
  final String soupName;

  /// Current status.
  SyncStatus status;

  /// Number of records processed.
  int progress;

  /// Total number of records.
  int totalSize;

  /// Maximum modification timestamp seen.
  int maxTimeStamp;

  /// Start time in milliseconds.
  double? startTime;

  /// End time in milliseconds.
  double? endTime;

  /// Error message if failed.
  String? error;

  SyncState({
    required this.id,
    required this.type,
    this.name,
    required this.target,
    required this.options,
    required this.soupName,
    this.status = SyncStatus.newSync,
    this.progress = 0,
    this.totalSize = -1,
    this.maxTimeStamp = -1,
    this.startTime,
    this.endTime,
    this.error,
  });

  /// Whether the sync is done.
  bool get isDone => status == SyncStatus.done;

  /// Whether the sync has failed.
  bool get hasFailed => status == SyncStatus.failed;

  /// Whether the sync is stopped.
  bool get isStopped => status == SyncStatus.stopped;

  /// Whether the sync is running.
  bool get isRunning => status == SyncStatus.running;

  /// The merge mode from options.
  MergeMode get mergeMode => options.mergeMode;

  Map<String, dynamic> toJson() => {
        soupEntryId: id,
        syncType: type.name,
        syncName: name,
        syncTarget: target,
        syncOptions: options.toJson(),
        syncSoupName: soupName,
        syncStatus: status.name,
        syncProgress: progress,
        syncTotalSize: totalSize,
        syncMaxTimeStamp: maxTimeStamp,
        syncStartTime: startTime,
        syncEndTime: endTime,
        syncError: error,
      };

  factory SyncState.fromJson(Map<String, dynamic> json) {
    return SyncState(
      id: json[soupEntryId] ?? json['id'] ?? 0,
      type: SyncType.values.firstWhere(
        (t) => t.name == json[syncType],
        orElse: () => SyncType.syncDown,
      ),
      name: json[syncName],
      target: json[syncTarget] is Map
          ? Map<String, dynamic>.from(json[syncTarget])
          : {},
      options: SyncOptions.fromJson(
        json[syncOptions] is Map
            ? Map<String, dynamic>.from(json[syncOptions])
            : {},
      ),
      soupName: json[syncSoupName] ?? '',
      status: SyncStatus.values.firstWhere(
        (s) => s.name == json[syncStatus],
        orElse: () => SyncStatus.newSync,
      ),
      progress: json[syncProgress] ?? 0,
      totalSize: json[syncTotalSize] ?? -1,
      maxTimeStamp: json[syncMaxTimeStamp] ?? -1,
      startTime: (json[syncStartTime] as num?)?.toDouble(),
      endTime: (json[syncEndTime] as num?)?.toDouble(),
      error: json[syncError],
    );
  }

  /// Creates a deep copy.
  SyncState copy() => SyncState.fromJson(toJson());

  /// Saves the sync state to SmartStore.
  Future<void> save(SmartStore store) async {
    final json = toJson();
    await store.upsert(syncsSoup, json);
  }

  /// Sets up the syncs_soup if it doesn't exist.
  static Future<void> setupSyncsSoupIfNeeded(SmartStore store) async {
    if (!(await store.hasSoup(syncsSoup))) {
      await store.registerSoup(syncsSoup, [
        IndexSpec.json1(syncType),
        IndexSpec.json1(syncName),
        IndexSpec.json1(syncStatus),
      ]);
    }
  }

  /// Cleans up interrupted syncs (marks RUNNING as STOPPED).
  static Future<void> cleanupSyncsSoupIfNeeded(SmartStore store) async {
    final runningSyncs = await getSyncsWithStatus(store, SyncStatus.running);
    for (final sync in runningSyncs) {
      sync.status = SyncStatus.stopped;
      await sync.save(store);
    }
  }

  /// Gets all syncs with a given status.
  static Future<List<SyncState>> getSyncsWithStatus(
      SmartStore store, SyncStatus status) async {
    final spec = QuerySpec.buildExactQuerySpec(
      syncsSoup,
      syncStatus,
      status.name,
      pageSize: 10000,
    );
    final results = await store.query(spec, 0);
    return results.map((r) => SyncState.fromJson(r)).toList();
  }

  /// Creates a sync-down state entry.
  static Future<SyncState> createSyncDown(
    SmartStore store,
    Map<String, dynamic> target,
    SyncOptions options,
    String soupName, {
    String? name,
  }) async {
    final syncState = SyncState(
      id: 0,
      type: SyncType.syncDown,
      name: name,
      target: target,
      options: options,
      soupName: soupName,
    );
    final json = syncState.toJson();
    final saved = await store.upsert(syncsSoup, json);
    return SyncState.fromJson(saved);
  }

  /// Creates a sync-up state entry.
  static Future<SyncState> createSyncUp(
    SmartStore store,
    Map<String, dynamic> target,
    SyncOptions options,
    String soupName, {
    String? name,
  }) async {
    final syncState = SyncState(
      id: 0,
      type: SyncType.syncUp,
      name: name,
      target: target,
      options: options,
      soupName: soupName,
    );
    final json = syncState.toJson();
    final saved = await store.upsert(syncsSoup, json);
    return SyncState.fromJson(saved);
  }

  /// Looks up a sync by ID.
  static Future<SyncState?> byId(SmartStore store, int id) async {
    final result = await store.retrieve(syncsSoup, id);
    if (result == null) return null;
    return SyncState.fromJson(result);
  }

  /// Looks up a sync by name.
  static Future<SyncState?> byName(SmartStore store, String name) async {
    final spec = QuerySpec.buildExactQuerySpec(
      syncsSoup,
      syncName,
      name,
      pageSize: 1,
    );
    final results = await store.query(spec, 0);
    if (results.isEmpty) return null;
    return SyncState.fromJson(results.first);
  }

  /// Checks if a sync with the given name exists.
  static Future<bool> hasSyncWithName(SmartStore store, String name) async {
    return (await byName(store, name)) != null;
  }

  /// Deletes a sync by ID.
  static Future<void> deleteSyncById(SmartStore store, int id) async {
    await store.delete(syncsSoup, id);
  }

  /// Deletes a sync by name.
  static Future<void> deleteSyncByName(
      SmartStore store, String name) async {
    final sync = await byName(store, name);
    if (sync != null) {
      await store.delete(syncsSoup, sync.id);
    }
  }

  @override
  String toString() =>
      'SyncState(id=$id, type=${type.name}, status=${status.name}, progress=$progress/$totalSize)';
}

/// Options for sync operations.
class SyncOptions {
  /// The merge mode.
  final MergeMode mergeMode;

  /// Field list for sync-up creates.
  final List<String>? fieldlist;

  const SyncOptions({
    this.mergeMode = MergeMode.overwrite,
    this.fieldlist,
  });

  /// Default options with OVERWRITE merge mode.
  static const SyncOptions defaultOptions =
      SyncOptions(mergeMode: MergeMode.overwrite);

  Map<String, dynamic> toJson() => {
        'mergeMode': mergeMode.name,
        if (fieldlist != null) 'fieldlist': fieldlist,
      };

  factory SyncOptions.fromJson(Map<String, dynamic> json) {
    return SyncOptions(
      mergeMode: MergeMode.values.firstWhere(
        (m) => m.name == json['mergeMode'],
        orElse: () => MergeMode.overwrite,
      ),
      fieldlist: (json['fieldlist'] as List<dynamic>?)?.cast<String>(),
    );
  }
}


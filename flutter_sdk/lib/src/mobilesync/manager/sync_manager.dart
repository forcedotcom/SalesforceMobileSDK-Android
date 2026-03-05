import 'dart:async';
import '../../analytics/logger/salesforce_logger.dart';
import '../../core/rest/rest_client.dart';
import '../../core/rest/rest_request.dart';
import '../../core/rest/rest_response.dart';
import '../../smartstore/store/smart_store.dart';
import '../model/sync_state.dart';
import '../target/sync_down_target.dart';
import '../target/sync_up_target.dart';

/// Sync manager state.
enum SyncManagerState {
  acceptingSyncs,
  stopRequested,
  stopped,
}

/// Callback for sync state updates.
typedef SyncUpdateCallback = void Function(SyncState syncState);

/// Callback for clean resync ghosts completion.
typedef CleanResyncGhostsCallback = void Function(int numRecords);

/// Exception for MobileSync operations.
class MobileSyncException implements Exception {
  final String message;
  final Object? cause;
  MobileSyncException(this.message, [this.cause]);
  @override
  String toString() => 'MobileSyncException: $message';
}

/// Core synchronization manager for offline data sync.
///
/// Orchestrates sync-up and sync-down operations between
/// the local SmartStore and the Salesforce server. Supports:
/// - Named syncs for easy re-execution
/// - Progress tracking and callbacks
/// - Conflict detection and merge modes
/// - Ghost record cleanup
/// - Stop/restart of sync operations
///
/// Usage:
/// ```dart
/// final syncManager = await SyncManager.getInstance(restClient: client);
///
/// // Sync down
/// await syncManager.syncDown(
///   target: SoqlSyncDownTarget(query: 'SELECT Id, Name FROM Account'),
///   soupName: 'Accounts',
///   callback: (state) => print('Progress: ${state.progress}'),
/// );
///
/// // Sync up
/// await syncManager.syncUp(
///   target: DefaultSyncUpTarget(
///     createFieldlist: ['Name'],
///     updateFieldlist: ['Name'],
///   ),
///   options: SyncOptions(mergeMode: MergeMode.leaveIfChanged),
///   soupName: 'Accounts',
/// );
/// ```
class SyncManager {
  static const String _tag = 'SyncManager';
  static final SalesforceLogger _logger =
      SalesforceLogger.getLogger('SyncManager');

  static final Map<String, SyncManager> _instances = {};

  /// The SmartStore instance for local data.
  final SmartStore smartStore;

  /// The REST client for server communication.
  RestClient? restClient;

  /// The API version to use.
  String apiVersion;

  SyncManagerState _state = SyncManagerState.acceptingSyncs;
  final Set<int> _activeSyncIds = {};

  SyncManager._({
    required this.smartStore,
    this.restClient,
    this.apiVersion = '59.0',
  });

  /// Gets or creates a SyncManager instance.
  static Future<SyncManager> getInstance({
    required SmartStore smartStore,
    RestClient? restClient,
    String apiVersion = '59.0',
    String? uniqueId,
  }) async {
    final key = uniqueId ?? 'default';
    if (_instances.containsKey(key)) {
      final instance = _instances[key]!;
      if (restClient != null) instance.restClient = restClient;
      return instance;
    }

    await SyncState.setupSyncsSoupIfNeeded(smartStore);
    await SyncState.cleanupSyncsSoupIfNeeded(smartStore);

    final instance = SyncManager._(
      smartStore: smartStore,
      restClient: restClient,
      apiVersion: apiVersion,
    );
    _instances[key] = instance;
    return instance;
  }

  /// Whether the manager is accepting new syncs.
  bool get isAcceptingSyncs =>
      _state == SyncManagerState.acceptingSyncs;

  /// Whether a stop has been requested.
  bool get isStopping => _state == SyncManagerState.stopRequested;

  /// Whether the manager is fully stopped.
  bool get isStopped => _state == SyncManagerState.stopped;

  // ===== Sync Status Queries =====

  /// Gets sync state by ID.
  Future<SyncState?> getSyncStatus(int syncId) async {
    return SyncState.byId(smartStore, syncId);
  }

  /// Gets sync state by name.
  Future<SyncState?> getSyncStatusByName(String name) async {
    return SyncState.byName(smartStore, name);
  }

  /// Checks if a sync with the given name exists.
  Future<bool> hasSyncWithName(String name) async {
    return SyncState.hasSyncWithName(smartStore, name);
  }

  /// Deletes a sync by ID.
  Future<void> deleteSync(int syncId) async {
    await SyncState.deleteSyncById(smartStore, syncId);
  }

  /// Deletes a sync by name.
  Future<void> deleteSyncByName(String name) async {
    await SyncState.deleteSyncByName(smartStore, name);
  }

  // ===== Sync Down Operations =====

  /// Performs a sync-down operation.
  Future<SyncState> syncDown({
    required SyncDownTarget target,
    SyncOptions options = SyncOptions.defaultOptions,
    required String soupName,
    String? syncName,
    SyncUpdateCallback? callback,
  }) async {
    _checkAcceptingSyncs();

    // Check for existing named sync
    if (syncName != null) {
      final existing = await getSyncStatusByName(syncName);
      if (existing != null) {
        throw MobileSyncException(
            'Sync with name $syncName already exists');
      }
    }

    final syncState = await SyncState.createSyncDown(
      smartStore,
      target.toJson(),
      options,
      soupName,
      name: syncName,
    );

    await _runSyncDown(syncState, target, callback);
    return syncState;
  }

  /// Creates a sync-down without running it.
  Future<SyncState> createSyncDown({
    required SyncDownTarget target,
    SyncOptions options = SyncOptions.defaultOptions,
    required String soupName,
    String? syncName,
  }) async {
    return SyncState.createSyncDown(
      smartStore,
      target.toJson(),
      options,
      soupName,
      name: syncName,
    );
  }

  // ===== Sync Up Operations =====

  /// Performs a sync-up operation.
  Future<SyncState> syncUp({
    required SyncUpTarget target,
    SyncOptions options = SyncOptions.defaultOptions,
    required String soupName,
    String? syncName,
    SyncUpdateCallback? callback,
  }) async {
    _checkAcceptingSyncs();

    if (syncName != null) {
      final existing = await getSyncStatusByName(syncName);
      if (existing != null) {
        throw MobileSyncException(
            'Sync with name $syncName already exists');
      }
    }

    final syncState = await SyncState.createSyncUp(
      smartStore,
      target.toJson(),
      options,
      soupName,
      name: syncName,
    );

    await _runSyncUp(syncState, target, callback);
    return syncState;
  }

  /// Creates a sync-up without running it.
  Future<SyncState> createSyncUp({
    required SyncUpTarget target,
    SyncOptions options = SyncOptions.defaultOptions,
    required String soupName,
    String? syncName,
  }) async {
    return SyncState.createSyncUp(
      smartStore,
      target.toJson(),
      options,
      soupName,
      name: syncName,
    );
  }

  // ===== Re-sync =====

  /// Re-runs a previously defined sync by ID.
  Future<SyncState> reSync(int syncId, {SyncUpdateCallback? callback}) async {
    _checkAcceptingSyncs();
    final syncState = await getSyncStatus(syncId);
    if (syncState == null) {
      throw MobileSyncException('Sync with id $syncId not found');
    }
    return _reRunSync(syncState, callback);
  }

  /// Re-runs a previously defined sync by name.
  Future<SyncState> reSyncByName(String name,
      {SyncUpdateCallback? callback}) async {
    _checkAcceptingSyncs();
    final syncState = await getSyncStatusByName(name);
    if (syncState == null) {
      throw MobileSyncException('Sync with name $name not found');
    }
    return _reRunSync(syncState, callback);
  }

  // ===== Ghost Record Cleanup =====

  /// Cleans up ghost records for a sync by ID.
  Future<int> cleanResyncGhosts(
    int syncId, {
    CleanResyncGhostsCallback? callback,
  }) async {
    final syncState = await getSyncStatus(syncId);
    if (syncState == null) {
      throw MobileSyncException('Sync with id $syncId not found');
    }
    if (syncState.type != SyncType.syncDown) {
      throw MobileSyncException('Can only clean ghosts for sync-down');
    }

    final target = SyncDownTarget.fromJson(syncState.target);
    final cleaned =
        await target.cleanGhosts(this, syncState.soupName, syncId);
    callback?.call(cleaned);
    return cleaned;
  }

  // ===== Lifecycle =====

  /// Stops accepting new syncs and waits for active syncs to complete.
  Future<void> stop() async {
    _state = SyncManagerState.stopRequested;
    // Wait for active syncs
    while (_activeSyncIds.isNotEmpty) {
      await Future.delayed(const Duration(milliseconds: 100));
    }
    _state = SyncManagerState.stopped;
  }

  /// Restarts the sync manager.
  Future<void> restart({
    bool restartStoppedSyncs = false,
    SyncUpdateCallback? callback,
  }) async {
    _state = SyncManagerState.acceptingSyncs;

    if (restartStoppedSyncs) {
      final stoppedSyncs =
          await SyncState.getSyncsWithStatus(smartStore, SyncStatus.stopped);
      for (final sync in stoppedSyncs) {
        await _reRunSync(sync, callback);
      }
    }
  }

  /// Sends a REST request with the MobileSync user agent.
  Future<RestResponse> sendSyncRequest(RestRequest request) async {
    if (restClient == null) {
      throw MobileSyncException('RestClient is not set');
    }
    return restClient!.sendAsync(request);
  }

  // ===== Internal =====

  void _checkAcceptingSyncs() {
    if (!isAcceptingSyncs) {
      throw MobileSyncException('SyncManager is not accepting syncs');
    }
  }

  Future<SyncState> _reRunSync(
      SyncState syncState, SyncUpdateCallback? callback) async {
    if (syncState.type == SyncType.syncDown) {
      final target = SyncDownTarget.fromJson(syncState.target);
      await _runSyncDown(syncState, target, callback);
    } else {
      final target = SyncUpTarget.fromJson(syncState.target);
      await _runSyncUp(syncState, target, callback);
    }
    return syncState;
  }

  Future<void> _runSyncDown(
    SyncState syncState,
    SyncDownTarget target,
    SyncUpdateCallback? callback,
  ) async {
    _activeSyncIds.add(syncState.id);
    try {
      syncState.status = SyncStatus.running;
      syncState.startTime =
          DateTime.now().millisecondsSinceEpoch.toDouble();
      syncState.progress = 0;
      await syncState.save(smartStore);
      callback?.call(syncState);

      // Fetch records
      var records =
          await target.startFetch(this, syncState.maxTimeStamp);
      syncState.totalSize = target.totalSize;

      final idsToSkip = syncState.mergeMode == MergeMode.leaveIfChanged
          ? await target.getIdsToSkip(this, syncState.soupName)
          : <String>{};

      while (records != null && records.isNotEmpty) {
        if (_state == SyncManagerState.stopRequested) {
          syncState.status = SyncStatus.stopped;
          await syncState.save(smartStore);
          callback?.call(syncState);
          return;
        }

        // Save records to soup
        for (final record in records) {
          final recordId =
              record[target.idFieldName]?.toString() ?? '';
          if (idsToSkip.contains(recordId)) continue;

          record['__local__'] = false;
          record['__locally_created__'] = false;
          record['__locally_updated__'] = false;
          record['__locally_deleted__'] = false;

          await smartStore.upsert(
            syncState.soupName,
            record,
            externalIdPath: target.idFieldName,
          );
        }

        // Update progress
        syncState.progress += records.length;
        final newMaxStamp =
            target.getLatestModificationTimeStamp(records);
        if (newMaxStamp > syncState.maxTimeStamp) {
          syncState.maxTimeStamp = newMaxStamp;
        }
        await syncState.save(smartStore);
        callback?.call(syncState);

        // Continue fetching
        records = await target.continueFetch(this);
      }

      syncState.status = SyncStatus.done;
      syncState.endTime =
          DateTime.now().millisecondsSinceEpoch.toDouble();
      await syncState.save(smartStore);
      callback?.call(syncState);
    } catch (e) {
      _logger.e(_tag, 'Sync down failed', e);
      syncState.status = SyncStatus.failed;
      syncState.error = e.toString();
      syncState.endTime =
          DateTime.now().millisecondsSinceEpoch.toDouble();
      await syncState.save(smartStore);
      callback?.call(syncState);
    } finally {
      _activeSyncIds.remove(syncState.id);
    }
  }

  Future<void> _runSyncUp(
    SyncState syncState,
    SyncUpTarget target,
    SyncUpdateCallback? callback,
  ) async {
    _activeSyncIds.add(syncState.id);
    try {
      syncState.status = SyncStatus.running;
      syncState.startTime =
          DateTime.now().millisecondsSinceEpoch.toDouble();
      syncState.progress = 0;
      await syncState.save(smartStore);
      callback?.call(syncState);

      // Get dirty records
      final dirtyIds = await target.getIdsOfRecordsToSyncUp(
          this, syncState.soupName);
      syncState.totalSize = dirtyIds.length;

      for (final recordId in dirtyIds) {
        if (_state == SyncManagerState.stopRequested) {
          syncState.status = SyncStatus.stopped;
          await syncState.save(smartStore);
          callback?.call(syncState);
          return;
        }

        // Find the record in the soup
        final entryId = await smartStore.lookupSoupEntryId(
            syncState.soupName, target.idFieldName, recordId);
        if (entryId == null) continue;

        final record =
            await smartStore.retrieve(syncState.soupName, entryId);
        if (record == null) continue;

        try {
          final isLocallyCreated =
              record['__locally_created__'] == true;
          final isLocallyDeleted =
              record['__locally_deleted__'] == true;
          final isLocallyUpdated =
              record['__locally_updated__'] == true;

          if (isLocallyDeleted) {
            if (!isLocallyCreated) {
              await target.deleteOnServer(this, record);
            }
            await smartStore.delete(syncState.soupName, entryId);
          } else if (isLocallyCreated) {
            final newId = await target.createOnServer(
              this,
              record,
              syncState.options.fieldlist ?? target.createFieldlist,
            );
            if (newId != null) {
              record[target.idFieldName] = newId;
              record['__local__'] = false;
              record['__locally_created__'] = false;
              record['__locally_updated__'] = false;
              await smartStore.update(
                  syncState.soupName, record, entryId);
            }
          } else if (isLocallyUpdated) {
            // Check for conflicts if using LEAVE_IF_CHANGED
            if (syncState.mergeMode == MergeMode.leaveIfChanged) {
              final isNewer =
                  await target.isNewerThanServer(this, record);
              if (!isNewer) {
                syncState.progress++;
                continue; // Skip - server is newer
              }
            }
            final statusCode = await target.updateOnServer(
              this,
              record,
              syncState.options.fieldlist ?? target.updateFieldlist,
            );
            if (statusCode >= 200 && statusCode < 300) {
              record['__local__'] = false;
              record['__locally_updated__'] = false;
              await smartStore.update(
                  syncState.soupName, record, entryId);
            }
          }
        } catch (e) {
          _logger.e(
              _tag, 'Failed to sync record $recordId', e);
          await target.saveRecordToLocalStoreWithLastError(
            this,
            syncState.soupName,
            record,
            errorMessage: e.toString(),
          );
        }

        syncState.progress++;
        await syncState.save(smartStore);
        callback?.call(syncState);
      }

      syncState.status = SyncStatus.done;
      syncState.endTime =
          DateTime.now().millisecondsSinceEpoch.toDouble();
      await syncState.save(smartStore);
      callback?.call(syncState);
    } catch (e) {
      _logger.e(_tag, 'Sync up failed', e);
      syncState.status = SyncStatus.failed;
      syncState.error = e.toString();
      syncState.endTime =
          DateTime.now().millisecondsSinceEpoch.toDouble();
      await syncState.save(smartStore);
      callback?.call(syncState);
    } finally {
      _activeSyncIds.remove(syncState.id);
    }
  }

  /// Resets all instances.
  static void reset() {
    _instances.clear();
  }

  /// Resets a specific instance.
  static void resetInstance(String uniqueId) {
    _instances.remove(uniqueId);
  }
}

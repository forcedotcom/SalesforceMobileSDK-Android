# MobileSync — Salesforce Mobile SDK for Android

This document covers the MobileSync library in `libs/MobileSync`. All classes live under `com.salesforce.androidsdk.mobilesync`.

For cross-platform concepts (architecture, data flows, conflict resolution) see the [workspace-level MobileSync doc](../../../SalesforceMobileSDK-Workspace/docs/mobilesync/README.md).

---

## Table of Contents

1. [Package Overview](#package-overview)
2. [SyncManager](#syncmanager)
3. [SyncState](#syncstate)
4. [SyncOptions](#syncoptions)
5. [Sync-Down Targets](#sync-down-targets)
6. [Sync-Up Targets](#sync-up-targets)
7. [Layout and Metadata Sync](#layout-and-metadata-sync)
8. [SOQL and SOSL Builders](#soql-and-sosl-builders)
9. [JSON-Driven Configuration](#json-driven-configuration)
10. [Testing](#testing)

---

## Package Overview

| Package | Key classes |
|---|---|
| `app` | `MobileSyncSDKManager`, `MobileSyncUpgradeManager`, `Features` |
| `config` | `SyncsConfig` |
| `manager` | `SyncManager`, `LayoutSyncManager`, `MetadataSyncManager`, `QueueSyncUpTask` |
| `model` | `Layout`, `Metadata`, `SalesforceObject` |
| `target` | All sync targets (see below) |
| `util` | `SyncState`, `SyncQueue`, `SyncOptions`, `SOQLBuilder`, `SOQLMutator`, `SOSLBuilder`, `ParentInfo`, `ChildrenInfo`, `BriefcaseObjectInfo`, `Constants` |

---

## SyncManager

`com.salesforce.androidsdk.mobilesync.manager.SyncManager`

Central coordinator. One instance per user + SmartStore. All work runs on a single-thread `ExecutorService`, serializing syncs for a given manager.

### Singleton Access

```kotlin
SyncManager.getInstance(): SyncManager
SyncManager.getInstance(account: UserAccount?): SyncManager
SyncManager.getInstance(account: UserAccount?, communityId: String?): SyncManager
SyncManager.getInstance(account: UserAccount?, communityId: String?, smartStore: SmartStore?): SyncManager
SyncManager.reset()
SyncManager.reset(account: UserAccount?)
```

### State Machine

```
ACCEPTING_SYNCS ──► STOP_REQUESTED ──► STOPPED
        ▲___________________________________|
                  restart()
```

### Sync Methods

```kotlin
// Sync-down: create and run immediately
fun syncDown(
    target: SyncDownTarget,
    soupName: String,
    callback: SyncUpdateCallback?
): SyncState

fun syncDown(
    target: SyncDownTarget,
    options: SyncOptions,
    soupName: String,
    callback: SyncUpdateCallback?
): SyncState

fun syncDown(
    target: SyncDownTarget,
    options: SyncOptions,
    soupName: String,
    syncName: String?,
    callback: SyncUpdateCallback?
): SyncState

// Sync-up: create and run immediately
fun syncUp(
    target: SyncUpTarget,
    options: SyncOptions,
    soupName: String,
    callback: SyncUpdateCallback?
): SyncState

fun syncUp(
    target: SyncUpTarget,
    options: SyncOptions,
    soupName: String,
    syncName: String?,
    callback: SyncUpdateCallback?
): SyncState

// Re-run an existing sync (incremental)
fun reSync(syncId: Long, callback: SyncUpdateCallback?): SyncState
fun reSync(syncName: String, callback: SyncUpdateCallback?): SyncState

// Coroutine wrappers
suspend fun suspendReSync(syncId: Long): SyncState
suspend fun suspendReSync(syncName: String): SyncState

// Ghost cleanup
fun cleanResyncGhosts(syncId: Long, callback: CleanResyncGhostsCallback? = null)
fun cleanResyncGhosts(syncName: String, callback: CleanResyncGhostsCallback?)
suspend fun suspendCleanResyncGhosts(syncId: Long): Int
suspend fun suspendCleanResyncGhosts(syncName: String): Int
```

### Create Without Running

```kotlin
fun createSyncDown(
    target: SyncDownTarget,
    options: SyncOptions,
    soupName: String,
    syncName: String?
): SyncState

fun createSyncUp(
    target: SyncUpTarget,
    options: SyncOptions,
    soupName: String,
    syncName: String?
): SyncState
```

### Query / Management

```kotlin
fun getSyncStatus(syncId: Long): SyncState?
fun getSyncStatus(name: String?): SyncState?
fun hasSyncWithName(name: String?): Boolean
fun deleteSync(syncId: Long)
fun deleteSync(name: String?)
```

### Local Write API and Sync-Up Queue

`createLocal`, `updateLocal` and `deleteLocal` write a record in a record soup and append one entry to the sync-up change queue, in a single SmartStore transaction: both writes happen or neither does. The queue is the `mobilesync_queue` soup (one per store), created empty by `SyncQueue.setupQueueSoupIfNeeded(store)` when a `SyncManager` is created (right after `SyncState.setupSyncsSoupIfNeeded`). Apps that never call the write API only see an empty soup; only a sync up with a `QueueSyncUpTarget` reads it (see below), so sync-up behavior is unchanged for everyone else.

```kotlin
fun createLocal(soupName: String, record: JSONObject): JSONObject
fun updateLocal(soupName: String, id: String, changes: JSONObject): JSONObject
fun deleteLocal(soupName: String, id: String): JSONObject
```

All three return the saved record, throw `MobileSyncException` (nothing is written) on invalid input or failed writes, and block while holding the database lock, like other SmartStore calls. They do not require the manager to be accepting syncs. The record soup must exist and index `Id`; the dirty flags (`__local__`, `__locally_created__`, `__locally_updated__`, `__locally_deleted__`) should be string-indexed so sync-down skip logic works.

| Call | Record write | Flags set | Queue entry |
|---|---|---|---|
| `createLocal` | Inserts the record (`attributes.type` required; `Id` absent or a local id, generated when absent; the input is not modified) | `__local__` and `__locally_created__` true, `__locally_updated__` and `__locally_deleted__` false | `kind` `CREATE`, `changes` = all non-internal fields |
| `updateLocal` | Merges `changes` into the stored record | `__local__` and `__locally_updated__` true; `__locally_created__` preserved | `kind` `UPDATE`, `changes` = the given fields only |
| `deleteLocal` | Keeps the record in its soup (soft delete) | `__local__` and `__locally_deleted__` true; others unchanged | `kind` `DELETE`, no `changes` |

- `changes` must not be empty and must not contain internal fields (`Id`, `attributes`, `_soup*`, the dirty flags, `__last_error__`, `__sync_id__`, `__display__`).
- To clear a field pass `JSONObject.NULL` (`put(key, null)` removes the key from a `JSONObject` instead).
- `updateLocal` and `deleteLocal` fail if the record is missing or already flagged deleted.
- `__display__` is neither written nor refreshed.
- A record soup must use either queue-based or flag-based sync up, never both. The write API sets the dirty flags that flag-based sync up reads, and a flag-based sync up does not read the queue, so mixing both on the same soup is unsupported.

Queue entries are `{sequence, soupName, recordId, sobjectType, kind, changes, status: "PENDING", attempts: 0}`. `sequence` is an integer allocated in the write transaction (highest + 1, starting at 1), so it is unique and increases in write order across all record soups; order entries by it. `base` and `parentRef` are indexed but the local write API does not write them yet (replay rewrites `parentRef` when it is present); `lastError` is written by replay (the error of the last failed attempt, with `attempts`). Index specs: `sequence`, `attempts` (integer); `soupName`, `recordId`, `sobjectType`, `kind`, `status`, `parentRef` (string); `changes`, `base`, `lastError` (json1).

#### Replaying the queue: `QueueSyncUpTarget`

A queue sync is a normal sync up whose target is a `QueueSyncUpTarget` and whose soup name is the queue soup (`SyncQueue.QUEUE_SOUP`). `SyncManager.runSync` picks `QueueSyncUpTask` for it, so `syncUp(target, options, SyncQueue.QUEUE_SOUP, name, callback)`, sync names, `reSync(id)` and `reSync(name)` work as for any sync up. The target persists with `androidImpl` like other targets, so a re-sync rebuilds it.

```kotlin
val target = QueueSyncUpTarget(externalIdFieldName = null) // optional upsert key for CREATE
syncManager.syncUp(target, SyncOptions.optionsForSyncUp(emptyList()), SyncQueue.QUEUE_SOUP, "queueSync", callback)
```

The task replays the `PENDING` entries in `sequence` order, strictly one at a time (never reordered or batched), building each request from the entry, not from the dirty flags: CREATE is `POST /sobjects/<type>` (or an upsert on `externalIdFieldName` when the entry has a value for it) with `changes`; UPDATE is `PATCH /sobjects/<type>/<id>` with `changes` only; DELETE is `DELETE /sobjects/<type>/<id>`. `fieldlist`, `createFieldlist`, `updateFieldlist` are ignored. The merge mode is ignored too for now (no conflict check on queue syncs yet). Flagged records without a queue entry are never touched.

| Outcome | What happens |
|---|---|
| Success (2xx; DELETE also 404) | The entry is removed. CREATE: the record gets the server id (`__last_error__` removed). UPDATE and CREATE: the record is cleaned (four flags false, `__last_error__` removed) if no other entry for it exists; otherwise it stays dirty for them (any other `PENDING` or `PARKED` entry counts, earlier failed ones included) and keeps its `__last_error__`: `__locally_created__` false, `__local__` true, `__locally_updated__` true if an UPDATE is pending, `__locally_deleted__` unchanged (a pending DELETE stays flagged). The last entry to go cleans the record. The check runs in the same transaction as the entry deletion. DELETE: the record is removed from its soup. A DELETE of a record that still has a local id makes no server call and also removes the other entries of that record (a failed CREATE, for instance) in the same transaction; a successful DELETE of a server id also removes the record's other entries. A CREATE whose server id is already in the soup (a sync down got there first, or an upsert hit a record already stored) removes that duplicate row: the surviving row keeps the fields of the duplicate that the CREATE row does not have (a partial upsert does not wipe the cached fields of the record), and then the `changes` of the entries queued later for that record have been are applied over it (they hold the newer local edits), so the row ends with the latest edit and stays dirty until its last entry is replayed |
| Upsert (CREATE with `externalIdFieldName`) answering 204 | An existing record was updated and the body has no id: the server id is fetched with `GET /sobjects/<type>/<externalIdField>/<value>?fields=Id` (value from the entry's `changes`), then the CREATE is completed as above. A failing lookup is classified like any other request. A 300 (several records match) is a record-level error; a 201 carries the id in its body |
| Record-level error (HTTP 4xx other than 401, 408, 429 and `REQUEST_LIMIT_EXCEEDED`, or a missing record, soup or `sobjectType`) | The sync does not fail. The entry keeps `status` `PENDING`, gets `lastError` (the server error body) and `attempts` + 1; the record gets `__last_error__`. The sync goes on with unrelated records. The later entries of the same record (same soup and `recordId` for a local id; same `sobjectType` and `recordId` for a server id, whatever the soup that holds a copy of the record), and the entries pointing to it (`parentRef` or a top-level `changes` value), are deferred: they stay `PENDING`, untouched, until the next run (see Write order across retries) |
| Transport error (any exception while sending or reading the answer, including a 2xx CREATE answer without a usable id; HTTP 401, 408, 429, 5xx; 403 with `REQUEST_LIMIT_EXCEEDED`) | The sync fails with the error. The entry stays first in line with `attempts` + 1 and `lastError`; the later entries are not sent. `reSync` retries it |

**Write order across retries.** When an entry is rejected at the record level, every later entry for the same record (same soup and `recordId`) is deferred for the rest of the run, and so are the entries whose `parentRef` or top-level `changes` value is that record id (a dependent of a rejected CREATE, for instance). Deferred entries stay `PENDING` and untouched: not sent, no `attempts` increment, no `lastError`. The sync goes on with unrelated records and ends `DONE`. An upsert CREATE (a CREATE whose `changes` hold an `externalIdFieldName` value that is a nonblank string that is not a local id, or a JSON number that can be preserved, used in its canonical string form: integers that fit a Long as plain digits (`12345`, never `12345.0` or `1.2345E4`); floating-point values (and non-integral decimals) only if their shortest round-trip decimal form has at most 15 significant digits, written without exponent (`12345.5`, `0.1`); a number that cannot be preserved (`BigDecimal("123456789012345.678")` comes back from persistence as a double) is rejected like the unsupported types below, with `lastError` "External id <field> is a number whose precision cannot be preserved; store it as a string" (a string always carries a long decimal exactly); JSON null, absent, blank, whitespace-only and local-id values make it a plain POST that leaves the external id field out of its body; a boolean, object or array value is rejected before any request as a record-level error (`lastError` "unsupported external id value type", `attempts` + 1, the entry stays, the sync goes on and the usual blocking applies), and the same value serves the request, the lookup and the lookup cache) can land on an existing record, so while something is blocked in the run its destination is looked up first (`GET /sobjects/<type>/<field>/<url-encoded value>?fields=Id`; no lookup when nothing is blocked). It is deferred if the lookup finds the id of a blocked record of the same type, or if its external id value equals that of a blocked (rejected or deferred) upsert CREATE of the same type (no lookup needed: that CREATE has no server id yet). A 404 or the id of an unblocked record lets it go. A lookup failing like a transport error fails the sync (the CREATE entry keeps `attempts` + 1 and `lastError`, and is not sent); a 2xx answer without a usable `Id` (missing, blank or whitespace-only, or not parseable) is a transport-class failure too; any other non-404 answer (300, 403, other 4xx) is inconclusive and defers the CREATE. A deferred upsert CREATE resolves its destination the same way (only if entries follow it): the id found is blocked (later entries for it, in any soup, wait), a 404 blocks nothing more, a failing lookup fails the sync with `attempts` + 1 once on the CREATE, an inconclusive answer defers later server-id entries of that type. Each (`sobjectType`, value) is looked up at most once as long as no write succeeded in between: the cache is cleared only after a write the server accepted with a 2xx (CREATE, UPDATE, DELETE or upsert), since a write can move an external id to another record; rejected writes, lookups, a DELETE of a local id (no call) and a DELETE answered 404 (nothing changed) do not clear it. A rejected upsert CREATE is resolved with the same lookup (only if entries follow it): the id found is blocked like a rejected record (later entries for it, in any soup, wait); a 404 blocks nothing more; a failing lookup (including a 2xx answer without a usable `Id`) fails the sync, the rejected entry keeping `attempts` + 1 once; an inconclusive answer (300, 403, other 4xx) defers every later entry of that `sobjectType` that addresses a server id. Plain CREATEs are never affected. A DELETE of a record that still has a local id makes no server call, is never deferred, and removes the record and all its entries. On the next run the rejected entry is again first for its record, so an older edit is never sent after a newer one. A successful DELETE removes the record and every other queue entry for it.

**Local ids are unique in the store.** Local ids are used as references between soups (`parentRef`, lookup values in `changes`), so `createLocal` rejects a caller-supplied id already used by a record of another soup or by a queue entry of another soup. A CREATE rewrites the `recordId` of its own record's entries only within its soup; `parentRef` and `changes` values are rewritten store-wide. A CREATE answer (or the 204 lookup) without a nonblank string id is a transport error: the entry is kept and the record is not rewritten. Generated local ids go through the same uniqueness check. Only one queue replay at a time runs per store (a lock held for the whole run, taken before the queue is read), so two sync managers on the same store never send the same entry twice and a waiting run reads the fresh queue.

**Temp-id remapping.** After a CREATE succeeds, in one transaction: the replayed entry is removed, the record is rewritten in place by soup entry id (`Id` = server id, flags cleared, `__last_error__` removed; no second row), and the `PENDING` entries are rewritten: `recordId` only for the entries of the CREATE's own soup, while `parentRef` and the top-level `changes` values that are exactly the local id (a lookup to the new record) are rewritten in every soup. The record is cleaned only if no other entry for it remains; otherwise it stays dirty (`__locally_created__` false). The external id of an upsert is URL-encoded in the request path. An in-memory map for the run is also applied to the request being built. Limitation: lookup fields in the record-soup copies of later, still-unsent records keep the local id; their queue entries are remapped (that is what is sent) and the copies heal on the next sync down. If a CREATE is rejected, later entries referencing its local id are deferred (see above).

### Callbacks

```kotlin
interface SyncUpdateCallback {
    fun onUpdate(sync: SyncState)
}

interface CleanResyncGhostsCallback {
    fun onSuccess(numRecords: Int)
    fun onError(e: Exception?)
}
```

### Exceptions

| Exception | When thrown |
|---|---|
| `MobileSyncException` | Base runtime exception |
| `SyncManagerStoppedException` | Sync submitted to a stopped/stopping manager |
| `ReSyncException.FailedToStart` | Coroutine `suspendReSync` — sync could not start |
| `ReSyncException.FailedToFinish` | Coroutine `suspendReSync` — sync ended in `FAILED`/`STOPPED` |
| `CleanResyncGhostsException.FailedToStart` | Coroutine ghost cleanup — could not start |
| `CleanResyncGhostsException.FailedToFinish` | Coroutine ghost cleanup — ended in error |

---

## SyncState

`com.salesforce.androidsdk.mobilesync.util.SyncState`

Persisted as entries in `syncs_soup` (indexed on `type`, `name`, `status`).

### Key Fields

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | SmartStore entry ID |
| `name` | `String?` | Optional stable identifier |
| `type` | `Type` | `syncDown` or `syncUp` |
| `target` | `SyncTarget` | Serialized as JSON |
| `options` | `SyncOptions` | Serialized as JSON |
| `soupName` | `String` | Target SmartStore soup |
| `status` | `Status?` | See below |
| `progress` | `Int` | 0–100 |
| `totalSize` | `Int` | −1 until known |
| `maxTimeStamp` | `Long` | Watermark for incremental sync (epoch ms) |
| `startTime` / `endTime` | `Long` | Epoch ms |
| `error` | `String?` | Last error message |

### Status Enum

`NEW` → `RUNNING` → `DONE`  
`RUNNING` → `FAILED` (exception)  
`RUNNING` → `STOPPED` (stop requested or app killed)  
`STOPPED` → `RUNNING` (via `reSync`)

### MergeMode Enum

`OVERWRITE` (default) — always push/pull regardless of local changes.  
`LEAVE_IF_CHANGED` — skip locally-dirty records on sync-down; skip server-newer records on sync-up.

### SmartStore Lifecycle

```kotlin
SyncState.setupSyncsSoupIfNeeded(store)     // called at SyncManager init
SyncState.cleanupSyncsSoupIfNeeded(store)   // called at SyncManager init; fixes RUNNING→STOPPED
```

---

## SyncOptions

`com.salesforce.androidsdk.mobilesync.util.SyncOptions`

```kotlin
class SyncOptions(val fieldlist: List<String>?, val mergeMode: MergeMode)
```

### Factory Methods

```kotlin
SyncOptions.optionsForSyncDown(mergeMode: MergeMode): SyncOptions   // fieldlist = null
SyncOptions.optionsForSyncUp(fieldlist: List<String>): SyncOptions  // OVERWRITE
SyncOptions.optionsForSyncUp(fieldlist: List<String>, mergeMode: MergeMode): SyncOptions
SyncOptions.fromJSON(options: JSONObject): SyncOptions
```

`fieldlist` on `SyncOptions` is the legacy location; `SyncUpTarget` now prefers `createFieldlist` and `updateFieldlist` on the target directly.

---

## Sync-Down Targets

### Target Class Hierarchy

```
SyncDownTarget (abstract)
├── SoqlSyncDownTarget
│   └── ParentChildrenSyncDownTarget
├── SoslSyncDownTarget
├── MruSyncDownTarget
├── RefreshSyncDownTarget
├── BriefcaseSyncDownTarget
├── LayoutSyncDownTarget
└── MetadataSyncDownTarget
```

All targets serialize themselves to JSON (including `androidImpl` = fully-qualified class name for custom targets). Deserialized via `SyncDownTarget.fromJSON(target: JSONObject)`.

---

### SoqlSyncDownTarget

```kotlin
SoqlSyncDownTarget(query: String)
SoqlSyncDownTarget(
    idFieldName: String?,
    modificationDateFieldName: String?,
    query: String,
    maxBatchSize: Int = DEFAULT_BATCH_SIZE
)
```

The query is auto-mutated at construction to ensure `Id`, `LastModifiedDate`, and an ORDER BY clause are present. `isSyncDownSortedByLatestModification` returns `true` when the ORDER BY is on `LastModifiedDate`, enabling mid-sync watermark updates.

**reSync filter (added automatically):**
```kotlin
SoqlSyncDownTarget.addFilterForReSync(query, modificationDateFieldName, maxTimeStamp): String
// prepends: LastModifiedDate > <ISO8601 timestamp>
```

---

### SoslSyncDownTarget

```kotlin
SoslSyncDownTarget(query: String)
```

One-shot — no pagination, no incremental timestamp. Ghost detection re-runs the full query.

---

### MruSyncDownTarget

```kotlin
MruSyncDownTarget(fieldlist: List<String>, objectType: String)
```

Fetches `/sobjects/<objectType>` describe for `recentItems` IDs, then runs `SELECT <fieldlist> FROM <objectType> WHERE Id IN (...)`. No incremental sync.

---

### RefreshSyncDownTarget

```kotlin
RefreshSyncDownTarget(fieldlist: List<String>, objectType: String, soupName: String)
RefreshSyncDownTarget(fieldlist, objectType, soupName, countIdsPerSoql: Int)  // max 500
```

Paginates through IDs already in the soup. First run fetches all; `reSync` fetches only records changed since the local max timestamp.

---

### BriefcaseSyncDownTarget

```kotlin
BriefcaseSyncDownTarget(infos: List<BriefcaseObjectInfo>)
BriefcaseSyncDownTarget(infos: List<BriefcaseObjectInfo>, countIdsPerRetrieve: Int)  // max 200
```

`BriefcaseObjectInfo` fields: `soupName`, `sobjectType`, `fieldlist`, `idFieldName`, `modificationDateFieldName`.

Uses `/connect/briefcase/priming-records` with relay-token pagination, then Collection Retrieve for full records. Routes records to different soups based on `attributes.type`. Ghost cleanup paginates the priming API without a timestamp filter.

---

### ParentChildrenSyncDownTarget

```kotlin
ParentChildrenSyncDownTarget(
    parentInfo: ParentInfo,
    parentFieldlist: List<String>,
    parentSoqlFilter: String?,
    childrenInfo: ChildrenInfo,
    childrenFieldlist: List<String>,
    relationshipType: RelationshipType   // LOOKUP or MASTER_DETAIL
)
```

`ParentInfo` fields: `sobjectType`, `soupName`, `idFieldName`, `modificationDateFieldName`, `externalIdFieldName`.

`ChildrenInfo` extends `ParentInfo` with: `sobjectTypePlural` (SOQL relationship name), `parentIdFieldName`.

Saves parents to the parent soup and children to the child soup within a single transaction. Ghost cleanup handles both soups.

---

### LayoutSyncDownTarget

```kotlin
LayoutSyncDownTarget(
    objectAPIName: String,
    formFactor: String,     // Large | Medium | Small
    layoutType: String,     // Compact | Full
    mode: String,           // Create | Edit | View
    recordTypeId: String?
)
```

Fetches `/ui-api/layout/<objectAPIName>`. Composite SmartStore key: `"<objectAPIName>-<formFactor>-<layoutType>-<mode>-<recordTypeId>"`. Ghost cleanup is a no-op.

---

### MetadataSyncDownTarget

```kotlin
MetadataSyncDownTarget(objectType: String)
```

Fetches `/sobjects/<objectType>/describe`. SmartStore key = `objectType`. Ghost cleanup is a no-op.

---

## Sync-Up Targets

### Target Class Hierarchy

```
SyncUpTarget (base)
├── BatchSyncUpTarget           (Composite Batch API; max 25/call)
│   └── CollectionSyncUpTarget  (Collections API; max 200/call) ← DEFAULT
├── ParentChildrenSyncUpTarget  (one parent-tree per Composite Batch call)
└── QueueSyncUpTarget           (replays the sync-up change queue, one request per entry; see "Local Write API and Sync-Up Queue")

AdvancedSyncUpTarget (interface) — implemented by the three advanced targets above (not `QueueSyncUpTarget`)
```

`CollectionSyncUpTarget` is the default when `androidImpl` is absent in the target JSON.

---

### SyncUpTarget (base)

```kotlin
SyncUpTarget(
    createFieldlist: List<String>? = null,
    updateFieldlist: List<String>? = null,
    idFieldName: String? = null,
    modificationDateFieldName: String? = null,
    externalIdFieldName: String? = null   // enables upsert
)
```

Standard server operations:

```kotlin
open fun createOnServer(syncManager, record, fieldlist): String?   // server ID or null
open fun updateOnServer(syncManager, record, fieldlist): Int       // HTTP status
open fun deleteOnServer(syncManager, record): Int                  // HTTP status
```

Conflict detection:

```kotlin
open fun isNewerThanServer(syncManager, record): Boolean
open fun areNewerThanServer(syncManager, records): MutableMap<String, Boolean>
```

Returns `true` (proceed with sync-up) when the local record is the same age or newer than the server record, when the local mod date is `null`, or when the record is locally created (no server counterpart).

---

### CollectionSyncUpTarget

Default target. Groups operations by type and sends via `/composite/sobjects`. Max batch size: 200.

Overrides `areNewerThanServer` to batch-fetch `LastModifiedDate` via Collection Retrieve (up to 200 records per call).

---

### BatchSyncUpTarget

```kotlin
BatchSyncUpTarget(createFieldlist, updateFieldlist, maxBatchSize: Int = 25)
```

Uses `/composite/batch`. Max batch size: 25 (Composite API sub-request limit).

---

### ParentChildrenSyncUpTarget

```kotlin
ParentChildrenSyncUpTarget(
    parentInfo: ParentInfo,
    parentCreateFieldlist, parentUpdateFieldlist,
    childrenInfo: ChildrenInfo,
    childrenCreateFieldlist, childrenUpdateFieldlist,
    relationshipType: RelationshipType
)
```

Max batch size: 1 (one parent + its children per Composite Batch call). Uses `@{refId.id}` reference substitution so child creates can reference a newly created parent's server ID in the same request.

`isNewerThanServer` checks the parent and all its children in one SOQL query.

---

### AdvancedSyncUpTarget Interface

```kotlin
interface AdvancedSyncUpTarget {
    val maxBatchSize: Int
    fun syncUpRecords(
        syncManager: SyncManager,
        records: List<JSONObject>,
        fieldlist: List<String>?,
        mergeMode: SyncState.MergeMode,
        syncSoupName: String
    )
}
```

---

## Layout and Metadata Sync

### LayoutSyncManager

```kotlin
LayoutSyncManager.getInstance(user: UserAccount?): LayoutSyncManager
LayoutSyncManager.getInstance(user: UserAccount?, communityId: String?): LayoutSyncManager

fun fetchLayout(
    objectAPIName: String,
    formFactor: String,
    layoutType: String,
    mode: String,
    recordTypeId: String?,
    syncMode: Constants.Mode,
    syncCallback: LayoutSyncCallback
)
```

Uses soup `sfdcLayouts`. `Constants.Mode`: `CACHE_ONLY`, `CACHE_FIRST`, `SERVER_FIRST`.

### MetadataSyncManager

```kotlin
MetadataSyncManager.getInstance(user: UserAccount?): MetadataSyncManager

fun fetchMetadata(
    objectType: String,
    mode: Constants.Mode,
    syncCallback: MetadataSyncCallback
)
```

Uses soup `sfdcMetadata`.

---

## SOQL and SOSL Builders

### SOQLBuilder

```kotlin
SOQLBuilder.getInstanceWithFields(fields: String): SOQLBuilder
SOQLBuilder.getInstanceWithFields(vararg fields: String?): SOQLBuilder
SOQLBuilder.getInstanceWithFields(fields: List<String>): SOQLBuilder
```

Chainable setters: `.from(from)`, `.where(where)`, `.with(with)`, `.groupBy(groupBy)`, `.having(having)`, `.orderBy(orderBy)`, `.limit(limit)`, `.offset(offset)`

Terminal methods: `.build()`, `.buildAndEncode()`, `.buildAndEncodeWithPath(path)`, `.buildWithPath(path)`

### SOQLMutator

Parses and modifies an existing SOQL string.

```kotlin
SOQLMutator(originalSoql: String)
```

Mutations: `.replaceSelectFields(fields)`, `.addSelectFields(fields)`, `.addWherePredicates(predicates)`, `.replaceOrderBy(fields)`

Queries: `.isSelectingField(field)`, `.hasOrderBy()`, `.isOrderingBy(fields)`

Output: `.asBuilder(): SOQLBuilder`

### SOSLBuilder

```kotlin
SOSLBuilder.getInstanceWithSearchTerm(searchTerm: String?): SOSLBuilder
```

Chainable: `.searchGroup(group)`, `.returning(SOSLReturningBuilder)`, `.divisionFilter(filter)`, `.dataCategory(category)`, `.limit(limit)`

Terminal: `.build()`, `.buildAndEncode()`, `.buildAndEncodeWithPath(path)`, `.buildWithPath(path)`

The search term is automatically escaped (special characters: `+ ^ ~ ' - [ ] { } ( ) & : !`).

### SOSLReturningBuilder

```kotlin
SOSLReturningBuilder.getInstanceWithObjectName(name: String): SOSLReturningBuilder
```

Chainable: `.fields(fields)`, `.where(where)`, `.orderBy(orderBy)`, `.limit(limit)`, `.withNetwork(networkId)`

---

## JSON-Driven Configuration

### SyncsConfig

```kotlin
SyncsConfig(context: Context, resourceId: Int)
SyncsConfig(context: Context, assetPath: String)

fun createSyncs(store: SmartStore)   // idempotent: skips existing named syncs
fun hasSyncs(): Boolean
```

### MobileSyncSDKManager

```kotlin
fun setupGlobalSyncsFromDefaultConfig()  // reads globalsyncs.json → global store
fun setupUserSyncsFromDefaultConfig()    // reads usersyncs.json → current user store
```

Call these from `SalesforceSDKManager.postLaunchAction` after login.

### JSON Format

```json
{
  "syncs": [
    {
      "syncType": "syncDown",
      "syncName": "myAccountsSync",
      "soupName": "accounts",
      "target": {
        "type": "soql",
        "query": "SELECT Id, Name, Phone FROM Account ORDER BY LastModifiedDate"
      },
      "options": { "mergeMode": "OVERWRITE" }
    },
    {
      "syncType": "syncUp",
      "syncName": "myAccountsUpSync",
      "soupName": "accounts",
      "target": { "type": "rest" },
      "options": {
        "fieldlist": ["Name", "Phone"],
        "mergeMode": "LEAVE_IF_CHANGED"
      }
    }
  ]
}
```

Target `type` values: `soql`, `sosl`, `mru`, `refresh`, `briefcase`, `parent_children`, `layout`, `metadata`, `custom`.

For `custom` targets, add `"androidImpl": "com.example.MyTarget"` — the class must extend `SyncDownTarget` or `SyncUpTarget` and have a `fromJSON` static method.

---

## Testing

MobileSync tests live in `libs/MobileSync/src/androidTest/`:

```bash
./gradlew :libs:MobileSync:connectedAndroidTest
```

Key test classes: `SyncManagerTest`, `SyncManagerTestCase`, `ParentChildrenSyncTest`, `BriefcaseSyncTest`.

The test suite uses a real SmartStore database; there is no mock of the SmartStore layer. REST calls are mocked via `MockRestClient`.

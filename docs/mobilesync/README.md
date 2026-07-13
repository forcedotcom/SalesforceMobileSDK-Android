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
| `manager` | `SyncManager`, `LayoutSyncManager`, `MetadataSyncManager` |
| `model` | `Layout`, `Metadata`, `SalesforceObject` |
| `target` | All sync targets (see below) |
| `util` | `SyncState`, `SyncOptions`, `SOQLBuilder`, `SOQLMutator`, `SOSLBuilder`, `ParentInfo`, `ChildrenInfo`, `BriefcaseObjectInfo`, `Constants` |

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
└── ParentChildrenSyncUpTarget  (one parent-tree per Composite Batch call)

AdvancedSyncUpTarget (interface) — implemented by all three above
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

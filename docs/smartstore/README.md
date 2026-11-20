# SmartStore — Salesforce Mobile SDK for Android

This document covers the SmartStore library in `libs/SmartStore`. All classes live under `com.salesforce.androidsdk.smartstore`. The library is written in Java (no Kotlin sources) and depends on `libs/SalesforceSDK` and on SQLCipher for Android (`net.zetetic:sqlcipher-android`, version pinned in `gradle/libs.versions.toml`).

Everything below was checked against the source in this repo (`dev141` base). Where something could not be confirmed in code it is called out explicitly. Section [Differences From Other Docs / Platforms](#differences-from-other-docs--platforms) lists features you may expect (for example `SoupSpec` and external storage) that do **not** exist in this code base.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Getting a Store](#getting-a-store)
4. [Key Classes](#key-classes)
5. [Soups](#soups)
6. [Records](#records)
7. [Queries](#queries)
8. [Smart SQL](#smart-sql)
9. [Full-Text Search](#full-text-search)
10. [Transactions](#transactions)
11. [Encryption and Keys](#encryption-and-keys)
12. [Declarative Store Configuration](#declarative-store-configuration)
13. [Key-Value Stores](#key-value-stores)
14. [Errors, Threading and Limits](#errors-threading-and-limits)
15. [Differences From Other Docs / Platforms](#differences-from-other-docs--platforms)
16. [Testing](#testing)
17. [Key Files](#key-files)

---

## Overview

SmartStore is an encrypted, on-device, JSON document store. A **soup** is a named collection of JSON objects (a "soup element" or "entry"). Each soup is backed by one SQLite table inside a SQLCipher database. Paths into the JSON documents can be **indexed** so they can be queried with exact / range / like / full-text predicates or referenced in **Smart SQL** (a SQL dialect that addresses soups and JSON paths by name).

Public entry points:

| Entry point | Purpose |
|---|---|
| `SmartStoreSDKManager` | SDK manager subclass; hands out `SmartStore` instances (global, per-user, named) and key-value stores |
| `SmartStore` | The store API: soups, records, queries, transactions |
| `QuerySpec` / `IndexSpec` | Query and index descriptions |
| `StoreConfig` | Registers soups from a JSON resource/asset |
| `KeyValueEncryptedFileStore` | Separate encrypted file-based key/value store (not SQLite) |

`MobileSyncSDKManager` (in `libs/MobileSync`) extends `SmartStoreSDKManager`, so MobileSync apps get SmartStore through it.

---

## Architecture

```
App code
 └── SmartStoreSDKManager.getInstance()           ← factory, derives encryption key
      └── SmartStore (one instance per call; cheap wrapper)
           ├── SmartSqlHelper   (per SQLiteDatabase)  ← smart sql -> sql
           ├── DBHelper         (per SQLiteDatabase)  ← LRU caches + low-level insert/update/delete/query
           ├── AlterSoupLongOperation / LongOperation ← resumable alterSoup
           └── DBOpenHelper     (cached per db file name; extends SQLCipher SQLiteOpenHelper)
                └── net.zetetic.database.sqlcipher.SQLiteDatabase  (WAL enabled)
                     └── <app>/databases/<dbName>[<suffix>].db
```

Layers from top to bottom:

1. `SmartStoreSDKManager.getSmartStore(...)` / `getGlobalSmartStore(...)` compute the database file name, call `DBOpenHelper.getOpenHelper(key, ctx, dbName, account, communityId)` and wrap the result in a new `SmartStore`.
2. `SmartStore` methods take `synchronized (db)` on the `SQLiteDatabase` object, resolve the soup name to a table name and index specs through `DBHelper`, then run SQL.
3. `DBHelper` (singleton per `SQLiteDatabase`, `DBHelper.getInstance(db)`) holds LRU caches (1024 entries each) for: soup exists, soup table name, index specs, has-FTS, compiled "next id" statements, insert helpers, raw count statements.
4. `DBOpenHelper` opens the SQLCipher database with the key, enables WAL (`onConfigure`), and creates the meta tables on first creation (`onCreate` -> `SmartStore.createMetaTables`).

### Database layout

Meta tables (created by `SmartStore.createMetaTables`):

| Table | Constant | Columns | Role |
|---|---|---|---|
| `soup_attrs` | `SmartStore.SOUP_ATTRS_TABLE` | `id INTEGER PRIMARY KEY AUTOINCREMENT`, `soupName TEXT` | One row per soup; the `id` gives the backing table name |
| `soup_index_map` | `SmartStore.SOUP_INDEX_MAP_TABLE` | `soupName`, `path`, `columnName`, `columnType` | One row per `IndexSpec` of each soup |
| `long_operations_status` | (protected) | `id`, `type`, `details`, `status`, `created`, `lastModified` | Journal for resumable long operations (currently only `alterSoup`) |

Per-soup tables:

| Table | Name | Columns |
|---|---|---|
| Soup table | `TABLE_<soupAttrsId>` (`SmartStore.getSoupTableName(long)`) | `id INTEGER PRIMARY KEY AUTOINCREMENT`, `soup TEXT` (the JSON), `created INTEGER`, `lastModified INTEGER`, plus one column `TABLE_<id>_<i>` per `string`/`integer`/`floating`/`full_text` index spec (types `TEXT`/`INTEGER`/`REAL`/`TEXT`); `<i>` is the position in the `IndexSpec` array, so `json1` specs consume an index number without having a column |
| FTS table | `TABLE_<id>_fts` | Virtual table (`fts5` by default, `fts4` selectable) with one column per `full_text` index; only created if the soup has at least one `full_text` index. Rows are keyed by `rowid` = soup entry id |
| DB indexes | `TABLE_<id>_created_idx`, `TABLE_<id>_lastModified_idx`, `TABLE_<id>_<i>_idx` | One per index spec, plus the two date columns. For `json1` specs the index is a functional index on `json_extract(soup, '$.<path>')` |

`DBOpenHelper.DB_VERSION` is 3 (history in the source: 2 added `long_operations_status`, 3 renamed `soup_names` to `soup_attrs`). `onUpgrade` is empty.

---

## Getting a Store

`com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager extends SalesforceSDKManager`.

### Initialization

```java
// Application.onCreate()
SmartStoreSDKManager.initNative(getApplicationContext(), MainActivity.class);
// overloads: (ctx, main, loginActivity) and (ctx, main, loginActivity, nativeLoginActivity)
```

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SmartStoreSDKManager.initNative(applicationContext, MainActivity::class.java)
    }
}
```

`init` also runs `SmartStoreUpgradeManager.getInstance().upgrade()`, which currently only records the SmartStore version in shared prefs (key `smart_store_version`); there are no upgrade steps. `getInstance()` throws `RuntimeException` if `initNative` was not called.

### Accessors

```java
SmartStoreSDKManager mgr = SmartStoreSDKManager.getInstance();

// Global stores (not tied to any user)
SmartStore getGlobalSmartStore();
SmartStore getGlobalSmartStore(String dbName);          // null/empty -> "smartstore"

// User stores
SmartStore getSmartStore();                              // current (cached) user
SmartStore getSmartStore(UserAccount account);
SmartStore getSmartStore(UserAccount account, String communityId);
SmartStore getSmartStore(String dbNamePrefix, UserAccount account, String communityId);
```

```kotlin
val mgr = SmartStoreSDKManager.getInstance()
val global = mgr.globalSmartStore
val named = mgr.getGlobalSmartStore("myCache")
val userStore = mgr.smartStore                       // current user
val other = mgr.getSmartStore("orders", account, null)
```

Each call returns a **new `SmartStore` object**, but `DBOpenHelper` instances are cached by full file name in a static map, so all `SmartStore` objects for the same file share the same `SQLiteDatabase` (and the same `DBHelper`/`SmartSqlHelper` and caches).

`getSmartStore()` with no logged-in user passes a null account and therefore resolves to the global database name (see below). `getUserAccountManager().getCachedCurrentUser()` is what is used for "current user".

### Database naming (global vs user, named, community)

Full file name = `dbNamePrefix` + suffix + `.db`, where `dbNamePrefix` defaults to `DBOpenHelper.DEFAULT_DB_NAME` (`"smartstore"`) and:

| Store | Suffix | Example |
|---|---|---|
| Global | none | `smartstore.db`, `myCache.db` |
| User | `account.getCommunityLevelFilenameSuffix(communityId)` = `_<orgId>_<userId>_<communityId or "internal">` | `smartstore_00Dxx..._005xx..._internal.db` |

`communityId` null or the internal community id maps to `internal`. So one user in two communities has two distinct databases; the default `getSmartStore(account)` always uses the internal one (communityId null), regardless of `account.getCommunityId()`. `getUserStoresPrefixList()` is the exception: it passes `account.getCommunityId()`.

`DBOpenHelper.getOpenHelper` also emits an analytics event (`globalSmartStoreInit` / `userSmartStoreInit`, with the number of existing stores) the first time a given file is opened in the process. `getGlobalSmartStore` registers app feature `GS`, `getSmartStore` registers `US` (`smartstore.app.Features`).

### Existence, listing, removal

```java
boolean hasGlobalSmartStore(String dbName);
boolean hasSmartStore();  hasSmartStore(account);  hasSmartStore(account, communityId);
boolean hasSmartStore(String dbNamePrefix, UserAccount account, String communityId);

void removeGlobalSmartStore(String dbName);
void removeSmartStore();  removeSmartStore(account);  removeSmartStore(account, communityId);
void removeSmartStore(String dbNamePrefix, UserAccount account, String communityId);
void removeAllGlobalStores();
void removeAllUserStores();  removeAllUserStores(UserAccount account);

List<String> getGlobalStoresPrefixList();
List<String> getUserStoresPrefixList();  getUserStoresPrefixList(UserAccount account);
```

Behavior worth knowing:

- `removeSmartStore(prefix, account, communityId)` -> `DBOpenHelper.deleteDatabase`: closes and uncaches the helper, calls `Context.deleteDatabase`, and **if `communityId` is empty and an account is given, also deletes every database file of that user whose name starts with `prefix + userLevelSuffix`** (i.e. all communities).
- On logout, `SmartStoreSDKManager.cleanUp(UserAccount)` deletes all databases (`DBOpenHelper.deleteAllDatabases`) and all key-value stores for that user. With a null account it calls only `DBOpenHelper.deleteAllUserDatabases`; user key-value stores are **not** removed in that case. `removeAllUserStores(account)` likewise removes only databases. `DBOpenHelper.deleteDatabase` catches and logs all exceptions, so a failed delete is silent.
- `hasSmartStore` checks `ctx.getDatabasePath(name).exists()`, not whether the database can be opened.
- Global vs user listing relies on the org-id prefix `00D` in file names (`DBOpenHelper.ORG_KEY_PREFIX`): `getGlobalDatabasePrefixList` passes it as the "excluding" filter to `ManagedFilesHelper.getPrefixList`. The filter is a substring test, not a prefix test: `ManagedFilesHelper.getFiles` keeps files where `name.contains(suffix) && name.endsWith(ext) && (excl is empty || !name.contains(excl))`. So the global list is every `*.db` file in `<dataDir>/databases` whose name does not contain `00D` anywhere, which would also include any non-SmartStore `.db` file lacking `00D`. `getPrefixList` returns the name up to the first occurrence of the suffix (or of the extension).

---

## Key Classes

### `SmartStore`

`com.salesforce.androidsdk.smartstore.store.SmartStore`

```java
public SmartStore(SQLiteOpenHelper dbOpenHelper)
public SQLiteDatabase getDatabase()          // getWritableDatabase(); first call resumes long operations

// Soups
public void registerSoup(String soupName, IndexSpec[] indexSpecs)
public boolean hasSoup(String soupName)
public void dropSoup(String soupName)
public void dropAllSoups()
public List<String> getAllSoupNames()
public void clearSoup(String soupName)
public IndexSpec[] getSoupIndexSpecs(String soupName)
public boolean hasIndexForPath(String soupName, String path)
public void alterSoup(String soupName, IndexSpec[] indexSpecs, boolean reIndexData) throws JSONException
public void reIndexSoup(String soupName, String[] indexPaths, boolean handleTx)

// Records
public JSONObject create(String soupName, JSONObject soupElt) throws JSONException
public JSONObject create(String soupName, JSONObject soupElt, boolean handleTx) throws JSONException
public JSONObject update(String soupName, JSONObject soupElt, long soupEntryId) throws JSONException
public JSONObject update(String soupName, JSONObject soupElt, long soupEntryId, boolean handleTx) throws JSONException
public JSONObject upsert(String soupName, JSONObject soupElt) throws JSONException
public JSONObject upsert(String soupName, JSONObject soupElt, String externalIdPath) throws JSONException
public JSONObject upsert(String soupName, JSONObject soupElt, String externalIdPath, boolean handleTx) throws JSONException
public JSONArray retrieve(String soupName, Long... soupEntryIds) throws JSONException
public long lookupSoupEntryId(String soupName, String fieldPath, String fieldValue)
public void delete(String soupName, Long... soupEntryIds)
public void delete(String soupName, Long[] soupEntryIds, boolean handleTx)
public void deleteByQuery(String soupName, QuerySpec querySpec)
public void deleteByQuery(String soupName, QuerySpec querySpec, boolean handleTx)

// Queries
public JSONArray query(QuerySpec querySpec, int pageIndex) throws JSONException
public JSONArray queryWithArgs(QuerySpec querySpec, int pageIndex, String... whereArgs) throws JSONException
public void queryAsString(StringBuilder resultBuilder, QuerySpec querySpec, int pageIndex)
public int countQuery(QuerySpec querySpec)
public String convertSmartSql(String smartSql)

// Transactions
public void beginTransaction()
public void setTransactionSuccessful()
public void endTransaction()

// Misc
public static Object project(JSONObject soup, String path)
public static void setLicenseKey(String licenseKey)             // SQLCipher commercial/enterprise
public int getDatabaseSize()
public void setCaptureExplainQueryPlan(boolean)  /  public JSONObject getLastExplainQueryPlan()
public String getSQLCipherVersion()  getCipherProviderVersion()  boolean getCipherFIPSStatus()
public List<String> getRuntimeSettings()  getCompileOptions()
public FtsExtension getFtsExtension()  /  setFtsExtension(FtsExtension)   // setter documented as test-only
```

Nested types: `SmartStore.Type` (`string`, `integer`, `floating`, `full_text`, `json1`), `SmartStore.TypeGroup`, `SmartStore.FtsExtension` (`fts4`, `fts5`), `SmartStore.SmartStoreException extends RuntimeException`.

### `IndexSpec`

```java
public IndexSpec(String path, Type type)
public IndexSpec(String path, Type type, String columnName)   // columnName filled in by the store; callers normally omit it
public static IndexSpec[] fromJSON(JSONArray)  /  fromJSON(JSONObject)   // keys: path, type, columnName; a missing columnName yields "" (optString), not null
public JSONObject toJSON()  /  static JSONArray toJSON(IndexSpec[])
public static boolean hasFTS(IndexSpec[])  /  hasJSON1(IndexSpec[])
```

### `QuerySpec`

See [Queries](#queries). Immutable; created only through static `build*QuerySpec` factories or `QuerySpec.fromJSON(soupName, json)`.

### `SmartSqlHelper`

`SmartSqlHelper.getInstance(SQLiteDatabase)`, `convertSmartSql(SQLiteDatabase, String)`, constant `SmartSqlHelper.SOUP = "_soup"`, `SmartSqlHelper.SmartSqlException extends SmartStoreException`. See [Smart SQL](#smart-sql).

### `StoreCursor`

`StoreCursor(SmartStore, QuerySpec)` — a lightweight "cursor" that stores the query spec, total entries (via `countQuery`) and a current page index; no SQLite cursor is kept open. `moveToPageIndex(int)`, `getDataSerialized(SmartStore)`, `getDataDeserialized(SmartStore)`. JSON keys: `cursorId`, `pageSize`, `currentPageIndex`, `totalEntries`, `totalPages`, `currentPageOrderedEntries`. Used by bridge layers (hybrid); native callers normally use `query` directly.

### Inspectors

`ui/SmartStoreInspectorActivity` and `ui/KeyValueStoreInspectorActivity` are debug UIs. `SmartStoreSDKManager.getDevActions` adds "Inspect SmartStore" and "Inspect KeyValue Store" and `getDevSupportInfo` adds a "Smart Store" section (SQLCipher version, compile options, runtime settings, store lists).

---

## Soups

### registerSoup

```kotlin
val store = SmartStoreSDKManager.getInstance().smartStore
store.registerSoup("accounts", arrayOf(
    IndexSpec("Id", SmartStore.Type.string),
    IndexSpec("Name", SmartStore.Type.full_text),
    IndexSpec("Revenue", SmartStore.Type.floating),
    IndexSpec("Address.city", SmartStore.Type.json1)
))
```

```java
store.registerSoup("accounts", new IndexSpec[] {
    new IndexSpec("Id", SmartStore.Type.string),
    new IndexSpec("Name", SmartStore.Type.full_text),
});
```

Behavior (`SmartStore.registerSoup`):

- Throws `SmartStoreException` if `soupName == null` or `indexSpecs.length == 0` (at least one index spec is required).
- **If the soup already exists it silently returns** without comparing index specs. Use `alterSoup` to change them.
- In one transaction: inserts a row in `soup_attrs`, derives the table name `TABLE_<id>`, then `registerSoupUsingTableName` creates the table, FTS table (if any `full_text`), DB indexes, `soup_index_map` rows, and primes the `DBHelper` caches.
- Emits a `registerSoup` analytics event listing `JSON1`/`FTS` features (synchronously when `getIsTestRun()`, otherwise on a single-thread executor).

### Index types

| `SmartStore.Type` | Column type | How the value is stored/indexed |
|---|---|---|
| `string` | `TEXT` | Value copied to column `TABLE_<id>_<i>` using `value.toString()` |
| `integer` | `INTEGER` | Cast `(Number) value` -> `longValue()` |
| `floating` | `REAL` | Cast `(Number) value` -> `doubleValue()` |
| `full_text` | `TEXT` | Stored like `string` **and** mirrored into the `_fts` virtual table column |
| `json1` | none | No column; the index is a functional index on `json_extract(soup, '$.<path>')`. Value is read from the JSON at query time. Single quotes in the path are escaped |

`TypeGroup` groups the types: `value_extracted_to_column` (string/integer/floating/full_text), `value_extracted_to_fts_column` (full_text), `value_indexed_with_json_extract` (json1).

Index paths use dot notation (`a.b.c`). `SmartStore.project` handles arrays: projecting through a JSON array returns an array of the sub-values (e.g. `a.b.c` on `{"a":{"b":[{"c":"x"},{"c":"y"}]}}` -> `["x","y"]`). If a value cannot be cast (e.g. a string in an `integer` index) the error is logged and `NULL` is written to the column; the record is still saved.

### alterSoup

```java
store.alterSoup("accounts", newIndexSpecs, true /* reIndexData */);
```

Runs `AlterSoupLongOperation`, a journaled multi-step operation. Each step runs in its own transaction and records its progress in `long_operations_status`:

`STARTING` -> `RENAME_OLD_SOUP_TABLE` -> `DROP_OLD_INDEXES` -> `REGISTER_SOUP_USING_TABLE_NAME` -> `COPY_TABLE` -> `RE_INDEX_SOUP` (only if `reIndexData`) -> `DROP_OLD_TABLE` (row deleted).

- Same table name is kept (`TABLE_<id>`); the old data lives in `TABLE_<id>_old` (and `_old_fts`) until the end.
- `COPY_TABLE` copies `id`, `soup`, `created`, `lastModified` and the columns of paths that exist in both old and new specs and whose column type is compatible (or the old one was `json1`); `json1` targets have no column to fill.
- `RE_INDEX_SOUP` re-indexes only paths whose `path|type` pair is new (`IndexSpec.getPathType()`).
- If `reIndexData` is false, new index columns stay `NULL` until the records are rewritten or `reIndexSoup` is called.
- If the process dies mid-way, `SmartStore.getDatabase()` calls `resumeLongOperations()` the first time the database is obtained after the `SmartStore` object is created (flag `dbJustOpened`), re-reading `long_operations_status` and continuing from the last completed step. Exceptions during resume are logged, not thrown.
- Throws `SmartStoreException("Soup: x does not exist")` for an unknown soup.

### reIndexSoup

```java
store.reIndexSoup("accounts", arrayOf("Name"), /* handleTx */ true)
```

Reads every row's JSON and rewrites the index columns (and FTS columns) for the given paths. Paths without an existing index spec are skipped with a warning; `json1` specs are skipped (nothing to extract). Does nothing if no eligible spec remains. Note `handleTx == true` calls `setTransactionSuccessful()` in the `finally` block, so a failure part way still commits what was done.

### clearSoup / dropSoup / hasSoup

| Method | Effect |
|---|---|
| `clearSoup(name)` | Deletes all rows from the soup table (and FTS table). Soup definition stays. Throws if the soup does not exist |
| `dropSoup(name)` | `DROP TABLE` (and `_fts`), deletes rows in `soup_attrs`/`soup_index_map`, evicts caches. No-op if the soup does not exist. **There is no `removeSoup` method in this API; the name is `dropSoup`** |
| `dropAllSoups()` | `dropSoup` for every name from `getAllSoupNames()` |
| `hasSoup(name)` | Cached existence check |
| `getAllSoupNames()` | Reads `soup_attrs`, ordered by `soupName` |
| `getSoupIndexSpecs(name)` | Returns the specs with `columnName` populated; throws if soup does not exist |
| `hasIndexForPath(soup, path)` | True if the path has an index spec |

---

## Records

### Reserved fields

Added to the JSON by the store:

| Constant | JSON key | Column | Set when |
|---|---|---|---|
| `SmartStore.SOUP_ENTRY_ID` | `_soupEntryId` | `id` | create and update (`soupElt.put`) |
| `SmartStore.SOUP_LAST_MODIFIED_DATE` | `_soupLastModifiedDate` | `lastModified` | create and update (`soupElt.put`), epoch millis |
| `SmartStore.SOUP_CREATED_DATE` | `_soupCreatedDate` | `created` | **Column only.** `create` writes the `created` column but does **not** put `_soupCreatedDate` into the stored JSON. The name is only resolvable inside Smart SQL (see below) |

`create`/`update`/`upsert` **mutate the `JSONObject` you pass in** (adding `_soupEntryId` and `_soupLastModifiedDate`) and return that same object. `create` returns `null` if the id returned by the insert does not equal the expected id (`DBHelper.insert` itself throws `SQLException` on failure); `update` returns `null` if the affected row count is not 1.

### create / update / upsert

```kotlin
val rec = JSONObject().put("Id", "001xx").put("Name", "Acme")

store.create("accounts", rec)                         // commits
store.update("accounts", rec, soupEntryId)            // commits; returns null if no such row
store.upsert("accounts", rec)                         // uses _soupEntryId in rec: update if present, else create
store.upsert("accounts", rec, "Id")                   // external id path
store.upsert("accounts", rec, "Id", false)            // handleTx = false: caller owns the transaction
```

Upsert semantics (`upsert(soup, elt, externalIdPath, handleTx)`):

- `externalIdPath == "_soupEntryId"` (the default overload): update if `elt` has `_soupEntryId`, otherwise create. A `_soupEntryId` that does not exist in the table makes `update` return `null` (it does **not** fall back to create).
- Any other `externalIdPath`: the value is projected from the element; if it is null/missing, throws `SmartStoreException("For upsert with external ID path '...', value cannot be empty for any entries.")`. Otherwise `lookupSoupEntryId` is called; found -> update, not found -> create.
- `lookupSoupEntryId` queries the **index column** for `fieldPath`, so the path must be indexed (otherwise `DBHelper.getColumnNameForPath` throws `SmartStoreException`) and it throws if more than one row matches. It compares with `column = ?` against `value.toString()`.
- `handleTx` defaults to `true` in the overloads without the flag. With `true` the method opens its own transaction and only calls `setTransactionSuccessful()` on success; with `false` it assumes you are inside one (see [Transactions](#transactions)).
- Entry ids come from `SQLITE_SEQUENCE` (`DBHelper.getNextId`), i.e. monotonically increasing per soup table, never reused.
- An id passed in `_soupEntryId` is trusted; `update` always overwrites `_soupEntryId` in the JSON with the `soupEntryId` argument.

### retrieve

```java
JSONArray rows = store.retrieve("accounts", 1L, 2L, 3L);
```

Returns the JSON of the matching entries (`id IN (...)`), in the database's order, not necessarily the argument order; missing ids are simply absent. Throws if the soup does not exist.

### delete

```java
store.delete("accounts", 1L, 2L);                         // commits
store.delete("accounts", new Long[]{1L, 2L}, false);      // caller-managed transaction
```

Also removes the rows from the FTS table. Deleting with an empty id list produces `id IN ()` (accepted by SQLite, deletes nothing; no test covers it).

### deleteByQuery

```java
store.deleteByQuery("accounts", QuerySpec.buildExactQuerySpec("accounts", "Id", "001xx", null, null, 100));
```

Builds `DELETE ... WHERE id IN (SELECT id FROM (<querySpec.idsSmartSql>) LIMIT <querySpec.pageSize>)` (and the same for the FTS `rowid`). Important consequences:

- **Only up to `pageSize` rows are deleted per call.** Loop (or use a large page size) to delete everything that matches.
- **The FTS table can go out of sync when more rows match than `pageSize`.** The soup table is deleted first; the FTS statement then re-runs the same sub-query (same `LIMIT`, same args) against the already-modified soup table. The first `pageSize` matches are gone, so the sub-query now returns the *next* `pageSize` matching entries, which are still in the soup. Their FTS rows are deleted, while the FTS rows of the entries that were really removed stay behind. Example: three matching entries 1, 2, 3 with `pageSize = 1` leave soup entries 2 and 3 but FTS rows 1 and 3. Two effects follow: (1) orphaned FTS rows (entry 1; match queries join back to the soup table by id, so they are not returned, but they are never cleaned up except by `clearSoup` / `dropSoup`); (2) **surviving entries missing from full-text search** (entry 2 no longer matches `match` queries) while entry 3 keeps a stale FTS row. When the number of matches is `<= pageSize` the soup delete removes every match, the FTS sub-query then finds nothing, and **all of the deleted entries' FTS rows stay orphaned** (verified with one match and `pageSize = 10`: zero soup rows, one FTS row); a large page size only avoids deleting surviving entries' FTS rows. Derived from the generated SQL (reproduced with sqlite); not covered by a test. Workaround: on soups with a full-text index, delete by ids (`delete(soupName, ids)`); that is the only consistency-preserving option. A `pageSize` at least as large as the match count avoids the missing-search-results effect but still orphans the FTS rows.
- For a smart-SQL `QuerySpec`, `idsSmartSql` is `SELECT id FROM (<your smartSql>)`, so your query must return the entry ids in a column named `id` (`{accounts:_soupEntryId}` converts to `TABLE_x.id`), e.g. `SELECT {accounts:_soupEntryId} FROM {accounts} WHERE ...`.

---

## Queries

### QuerySpec factories

All are `public static` on `QuerySpec`; each soup-based factory has an overload with a `String[] selectPaths` parameter (after the soup name) that restricts the returned columns; `buildSmartQuerySpec` has none.

```java
buildAllQuerySpec(String soupName, String orderPath, Order order, int pageSize)
buildExactQuerySpec(String soupName, String path, String exactMatchKey, String orderPath, Order order, int pageSize)
buildRangeQuerySpec(String soupName, String path, String beginKey, String endKey, String orderPath, Order order, int pageSize)
buildLikeQuerySpec(String soupName, String path, String likeKey, String orderPath, Order order, int pageSize)
buildMatchQuerySpec(String soupName, String path, String matchKey, String orderPath, Order order, int pageSize)   // full-text
buildSmartQuerySpec(String smartSql, int pageSize)
QuerySpec.fromJSON(String soupName, JSONObject querySpecJson)
```

`QuerySpec.QueryType` has exactly five values: `exact`, `range`, `like`, `match`, `smart`. **There is no `all` type**: `buildAllQuerySpec` creates a `range` query with a null path and null keys, which yields no `WHERE` clause. `QuerySpec.Order` is `ascending` (`ASC`) / `descending` (`DESC`).

| Type | Generated predicate (smart sql form) | Bind args |
|---|---|---|
| all (range, no path) | none | none |
| exact | `{soup:path} = ?` | `[matchKey]` |
| range | `>= ?`, `<= ?`, or `>= ? AND <= ?` depending on which of begin/end keys are non-null; none if both null | begin and/or end key |
| like | `{soup:path} LIKE ?` | `[likeKey]` |
| match | `{soup:_soupEntryId} IN (SELECT rowid FROM {soup}_fts WHERE {soup}_fts MATCH '<key>')` | none (match key is inlined, `'` doubled) |
| smart | whatever you pass | none from the spec; use `queryWithArgs` for `?` binds |

Select clause: `{soup:_soup}` (the full JSON) unless `selectPaths` is given. Order clause: only emitted if both `orderPath` and `order` are non-null. `idsSmartSql` and `countSmartSql` are derived alongside `smartSql` in the constructor.

Paths used in exact/range/like/order should be indexed to be efficient. Unindexed paths still work because Smart SQL falls back to `json_extract` (see below), but they are not index-assisted.

`QuerySpec.fromJSON` keys (constants on `QuerySpec`): `queryType`, `indexPath`, `matchKey`, `beginKey`, `endKey`, `likeKey`, `smartSql`, `orderPath`, `order` (default `ascending`), `pageSize` (required, `getInt`), `selectPaths`.

### Running queries

```kotlin
val spec = QuerySpec.buildLikeQuerySpec("accounts", "Name", "Acme%", "Name", QuerySpec.Order.ascending, 50)
val total = store.countQuery(spec)
val page0: JSONArray = store.query(spec, 0)      // pageIndex is zero-based
val page1: JSONArray = store.query(spec, 1)
```

```java
QuerySpec smart = QuerySpec.buildSmartQuerySpec(
    "SELECT {accounts:Name}, {accounts:Revenue} FROM {accounts} WHERE {accounts:Revenue} > ?", 100);
JSONArray rows = store.queryWithArgs(smart, 0, "1000000");
```

- Paging: `LIMIT <pageSize*pageIndex>,<pageSize>` wrapping the converted SQL (`SELECT * FROM (<sql>) LIMIT off,n`). There is no upper bound on `pageSize` enforced in SmartStore.
- Result shape: for non-smart queries without `selectPaths`, each element is the soup `JSONObject`. For smart queries, or any query with `selectPaths`, each element is a `JSONArray` of column values (nulls -> `null`, integers -> `long`, reals -> `double`, text -> string). A column named `soup` (or starting with `soup:`, which SQLite produces for duplicated names) is parsed as JSON into a `JSONObject`; aliasing it makes it come back as a string.
- `queryWithArgs` throws `SmartStoreException("whereArgs can only be provided for smart queries")` if `whereArgs != null` and the spec is not `smart`. Note that the Java varargs call `queryWithArgs(spec, 0)` passes an empty (non-null) array and so also throws for non-smart specs; use `query()` instead.
- `queryAsString(StringBuilder, QuerySpec, int)` builds the JSON text directly from the cursor without creating `JSONObject`s (cheaper for bridges). It escapes `\`, `"`, `/`, control chars in non-JSON string columns.
- `countQuery` runs `countSmartSql` (`SELECT count(*) FROM (<smartSql>)` for smart specs) through a cached compiled statement. It binds only `querySpec.getArgs()` (there is no `whereArgs` parameter), so a smart query containing `?` cannot be counted with binds. It returns -1 on `SQLiteDoneException`.
- `setCaptureExplainQueryPlan(true)` runs `EXPLAIN QUERY PLAN` for each paged query, logs it, and exposes it via `getLastExplainQueryPlan()` (keys `sql`, `args`, `rows`).

---

## Smart SQL

Smart SQL is SQL where soups and JSON paths are written in braces. `SmartStore.convertSmartSql(String)` / `SmartSqlHelper.convertSmartSql(db, smartSql)` translate it.

| Smart SQL | Translated to |
|---|---|
| `{soup}` | the soup table name (`TABLE_<id>`) |
| `{soup:_soup}` | `TABLE_<id>.soup` (the JSON text) |
| `{soup:_soupEntryId}` | `TABLE_<id>.id` |
| `{soup:_soupCreatedDate}` | `TABLE_<id>.created` |
| `{soup:_soupLastModifiedDate}` | `TABLE_<id>.lastModified` |
| `{soup:path}` (indexed) | the index column (`TABLE_<id>_<i>`) or the `json_extract(...)` expression for `json1` |
| `{soup:path}` (not indexed) | `json_extract(soup, '$.<path>')` (no table qualifier, so in multi-soup joins `soup` is ambiguous unless written `alias.{soup:path}`, which is rewritten to `json_extract(alias.soup, ...)`) — works without an index |

Rules enforced by the converter:

- Only selects: a statement whose trimmed lower-cased text starts with `insert`, `update` or `delete` throws `SmartSqlException("Only SELECT are supported")`. (Prefix check only.)
- Unknown soup -> `SmartSqlException("Unknown soup x at character N")`.
- More than one colon (`{a:b:c}`) -> `SmartSqlException("Invalid soup/path reference ...")`.
- Braces inside single-quoted string literals are left alone, **except** inside `MATCH '...'` predicates (matched case-sensitively, uppercase `MATCH` followed by spaces and `'`; lowercase `match '...'` is not special-cased), where they are still translated (this is how `{soup:path}:term` column qualifiers work in FTS keys).
- If a `{soup:field}` is directly preceded by `.` (table-qualified, e.g. `a.{accounts:Name}`) the table qualifier is not added. A `table.json_extract(soup` produced by that replacement is rewritten to `json_extract(table.soup` after conversion.
- Joins across soups, aggregates, sub-queries and aliases all go through unchanged since the output is plain SQLite SQL.

Example:

```java
QuerySpec q = QuerySpec.buildSmartQuerySpec(
    "SELECT {accounts:Name}, {contacts:LastName} " +
    "FROM {accounts}, {contacts} " +
    "WHERE {contacts:AccountId} = {accounts:Id}", 100);
```

---

## Full-Text Search

- Declare one or more paths with `Type.full_text`. Each gets a column in the soup table **and** in the `TABLE_<id>_fts` virtual table.
- FTS table creation: `CREATE VIRTUAL TABLE TABLE_<id>_fts USING fts5(<cols>)`. `ftsExtension` defaults to `FtsExtension.fts5`; `setFtsExtension(fts4)` exists but is documented as test-only, and it is an instance field of the `SmartStore` wrapper (not persisted), so it only affects soups registered through that instance.
- On create/update/delete-by-id/clear/drop/alter the FTS row (keyed `rowid = id`) is kept in sync inside the same transaction. **Exception:** `deleteByQuery` can delete the wrong FTS rows when more entries match than `pageSize` (orphaned FTS rows and surviving entries that stop matching full-text queries); see [deleteByQuery](#deletebyquery).
- Query with `buildMatchQuerySpec(soup, path, matchKey, orderPath, order, pageSize)`. `path` may be `null` to search all FTS columns.
- `QuerySpec.qualifyMatchKey(field, matchKey)` rewrites the user key so that each term is prefixed with `{soup:path}:` (turned into `column:` by Smart SQL), except `and`/`or`/`not` (case-insensitive) and terms already starting with `{`. Tokens are split on spaces and parentheses. FTS5 syntax such as `Name*` prefix terms, `AND`/`OR`/`NOT`, and phrase handling is whatever SQLite FTS5 accepts after that rewrite. `qualifyMatchKey` splits on spaces and parentheses (`[^\(\) ]+`) and prefixes every non-operator token, so for a quoted phrase the qualifier is inserted inside the quotes before each word after the first; `NEAR(...)` is likewise rewritten token by token. No test covers phrases or `NEAR`, and the resulting FTS5 behavior was not executed.
- The match key is inlined into the SQL with `'` doubled (bind args did not work with MATCH, per the source comment).
- In a hand-written smart query use: `{soup:_soupEntryId} IN (SELECT rowid FROM {soup}_fts WHERE {soup}_fts MATCH '{soup:Name}:acme')`. Note the FTS table is referenced as `{soup}_fts`, and the column qualifier must be a Smart SQL reference (`{soup:Name}`) so it is converted to the generated FTS column name (e.g. `TABLE_1_1`); a literal `Name:acme` fails with `no such column: Name`.

---

## Transactions

```java
SQLiteDatabase db = store.getDatabase();
synchronized (db) {              // the javadoc asks for this to avoid deadlock
    store.beginTransaction();
    try {
        store.upsert("accounts", a, "Id", false);   // handleTx=false -> join the outer transaction
        store.upsert("contacts", c, "Id", false);
        store.setTransactionSuccessful();
    } finally {
        store.endTransaction();                      // commit if marked successful, else rollback
    }
}
```

```kotlin
val db = store.database
synchronized(db) {
    store.beginTransaction()
    try {
        records.forEach { store.upsert("accounts", it, "Id", false) }
        store.setTransactionSuccessful()
    } finally {
        store.endTransaction()
    }
}
```

- `beginTransaction`/`setTransactionSuccessful`/`endTransaction` on `SmartStore` are thin pass-throughs to `getDatabase().beginTransaction()` etc. (`net.zetetic.database.sqlcipher.SQLiteDatabase`). Commit/rollback/nesting semantics are therefore those of that class, which follows the `android.database.sqlite.SQLiteDatabase` contract (outermost `endTransaction` commits only if every level was marked successful). This was confirmed in the SQLCipher 4.17.0 sources: `SQLiteSession` keeps a transaction stack with `mMarkedSuccessful`/`mChildFailed`; a child that ends unsuccessfully marks its parent failed, and only the outermost end issues `COMMIT`/`ROLLBACK`.
- Every mutating method that takes `handleTx` begins and ends its own transaction when `true`, and does nothing transactional when `false`. The no-flag overloads use `true`. When the caller already has a transaction open, pass `false`.
- Methods that always manage their own transactions: `registerSoup`, `dropSoup`, `clearSoup`, each alter step. These are safe to call inside an outer transaction only under the nested semantics above.
- Bulk writes should use one outer transaction with `handleTx=false`; otherwise each record is its own commit.
- All `SmartStore` methods synchronize on the `SQLiteDatabase` object, so the explicit `synchronized (db)` also blocks other `SmartStore` instances on the same file.
- `dropSoup` runs `DROP TABLE` outside its transaction; the transaction only wraps the `soup_attrs`/`soup_index_map` deletes.
- Rollback caveat in existing code: some methods call `db.setTransactionSuccessful()` inside `finally` (`clearSoup`, `reIndexSoup` with `handleTx`), so those commit even when an exception was thrown.
- WAL is enabled on every open (`onConfigure`), so readers on other connections do not block on a writer.

---

## Encryption and Keys

### Database

- The database is a SQLCipher database. `DBOpenHelper` passes `encryptionKey` straight to `net.zetetic.database.sqlcipher.SQLiteOpenHelper` as the passphrase.
- `DBHook.postKey` executes `PRAGMA kdf_iter = 4000` **after keying**. The source comment: SQLCipher 2.x KDF iteration count is used because the 3.x (64000) and 4.x (256000) defaults are too slow, and it lets 2.x databases open without migration. Any change here breaks opening existing databases.
- `DBHook.preKey` runs `PRAGMA cipher_license = '<key>'` if `SmartStore.setLicenseKey(...)` was called (must be called before the first open). The key is interpolated into the PRAGMA string.
- `DBErrorHandler.onCorruption` throws `SmartStoreException("Database is corrupted", e)` instead of the default of deleting the database.

### Where the passphrase comes from

`SmartStoreSDKManager` calls `getEncryptionKey()` (static in `SalesforceSDKManager`, defined in `SalesforceSDKManager.kt`) which is `SalesforceKeyGenerator.getEncryptionKey(INTERNAL_ENTROPY)` (`INTERNAL_ENTROPY` is a constant string in the SDK). `SalesforceKeyGenerator`:

1. `getUniqueId(name)`: looks for a value in the SDK's shared preferences (file `identifier.xml`, key `encrypted_id_<name>`), decrypts it with the RSA private key `com.salesforce.androidsdk.security.MSDK_KEYPAIR` from the Android Keystore (RSA OAEP SHA-256). Falls back to the legacy key pair `com.salesforce.androidsdk.security.KEYPAIR` and, as a last resort, to RSA PKCS1 (a `TODO W-23774852` in the source says to remove this PKCS1 fallback in 15.0). Anything decrypted with a legacy path is re-encrypted with the new key pair.
2. If none exists, creates a random 256-bit AES key (`KeyGenerator`, Base64) as the unique id and stores it RSA-encrypted (public key of `MSDK_KEYPAIR`).
3. `generateEncryptionKey(name)` = Base64(SHA-256(uniqueId)) (32 bytes), cached in-memory in `CACHED_ENCRYPTION_KEYS`.

Logout caveat: when the last authenticated user logs out, `SalesforceSDKManager.cleanUp(...)` calls `SalesforceKeyGenerator.clearAll()`, which clears the stored identifiers and `CACHED_ENCRYPTION_KEYS`, so the next `getEncryptionKey()` generates a new key. Global SmartStore databases and key-value stores are not deleted on logout; since they were encrypted with the old key, they will presumably no longer open (inferred from code, not run on a device).

So the **same app-wide key is used for every SmartStore database and key-value store** (global and per-user alike); the user separation is by file name, not by key. The key is not derived from a user passcode or biometric.

### Key change / rotation

- `SmartStore.changeKey(SQLiteDatabase db, String oldKey, String newKey)` (static, synchronized on the db) runs `PRAGMA rekey = '<newKey>'` via `DBOpenHelper.changeKey`, only if `newKey` is non-empty. `oldKey` is unused.
- **Nothing in `libs/SmartStore`, `libs/SalesforceSDK` or `libs/MobileSync` calls `SmartStore.changeKey` or `DBOpenHelper.changeKey`** (searched with ripgrep) — there is no automatic rotation or migration path. It is a utility for app code.
- `KeyValueEncryptedFileStore` has `public boolean changeEncryptionKey(String newEncryptionKey)`. It re-encrypts every file in the store directory (including the version file) into `<storeName>-tmp`, deletes the original directory and renames the tmp directory over it. It returns `false` on failure and the tmp directory is left behind. It is not called from `libs/SmartStore`, `libs/SalesforceSDK` or `libs/MobileSync`.
- `SmartStoreUpgradeManager.upgradeSmartStore()` has no upgrade steps.

---

## Declarative Store Configuration

`StoreConfig` registers soups from JSON so apps do not need Java/Kotlin code:

```json
{
  "soups": [
    {
      "soupName": "accounts",
      "indexes": [
        { "path": "Id",   "type": "string" },
        { "path": "Name", "type": "full_text" }
      ]
    }
  ]
}
```

```kotlin
val config = StoreConfig(context, R.raw.userstore)      // or StoreConfig(context, "assets/path.json")
if (config.hasSoups()) config.registerSoups(SmartStoreSDKManager.getInstance().smartStore)

// Convenience: reads res/raw/globalstore.json / res/raw/userstore.json
SmartStoreSDKManager.getInstance().setupGlobalStoreFromDefaultConfig()
SmartStoreSDKManager.getInstance().setupUserStoreFromDefaultConfig()
```

- `libs/SmartStore/res/raw/globalstore.json` and `userstore.json` ship with an empty `soups` array. An app overrides them by providing resources of the same name.
- Soups that already exist are skipped (no alter). Parse errors are only logged.
- Index spec JSON uses `path` and `type` (and optionally `columnName`), parsed with `IndexSpec.fromJSON`.

---

## Key-Value Stores

Separate from SQLite: `KeyValueEncryptedFileStore` stores one encrypted file per key/value under `<dataDir>/keyvaluestores/<storeName>/` (`<hash(key)>.key` and `.value`, plus a `version` file; `KV_VERSION = 2`). `saveValue`/`saveStream` return `false` (they do not throw) for a null or empty key or a null value; `getValue` returns `null` if the key is missing; `keySet` throws `UnsupportedOperationException` on v1 stores. Valid store names: `^[a-zA-Z0-9_]*$`, 1 to `MAX_STORE_NAME_LENGTH` (96) characters (`isValidStoreName`); otherwise the constructor throws `IllegalArgumentException`. Encryption is done in memory (see class comment) using the same app-wide key. `KeyValueStore` is the interface (`getValue`, `saveValue`, `getStream`, `saveStream`, `deleteValue`, `deleteAll`, `keySet`, `count`, ...); `MemCachedKeyValueStore` is a caching wrapper.

Accessors on `SmartStoreSDKManager` mirror the SmartStore ones: `getKeyValueStore(name[, account[, communityId]])`, `hasKeyValueStore`, `removeKeyValueStore`, `getKeyValueStoresPrefixList`, `removeAllKeyValueStores`, and `getGlobalKeyValueStore(name)` (suffix `_global`, `SmartStoreSDKManager.GLOBAL_SUFFIX`), `hasGlobalKeyValueStore`, `removeGlobalKeyValueStore`, `getGlobalKeyValueStoresPrefixList`, `removeAllGlobalKeyValueStores`. The user variants take a `UserAccount` and call `account.getCommunityLevelFilenameSuffix(communityId)`, so `getKeyValueStore`, `hasKeyValueStore` and `removeKeyValueStore` with an account throw a `NullPointerException` if `account` is null (e.g. no logged-in user); `getKeyValueStoresPrefixList(account)` and `removeAllKeyValueStores(account)` are null-safe. `getKeyValueStore(name)` uses the cached current user.

---

## Errors, Threading and Limits

**Errors**

- `SmartStore.SmartStoreException` (unchecked) is thrown for: null soup name, empty index specs, unknown soup (`"Soup: x does not exist"`), upsert with empty external id, duplicate matches in `lookupSoupEntryId`, unindexed path in `lookupSoupEntryId`, `whereArgs` on non-smart queries, corrupted database.
- `SmartSqlHelper.SmartSqlException` (subclass) for invalid smart SQL (position reported as character index).
- `JSONException` (checked) from `create`/`update`/`upsert`/`retrieve`/`query`/`queryWithArgs`/`alterSoup`.
- `create` returns `null` (not an exception) when the insert's returned id differs from the expected one, and `update` returns `null` when the affected row count is not 1.
- Underlying SQLCipher exceptions (`net.zetetic.database.sqlcipher.SQLiteException` etc.) propagate unwrapped.
- Logging goes through `SmartStoreLogger`.

**Threading**

- Thread-safety is by `synchronized (db)` on the shared `SQLiteDatabase` in nearly every `SmartStore` method; static caches (`DBOpenHelper.openHelpers`, `DBHelper`/`SmartSqlHelper` instance maps) are guarded by `synchronized` static methods. `DBOpenHelper.getOpenHelper(key, ctx, dbNamePrefix, account, communityId)` is **not** `synchronized` (only the shorter overloads are), although it mutates the shared map **(possible race, see below)**.
- SmartStore methods do blocking disk I/O; call them off the main thread. Nothing in the library enforces it.
- The `SmartStore` instance has a private single-thread executor used only for analytics logging in `registerSoup`.
- `SmartStoreConcurrencyTest` exercises multi-threaded access.

**Limits visible in code**

- No explicit limit on page size, JSON document size or number of soups. SQLite/SQLCipher limits apply.
- `getDatabaseSize()` casts the file length to `int` (breaks above 2 GB; noted in a source comment) and forces a `wal_checkpoint(FULL)`.
- `DBHelper` caches hold at most 1024 entries each (`CACHES_COUNT_LIMIT`).
- `MAX_STORE_NAME_LENGTH = 96` applies to key-value store names only; SmartStore database names are not validated.
- `ID IN (...)` predicates are built by string-joining ids, so very large id lists are subject to SQLite's statement-length limits (not tested).

---

## Differences From Other Docs / Platforms

These do not exist in the Android SmartStore code in this repo (searched with ripgrep for `SoupSpec`/`external storage`; only the JS sample app and a constant in `SalesforceHybrid` mention them):

- **No `SoupSpec` class and no soup "features"** (e.g. external storage). `registerSoup` takes only `String` + `IndexSpec[]`. Remnants: `AlterSoupLongOperation` still declares unused constants `OLD_SOUP_SPEC = "oldSoupFeatures"` / `NEW_SOUP_SPEC = "newSoupFeatures"`, and the Javadoc of `alterSoup` says "without extra soup features".
- **No `removeSoup`**: use `dropSoup`.
- **No `all` query type**: `buildAllQuerySpec` is a `range` query without keys.
- **No `registerSoupWithSpec` / `alterSoup(soupName, soupSpec, ...)` overloads.**

---

## Testing

Tests are instrumented (Android) tests that live outside the library, in `libs/test/SmartStoreTest/` (sources in `src/`, resources in `res/`), wired into `:libs:SmartStore`'s `androidTest` source set by `libs/SmartStore/build.gradle.kts` (`setRoot("../test/SmartStoreTest")`). Test app id: `com.salesforce.androidsdk.smartstore.tests`, runner `AndroidJUnitRunner`.

```bash
# all SmartStore tests (needs a connected emulator/device)
./gradlew :libs:SmartStore:connectedAndroidTest

# one class
./gradlew :libs:SmartStore:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.androidsdk.smartstore.store.SmartStoreTest

# one method
./gradlew :libs:SmartStore:connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.androidsdk.smartstore.store.SmartSqlTest#<methodName>
```

(The repo `CLAUDE.md` also says to check for `shared/test/test_credentials.json` before running suites; no SmartStore test source in `libs/test/SmartStoreTest` references credentials, so they do not appear to need it; the file `shared/test/test_credentials.json` exists in this repo.)

Test classes (all under `libs/test/SmartStoreTest/src/com/salesforce/androidsdk/smartstore/`):

| Class | Covers |
|---|---|
| `store/SmartStoreTest` (+ `SmartStoreTestCase` base) | Soup registration, CRUD, upsert/external id, queries, paging, JSON1 |
| `store/SmartSqlTest` | Smart SQL conversion and execution |
| `store/SmartStoreAlterTest` | `alterSoup` including interrupted-step resumption |
| `store/SmartStoreFullTextSearchTest`, `SmartStoreFullTextSearchSpeedTest` | FTS4/FTS5 |
| `store/SmartStoreConcurrencyTest` | Multi-threaded access |
| `store/SmartStoreLoadTest`, `SmartStoreOtherLoadTest`, `SmartStoreLoadTestCase` | Load/performance |
| `store/QuerySpecTest`, `IndexSpecTest`, `StoreConfigTest` | Spec building / JSON parsing / config |
| `store/DBOpenHelperTest` | Database naming, creation, deletion |
| `app/SmartStoreSDKManagerTest` | Global/user/named stores, key-value stores, removal |
| `store/KeyValueEncryptedFileStoreTest`, `MemCachedKeyValueStoreTest` | Key-value stores |
| `store/SmartStoreInspectorActivityTest`, `KeyValueStoreInspectorActivityTest` | Inspector UIs |

`SmartStoreTest.java` has no transaction tests, but `beginTransaction`/`endTransaction` are used in `SmartStoreConcurrencyTest`, `SmartStoreLoadTestCase` and `SmartStoreFullTextSearchSpeedTest`, and `SmartStoreAlterTest` covers alter resumption.

---

## Key Files

Paths are relative to the repo root.

| File | Role |
|---|---|
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/app/SmartStoreSDKManager.java` | Store/key-value store factory, init, cleanup on logout, dev actions |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/app/SmartStoreUpgradeManager.java` | Version tracking (no steps yet) |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/app/Features.java` | User-agent feature flags (`GS`, `US`, `RR`) |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/SmartStore.java` | Main store API, schema creation, index typing, `project` |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/DBOpenHelper.java` | SQLCipher open helper, db naming/deletion, `changeKey`, `kdf_iter` |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/DBHelper.java` | Per-database caches and low-level SQL helpers, explain query plan |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/QuerySpec.java` | Query descriptions, smart sql generation, FTS key qualification |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/SmartSqlHelper.java` | Smart SQL to SQL conversion |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/IndexSpec.java` | Index description and JSON (de)serialization |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/AlterSoupLongOperation.java`, `LongOperation.java` | Resumable alterSoup |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/StoreCursor.java` | Page-wrapping cursor for bridges |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/store/KeyValueEncryptedFileStore.java`, `KeyValueStore.java`, `MemCachedKeyValueStore.java` | File-based encrypted key-value stores |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/config/StoreConfig.java` | JSON-driven soup registration |
| `libs/SmartStore/src/com/salesforce/androidsdk/smartstore/ui/` | Inspector activities |
| `libs/SmartStore/res/raw/globalstore.json`, `userstore.json` | Default (empty) store configs |
| `libs/SmartStore/build.gradle.kts` | Dependencies (SQLCipher) and test source wiring |
| `libs/SalesforceSDK/src/com/salesforce/androidsdk/security/SalesforceKeyGenerator.java` | Passphrase derivation and storage |
| `libs/SalesforceSDK/src/com/salesforce/androidsdk/app/SalesforceSDKManager.kt` | `encryptionKey`, `INTERNAL_ENTROPY` |
| `libs/SalesforceSDK/src/com/salesforce/androidsdk/accounts/UserAccount.java` | `getCommunityLevelFilenameSuffix` / `getUserLevelFilenameSuffix` (db naming) |
| `libs/MobileSync/src/com/salesforce/androidsdk/mobilesync/app/MobileSyncSDKManager.java` | Extends `SmartStoreSDKManager` |
| `libs/test/SmartStoreTest/` | Instrumented tests |

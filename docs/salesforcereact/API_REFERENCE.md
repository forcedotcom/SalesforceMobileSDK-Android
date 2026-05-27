# SalesforceReact API Reference

> **MOVED**: As of SDK 14.0, the bridge module classes (SFOauthReactBridge, SFNetReactBridge, SFSmartStoreReactBridge, SFMobileSyncReactBridge, ReactBridgeHelper) have moved to `SalesforceMobileSDK-ReactNative/android/`. The API below documents the app/UI layer classes that remain in this repo.

Complete API documentation for all public classes and methods in the SalesforceReact library.

## Table of Contents

- [App Package](#app-package)
  - [SalesforceReactSDKManager](#salesforcereactsdkmanager)
  - [SalesforceReactUpgradeManager](#salesforcereactupgrademanager)
- [Bridge Package](#bridge-package)
  - [SalesforceOauthReactBridge](#salesforceoauthreactbridge)
  - [SmartStoreReactBridge](#smartstorereactbridge)
  - [MobileSyncReactBridge](#mobilesyncreactbridge)
  - [SalesforceNetReactBridge](#salesforcenetreactbridge)
  - [ReactBridgeHelper](#reactbridgehelper)
- [UI Package](#ui-package)
  - [SalesforceReactActivity](#salesforcereactactivity)
  - [SalesforceReactActivityDelegate](#salesforcereactactivitydelegate)
- [Util Package](#util-package)
  - [SalesforceReactLogger](#salesforcereactlogger)

---

## App Package

### SalesforceReactSDKManager

**Package:** `com.salesforce.androidsdk.reactnative.app`

**Extends:** `MobileSyncSDKManager`

**Purpose:** Singleton SDK manager for React Native applications. Manages initialization, configuration, and provides access to React Native bridge modules.

#### Methods

##### initReactNative

```java
public static void initReactNative(Context context, Class<? extends Activity> mainActivity)
```

Initializes the React Native SDK components. This method should be called in your `Application.onCreate()` method before any other SDK operations.

**Parameters:**
- `context` - Application context
- `mainActivity` - Activity class to launch after successful login (typically your main React Native activity)

**Example:**
```java
public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SalesforceReactSDKManager.initReactNative(
            getApplicationContext(),
            MainActivity.class
        );
    }
}
```

---

##### initReactNative (with custom login activity)

```java
public static void initReactNative(
    Context context,
    Class<? extends Activity> mainActivity,
    Class<? extends Activity> loginActivity
)
```

Initializes the React Native SDK with a custom login activity.

**Parameters:**
- `context` - Application context
- `mainActivity` - Activity to launch after login
- `loginActivity` - Custom login activity class (use `LoginActivity.class` for default)

---

##### getInstance

```java
@NonNull
public static SalesforceReactSDKManager getInstance()
```

Returns the singleton instance of the SDK manager.

**Returns:** The singleton `SalesforceReactSDKManager` instance

**Throws:** `RuntimeException` if `initReactNative()` has not been called first

**Example:**
```java
SalesforceReactSDKManager manager = SalesforceReactSDKManager.getInstance();
```

---

##### getReactPackage

```java
public ReactPackage getReactPackage()
```

Returns the React Native package containing all Salesforce bridge modules. This package should be added to your React Native host's package list.

**Returns:** `ReactPackage` containing `SalesforceOauthReactBridge`, `SalesforceNetReactBridge`, `SmartStoreReactBridge`, and `MobileSyncReactBridge`

**Example:**
```java
@Override
protected List<ReactPackage> getPackages() {
    List<ReactPackage> packages = new PackageList(this).getPackages();
    packages.add(SalesforceReactSDKManager.getInstance().getReactPackage());
    return packages;
}
```

---

##### getAppType

```java
@NonNull
@Override
public String getAppType()
```

Returns the application type identifier.

**Returns:** `"ReactNative"` - used for analytics and logging

---

##### getDevActions

```java
@NonNull
@Override
public Map<String, DevActionHandler> getDevActions(Activity frontActivity)
```

Returns development actions available in the SDK's developer menu.

**Parameters:**
- `frontActivity` - The current foreground activity

**Returns:** Map of action names to handlers, including "React Native Dev Support" action

---

### SalesforceReactUpgradeManager

**Package:** `com.salesforce.androidsdk.reactnative.app`

**Extends:** `MobileSyncUpgradeManager`

**Purpose:** Handles SDK version upgrades and data migrations.

#### Methods

##### getInstance

```java
public static synchronized SalesforceReactUpgradeManager getInstance()
```

Returns the singleton instance of the upgrade manager.

**Returns:** The singleton `SalesforceReactUpgradeManager` instance

---

##### upgrade

```java
@Override
public void upgrade()
```

Performs any necessary upgrade operations for the current SDK version. Called automatically during SDK initialization.

---

## Bridge Package

### SalesforceOauthReactBridge

**Package:** `com.salesforce.androidsdk.reactnative.bridge`

**Extends:** `ReactContextBaseJavaModule`

**JavaScript Module Name:** `SalesforceOauthReactBridge`

**Purpose:** Exposes OAuth authentication and credential management to React Native.

#### Methods

##### authenticate

```java
@ReactMethod
public void authenticate(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Initiates authentication flow. If already authenticated, returns credentials immediately. If not authenticated, launches OAuth flow.

**Parameters:**
- `args` - Arguments map (currently unused, pass empty object)
- `successCallback` - Invoked with user credentials JSON on success
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
import { NativeModules } from 'react-native';
const { SalesforceOauthReactBridge } = NativeModules;

SalesforceOauthReactBridge.authenticate(
    {},
    (credentials) => {
        const creds = JSON.parse(credentials);
        console.log('Access Token:', creds.accessToken);
        console.log('Instance URL:', creds.instanceUrl);
    },
    (error) => {
        console.error('Auth failed:', error);
    }
);
```

**Credentials Object Structure:**
```json
{
    "accessToken": "00D...",
    "refreshToken": "5Aep...",
    "instanceUrl": "https://instance.salesforce.com",
    "loginUrl": "https://login.salesforce.com",
    "identityUrl": "https://login.salesforce.com/id/00D.../005...",
    "clientId": "3MVG9...",
    "orgId": "00D...",
    "userId": "005...",
    "userFirstName": "John",
    "userLastName": "Doe",
    "userEmail": "john.doe@example.com",
    "userName": "john.doe@example.com",
    "userDisplayName": "John Doe",
    "photoUrl": "https://...",
    "communityId": null,
    "communityUrl": null
}
```

---

##### getAuthCredentials

```java
@ReactMethod
public void getAuthCredentials(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Retrieves current authentication credentials without triggering OAuth flow.

**Parameters:**
- `args` - Arguments map (currently unused)
- `successCallback` - Invoked with credentials JSON if authenticated
- `errorCallback` - Invoked with "Not authenticated" if no active session

**JavaScript Example:**
```javascript
SalesforceOauthReactBridge.getAuthCredentials(
    {},
    (credentials) => {
        const creds = JSON.parse(credentials);
        console.log('Current user:', creds.userName);
    },
    (error) => {
        console.log('Not logged in');
    }
);
```

---

##### logoutCurrentUser

```java
@ReactMethod
public void logoutCurrentUser(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Logs out the current user and clears all local data.

**Parameters:**
- `args` - Arguments map (currently unused)
- `successCallback` - Invoked with "Logout complete" message on success
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SalesforceOauthReactBridge.logoutCurrentUser(
    {},
    (result) => {
        console.log('Logged out successfully');
    },
    (error) => {
        console.error('Logout failed:', error);
    }
);
```

---

### SmartStoreReactBridge

**Package:** `com.salesforce.androidsdk.reactnative.bridge`

**Extends:** `ReactContextBaseJavaModule`

**JavaScript Module Name:** `SmartStoreReactBridge`

**Purpose:** Exposes SmartStore encrypted local database functionality to React Native.

#### Methods

##### registerSoup

```java
@ReactMethod
public void registerSoup(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Creates a new soup (table) with specified indexes.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of the soup to create
  - `indexes` (Array): Array of index specifications
  - `isGlobalStore` (Boolean, optional): If true, uses global store instead of user store
  - `storeName` (String, optional): Custom store name (defaults to default store)
- `successCallback` - Invoked with soup name on success
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.registerSoup(
    {
        soupName: 'accounts',
        indexes: [
            { path: 'Id', type: 'string' },
            { path: 'Name', type: 'string' },
            { path: 'LastModifiedDate', type: 'string' }
        ],
        isGlobalStore: false
    },
    (soupName) => {
        console.log(`Soup '${soupName}' registered`);
    },
    (error) => {
        console.error('Registration failed:', error);
    }
);
```

**Index Types:**
- `"string"` - String index
- `"integer"` - Integer index
- `"floating"` - Floating point index
- `"full_text"` - Full-text search index
- `"json1"` - JSON1 index (for nested JSON queries)

---

##### upsertSoupEntries

```java
@ReactMethod
public void upsertSoupEntries(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Inserts or updates entries in a soup. All operations are performed within a transaction.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of the soup
  - `entries` (Array): Array of entry objects to upsert
  - `externalIdPath` (String): Path to field used as external ID
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with array of upserted entries (including _soupEntryId)
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.upsertSoupEntries(
    {
        soupName: 'accounts',
        entries: [
            { Id: '001xx000000001', Name: 'Acme Corp', __local__: false },
            { Id: '001xx000000002', Name: 'Global Inc', __local__: false }
        ],
        externalIdPath: 'Id',
        isGlobalStore: false
    },
    (entries) => {
        const saved = JSON.parse(entries);
        saved.forEach(entry => {
            console.log(`Saved with entry ID: ${entry._soupEntryId}`);
        });
    },
    (error) => {
        console.error('Upsert failed:', error);
    }
);
```

---

##### querySoup

```java
@ReactMethod
public void querySoup(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Queries a soup using various query types. Returns a cursor for pagination.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of the soup to query
  - `querySpec` (Object): Query specification
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with cursor object containing first page of results
- `errorCallback` - Invoked with error message on failure

**Query Types:**

1. **Exact Query:**
```javascript
{
    queryType: 'exact',
    indexPath: 'Id',
    matchKey: '001xx000000001',
    pageSize: 10
}
```

2. **Range Query:**
```javascript
{
    queryType: 'range',
    indexPath: 'LastModifiedDate',
    beginKey: '2024-01-01',
    endKey: '2024-12-31',
    order: 'ascending',
    pageSize: 25
}
```

3. **Like Query:**
```javascript
{
    queryType: 'like',
    indexPath: 'Name',
    likeKey: 'Acme%',
    order: 'ascending',
    pageSize: 50
}
```

4. **All Query:**
```javascript
{
    queryType: 'all',
    indexPath: 'Name',
    order: 'ascending',
    pageSize: 100
}
```

**JavaScript Example:**
```javascript
SmartStoreReactBridge.querySoup(
    {
        soupName: 'accounts',
        querySpec: {
            queryType: 'range',
            indexPath: 'Name',
            beginKey: 'A',
            endKey: 'M',
            order: 'ascending',
            pageSize: 10
        }
    },
    (cursor) => {
        const result = JSON.parse(cursor);
        console.log(`Total entries: ${result.totalEntries}`);
        console.log(`Current page: ${result.currentPageIndex}`);
        console.log(`Total pages: ${result.totalPages}`);
        result.currentPageOrderedEntries.forEach(entry => {
            console.log(`- ${entry.Name}`);
        });
    },
    (error) => {
        console.error('Query failed:', error);
    }
);
```

**Cursor Object Structure:**
```json
{
    "cursorId": 12345,
    "totalEntries": 150,
    "currentPageIndex": 0,
    "totalPages": 15,
    "pageSize": 10,
    "currentPageOrderedEntries": [
        { "_soupEntryId": 1, "Id": "001...", "Name": "Acme" },
        { "_soupEntryId": 2, "Id": "001...", "Name": "Beta" }
    ]
}
```

---

##### runSmartQuery

```java
@ReactMethod
public void runSmartQuery(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Runs a Smart SQL query across soups. Smart SQL allows joining data from multiple soups.

**Parameters:**
- `args` - Arguments map containing:
  - `querySpec` (Object): Smart SQL query specification
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with cursor object
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.runSmartQuery(
    {
        querySpec: {
            queryType: 'smart',
            smartSql: 'SELECT {accounts:Name}, {accounts:Industry} FROM {accounts} WHERE {accounts:Industry} = ?',
            pageSize: 25
        }
    },
    (cursor) => {
        const result = JSON.parse(cursor);
        result.currentPageOrderedEntries.forEach(row => {
            console.log(`${row[0]} - ${row[1]}`); // Name - Industry
        });
    },
    (error) => {
        console.error('Smart query failed:', error);
    }
);
```

**Smart SQL Syntax:**
- Soup references: `{soupName:fieldPath}`
- Supports: SELECT, WHERE, ORDER BY, GROUP BY, JOIN
- Returns array rows instead of objects

---

##### moveCursorToPageIndex

```java
@ReactMethod
public void moveCursorToPageIndex(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Moves a query cursor to a specific page.

**Parameters:**
- `args` - Arguments map containing:
  - `cursorId` (Number): ID of cursor from previous query
  - `index` (Number): Zero-based page index to navigate to
  - `isGlobalStore` (Boolean, optional): Must match original query
  - `storeName` (String, optional): Must match original query
- `successCallback` - Invoked with cursor object for requested page
- `errorCallback` - Invoked with "Invalid cursor id" or error message

**JavaScript Example:**
```javascript
// After initial query that returned cursor
SmartStoreReactBridge.moveCursorToPageIndex(
    {
        cursorId: result.cursorId,
        index: 2 // Go to page 3 (zero-based)
    },
    (cursor) => {
        const page = JSON.parse(cursor);
        console.log(`Page ${page.currentPageIndex + 1} of ${page.totalPages}`);
    },
    (error) => {
        console.error('Cursor move failed:', error);
    }
);
```

---

##### closeCursor

```java
@ReactMethod
public void closeCursor(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Closes a query cursor and frees associated resources. Always close cursors when done.

**Parameters:**
- `args` - Arguments map containing:
  - `cursorId` (Number): ID of cursor to close
  - `isGlobalStore` (Boolean, optional): Must match original query
  - `storeName` (String, optional): Must match original query
- `successCallback` - Invoked on successful close
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.closeCursor(
    { cursorId: result.cursorId },
    () => console.log('Cursor closed'),
    (error) => console.error('Close failed:', error)
);
```

---

##### retrieveSoupEntries

```java
@ReactMethod
public void retrieveSoupEntries(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Retrieves specific soup entries by their internal IDs.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of the soup
  - `entryIds` (Array): Array of `_soupEntryId` values
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with array of retrieved entries
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.retrieveSoupEntries(
    {
        soupName: 'accounts',
        entryIds: [1, 5, 10]
    },
    (entries) => {
        const retrieved = JSON.parse(entries);
        console.log(`Retrieved ${retrieved.length} entries`);
    },
    (error) => {
        console.error('Retrieve failed:', error);
    }
);
```

---

##### removeFromSoup

```java
@ReactMethod
public void removeFromSoup(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Removes entries from a soup by entry IDs or query specification.

**Parameters:**
- `args` - Arguments map containing ONE OF:
  - `entryIds` (Array): Array of `_soupEntryId` values to delete
  - `querySpec` (Object): Query specification to identify entries to delete
  - Plus: `soupName` (String), `isGlobalStore`, `storeName`
- `successCallback` - Invoked on successful deletion
- `errorCallback` - Invoked with error message on failure

**JavaScript Examples:**

Delete by IDs:
```javascript
SmartStoreReactBridge.removeFromSoup(
    {
        soupName: 'accounts',
        entryIds: [1, 2, 3]
    },
    () => console.log('Entries deleted'),
    (error) => console.error('Delete failed:', error)
);
```

Delete by query:
```javascript
SmartStoreReactBridge.removeFromSoup(
    {
        soupName: 'accounts',
        querySpec: {
            queryType: 'exact',
            indexPath: '__local__',
            matchKey: true
        }
    },
    () => console.log('Local entries deleted'),
    (error) => console.error('Delete failed:', error)
);
```

---

##### soupExists

```java
@ReactMethod
public void soupExists(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Checks if a soup exists in the store.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of the soup to check
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with `"true"` or `"false"` string
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.soupExists(
    { soupName: 'accounts' },
    (exists) => {
        if (JSON.parse(exists)) {
            console.log('Soup exists');
        } else {
            console.log('Soup does not exist');
        }
    },
    (error) => console.error('Check failed:', error)
);
```

---

##### removeSoup

```java
@ReactMethod
public void removeSoup(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Completely removes a soup and all its data from the store.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of soup to remove
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked on successful removal
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.removeSoup(
    { soupName: 'old_accounts' },
    () => console.log('Soup removed'),
    (error) => console.error('Remove failed:', error)
);
```

---

##### clearSoup

```java
@ReactMethod
public void clearSoup(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Removes all entries from a soup but keeps the soup structure and indexes.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of soup to clear
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked on successful clear
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.clearSoup(
    { soupName: 'cache' },
    () => console.log('Soup cleared'),
    (error) => console.error('Clear failed:', error)
);
```

---

##### alterSoup

```java
@ReactMethod
public void alterSoup(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Modifies the indexes of an existing soup.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of soup to alter
  - `indexes` (Array): New index specifications
  - `reIndexData` (Boolean): If true, re-indexes existing data with new indexes
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with soup name on success
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.alterSoup(
    {
        soupName: 'accounts',
        indexes: [
            { path: 'Id', type: 'string' },
            { path: 'Name', type: 'string' },
            { path: 'Industry', type: 'string' }, // New index
            { path: 'Description', type: 'full_text' } // New full-text index
        ],
        reIndexData: true
    },
    (soupName) => console.log(`Soup '${soupName}' altered`),
    (error) => console.error('Alter failed:', error)
);
```

---

##### reIndexSoup

```java
@ReactMethod
public void reIndexSoup(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Re-indexes specific paths in a soup. Useful after bulk data updates.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of soup to re-index
  - `paths` (Array): Array of index paths to re-index
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with soup name on success
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.reIndexSoup(
    {
        soupName: 'accounts',
        paths: ['Name', 'Industry']
    },
    (soupName) => console.log(`Re-indexed '${soupName}'`),
    (error) => console.error('Re-index failed:', error)
);
```

---

##### getSoupIndexSpecs

```java
@ReactMethod
public void getSoupIndexSpecs(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Retrieves the current index specifications for a soup.

**Parameters:**
- `args` - Arguments map containing:
  - `soupName` (String): Name of soup
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with array of index specs
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.getSoupIndexSpecs(
    { soupName: 'accounts' },
    (specs) => {
        const indexes = JSON.parse(specs);
        indexes.forEach(spec => {
            console.log(`Index: ${spec.path} (${spec.type})`);
        });
    },
    (error) => console.error('Get specs failed:', error)
);
```

---

##### getDatabaseSize

```java
@ReactMethod
public void getDatabaseSize(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Returns the size of the SmartStore database in bytes.

**Parameters:**
- `args` - Arguments map containing:
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with database size as string number
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.getDatabaseSize(
    {},
    (size) => {
        const bytes = JSON.parse(size);
        const mb = (bytes / (1024 * 1024)).toFixed(2);
        console.log(`Database size: ${mb} MB`);
    },
    (error) => console.error('Get size failed:', error)
);
```

---

##### getAllStores

```java
@ReactMethod
public void getAllStores(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Returns a list of all user-scoped stores.

**Parameters:**
- `args` - Arguments map (currently unused)
- `successCallback` - Invoked with array of store configurations
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.getAllStores(
    {},
    (stores) => {
        const storeList = JSON.parse(stores);
        storeList.forEach(store => {
            console.log(`Store: ${store.storeName}, Global: ${store.isGlobalStore}`);
        });
    },
    (error) => console.error('Get stores failed:', error)
);
```

---

##### getAllGlobalStores

```java
@ReactMethod
public void getAllGlobalStores(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Returns a list of all global (shared across users) stores.

**Parameters:**
- `args` - Arguments map (currently unused)
- `successCallback` - Invoked with array of store configurations
- `errorCallback` - Invoked with error message on failure

---

##### removeStore

```java
@ReactMethod
public void removeStore(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Removes an entire store (all soups within it).

**Parameters:**
- `args` - Arguments map containing:
  - `storeName` (String): Name of store to remove
  - `isGlobalStore` (Boolean): Whether this is a global store
- `successCallback` - Invoked with `"true"` on success
- `errorCallback` - Invoked with error message on failure

**JavaScript Example:**
```javascript
SmartStoreReactBridge.removeStore(
    {
        storeName: 'customStore',
        isGlobalStore: false
    },
    () => console.log('Store removed'),
    (error) => console.error('Remove store failed:', error)
);
```

---

##### removeAllStores

```java
@ReactMethod
public void removeAllStores(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Removes all user-scoped stores for the current user.

**Parameters:**
- `args` - Arguments map (currently unused)
- `successCallback` - Invoked with `"true"` on success
- `errorCallback` - Invoked with error message on failure

---

##### removeAllGlobalStores

```java
@ReactMethod
public void removeAllGlobalStores(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Removes all global stores.

**Parameters:**
- `args` - Arguments map (currently unused)
- `successCallback` - Invoked with `"true"` on success
- `errorCallback` - Invoked with error message on failure

---

### MobileSyncReactBridge

**Package:** `com.salesforce.androidsdk.reactnative.bridge`

**Extends:** `ReactContextBaseJavaModule`

**JavaScript Module Name:** `MobileSyncReactBridge`

**Purpose:** Exposes MobileSync bidirectional data synchronization to React Native.

#### Methods

##### syncDown

```java
@ReactMethod
public void syncDown(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Synchronizes data from Salesforce to the local SmartStore.

**Parameters:**
- `args` - Arguments map containing:
  - `target` (Object): Sync down target specification
  - `soupName` (String): Name of soup to sync into
  - `options` (Object): Sync options
  - `syncName` (String, optional): Named sync for later reference
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with final sync state when complete
- `errorCallback` - Invoked with sync state if sync fails

**Target Types:**

1. **SOQL Target:**
```javascript
{
    type: 'soql',
    query: 'SELECT Id, Name, Industry FROM Account WHERE Industry = \'Technology\' ORDER BY Name'
}
```

2. **SOSL Target:**
```javascript
{
    type: 'sosl',
    query: 'FIND {Acme} IN ALL FIELDS RETURNING Account(Id, Name)'
}
```

3. **MRU Target (Most Recently Used):**
```javascript
{
    type: 'mru',
    sobjectType: 'Account',
    fieldlist: ['Id', 'Name', 'Industry', 'Phone']
}
```

4. **Metadata Target:**
```javascript
{
    type: 'metadata',
    sobjectType: 'Account'
}
```

5. **Layout Target:**
```javascript
{
    type: 'layout',
    sobjectType: 'Account',
    layoutType: 'Compact',
    formFactor: 'Medium'
}
```

**Sync Options:**
```javascript
{
    mergeMode: 'OVERWRITE' // or 'LEAVE_IF_CHANGED'
}
```

**JavaScript Example:**
```javascript
MobileSyncReactBridge.syncDown(
    {
        target: {
            type: 'soql',
            query: 'SELECT Id, Name, Industry FROM Account LIMIT 100'
        },
        soupName: 'accounts',
        options: {
            mergeMode: 'OVERWRITE'
        },
        syncName: 'accountsSync'
    },
    (result) => {
        const syncState = JSON.parse(result);
        console.log(`Synced ${syncState.totalSize} records`);
        console.log(`Sync ID: ${syncState._id}`);
    },
    (error) => {
        const syncState = JSON.parse(error);
        console.error(`Sync failed: ${syncState.error}`);
    }
);
```

**Sync State Object:**
```json
{
    "_id": 1,
    "type": "syncDown",
    "target": {...},
    "options": {...},
    "soupName": "accounts",
    "status": "DONE",
    "progress": 100,
    "totalSize": 150,
    "maxTimeStamp": 1642511234567,
    "error": null,
    "name": "accountsSync"
}
```

---

##### syncUp

```java
@ReactMethod
public void syncUp(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Synchronizes locally modified data from SmartStore to Salesforce.

**Parameters:**
- `args` - Arguments map containing:
  - `target` (Object): Sync up target specification
  - `soupName` (String): Name of soup to sync from
  - `options` (Object): Sync options
  - `syncName` (String, optional): Named sync
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with final sync state when complete
- `errorCallback` - Invoked with sync state if sync fails

**Target Types:**

1. **Standard Sync Up:**
```javascript
{
    type: 'syncUp',
    createFieldlist: ['Name', 'Industry', 'Phone'],
    updateFieldlist: ['Name', 'Industry', 'Phone']
}
```

2. **Batch Sync Up:**
```javascript
{
    type: 'batchSyncUp',
    createFieldlist: ['Name', 'Industry'],
    updateFieldlist: ['Name', 'Industry'],
    maxBatchSize: 200
}
```

3. **Advanced Sync Up (parent-child relationships):**
```javascript
{
    type: 'advancedSyncUp',
    createFieldlist: ['Name', 'Industry'],
    updateFieldlist: ['Name', 'Industry'],
    relationshipSpecs: [
        {
            sobjectType: 'Contact',
            soupName: 'contacts',
            parentIdFieldName: 'AccountId',
            idFieldName: 'Id',
            modificationDateFieldName: 'LastModifiedDate',
            externalIdFieldName: 'Id',
            createFieldlist: ['FirstName', 'LastName', 'Email'],
            updateFieldlist: ['FirstName', 'LastName', 'Email']
        }
    ]
}
```

**Sync Options:**
```javascript
{
    fieldlist: ['Name', 'Industry', 'Phone'],
    mergeMode: 'OVERWRITE'
}
```

**JavaScript Example:**
```javascript
MobileSyncReactBridge.syncUp(
    {
        target: {
            type: 'syncUp',
            createFieldlist: ['Name', 'Industry', 'Phone'],
            updateFieldlist: ['Name', 'Industry', 'Phone']
        },
        soupName: 'accounts',
        options: {
            fieldlist: ['Name', 'Industry', 'Phone'],
            mergeMode: 'OVERWRITE'
        },
        syncName: 'accountsSyncUp'
    },
    (result) => {
        const syncState = JSON.parse(result);
        console.log(`Sync up complete: ${syncState.totalSize} records processed`);
    },
    (error) => {
        const syncState = JSON.parse(error);
        console.error(`Sync up failed: ${syncState.error}`);
    }
);
```

---

##### getSyncStatus

```java
@ReactMethod
public void getSyncStatus(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Retrieves the status of a sync operation by ID or name.

**Parameters:**
- `args` - Arguments map containing ONE OF:
  - `syncId` (Number): Numeric ID of sync
  - `syncName` (String): Named sync identifier
  - Plus: `isGlobalStore`, `storeName`
- `successCallback` - Invoked with sync state or null if not found
- `errorCallback` - Invoked with error message

**JavaScript Example:**
```javascript
// By sync ID
MobileSyncReactBridge.getSyncStatus(
    { syncId: 1 },
    (state) => {
        if (state) {
            const syncState = JSON.parse(state);
            console.log(`Status: ${syncState.status}, Progress: ${syncState.progress}%`);
        } else {
            console.log('Sync not found');
        }
    },
    (error) => console.error('Get status failed:', error)
);

// By sync name
MobileSyncReactBridge.getSyncStatus(
    { syncName: 'accountsSync' },
    (state) => {
        const syncState = JSON.parse(state);
        console.log(`Last sync: ${new Date(syncState.maxTimeStamp)}`);
    },
    (error) => console.error('Get status failed:', error)
);
```

---

##### reSync

```java
@ReactMethod
public void reSync(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Re-runs a previously executed sync using the same configuration.

**Parameters:**
- `args` - Arguments map containing ONE OF:
  - `syncId` (Number): ID of sync to re-run
  - `syncName` (String): Name of sync to re-run
  - Plus: `isGlobalStore`, `storeName`
- `successCallback` - Invoked with final sync state when complete
- `errorCallback` - Invoked with sync state if sync fails

**JavaScript Example:**
```javascript
MobileSyncReactBridge.reSync(
    { syncName: 'accountsSync' },
    (result) => {
        const syncState = JSON.parse(result);
        console.log('Re-sync complete');
    },
    (error) => {
        const syncState = JSON.parse(error);
        console.error('Re-sync failed:', syncState.error);
    }
);
```

---

##### deleteSync

```java
@ReactMethod
public void deleteSync(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Deletes a sync record from the sync state soup.

**Parameters:**
- `args` - Arguments map containing ONE OF:
  - `syncId` (Number): ID of sync to delete
  - `syncName` (String): Name of sync to delete
  - Plus: `isGlobalStore`, `storeName`
- `successCallback` - Invoked on successful deletion
- `errorCallback` - Invoked with error message

**JavaScript Example:**
```javascript
MobileSyncReactBridge.deleteSync(
    { syncId: 1 },
    () => console.log('Sync deleted'),
    (error) => console.error('Delete failed:', error)
);
```

---

##### cleanResyncGhosts

```java
@ReactMethod
public void cleanResyncGhosts(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Removes locally cached records that were deleted on the server. Compares local soup entries with server to find "ghost" records.

**Parameters:**
- `args` - Arguments map containing:
  - `syncId` (Number): ID of the sync down to clean ghosts for
  - `isGlobalStore` (Boolean, optional): Global vs user store
  - `storeName` (String, optional): Custom store name
- `successCallback` - Invoked with number of ghost records removed
- `errorCallback` - Invoked with error message

**JavaScript Example:**
```javascript
MobileSyncReactBridge.cleanResyncGhosts(
    { syncId: 1 },
    (numRecords) => {
        console.log(`Removed ${numRecords} ghost records`);
    },
    (error) => console.error('Clean ghosts failed:', error)
);
```

---

### SalesforceNetReactBridge

**Package:** `com.salesforce.androidsdk.reactnative.bridge`

**Extends:** `ReactContextBaseJavaModule`

**JavaScript Module Name:** `SalesforceNetReactBridge`

**Purpose:** Exposes REST API functionality with automatic authentication and token management.

#### Methods

##### sendRequest

```java
@ReactMethod
public void sendRequest(
    ReadableMap args,
    Callback successCallback,
    Callback errorCallback
)
```

Sends an authenticated REST request to Salesforce.

**Parameters:**
- `args` - Arguments map containing:
  - `method` (String): HTTP method - "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"
  - `path` (String): API path (e.g., "/services/data/v60.0/sobjects/Account")
  - `endPoint` (String, optional): Full endpoint URL (defaults to instance URL)
  - `queryParams` (Object, optional): Query string parameters
  - `headerParams` (Object, optional): Additional HTTP headers
  - `fileParams` (Object, optional): Files for multipart upload
  - `returnBinary` (Boolean, optional): If true, returns Base64-encoded binary
  - `doesNotRequireAuthentication` (Boolean, optional): Use unauthenticated client
- `successCallback` - Invoked with response body on success (2xx status)
- `errorCallback` - Invoked with error object on failure

**JavaScript Examples:**

1. **Simple GET Request:**
```javascript
SalesforceNetReactBridge.sendRequest(
    {
        method: 'GET',
        path: '/services/data/v60.0/sobjects/Account/001xx000000001'
    },
    (response) => {
        const account = JSON.parse(response);
        console.log(`Account Name: ${account.Name}`);
    },
    (error) => {
        const err = JSON.parse(error);
        console.error(`Error: ${err.response.statusCode}`);
    }
);
```

2. **GET with Query Parameters:**
```javascript
SalesforceNetReactBridge.sendRequest(
    {
        method: 'GET',
        path: '/services/data/v60.0/query',
        queryParams: {
            q: 'SELECT Id, Name FROM Account LIMIT 10'
        }
    },
    (response) => {
        const result = JSON.parse(response);
        console.log(`Found ${result.totalSize} records`);
        result.records.forEach(rec => console.log(rec.Name));
    },
    (error) => console.error('Query failed:', error)
);
```

3. **POST Request:**
```javascript
SalesforceNetReactBridge.sendRequest(
    {
        method: 'POST',
        path: '/services/data/v60.0/sobjects/Account',
        queryParams: {
            Name: 'New Account',
            Industry: 'Technology'
        }
    },
    (response) => {
        const result = JSON.parse(response);
        console.log(`Created account: ${result.id}`);
    },
    (error) => console.error('Create failed:', error)
);
```

4. **PATCH Request:**
```javascript
SalesforceNetReactBridge.sendRequest(
    {
        method: 'PATCH',
        path: '/services/data/v60.0/sobjects/Account/001xx000000001',
        queryParams: {
            Name: 'Updated Name',
            Phone: '555-1234'
        }
    },
    (response) => {
        console.log('Account updated');
    },
    (error) => console.error('Update failed:', error)
);
```

5. **DELETE Request:**
```javascript
SalesforceNetReactBridge.sendRequest(
    {
        method: 'DELETE',
        path: '/services/data/v60.0/sobjects/Account/001xx000000001'
    },
    (response) => {
        console.log('Account deleted');
    },
    (error) => console.error('Delete failed:', error)
);
```

6. **Custom Headers:**
```javascript
SalesforceNetReactBridge.sendRequest(
    {
        method: 'GET',
        path: '/services/data/v60.0/sobjects/Account/describe',
        headerParams: {
            'Sforce-Auto-Assign': 'FALSE',
            'Content-Type': 'application/json'
        }
    },
    (response) => {
        const metadata = JSON.parse(response);
        console.log(`Fields: ${metadata.fields.length}`);
    },
    (error) => console.error('Describe failed:', error)
);
```

7. **Binary Response:**
```javascript
SalesforceNetReactBridge.sendRequest(
    {
        method: 'GET',
        path: '/services/data/v60.0/sobjects/Account/001xx000000001/Photo__c',
        returnBinary: true
    },
    (response) => {
        const result = JSON.parse(response);
        console.log(`Content-Type: ${result.contentType}`);
        console.log(`Base64 data: ${result.encodedBody.substring(0, 50)}...`);
        // Can decode with: atob(result.encodedBody)
    },
    (error) => console.error('Binary fetch failed:', error)
);
```

8. **File Upload:**
```javascript
SalesforceNetReactBridge.sendRequest(
    {
        method: 'POST',
        path: '/services/data/v60.0/sobjects/ContentVersion',
        queryParams: {
            Title: 'My Document',
            PathOnClient: 'document.pdf'
        },
        fileParams: {
            VersionData: {
                fileMimeType: 'application/pdf',
                fileUrl: 'file:///path/to/document.pdf',
                fileName: 'document.pdf'
            }
        }
    },
    (response) => {
        const result = JSON.parse(response);
        console.log(`File uploaded: ${result.id}`);
    },
    (error) => console.error('Upload failed:', error)
);
```

9. **Unauthenticated Request:**
```javascript
SalesforceNetReactBridge.sendRequest(
    {
        method: 'GET',
        endPoint: 'https://api.example.com',
        path: '/public/data',
        doesNotRequireAuthentication: true
    },
    (response) => {
        console.log('Public data:', response);
    },
    (error) => console.error('Request failed:', error)
);
```

**Error Response Object:**
```json
{
    "error": "HTTP error message",
    "response": {
        "statusCode": 400,
        "headers": {
            "Content-Type": "application/json",
            "Date": "..."
        },
        "body": [
            {
                "message": "Required field missing",
                "errorCode": "REQUIRED_FIELD_MISSING",
                "fields": ["Name"]
            }
        ]
    }
}
```

**Notes:**
- Automatically includes OAuth access token in Authorization header
- Automatically refreshes expired tokens (401 responses)
- For GET/DELETE/HEAD: `queryParams` are appended to URL
- For POST/PUT/PATCH: `queryParams` become request body (unless `fileParams` provided)
- File uploads use multipart/form-data encoding

---

### ReactBridgeHelper

**Package:** `com.salesforce.androidsdk.reactnative.bridge`

**Purpose:** Utility class for converting between React Native and Java data types, and invoking callbacks with proper serialization.

#### Methods

##### invoke (JSONObject)

```java
public static void invoke(Callback callback, JSONObject json)
```

Invokes callback with a JSON object (serialized to string).

---

##### invoke (JSONArray)

```java
public static void invoke(Callback callback, JSONArray json)
```

Invokes callback with a JSON array (serialized to string).

---

##### invoke (String)

```java
public static void invoke(Callback callback, String value)
```

Invokes callback with a string value (wrapped in quotes for JSON.parse).

---

##### invoke (boolean)

```java
public static void invoke(Callback callback, boolean value)
```

Invokes callback with a boolean value (converted to string).

---

##### invoke (int)

```java
public static void invoke(Callback callback, int value)
```

Invokes callback with an integer value (converted to string).

---

##### toJavaMap

```java
public static Map<String, Object> toJavaMap(ReadableMap map)
```

Converts React Native `ReadableMap` to Java `Map<String, Object>`.

**Returns:** Java map with values converted to appropriate types (null, Boolean, Double, String, Map, List)

---

##### toJavaStringStringMap

```java
public static Map<String, String> toJavaStringStringMap(ReadableMap map)
```

Converts React Native `ReadableMap` to Java `Map<String, String>` (only string values).

---

##### toJavaStringMapMap

```java
public static Map<String,Map<String,String>> toJavaStringMapMap(ReadableMap map)
```

Converts React Native `ReadableMap` to nested Java map structure.

---

##### toJavaList

```java
public static List<Object> toJavaList(ReadableArray array)
```

Converts React Native `ReadableArray` to Java `List<Object>`.

---

##### toJavaStringList

```java
public static List<String> toJavaStringList(ReadableArray array)
```

Converts React Native `ReadableArray` to Java `List<String>` (only string values).

---

## UI Package

### SalesforceReactActivity

**Package:** `com.salesforce.androidsdk.reactnative.ui`

**Extends:** `ReactActivity`

**Implements:** `SalesforceActivityInterface`

**Purpose:** Base activity for React Native apps that integrates Salesforce authentication and SDK functionality.

#### Methods

##### shouldAuthenticate

```java
public boolean shouldAuthenticate()
```

Override to control whether authentication is required on app launch.

**Returns:** `true` to require login before app loads (default), `false` to allow anonymous access

**Example:**
```java
@Override
public boolean shouldAuthenticate() {
    // Allow app to load without authentication
    return false;
}
```

---

##### onErrorAuthenticateOffline

```java
public void onErrorAuthenticateOffline()
```

Called when `shouldAuthenticate()` returns true but device is offline. Override to customize offline behavior.

**Default behavior:** Shows a toast message

**Example:**
```java
@Override
public void onErrorAuthenticateOffline() {
    // Show custom offline dialog
    new AlertDialog.Builder(this)
        .setTitle("Offline")
        .setMessage("Please connect to the internet to log in")
        .setPositiveButton("OK", null)
        .show();
}
```

---

##### authenticate

```java
public void authenticate(Callback successCallback, Callback errorCallback)
```

Initiates authentication flow from JavaScript bridge. Called by `SalesforceOauthReactBridge`.

**Parameters:**
- `successCallback` - Invoked with credentials on success
- `errorCallback` - Invoked with error message on failure

**Note:** This is typically called from JavaScript, not directly from Java.

---

##### getAuthCredentials

```java
public void getAuthCredentials(Callback successCallback, Callback errorCallback)
```

Retrieves current authentication credentials. Called by `SalesforceOauthReactBridge`.

---

##### logout

```java
public void logout(Callback successCallback)
```

Logs out current user and clears local data. Called by `SalesforceOauthReactBridge`.

---

##### getRestClient

```java
public RestClient getRestClient()
```

Returns the authenticated REST client for making API calls.

**Returns:** `RestClient` instance, or null if not authenticated

**Example:**
```java
RestClient client = getRestClient();
if (client != null) {
    // Use client for API calls
}
```

---

##### buildClientManager

```java
public ClientManager buildClientManager()
```

Returns the client manager for managing authentication state.

**Returns:** `ClientManager` instance

---

##### showReactDevOptionsDialog

```java
public void showReactDevOptionsDialog()
```

Shows the React Native developer options dialog (debug builds only). Accessible via device shake or dev menu.

---

##### onResume (RestClient)

```java
@Override
public void onResume(RestClient c)
```

Called when activity resumes with a valid REST client. Override to perform actions after authentication.

**Example:**
```java
@Override
public void onResume(RestClient client) {
    super.onResume(client);
    // Perform authenticated operations
    if (client != null) {
        Log.i(TAG, "Resumed with authenticated client");
    }
}
```

---

##### onLogoutComplete

```java
@Override
public void onLogoutComplete()
```

Called after logout completes. Override to perform cleanup.

---

##### onUserSwitched

```java
@Override
public void onUserSwitched()
```

Called when user switches to a different account (multi-user scenario).

---

##### createReactActivityDelegate

```java
@Override
protected ReactActivityDelegate createReactActivityDelegate()
```

Creates the React activity delegate. Override to customize React Native loading behavior.

**Returns:** `SalesforceReactActivityDelegate` instance

---

### SalesforceReactActivityDelegate

**Package:** `com.salesforce.androidsdk.reactnative.ui`

**Extends:** `ReactActivityDelegate`

**Purpose:** Delegate that controls when the React Native app loads, ensuring authentication completes first.

#### Methods

##### loadReactAppOnceIfReady

```java
public void loadReactAppOnceIfReady(String appKey)
```

Loads the React Native app once ready (authenticated and permissions granted).

**Parameters:**
- `appKey` - React Native app component name

**Note:** Called automatically by the framework. Ensures app loads only once and only when ready.

---

## Util Package

### SalesforceReactLogger

**Package:** `com.salesforce.androidsdk.reactnative.util`

**Purpose:** Logging utility wrapper around `SalesforceLogger` specific to SalesforceReact library.

#### Methods

##### e (error with message)

```java
public static void e(String tag, String message)
```

Logs an error message.

**Parameters:**
- `tag` - Log tag (typically class name)
- `message` - Error message

**Example:**
```java
SalesforceReactLogger.e(TAG, "Failed to initialize bridge");
```

---

##### e (error with exception)

```java
public static void e(String tag, String message, Throwable e)
```

Logs an error message with exception.

**Parameters:**
- `tag` - Log tag
- `message` - Error message
- `e` - Exception/throwable

**Example:**
```java
try {
    // Operation
} catch (Exception e) {
    SalesforceReactLogger.e(TAG, "Operation failed", e);
}
```

---

##### w (warning with message)

```java
public static void w(String tag, String message)
```

Logs a warning message.

---

##### w (warning with exception)

```java
public static void w(String tag, String message, Throwable e)
```

Logs a warning message with exception.

---

##### i (info with message)

```java
public static void i(String tag, String message)
```

Logs an info message.

**Example:**
```java
SalesforceReactLogger.i(TAG, "Authentication successful");
```

---

##### i (info with exception)

```java
public static void i(String tag, String message, Throwable e)
```

Logs an info message with exception.

---

##### d (debug with message)

```java
public static void d(String tag, String message)
```

Logs a debug message.

---

##### d (debug with exception)

```java
public static void d(String tag, String message, Throwable e)
```

Logs a debug message with exception.

---

##### v (verbose with message)

```java
public static void v(String tag, String message)
```

Logs a verbose message.

---

##### v (verbose with exception)

```java
public static void v(String tag, String message, Throwable e)
```

Logs a verbose message with exception.

---

##### setLogLevel

```java
public static void setLogLevel(SalesforceLogger.Level level)
```

Sets the log level for SalesforceReact logging.

**Parameters:**
- `level` - Log level: `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`

**Example:**
```java
// In debug builds, enable verbose logging
if (BuildConfig.DEBUG) {
    SalesforceReactLogger.setLogLevel(SalesforceLogger.Level.VERBOSE);
}
```

---

## Common Patterns

### Error Handling

All bridge methods follow this pattern:

```java
try {
    // Perform operation
    Object result = performOperation(args);
    ReactBridgeHelper.invoke(successCallback, result);
} catch (Exception e) {
    SalesforceReactLogger.e(TAG, "Operation failed", e);
    errorCallback.invoke(e.toString());
}
```

### Store Selection

SmartStore and MobileSync methods use these optional parameters:

- `isGlobalStore` (Boolean): If true, uses global store (shared across users); if false, uses user-specific store
- `storeName` (String): Custom store name; defaults to default store if omitted

### Callback Serialization

Results are serialized as strings for React Native bridge:

- `JSONObject` / `JSONArray` → `toString()` → JavaScript calls `JSON.parse(result)`
- String values → Wrapped in quotes → JavaScript calls `JSON.parse(result)`
- Boolean / Number → Converted to string → JavaScript calls `JSON.parse(result)`

This pattern is necessary because React Native bridge doesn't support direct JSON object transfer in older architecture.

## See Also

- [README.md](README.md) - Library overview and getting started
- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical architecture details
- [TESTING.md](TESTING.md) - Testing guidelines
- [Android Javadoc](https://forcedotcom.github.io/SalesforceMobileSDK-Android/index.html) - Complete API documentation

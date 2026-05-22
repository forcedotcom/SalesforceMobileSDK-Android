# SalesforceReact Architecture

This document provides a deep technical dive into the architecture of the SalesforceReact library, including design patterns, component relationships, and data flow.

## Table of Contents

- [Architectural Overview](#architectural-overview)
- [Bridge Pattern](#bridge-pattern)
- [Component Hierarchy](#component-hierarchy)
- [Authentication Architecture](#authentication-architecture)
- [Data Flow Patterns](#data-flow-patterns)
- [Lifecycle Management](#lifecycle-management)
- [Threading Model](#threading-model)
- [Storage Architecture](#storage-architecture)

## Architectural Overview

SalesforceReact implements a bridge architecture that connects React Native JavaScript code with native Android Salesforce SDK functionality. The library sits at the intersection of three major systems:

```mermaid
graph TB
    subgraph "React Native Layer"
        A[JavaScript Code]
        B[React Native Bridge]
    end
    
    subgraph "SalesforceReact Bridge Layer"
        C[SalesforceOauthReactBridge]
        D[SmartStoreReactBridge]
        E[MobileSyncReactBridge]
        F[SalesforceNetReactBridge]
        G[ReactBridgeHelper]
    end
    
    subgraph "Activity Layer"
        H[SalesforceReactActivity]
        I[SalesforceReactActivityDelegate]
    end
    
    subgraph "SDK Management Layer"
        J[SalesforceReactSDKManager]
        K[SalesforceReactUpgradeManager]
    end
    
    subgraph "Core SDK Layer"
        L[MobileSyncSDKManager]
        M[SmartStore]
        N[SyncManager]
        O[RestClient]
        P[ClientManager]
    end
    
    A --> B
    B --> C & D & E & F
    C & D & E & F --> G
    C --> H
    D --> M
    E --> N
    F --> O
    H --> I
    H --> P
    J --> L
    K --> L
    
    style A fill:#61dafb
    style B fill:#00d8ff
    style C fill:#00d8ff
    style D fill:#00d8ff
    style E fill:#00d8ff
    style F fill:#00d8ff
    style G fill:#00d8ff
    style H fill:#87ceeb
    style I fill:#87ceeb
    style J fill:#4169e1
    style K fill:#4169e1
    style L fill:#0070d2
    style M fill:#0070d2
    style N fill:#0070d2
    style O fill:#0070d2
    style P fill:#0070d2
```

### Key Design Principles

1. **Separation of Concerns**: Each layer has a distinct responsibility
2. **Singleton Pattern**: SDK managers and stores use singletons for global access
3. **Callback Pattern**: Asynchronous operations return results via callbacks
4. **Delegation Pattern**: Activity delegates lifecycle management
5. **Factory Pattern**: SDK manager creates and registers bridge modules

## Bridge Pattern

### React Native Bridge Communication

The React Native bridge enables bidirectional communication between JavaScript and native code:

```mermaid
sequenceDiagram
    participant JS as JavaScript
    participant RNBridge as React Native Bridge
    participant NativeModule as Native Module
    participant SDK as SDK Component
    
    JS->>RNBridge: Call bridgeMethod(args, success, error)
    RNBridge->>NativeModule: @ReactMethod invoked
    NativeModule->>SDK: Native operation
    SDK-->>NativeModule: Result/Error
    alt Success
        NativeModule->>RNBridge: successCallback.invoke(result)
        RNBridge-->>JS: success(result)
    else Error
        NativeModule->>RNBridge: errorCallback.invoke(error)
        RNBridge-->>JS: error(error)
    end
```

### Bridge Module Registration

Bridge modules are registered via ReactPackage:

```java
public ReactPackage getReactPackage() {
    return new ReactPackage() {
        @Override
        public List<NativeModule> createNativeModules(
                ReactApplicationContext reactContext) {
            List<NativeModule> modules = new ArrayList<>();
            modules.add(new SalesforceOauthReactBridge(reactContext));
            modules.add(new SalesforceNetReactBridge(reactContext));
            modules.add(new SmartStoreReactBridge(reactContext));
            modules.add(new MobileSyncReactBridge(reactContext));
            return modules;
        }
        
        @Override
        public List<ViewManager> createViewManagers(
                ReactApplicationContext reactContext) {
            return Collections.emptyList();
        }
    };
}
```

### Bridge Helper Pattern

`ReactBridgeHelper` provides utility methods for data conversion between React Native and Java types:

- **JavaScript → Java**: `ReadableMap`/`ReadableArray` → `Map`/`List`
- **Java → JavaScript**: `JSONObject`/`JSONArray` → String (serialized for `JSON.parse()`)

This conversion is necessary because React Native bridge doesn't support direct JSON object transfer. The pattern is:

1. Java serializes `JSONObject` to String
2. Callback passes String to JavaScript
3. JavaScript calls `JSON.parse(result)` to reconstruct object

## Component Hierarchy

### Class Diagram

```mermaid
classDiagram
    class SalesforceReactSDKManager {
        -INSTANCE: SalesforceReactSDKManager
        +initReactNative(context, mainActivity)
        +getInstance() SalesforceReactSDKManager
        +getReactPackage() ReactPackage
        +getAppType() String
        +getDevActions() Map
    }
    
    class MobileSyncSDKManager {
        +getClientManager() ClientManager
        +getSmartStore() SmartStore
        +getSyncManager() SyncManager
    }
    
    class SalesforceReactUpgradeManager {
        -INSTANCE: SalesforceReactUpgradeManager
        +getInstance() SalesforceReactUpgradeManager
        +upgrade() void
    }
    
    class MobileSyncUpgradeManager {
        +upgrade() void
    }
    
    class SalesforceReactActivity {
        <<abstract>>
        -client: RestClient
        -clientManager: ClientManager
        -delegate: SalesforceActivityDelegate
        -reactActivityDelegate: SalesforceReactActivityDelegate
        -pendingAuthSuccessCallback: Callback
        -pendingAuthErrorCallback: Callback
        +shouldAuthenticate() boolean
        +authenticate(success, error)
        +getAuthCredentials(success, error)
        +logout(success)
        +getRestClient() RestClient
        #createReactActivityDelegate() ReactActivityDelegate
    }
    
    class ReactActivity {
        +onCreate(Bundle)
        +onResume()
        +onPause()
        +onDestroy()
        #getMainComponentName() String
    }
    
    class SalesforceActivityInterface {
        <<interface>>
        +onResume(RestClient)
        +onLogoutComplete()
        +onUserSwitched()
    }
    
    class SalesforceReactActivityDelegate {
        -salesforceReactActivity: SalesforceReactActivity
        -loaded: boolean
        +loadReactAppOnceIfReady(appKey)
    }
    
    class ReactActivityDelegate {
        #loadApp(appKey)
    }
    
    SalesforceReactSDKManager --|> MobileSyncSDKManager
    SalesforceReactUpgradeManager --|> MobileSyncUpgradeManager
    SalesforceReactActivity --|> ReactActivity
    SalesforceReactActivity ..|> SalesforceActivityInterface
    SalesforceReactActivityDelegate --|> ReactActivityDelegate
    SalesforceReactActivity --> SalesforceReactActivityDelegate
    SalesforceReactActivity --> SalesforceReactSDKManager
```

### Bridge Modules Hierarchy

```mermaid
classDiagram
    class ReactContextBaseJavaModule {
        <<React Native>>
        +getName() String
        #getReactApplicationContext() ReactApplicationContext
        #getCurrentActivity() Activity
    }
    
    class SalesforceOauthReactBridge {
        +authenticate(args, success, error)
        +getAuthCredentials(args, success, error)
        +logoutCurrentUser(args, success, error)
    }
    
    class SmartStoreReactBridge {
        -STORE_CURSORS: Map~SQLiteDatabase, SparseArray~
        +registerSoup(args, success, error)
        +querySoup(args, success, error)
        +runSmartQuery(args, success, error)
        +upsertSoupEntries(args, success, error)
        +removeFromSoup(args, success, error)
        +retrieveSoupEntries(args, success, error)
        +soupExists(args, success, error)
        +alterSoup(args, success, error)
        +getDatabaseSize(args, success, error)
        +getAllStores(args, success, error)
        +removeStore(args, success, error)
        +getSmartStore(args) SmartStore
    }
    
    class MobileSyncReactBridge {
        +syncDown(args, success, error)
        +syncUp(args, success, error)
        +getSyncStatus(args, success, error)
        +reSync(args, success, error)
        +deleteSync(args, success, error)
        +cleanResyncGhosts(args, success, error)
        -handleSyncUpdate(sync, success, error)
        -getSyncManager(args) SyncManager
    }
    
    class SalesforceNetReactBridge {
        +sendRequest(args, success, error)
        #getRestClient(doesNotRequireAuth) RestClient
        -prepareRestRequest(args) RestRequest
        -buildQueryString(params) String
        -buildRequestBody(params, fileParams) RequestBody
        -parsedResponse(response) Object
    }
    
    class ReactBridgeHelper {
        <<utility>>
        +invoke(callback, JSONObject)
        +invoke(callback, JSONArray)
        +invoke(callback, String)
        +invoke(callback, boolean)
        +invoke(callback, int)
        +toJavaMap(ReadableMap) Map
        +toJavaList(ReadableArray) List
        +toJavaStringStringMap(ReadableMap) Map
        +toJavaStringMapMap(ReadableMap) Map
    }
    
    ReactContextBaseJavaModule <|-- SalesforceOauthReactBridge
    ReactContextBaseJavaModule <|-- SmartStoreReactBridge
    ReactContextBaseJavaModule <|-- MobileSyncReactBridge
    ReactContextBaseJavaModule <|-- SalesforceNetReactBridge
    
    SalesforceOauthReactBridge ..> ReactBridgeHelper
    SmartStoreReactBridge ..> ReactBridgeHelper
    MobileSyncReactBridge ..> ReactBridgeHelper
    SalesforceNetReactBridge ..> ReactBridgeHelper
```

## Authentication Architecture

### Authentication State Machine

```mermaid
stateDiagram-v2
    [*] --> NotAuthenticated: App Launch
    
    NotAuthenticated --> CheckingAuth: shouldAuthenticate() == true
    NotAuthenticated --> AppRunning: shouldAuthenticate() == false
    
    CheckingAuth --> PeekClient: Check for cached client
    
    PeekClient --> AlreadyAuthenticated: Client exists
    PeekClient --> NeedsOAuth: Client null
    
    AlreadyAuthenticated --> AppRunning: Load React app
    
    NeedsOAuth --> CheckNetwork: Online?
    CheckNetwork --> OAuthFlow: Yes
    CheckNetwork --> OfflineError: No
    
    OAuthFlow --> LoginActivity: Launch login
    LoginActivity --> ActivityPaused: Activity paused
    ActivityPaused --> ActivityResumed: User completes login
    ActivityResumed --> AlreadyAuthenticated: onResume(client)
    
    AppRunning --> TokenExpired: API call with 401
    TokenExpired --> TokenRefresh: Auto refresh
    TokenRefresh --> AppRunning: Success
    TokenRefresh --> ReAuthenticate: Refresh failed
    ReAuthenticate --> OAuthFlow
    
    AppRunning --> LogoutRequested: logout()
    LogoutRequested --> NotAuthenticated: Clear session
```

### Pending Callback Mechanism

The authentication flow uses a sophisticated callback coordination mechanism to handle race conditions between OAuth and activity lifecycle:

```mermaid
sequenceDiagram
    participant JS as JavaScript
    participant Bridge as OAuthBridge
    participant Activity as ReactActivity
    participant ClientMgr as ClientManager
    participant OAuth as OAuth Screen
    
    Note over JS,OAuth: Scenario 1: Already Authenticated
    JS->>Bridge: authenticate(success, error)
    Bridge->>Activity: authenticate(success, error)
    Activity->>Activity: Store in pending callbacks
    Activity->>ClientMgr: getRestClient()
    ClientMgr-->>Activity: authenticatedRestClient(client)
    Activity->>Activity: Check pending callbacks (NOT NULL)
    Activity->>Activity: Invoke callbacks
    Activity->>Activity: Clear pending callbacks
    Activity-->>JS: Return credentials
    
    Note over JS,OAuth: Scenario 2: OAuth Required
    JS->>Bridge: authenticate(success, error)
    Bridge->>Activity: authenticate(success, error)
    Activity->>Activity: Store in pending callbacks
    Activity->>ClientMgr: getRestClient()
    ClientMgr->>OAuth: Launch OAuth
    Activity->>Activity: onPause()
    OAuth->>OAuth: User logs in
    OAuth-->>ClientMgr: Return client
    ClientMgr-->>Activity: authenticatedRestClient(client)
    
    par Race Condition - Either order works
        Activity->>Activity: authenticatedRestClient callback
        Activity->>Activity: Check pending (NOT NULL)
        Activity->>Activity: Invoke & clear callbacks
    and
        Activity->>Activity: onResume(client)
        Activity->>Activity: Check pending (NULL - already cleared)
        Activity->>Activity: No-op
    end
    
    Activity-->>JS: Return credentials
```

The key insight: **Both `authenticatedRestClient()` callback and `onResume()` check and clear pending callbacks atomically. Whichever runs first invokes them; the other sees null and does nothing.**

### Authentication Code Flow

```java
// Store callbacks for both scenarios
pendingAuthSuccessCallback = successCallback;
pendingAuthErrorCallback = errorCallback;

clientManager.getRestClient(this, new RestClientCallback() {
    @Override
    public void authenticatedRestClient(RestClient client) {
        setRestClient(client);
        
        // ALWAYS invoke callbacks here (both scenarios)
        if (pendingAuthSuccessCallback != null) {
            getAuthCredentials(pendingAuthSuccessCallback, pendingAuthErrorCallback);
            pendingAuthSuccessCallback = null;
            pendingAuthErrorCallback = null;
        }
    }
});

// onResume also checks and clears (race condition safe)
@Override
public void onResume(RestClient c) {
    setRestClient(clientManager.peekRestClient());
    
    // Only invoke if authenticatedRestClient hasn't run yet
    if (pendingAuthSuccessCallback != null) {
        getAuthCredentials(pendingAuthSuccessCallback, pendingAuthErrorCallback);
        pendingAuthSuccessCallback = null;
        pendingAuthErrorCallback = null;
    }
}
```

## Data Flow Patterns

### SmartStore Query Flow

```mermaid
sequenceDiagram
    participant JS as JavaScript
    participant Bridge as SmartStoreReactBridge
    participant Store as SmartStore
    participant SQLCipher as SQLCipher Database
    participant Cursor as StoreCursor
    
    JS->>Bridge: querySoup({soupName, querySpec})
    Bridge->>Bridge: toJavaMap(args)
    Bridge->>Bridge: getSmartStore(args)
    alt Global Store
        Bridge->>Store: getGlobalSmartStore(name)
    else User Store
        Bridge->>Store: getSmartStore(name, account, communityId)
    end
    
    Bridge->>Store: new StoreCursor(querySpec)
    Store->>SQLCipher: Execute query
    SQLCipher-->>Store: Result set
    Store-->>Cursor: Initialize cursor
    Bridge->>Bridge: Store cursor by ID
    Bridge->>Cursor: getDataSerialized()
    Cursor-->>Bridge: JSON result
    Bridge->>Bridge: ReactBridgeHelper.invoke(success, json)
    Bridge-->>JS: Return serialized JSON
    JS->>JS: JSON.parse(result)
```

### MobileSync Sync Flow

```mermaid
sequenceDiagram
    participant JS as JavaScript
    participant Bridge as MobileSyncReactBridge
    participant SyncMgr as SyncManager
    participant Store as SmartStore
    participant Rest as RestClient
    participant Server as Salesforce Server
    
    Note over JS,Server: Sync Down Flow
    JS->>Bridge: syncDown({target, soupName, options})
    Bridge->>Bridge: getSyncManager(args)
    Bridge->>Bridge: SyncDownTarget.fromJSON(target)
    Bridge->>SyncMgr: syncDown(target, options, soup, callback)
    
    loop Sync Progress
        SyncMgr->>Rest: Fetch batch from server
        Rest->>Server: SOQL/SOSL/MRU query
        Server-->>Rest: Return batch
        Rest-->>SyncMgr: Records batch
        SyncMgr->>Store: Upsert batch to soup
        Store-->>SyncMgr: Confirm
        SyncMgr->>Bridge: onUpdate(syncState)
        alt Status == RUNNING
            Bridge->>Bridge: No callback yet
        else Status == DONE
            Bridge->>Bridge: ReactBridgeHelper.invoke(success, syncState)
            Bridge-->>JS: Success with final state
        else Status == FAILED
            Bridge->>Bridge: ReactBridgeHelper.invoke(error, syncState)
            Bridge-->>JS: Error with sync state
        end
    end
```

### REST Request Flow

```mermaid
sequenceDiagram
    participant JS as JavaScript
    participant Bridge as SalesforceNetReactBridge
    participant Activity as SalesforceReactActivity
    participant Rest as RestClient
    participant OkHttp as OkHttp
    participant Server as Salesforce Server
    
    JS->>Bridge: sendRequest({method, path, queryParams, headerParams})
    Bridge->>Bridge: prepareRestRequest(args)
    alt GET/DELETE
        Bridge->>Bridge: buildQueryString(params)
    else POST/PUT/PATCH
        Bridge->>Bridge: buildRequestBody(params, fileParams)
    end
    
    Bridge->>Activity: getRestClient(doesNotRequireAuth)
    Activity-->>Bridge: RestClient
    
    Bridge->>Rest: sendAsync(request, callback)
    Rest->>OkHttp: Execute HTTP request
    OkHttp->>Server: HTTP call
    Server-->>OkHttp: HTTP response
    OkHttp-->>Rest: Response
    
    alt Status 2xx
        Rest->>Bridge: onSuccess(request, response)
        alt returnBinary
            Bridge->>Bridge: Base64.encode(response.asBytes())
            Bridge-->>JS: {contentType, encodedBody}
        else
            Bridge->>Bridge: response.asString()
            Bridge-->>JS: JSON string
        end
    else Status 401 (Unauthorized)
        Rest->>Rest: Auto token refresh
        Rest->>Server: Retry with new token
        Server-->>Rest: Response
        Rest->>Bridge: onSuccess (after refresh)
    else Other Error
        Rest->>Bridge: onError(exception)
        Bridge-->>JS: Error object
    end
```

## Lifecycle Management

### Activity Lifecycle Integration

```mermaid
stateDiagram-v2
    [*] --> Created: onCreate()
    
    Created --> Created: Initialize clientManager
    Created --> Created: Initialize delegate
    Created --> Resumed: onResume()
    
    Resumed --> CheckAuth: Check authentication
    CheckAuth --> LoadApp: Authenticated
    CheckAuth --> StartOAuth: Not authenticated
    
    StartOAuth --> Paused: Login activity starts
    
    LoadApp --> Running: React app loaded
    
    Running --> Paused: onPause()
    Paused --> Resumed: onResume()
    
    Running --> Destroyed: onDestroy()
    Paused --> Destroyed: onDestroy()
    
    Destroyed --> [*]
```

### React App Loading Control

The `SalesforceReactActivityDelegate` ensures React app loads only when ready:

```java
public void loadReactAppOnceIfReady(String appKey) {
    if (!loaded && salesforceReactActivity != null 
        && salesforceReactActivity.shouldReactBeRunning()) {
        super.loadApp(appKey);
        loaded = true;
        super.onResume();
    }
}

protected boolean shouldReactBeRunning() {
    // Wait for overlay permission in dev mode
    // Wait for authentication if required
    return !shouldAskOverlayPermission() 
        && (!shouldAuthenticate() || client != null);
}
```

This prevents React Native from loading before:
1. Developer overlay permissions are granted (dev mode)
2. OAuth authentication completes (if required)

## Threading Model

### Thread Responsibilities

```mermaid
graph TB
    subgraph "Main Thread"
        A[Activity Lifecycle]
        B[UI Updates]
        C[React Native Bridge Calls]
    end
    
    subgraph "Background Threads"
        D[REST API Calls]
        E[OAuth Flow]
        F[Sync Operations]
    end
    
    subgraph "Database Thread"
        G[SmartStore Queries]
        H[SmartStore Writes]
    end
    
    C --> D
    C --> F
    C --> G
    D --> C
    F --> C
    G --> C
    
    style A fill:#ff6b6b
    style B fill:#ff6b6b
    style C fill:#ff6b6b
    style D fill:#4ecdc4
    style E fill:#4ecdc4
    style F fill:#4ecdc4
    style G fill:#95e1d3
    style H fill:#95e1d3
```

### Synchronization Patterns

**SmartStore Database Lock:**
```java
synchronized(smartStore.getDatabase()) {
    smartStore.beginTransaction();
    try {
        // Multiple operations in transaction
        smartStore.upsert(soupName, entry1);
        smartStore.upsert(soupName, entry2);
        smartStore.setTransactionSuccessful();
    } finally {
        smartStore.endTransaction();
    }
}
```

**Static Cursor Management:**
```java
private static Map<SQLiteDatabase, SparseArray<StoreCursor>> STORE_CURSORS = 
    new HashMap<>();

private synchronized static SparseArray<StoreCursor> getSmartStoreCursors(SmartStore store) {
    final SQLiteDatabase db = store.getDatabase();
    if (!STORE_CURSORS.containsKey(db)) {
        STORE_CURSORS.put(db, new SparseArray<>());
    }
    return STORE_CURSORS.get(db);
}
```

## Storage Architecture

### SmartStore Multi-Store Support

```mermaid
graph TB
    subgraph "SmartStore Architecture"
        A[SmartStoreReactBridge]
        
        subgraph "User Stores"
            B1[User1 Default Store]
            B2[User1 Custom Store]
            C1[User2 Default Store]
            C2[User2 Custom Store]
        end
        
        subgraph "Global Stores"
            D1[Global Default Store]
            D2[Global Custom Store]
        end
    end
    
    A -->|isGlobalStore=false| B1 & B2 & C1 & C2
    A -->|isGlobalStore=true| D1 & D2
    
    style B1 fill:#87ceeb
    style B2 fill:#87ceeb
    style C1 fill:#87ceeb
    style C2 fill:#87ceeb
    style D1 fill:#90ee90
    style D2 fill:#90ee90
```

### Store Resolution Logic

```java
public static SmartStore getSmartStore(ReadableMap args) throws Exception {
    boolean isGlobal = getIsGlobal(args);
    final String storeName = getStoreName(args);
    
    if (isGlobal) {
        return SmartStoreSDKManager.getInstance()
            .getGlobalSmartStore(storeName);
    } else {
        final UserAccount account = UserAccountManager.getInstance()
            .getCachedCurrentUser();
        if (account == null) {
            throw new Exception("No user account found");
        }
        return SmartStoreSDKManager.getInstance()
            .getSmartStore(storeName, account, account.getCommunityId());
    }
}
```

### Cursor Management

Cursors enable pagination of large query results:

```java
// Create cursor and cache it
final StoreCursor storeCursor = new StoreCursor(smartStore, querySpec);
getSmartStoreCursors(smartStore).put(storeCursor.cursorId, storeCursor);

// Return first page
JSONObject result = storeCursor.getDataSerialized(smartStore);

// Later: move to different page
StoreCursor cached = getSmartStoreCursors(smartStore).get(cursorId);
cached.moveToPageIndex(pageIndex);
JSONObject page = cached.getDataSerialized(smartStore);

// Close cursor when done
getSmartStoreCursors(smartStore).remove(cursorId);
```

## Error Handling Patterns

### Bridge Error Propagation

```java
try {
    // Perform operation
    JSONObject result = performOperation(args);
    ReactBridgeHelper.invoke(successCallback, result);
} catch (Exception e) {
    SalesforceReactLogger.e(TAG, "Operation failed", e);
    errorCallback.invoke(e.toString());
}
```

### REST Error Handling

```java
@Override
public void onSuccess(RestRequest request, RestResponse response) {
    // Check HTTP status
    if (!response.isSuccess()) {
        JSONObject errorObject = new JSONObject();
        errorObject.put("headers", response.getAllHeaders());
        errorObject.put("statusCode", response.getStatusCode());
        errorObject.put("body", parsedResponse(response));
        
        JSONObject error = new JSONObject();
        error.put("response", errorObject);
        errorCallback.invoke(error.toString());
    } else {
        successCallback.invoke(response.asString());
    }
}
```

## Performance Considerations

### Bridge Communication Overhead

- **String serialization**: JSON objects are serialized to strings for bridge crossing
- **Callback invocation**: Each callback requires bridge traversal
- **Large data sets**: Use pagination (cursors) for large query results

### Optimization Strategies

1. **Batch operations**: Use transactions for multiple SmartStore writes
2. **Cursor pagination**: Avoid loading entire result sets into memory
3. **Async operations**: All I/O is asynchronous to prevent blocking
4. **Connection pooling**: RestClient reuses OkHttp connections

## Future Architecture Considerations

### New Architecture Migration

React Native's "New Architecture" introduces:
- **TurboModules**: Lazy-loaded native modules with type safety
- **Fabric**: New rendering system
- **JSI**: JavaScript Interface for direct JS ↔ Native communication

Migration would involve:
1. Converting `ReactContextBaseJavaModule` to `TurboModule`
2. Defining TypeScript specs for type safety
3. Replacing callback pattern with promises/async-await
4. Direct object passing instead of string serialization

### Codegen Specifications

Example TurboModule spec:
```typescript
export interface Spec extends TurboModule {
  authenticate(): Promise<Credentials>;
  getAuthCredentials(): Promise<Credentials>;
  logout(): Promise<void>;
}
```

## Summary

The SalesforceReact architecture provides a robust bridge between React Native and native Salesforce SDK functionality through:

- **Layered design** separating concerns across bridge, activity, and SDK layers
- **Callback coordination** ensuring reliable async operation handling
- **Multi-store support** for user-specific and global data
- **Thread-safe operations** with proper synchronization
- **Lifecycle awareness** coordinating authentication with app loading

This architecture enables React Native developers to access the full power of Salesforce Mobile SDK while maintaining a clean separation between JavaScript and native code.

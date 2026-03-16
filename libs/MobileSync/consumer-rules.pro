# Salesforce Mobile SDK - MobileSync Consumer ProGuard Rules
# These rules are automatically applied to apps that consume this SDK library

# Keep SyncUpTarget subclasses - instantiated via reflection in SyncUpTarget.fromJSON
-keep class com.salesforce.androidsdk.mobilesync.target.SyncUpTarget {
    public <init>(...);
}
-keep class * extends com.salesforce.androidsdk.mobilesync.target.SyncUpTarget {
    public <init>(org.json.JSONObject);
}

# Keep SyncDownTarget subclasses - instantiated via reflection in SyncDownTarget.fromJSON
-keep class com.salesforce.androidsdk.mobilesync.target.SyncDownTarget {
    public <init>(...);
}
-keep class * extends com.salesforce.androidsdk.mobilesync.target.SyncDownTarget {
    public <init>(org.json.JSONObject);
}

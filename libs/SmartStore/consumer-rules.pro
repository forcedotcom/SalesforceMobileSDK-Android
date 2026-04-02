# Salesforce Mobile SDK - SmartStore Consumer ProGuard Rules
# These rules are automatically applied to apps that consume this SDK library

# Keep LongOperation subclasses - instantiated via reflection in LongOperation.LongOperationType
-keep class com.salesforce.androidsdk.smartstore.store.LongOperation {
    public <init>(...);
}
-keep class * extends com.salesforce.androidsdk.smartstore.store.LongOperation {
    public <init>(...);
}

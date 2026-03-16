# Salesforce Mobile SDK - Consumer ProGuard Rules
# These rules are automatically applied to apps that consume this SDK library

# Keep PushService and subclasses - instantiated via reflection in PushNotificationsRegistrationChangeWorker
-keep class com.salesforce.androidsdk.push.PushService {
    public <init>(...);
}
-keep class * extends com.salesforce.androidsdk.push.PushService {
    public <init>(...);
}

# Keep Transform implementations - instantiated via reflection in SalesforceAnalyticsManager
-keep class * implements com.salesforce.androidsdk.analytics.transform.Transform {
    public <init>(...);
}

# Keep AnalyticsPublisher implementations - instantiated via reflection in SalesforceAnalyticsManager
-keep class * extends com.salesforce.androidsdk.analytics.AnalyticsPublisher {
    public <init>(...);
}

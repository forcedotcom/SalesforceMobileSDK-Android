# Introduction

This folder contains the library projects provided in the Salesforce Mobile SDK, and the test projects associated with them.

### SDK notes

# Setting up your development environment

Please follow the instructions for setting up your development environment from the README file at the root of this repo, prior to working with the Salesforce SDK.

For the rest of this document, we assume that you have setup three shell variables:

1. ANDROID_SDK_DIR pointing to the Android SDK directory
2. SALESFORCE_SDK_DIR pointing to your clone of the Salesforce Mobile SDK repository e.g. `/home/jon/SalesforceMobileSDK-Android`.
3. LIBS_DIR pointing to $SALESFORCE_SDK_DIR/libs

**If you haven't, just make sure to replace $ANDROID_SDK_DIR, $SALESFORCE_SDK_DIR and $LIBS_DIR in the snippets below with the actual paths.**

Inside the $NATIVE_DIR, you will find several projects:

1. **SalesforceAnalytics**: The SalesforceAnalytics library project which provides instrumentation and analytics functionality
2. **SalesforceSDK**: The SalesforceSDK library project which provides support for OAuth2, REST API calls, pin screen (driven by mobile policy)
3. **SmartStore**: Library project which provides the SmartStore functionality
4. **SmartSync**: The SmartSync library project which provides support for Salesforce object metadata API calls, layout API calls, MRU API calls, caching
5. **SalesforceHybrid**: The SalesforceHybrid library project which provides support for Cordova based hybrid apps
6. **SalesforceReact**: The SalesforceReact library project which provides support for React based native apps
7. **test/SalesforceAnalyticsTest**: Test project for SalesforceAnalytics
8. **test/SalesforceSDKTest**: Test project for SalesforceSDK
9. **test/SmartStoreTest**: Test project for SmartStore
10. **test/SmartSyncTest**: Test project for SmartSync
11. **test/SalesforceHybridTest**: Test project for SalesforceHybrid
12. **test/SalesforceReactTest**: Test project for SalesforceReact

# Running sample apps from Android Studio

1. Launch Android Studio and select `Import project (Eclipse ADT, Gradle, etc.)`.
2. Navigate to $SALESFORCE_SDK_DIR, select it and click `Ok`.
3. If a popup appears with the message `Unregistered VCS roots detected`, click `Add roots`.
4. From the dropdown that displays the available targets, choose the sample app or test suite you want to run and click the play button.

# Creating a new native application using SalesforceSDK

To create a new native application, follow the instructions here:

* [Using forcedroid to create Mobile SDK apps](https://www.npmjs.org/package/forcedroid)

# Running your new native application from Android Studio

1. Launch Android Studio and select `Import project (Eclipse ADT, Gradle, etc.)`.
2. Navigate to your application's directory, select it and click `Ok`.
3. If a popup appears with the message `Unregistered VCS roots detected`, click `Add roots`.
4. From the dropdown that displays the available targets, choose your application and click the play button.

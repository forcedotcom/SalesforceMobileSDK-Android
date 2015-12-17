# Introduction

This folder contains sample hybrid apps that combine the native Salesforce SDK functionality with HTML5 and javascript, to create web-based apps that can make use of extended device capabilities for enhanced app functionality.

# Setting up your development environment

Please follow the instructions for setting up your development environment from the README file at the root of this repo, prior to working with the Salesforce SDK.

For the rest of this document, we assume that you have setup three shell variables:

1. ANDROID_SDK_DIR pointing to the Android SDK directory
2. SALESFORCE_SDK_DIR pointing to your clone of the Salesforce Mobile SDK repository e.g. `/home/jon/SalesforceMobileSDK-Android`.
3. NATIVE_DIR pointing to $SALESFORCE_SDK_DIR/native
4. HYBRID_DIR pointing to $SALESFORCE_SDK_DIR/hybrid
5. LIBS_DIR pointing to $SALESFORCE_SDK_DIR/libs

**If you haven't, just make sure to replace $ANDROID_SDK_DIR, $SALESFORCE_SDK_DIR, NATIVE_DIR, HYBRID_DIR and LIBS_DIR in the snippets below with the actual paths.**

Inside the HYBRID_DIR, you will find several projects:

1. **SampleApps/AccountEditor**: The AccountEditor sample app allows you to search / create / edit / delete accounts online and offline using the new SmartSync library (smartsync.js)
2. **SampleApps/SmartSyncExplorerHybrid**: The SmartSyncExplorerHybrid sample app demonstrates two way synching of Salesforce records
3. **SampleApps/NoteSync**: The NoteSync sample app demonstrates two way synching of Salesforce Note objects with custom sync down and sync up targets

# Running sample apps from Android Studio

1. Launch Android Studio and select `Import project (Eclipse ADT, Gradle, etc.)`.
2. Navigate to $SALESFORCE_SDK_DIR, select it and click `Ok`.
3. If a popup appears with the message `Unregistered VCS roots detected`, click `Add roots`.
4. From the dropdown that displays the available targets, choose the sample app or test suite you want to run and click the play button.

# Creating a new hybrid application using SalesforceSDK

To create a new hybrid application, follow the instructions here:

* [Using forcedroid to create Mobile SDK apps](https://www.npmjs.org/package/forcedroid)

# Running your new hybrid application from Android Studio

1. Launch Android Studio and select `Import project (Eclipse ADT, Gradle, etc.)`.
2. Navigate to your application's directory, select it and click `Ok`.
3. If a popup appears with the message `Unregistered VCS roots detected`, click `Add roots`.
4. From the dropdown that displays the available targets, choose your application and click the play button.

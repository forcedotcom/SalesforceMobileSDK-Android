# Introduction

This folder contains the native sample projects that can be used to exercise the SDK, and test projects associated with those sample projects.

# Setting up your development environment

Please follow the instructions for setting up your development environment from the README file at the root of this repo, prior to working with the Salesforce SDK.

For the rest of this document, we assume that you have setup three shell variables:

1. ANDROID_SDK_DIR pointing to the Android SDK directory
2. SALESFORCE_SDK_DIR pointing to your clone of the Salesforce Mobile SDK repository e.g. `/home/jon/SalesforceMobileSDK-Android`.
3. NATIVE_DIR pointing to $SALESFORCE_SDK_DIR/native
4. LIBS_DIR pointing to $SALESFORCE_SDK_DIR/libs

**If you haven't, just make sure to replace $ANDROID_SDK_DIR, $SALESFORCE_SDK_DIR, $NATIVE_DIR and $LIBS_DIR in the snippets below with the actual paths.**

Inside the $NATIVE_DIR, you will find several projects:

1. **NativeSampleApps/RestExplorer**: An app using SalesforceSDK to explore the REST API calls
2. **NativeSampleApps/test/RestExplorerTest**: Test project for the RestExplorer project
3. **NativeSampleApps/SmartSyncExplorer**: An app using SmartSync data framework to demonstrate synching a list of contacts from and to the server
4. **NativeSampleApps/AppConfigurator**: An app to specify runtime configurations for ConfiguredApp (it allows configuration of login server and oauth consumer key and redirect uri) 
5. **NativeSampleApps/ConfiguredApp**: An app that consumes the runtime configurations specified with AppConfigurator

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


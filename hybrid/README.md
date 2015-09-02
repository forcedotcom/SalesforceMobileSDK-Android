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

1. **test/ForcePluginsTest**: Test project for the phonegap plugins
2. **SampleApps/AccountEditor**: The AccountEditor sample app allows you to search / create / edit / delete accounts online and offline using the new SmartSync library (smartsync.js)
3. **SampleApps/SimpleSync**: The SimpleSync sample app demonstrates two way synching of Salesforce records
4. **SampleApps/NoteSync**: The SimpleSync sample app demonstrates two way synching of Salesforce Note objects with custom sync down and sync up targets

# Running sample apps from Eclipse

1. Launch Eclipse and select $SALESFORCE_SDK_DIR as your workspace 
2. Go to Window -> Preferences, choose the Android section, and enter the the Android SDK location.
3. Go to File -> Import and select General -> Existing Projects into Workspace.
4. Select $SALESFORCE_SDK_DIR as your root directory and import the projects described above along with the libs/SalesforceSDK library project.
5. Right click on any of the sample apps and choose Run As -> Android Application to run it.
6. Right click on any of the test project and choose Run As -> Android JUnit Test to run the tests.

# Creating a new hybrid application using SalesforceSDK

To create a new hybrid application, follow the instructions here:

* [Using forcedroid to create Mobile SDK apps](https://www.npmjs.org/package/forcedroid)

# Running your new native application from Eclipse
1. Launch Eclipse
2. Go to File -> Import and select General -> Existing Projects into Workspace.
3. Import the $SALESFORCE_SDK_DIR/libs/SalesforceSDK, the $SALESFORCE_SDK_DIR/libs/SmartStore, and the $SALESFORCE_SDK_DIR/libs/SmartSync library projects and your newly created project into the workspace
4. Right click on the your project and choose Run As -> Android Application


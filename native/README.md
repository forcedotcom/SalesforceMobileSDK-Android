# Introduction

This folder contains the native libraries of the Salesforce SDK, as well as test and sample projects that can be used to exercise the SDK.

### SDK notes

* The SDK is supported and tested for Android 2.2 and above.
* The SalesforceSDK project is built with the Android 3.0 (Honeycomb) library.  The primary reason for this is that we want to be able to make a conditional check at runtime for file system encryption capabilities.  This check is guarded from being called on earlier Android platforms, the net result being that you can still use the salesforcesdk.jar in earlier Android application versions, down to the mininum-supported Android 2.2.

# Setting up your development environment

Please follow the instructions for setting up your development environment from the README file at the root of this repo, prior to working with the Salesforce SDK.

# Downloading the Salesforce SDK

To pull down the SDK from github, create a new directory and git clone the salesforce SDK repo.
<pre>
git clone git@github.com:forcedotcom/SalesforceMobileSDK-Android-dev.git
</pre>

For the rest of this document, we assume that you have setup two shell variables:

1. ANDROID_SDK_DIR pointing to the Android SDK directory	
2. NATIVE_DIR pointing to the native directory of your clone of the Salesforce Mobile SDK repository, e.g. `/home/jon/SalesforceMobileSDK-Android/native`.	

**If you haven't, just make sure to replace $ANDROID_SDK_DIR and $NATIVE_DIR in the snippets below with the actual paths.**

There are four projects:

1. **SalesforceSDK**: The Salesforce SDK which provides support for OAuth2 and REST API calls
2. **SalesforceSDKTest**: Tests for SalesforceSDK
3. **RestExplorer**: A sample app using SalesforceSDK to exercise the REST API calls
4. **RestExplorerTest**: Tests for the RestExplorer project

# Setting up projects and building from Eclipse

1. Launch Eclipse and select $NATIVE_DIR as your workspace directory.
2. Go to Window -> Preferences, choose the Android section, and enter the the Android SDK location.
3. Go to File -> Import and select General -> Existing Projects into Workspace.
4. Select $NATIVE_DIR as your root directory and import the four projects above.
5. Create a gen folder for the SalesforceSDKTest, RestExplorer and RestExplorerTest projects (right-click the project and choose new -> folder).
6. Create a res folder for the SalesforceSDK project.

**Cleaning and rebuilding in Eclipse**: With the latest version of the Android SDK Tools (v14), there are issues around cleaning your workspace (Project -> Clean...) and rebuilding it.  Specifically, projects that are dependent on Android Library projects do not properly follow the build dependency ordering, so when every project is cleaned, dependent projects do not pick up the existence of the Library project.  The result is that all of the non-Library projects will have build errors after a clean.

If you would like to rebuild everything, we recommend cleaning/rebuilding the Library (SalesforceSDK) project *by itself* first, followed by the cleaning and rebuilding of the dependent projects, to avoid these build errors.

## Running the RestExplorer application and tests from Eclipse

The RestExplorer is a sample app that demonstrates how to use the OAuth and REST API functions of the Salesforce SDK. It is also useful to investigate the various REST API actions from a Honeycomb tablet.

* To run the application, right-click on the RestExplorer project and choose Run As -> Android Application.
* To run the tests, right-click on the RestExplorerTest project and choose run as -> Android JUnit Test.

# Building and testing the SDK from the command line

Make sure to generate the local.properties file for each project by doing:
<pre>
cd $NATIVE_DIR/[project dir]
android update project
</pre>

To build the SDK jar, run the following ant target:
<pre>
cd $NATIVE_DIR/SalesforceSDK
ant jar
</pre>

To run the SDK tests, first connect a device or start an emulator, then run the following ant targets:
<pre>
cd $NATIVE_DIR/SalesforceSDKTest
ant debug 
ant installt 
ant ftest
</pre>

To get code coverage, run the following ant targets:
<pre>
cd $NATIVE_DIR/SalesforceSDKTest
ant emma debug 
ant emma installt 
ant emma ftest
firefox file:///$NATIVE_DIR/SalesforceSDKTest/coverage/coverage.html
</pre>
**Note:** Code coverage is only supported on the emulator and rooted devices.

## Running the RestExplorer app from the command line

To compile and deploy the RestExplorer app, first plug in a device or start an emulator, then run the follwoing ant targets:
<pre>
cd $NATIVE_DIR/RestExplorer
ant debug 
ant installd
</pre>

To run the RestExplorer tests, first plug in a device or start an emulator then run the following ant targets:
<pre>
cd $NATIVE_DIR/RestExplorerTest
ant emma debug 
ant emma installt 
ant emma ftest
</pre>


To get code coverage, do:
<pre>
cd $NATIVE_DIR/RestExplorerTest
ant emma debug 
ant installt 
ant ftest
firefox file:///$NATIVE_DIR/RestExplorerSDKTest/coverage/coverage.html
</pre>
**Note:** Code coverage is only supported on the emulator and rooted devices.

# Using the Salesforce SDK in your projects

In your Android application project, you can either:

* Add SalesforceSDK.jar to a 'libs' folder in your project (the latest version of SalesforceSDK.jar is checked into the dist/ folder of the repository, or you can build it yourself from the SDK source).
* In Project -> Properties, select Android, and click Add... in the Library section to add a reference to the SalesforceSDK library project.  **Note:** The SalesforceSDK project must be included in your workspace for this method.
* If you change the SDK you can regenerate the jar by running the `jar` target of the ant build script.


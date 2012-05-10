# Introduction

This folder contains the native libraries of the Salesforce SDK, as well as test and sample projects that can be used to exercise the SDK.

### SDK notes

* The SDK is supported and tested for Android 2.2 and above.
* The SalesforceSDK project is built with the Android 3.0 (Honeycomb) library.  The primary reason for this is that we want to be able to make a conditional check at runtime for file system encryption capabilities.  This check is guarded from being called on earlier Android platforms, the net result being that you can still use the salesforcesdk.jar in earlier Android application versions, down to the mininum-supported Android 2.2.

# Setting up your development environment

Please follow the instructions for setting up your development environment from the README file at the root of this repo, prior to working with the Salesforce SDK.

For the rest of this document, we assume that you have setup three shell variables:

1. ANDROID_SDK_DIR pointing to the Android SDK directory
2. SALESFORCE_SDK_DIR pointing to your clone of the Salesforce Mobile SDK repository e.g. `/home/jon/SalesforceMobileSDK-Android`.
3. NATIVE_DIR pointing to $SALESFORCE_SDK_DIR/native

**If you haven't, just make sure to replace $ANDROID_SDK_DIR, $SALESFORCE_SDK_DIR and $NATIVE_DIR in the snippets below with the actual paths.**

Inside the $NATIVE_DIR, you will find several projects:
1. **SalesforceSDK**: The Salesforce SDK which provides support for OAuth2 and REST API calls
2. **SalesforceSDKTest**: Tests for the SalesforceSDK project
3. **TemplateApp**: Template used when creating new native application using SalesforceSDK
4. **RestExplorer**: A app using SalesforceSDK to explore the REST API calls
5. **RestExplorerTest**: Tests for the RestExplorer project
6.  **SampleApps/CloudTunes**: A sample native application using SalesforceSDK

# Creating a new native application using SalesforceSDK

There is a build.xml in $SALESFORCE_SDK_DIR.
If you type:
<pre>
ant
</pre>

It will print out information about available targets.

To create a new native application simply do:
<pre>
      ant create_native -Dapp.name={appName} -Dtarget.dir={targetDir} -Dpackage.name={packageName} 
</pre>

Where:
* appName: the name for the new application 
* targetDir: the directory where the code should reside 
* packageName: the java package for the new application e.g. com.acme.mobileapp

To build the new application, do the following:
<pre>
cd ${target.dir}
$ANDROID_SDK_DIR/android update project -p .
ant clean debug
</pre>

To deploy the application, start an emulator or plugin your device and run:
<pre>
ant installd
</pre>


Before you ship, make sure to plug in your oauth client id and callback url in:
<pre>
${target.dir}/res/values/rest.xml
</pre>

# Setting up projects and building from Eclipse

1. Launch Eclipse and select $NATIVE_DIR as your workspace directory.
2. Go to Window -> Preferences, choose the Android section, and enter the the Android SDK location.
3. Go to File -> Import and select General -> Existing Projects into Workspace.
4. Select $NATIVE_DIR as your root directory and import the five projects above.
5. Create a gen folder for the SalesforceSDKTest, RestExplorer and RestExplorerTest projects (right-click the project and choose new -> folder).
6. Create a res folder for the SalesforceSDK project.

**Cleaning and rebuilding in Eclipse**: With the latest version of the Android SDK Tools (v14), there are issues around cleaning your workspace (Project -> Clean...) and rebuilding it.  Specifically, projects that are dependent on Android Library projects do not properly follow the build dependency ordering, so when every project is cleaned, dependent projects do not pick up the existence of the Library project.  The result is that all of the non-Library projects will have build errors after a clean.

If you would like to rebuild everything, we recommend cleaning/rebuilding the Library (SalesforceSDK) project *by itself* first, followed by the cleaning and rebuilding of the dependent projects, to avoid these build errors.

## Running the RestExplorer application and tests from Eclipse

The RestExplorer is a sample app that demonstrates how to use the OAuth and REST API functions of the Salesforce SDK. It is also useful to investigate the various REST API actions from a Honeycomb tablet.

* To run the application, right-click on the RestExplorer project and choose Run As -> Android Application.
* To run the tests, right-click on the RestExplorerTest project and choose run as -> Android JUnit Test.

# Building the SDK and the sample apps from the command line

Make sure to generate the local.properties file for each project by doing:
<pre>
cd $NATIVE_DIR/[project dir]
android update project --path .
</pre>

To build the SDK jar, run the following ant target:
<pre>
cd $NATIVE_DIR/SalesforceSDK
ant jar
</pre>

To compile and deploy the RestExplorer app, first plug in a device or start an emulator, then run the follwoing ant targets:
<pre>
cd $NATIVE_DIR/RestExplorer
ant debug 
ant installd
</pre>



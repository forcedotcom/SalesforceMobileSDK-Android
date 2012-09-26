# Introduction

This folder contains sample hybrid apps that combine the native Salesforce SDK functionality with HTML5 and javascript, to create web-based apps that can make use of extended device capabilities for enhanced app functionality.

# Setting up your development environment

Please follow the instructions for setting up your development environment from the README file at the root of this repo, prior to working with the Salesforce SDK.

For the rest of this document, we assume that you have setup three shell variables:

1. ANDROID_SDK_DIR pointing to the Android SDK directory
2. SALESFORCE_SDK_DIR pointing to your clone of the Salesforce Mobile SDK repository e.g. `/home/jon/SalesforceMobileSDK-Android`.
3. NATIVE_DIR pointing to $SALESFORCE_SDK_DIR/native
4. HYBRID_DIR pointing to $SALESFORCE_SDK_DIR/hybrid

**If you haven't, just make sure to replace $ANDROID_SDK_DIR, $SALESFORCE_SDK_DIR and HYBRID_DIR in the snippets below with the actual paths.**

Inside the HYBRID_DIR, you will find several projects:

1. **SmartStore**: Library project which provides the SmartStore functionality
2. **test/SmartStoreTest**: Test project for SmartStore
3. **test/SmartStorePluginTest**: Test project for the SmartStore phonegap plugin
4. **SampleApps/ContactExplorer**: The ContactExplorer sample app uses PhoneGap (aka "cordova") to retrieve local device contacts.  It also uses the forcetk.js toolkit to implement REST transactions with the Salesforce REST API.  The app uses the OAuth2 support in Salesforce SDK to obtain OAuth credentials, then propagates those credentials to forcetk by sending a javascript event
5. **SampleApps/VFConnector**: The VFConnector sample app demonstrates how to wrap a VisualForce page in a native container.  This example assumes that your org has a VisualForce page called "BasicVFTest".  The app first obtains OAuth login credentials using the Salesforce SDK OAuth2 support, then uses those credentials to set appropriate webview cookies for accessing VisualForce pages
6. **SampleApps/SFDCAccounts**: The SFDCAccounts sample app demonstrates how to take accounts and opportunities offline using SmartStore and forcetk
7. **SampleApps/SmartStoreExplorer**: The SmartStoreExplorer sample app let you explore SmartStore APIs
8. **SampleApps/test/ContactExplorerTest**: Test project for ContactExplorer
9. **SampleApps/test/VFConnectorTest**: Test project for VFConnector
10. **SampleApps/test/SFDCAccountsTest**: Test project for SFDCAccounts
11. **SampleApps/test/SmartStoreExplorerTest**: Test project for SmartStoreExplorer

# Running sample apps from Eclipse

1. Launch Eclipse and select $SALESFORCE_SDK_DIR as your workspace 
2. Go to Window -> Preferences, choose the Android section, and enter the the Android SDK location.
3. Go to File -> Import and select General -> Existing Projects into Workspace.
4. Select $SALESFORCE_SDK_DIR as your root directory and import the projects described above along with the native/SalesforceSDK library project.
5. Right click on any of the sample apps and choose Run As -> Android Application to run it.
6. Right click on any of the test project and choose Run As -> Android JUnit Test to run the tests.


# Creating a new hybrid application using SalesforceSDK

There is a build.xml in $SALESFORCE_SDK_DIR.
If you type:
<pre>
ant
</pre>

It will print out information about available targets.

To create a new hybrid application with local html/js simply do:
<pre>
ant create_hybrid_local -Dapp.name={appName} -Dtarget.dir={targetDir} -Dpackage.name={packageName} [-Duse.smartstore=true]
</pre>

Where:
* appName: the name for the new application 
* targetDir: the directory where the code should reside 
* packageName: the java package for the new application e.g. com.acme.mobileapp
* only pass -Duse.smartstore=true if you want SmartStore support

Put your html/js in ${target.dir}/assets/www/.

To create a new hybrid application with remote html/js simply do:
<pre>
ant create_hybrid_vf -Dapp.name={appName} -Dtarget.dir={targetDir} -Dpackage.name={packageName} -Dapex.page={apexPage} [-Duse.smartstore=true]
</pre>

Where:
* appName: the name for the new application 
* targetDir: the directory where the code should reside 
* packageName: the java package for the new application e.g. com.acme.mobileapp
* apexPage: the apex page for the application e.g. /apex/MyFirstApp
* only pass -Duse.smartstore=true if you want SmartStore support


If it's your first time build an application with the Salesforce SDK, do the following:
cd $NATIVE_DIR/SalesforceSDK
$ANDROID_SDK_DIR/android update project -p .
cd $HYBRID_DIR/SmartStore
$ANDROID_SDK_DIR/android update project -p .

To build the new application, do the following:
<pre>
cd $TARGET_DIR
$ANDROID_SDK_DIR/android update project -p .
ant clean debug
</pre>

To deploy the application, start an emulator or plugin your device and run:
<pre>
ant installd
</pre>

Before you ship, make sure to plug in your oauth client id and callback url in:
<pre>
$TARGET_DIR/assets/www/bootconfig.js
</pre>

# Running your new native application from Eclipse
1. Launch Eclipse
2. Go to File -> Import and select General -> Existing Projects into Workspace.
3. Import the $SALESFORCE_SDK_DIR/native/SalesforceSDK and the $SALESFORCE_SDK_DIR/hybrid/SmartStore (only if you passed -Duse.smartstore=true) library projects and your newly created project $TARGET_DIR into the workspace
4. Right click on the your project and choose Run As -> Android Application


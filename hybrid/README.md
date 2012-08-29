# Introduction

This folder contains sample hybrid apps that combine the native Salesforce SDK functionality with HTML5 and javascript, to create web-based apps that can make use of extended device capabilities for enhanced app functionality.

# Setting up your development environment

Please follow the instructions for setting up your development environment from the README file at the root of this repo, prior to working with the Salesforce SDK.

For the rest of this document, we assume that you have setup three shell variables:

1. ANDROID_SDK_DIR pointing to the Android SDK directory
2. SALESFORCE_SDK_DIR pointing to your clone of the Salesforce Mobile SDK repository e.g. `/home/jon/SalesforceMobileSDK-Android`.
3. HYBRID_DIR pointing to $SALESFORCE_SDK_DIR/hybrid

**If you haven't, just make sure to replace $ANDROID_SDK_DIR, $SALESFORCE_SDK_DIR and HYBRID_DIR in the snippets below with the actual paths.**

Inside the HYBRID_DIR, you will find several projects:

1. **SampleApps/ContactExplorer**: The ContactExplorer sample app uses PhoneGap (aka "cordova") to retrieve local device contacts.  It also uses the forcetk.js toolkit to implement REST transactions with the Salesforce REST API.  The app uses the OAuth2 support in Salesforce SDK to obtain OAuth credentials, then propagates those credentials to forcetk by sending a javascript event
2. **SampleApps/VFConnector**: The VFConnector sample app demonstrates how to wrap a VisualForce page in a native container.  This example assumes that your org has a VisualForce page called "BasicVFTest".  The app first obtains OAuth login credentials using the Salesforce SDK OAuth2 support, then uses those credentials to set appropriate webview cookies for accessing VisualForce pages
3. **SampleApps/SFDCAccounts**: The SFDCAccounts sample app demonstrates how to take accounts and opportunities offline using SmartStore and forcetk
4. **SampleApps/SmartStoreExplorer**: The SmartStoreExplorer sample app let you explore SmartStore APIs
5. **SmartStorePluginTest**: Test project for the SmartStore phonegap plugin
6. **SampleApps/ContactExplorerTest**: Test project for ContactExplorer
7. **SampleApps/VFConnectorTest**: Test project for VFConnector

# Creating a new hybrid application using SalesforceSDK

There is a build.xml in $SALESFORCE_SDK_DIR.
If you type:
<pre>
ant
</pre>

It will print out information about available targets.

To create a new hybrid application with local html/js simply do:
<pre>
ant create_hybrid_local -Dapp.name={appName} -Dtarget.dir={targetDir} -Dpackage.name={packageName}
</pre>

Where:
* appName: the name for the new application 
* targetDir: the directory where the code should reside 
* packageName: the java package for the new application e.g. com.acme.mobileapp

Put your html/js in ${target.dir}/assets/www/.

To create a new hybrid application with remote html/js simply do:
<pre>
      ant create_hybrid_vf -Dapp.name={appName} -Dtarget.dir={targetDir} -Dpackage.name={packageName} -Dapex.page={apexPage}
</pre>

Where:
* appName: the name for the new application 
* targetDir: the directory where the code should reside 
* packageName: the java package for the new application e.g. com.acme.mobileapp
* apexPage: the apex page for the application e.g. /apex/MyFirstApp

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

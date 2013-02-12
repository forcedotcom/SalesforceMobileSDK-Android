# Salesforce.com Mobile SDK for Android

Installation (do this first - really)
==
After cloning the SalesforceMobileSDK-Android project from github, run the install script from the command line:

`./install.sh`

This pulls submodule dependencies from github.

(Windows users: run `cscript install.vbs` from the command line instead.)



Introduction
==
__What's New in 1.5__

**Updated iOS and Android SDKs to Cordova 2.3  **
Make sure to change all the older cordova-xx.js references to cordova-2.3.0.js in your projects

**Security Enhancements**
Snapshots of the app screen sent to the background can be substituted to a white screen to prevent capture of sensitive information

**iOS SQLCipher Versioning**
Enhanced passcode security and configuration are now available options for the secure offline database

**String Localization**
Both the iOS and Android SDK now support localized strings in an external resource

__What's New in 1.4__

**Updated SDK to Cordova 2.2**
Make sure to update to the latest cordova.js and associated SDK JS plugin files when you upgrade. These can either be taken from the repo, or from a newly-generated hybrid template app.

**API Versioning**
Cordova JavaScript libraries are now versioned to benefit hybrid apps that deploy multiple versions at the same time. 
See “Versioning and Javascript Library Compatibility” in the “Introduction to Hybrid Development” chapter of the Mobile SDK Developer Guide. 

**Reactive Session Management for Hybrid Apps** 
Developers get more control over managing web sessions inside the hybrid container. Apps that previously relied on proactive session management will require some modification. Please upgrade with caution. (forcetk.js clients should not be affected.)

**Passcode Reset**
Added option for users to logout from the passcode screen

**Miscellaneous**
* Library Project Settings
* Login host settings externalized to an XML file
* SQLCipher libraries updated to support the Android x86 Architecture
* "UpgradeManager" can be used to handle SmartStore schema changes and other artifacts 

__Version 1.3__
* Updated libraries, notably an upgrade to Cordova 1.8.1 which requires some JavaScript library reference changes in hybrid apps
* SmartStore databases are now encrypted with a default key when a PIN is not provided
* Refactored SalesforceSDK as a library project, which requires minor Eclipse project updates for upgraded apps

__Version 1.2__

**Connected Apps Pilot**
* Apps implemented with the Mobile SDK will now respect Connected Apps policies.  Rules defined by administrators for PIN code protection and session timeout intervals will now be enforced by native and hybrid app implementations. (This feature requires the Connected Apps Pilot be turned on.)

**SmartStore Enhancements**
* Upsert records based on external id. SmartStore can now determine record uniqueness and perform the proper updates based on an id defined by the developer. This design is reflects the Salesforce REST API, making it easier to implement data synchronization.
* Mock SmartStore Implementation. Developers can build and test SmartStore apps directly in the desktop browser.
* Option to self-encrypt the SmartStore databases, which can be securely backed-up.

__Version 1.1__ 

**Secure Offline API** 
Store sensitive business data directly on a device with enterprise-class encryption for offline access. The Salesforce SDK provides a robust API for storing, retrieving, and querying  data without internet connectivity. 

**Flexible OAuth2 authentication flow** 
For hybrid apps, you now have the flexibility to configure whether or not your app needs to authenticate immediately when the app starts, or whether you'd prefer to defer authentication to a more convenient time in your app's lifecycle. 

__Version 1.0__
This is the first generally available release of Salesforce Mobile SDK for Android that can be used to develop native and hybrid applications. The public facing APIs have been finalized. Due to the rapid pace of innovation of mobile operating systems, some of the APIs and modules may change in their implementation details, but should not have a direct impact on the application logic. All updates will be clearly communicated in advanced using github.  

Check http://developer.force.com/mobilesdk for additional articles and tutorials


__Native Mobile Libraries__
The Salesforce Mobile SDK provides essential libraries for quickly building native mobile apps that seamlessly integrate with the Salesforce cloud architecture.  Out of the box, we provide an implementation of OAuth2, abstracting away the complexity of securely storing refresh tokens or fetching a new session ID when a session expires. The SDK also provides Java wrappers for the Salesforce REST API, making it easy to retrieve, store, and manipulate data.

__Salesforce Mobile Container__
HTML5 is quickly emerging as dominant technology for developing cross-platform mobile applications. While developers can create sophisticated apps with HTML5 and JavaScript, some limitations remain, specifically: session management, access to the camera and address book, and the inability to distribute apps inside public App Stores. The Salesforce Mobile Container makes possible to combine the ease of web app development with power of the Android platform by wrapping a web app inside a thin native container, producing a hybrid application.

__WARNING: OAuth2 token storage on devices without encryption__
The Salesforce Mobile SDK provides PIN-based OAuth token encryption for Android devices that don't provide full storage encryption functionality.  The SDK implementation is **NOT** designed to provide complete security.  It's simply offered as an option for temporarily protecting your app from eavesdroppers.  Please use caution in your production deployment with sensitive data.  **We strongly recommend deploying production apps on the latest generation of Android devices with build-in device encryption.**



# Setting up your development environment

The following steps will help you get started with your development environment, whether you choose to develop native apps or hybrid apps.  See the README files in the native/ and hybrid/ folders for additional notes pertaining to development in those environments.

1. Install the Android SDK (r21 or above): http://developer.android.com/sdk/index.html
2. Install ant 1.8.0 or later: http://ant.apache.org/manual/install.html (in order to build from the command line)
3. Install Eclipse: http://www.eclipse.org/
4. Install the Android Development Tools (ADT) plugin for Eclipse (r21 or above): http://developer.android.com/sdk/eclipse-adt.html
5. Get setup on github: http://help.github.com/

# Downloading the Salesforce SDK

To pull down the SDK from github, create a new directory and git clone the salesforce SDK repo.
<pre>
git clone git@github.com:forcedotcom/SalesforceMobileSDK-Android.git
</pre>

# Documentation

* [SalesforceSDK](http://forcedotcom.github.com/SalesforceMobileSDK-Android/index.html)

# Discussion

If you would like to make suggestions, have questions, or encounter any issues, we'd love to hear from you.  Post any feedback you have to the [Mobile Community Discussion Board](http://boards.developerforce.com/t5/Mobile/bd-p/mobile) on developerforce.com.

[![CircleCI](https://circleci.com/gh/forcedotcom/SalesforceMobileSDK-Android/tree/dev.svg?style=svg)](https://circleci.com/gh/forcedotcom/workflows/SalesforceMobileSDK-Android/tree/dev)
[![codecov](https://codecov.io/gh/forcedotcom/SalesforceMobileSDK-Android/branch/dev/graph/badge.svg)](https://codecov.io/gh/forcedotcom/SalesforceMobileSDK-Android/tree/dev)

# Salesforce.com Mobile SDK for Android

You have arrived at the source repository for the Salesforce Mobile SDK for Android. Welcome! Starting with our 2.0 release, there are now two ways you can choose to work with the Mobile SDK:

- If you'd like to work with the source code of the SDK itself, you've come to the right place! You can browse sample app source code and debug down through the layers to get a feel for how everything works under the covers. Read on for instructions on how to get started with the SDK in your development environment.
- If you're just eager to start developing your own application, the quickest way is to use our npm binary distribution package, called [forcedroid](https://npmjs.org/package/forcedroid), which is hosted on [npmjs.org](https://npmjs.org/). Getting started is as simple as installing the npm package and launching your template app. You'll find more details on the forcedroid package page.
- If you'd like to add the SDK to your existing app, the easiest way is to add our libraries as Gradle dependencies from our Maven repo [here](https://bintray.com/forcedotcom/salesforcemobilesdk).

Installation (do this first - really)
==

After cloning the SalesforceMobileSDK-Android project from github, run the install script from the command line:

`./install.sh`

This pulls submodule dependencies from github.

(Windows users: run `cscript install.vbs` from the command line instead.)

Introduction
==

### What's New in 6.0

**OS Version Support**
- Android Oreo (API 27) is fully supported in Mobile SDK 6.0.
- The minimum Android OS version we support has been bumped up from KitKat (API 19) to Lollipop (API 21).

**IDE Support**
- Android Studio 3.0 and Gradle 4.1 are fully supported in Mobile SDK 6.0.

**Library Upgrades**
- We've updated React Native to version 0.50.4.
- We've updated Cordova to version 7.0.0.

**Login Enhancements**
- In version 6.0, Mobile SDK enhances its authentication handling by adding identity provider services.
- Identity providers help known users avoid reentering their Salesforce credentials every time they log in to a Mobile SDK app.
- A Mobile SDK app can be used to provide authentication services to other Mobile SDK apps.
- We have created a template that can be used with `forcedroid` that demonstrates this functionality. This template can be found [here](https://github.com/forcedotcom/SalesforceMobileSDK-AuthTemplates).
- Mobile SDK 6.0 allows developers to use Chrome custom tabs for authentication instead of the system WebView.

**SmartStore Enhancements**
- Mobile SDK 6.0 introduces the ability to define your SmartStore schemas through configuration files rather than code.
- To define soups for the default global store, provide a file named `globalstore.json`.
- To define soups for the default user store, provide a file named `userstore.json`.

**SmartSync Enhancements**
- Beginning in Mobile SDK 6.0, you can define sync configuration files and assign names to sync configurations.
- You can use sync names to run, edit, or delete a saved sync operation.
- You can define “sync down” and “sync up” operations through configuration files rather than code.
- To define sync operations for the default global store, provide a file named `globalsyncs.json`.
- To define sync operations for the default user store, provide a file named `usersyncs.json`.

**Mobile SDK Developer Tools**
- The Developer Support dialog box is the launchpad for all available support screens and other useful actions.
- The dialog box presents only the options that are pertinent to the type of app you’re running.
- During debugging on a desktop, you can access the home screen through a keyboard shortcut or gesture (`⌘m` keyboard shortcut or `adb shell input keyevent 82`).
- By default, these tools are available only in debug builds. However, you can use an API call to enable or disable the Developer Support screen at other times.

**Other Technical Improvements**
- Improvements to sample apps.
- Various bug fixes.

Check http://developer.force.com/mobilesdk for additional articles and tutorials.

### Native Applications
The Salesforce Mobile SDK provides essential libraries for quickly building native mobile apps that seamlessly integrate with the Salesforce cloud architecture.  Out of the box, we provide an implementation of OAuth2, abstracting away the complexity of securely storing refresh tokens or fetching a new session ID when a session expires. The SDK also provides Java wrappers for the Salesforce REST API, making it easy to retrieve, store, and manipulate data.

### Hybrid Applications
HTML5 is quickly emerging as dominant technology for developing cross-platform mobile applications. While developers can create sophisticated apps with HTML5 and JavaScript, some limitations remain, specifically: session management, access to the camera and address book, and the inability to distribute apps inside public App Stores. The Salesforce Mobile Container makes possible to combine the ease of web app development with power of the Android platform by wrapping a web app inside a thin native container, producing a hybrid application.

Setting up your Development Environment
==

The following steps will help you get started with your development environment, whether you choose to develop native apps or hybrid apps. See the `README` files in the `native/` and `hybrid/` folders for additional notes pertaining to development in those environments.

1. Install the Android SDK and Android Studio: http://developer.android.com/sdk/index.html
2. Get setup on github: http://help.github.com/

Downloading the Salesforce SDK
==

To pull down the SDK from github, create a new directory and git clone the salesforce SDK repo.
<pre>
git clone https://github.com/forcedotcom/SalesforceMobileSDK-Android.git
</pre>

Documentation
==

* [SalesforceSDK](http://forcedotcom.github.com/SalesforceMobileSDK-Android/index.html)
* Salesforce Mobile SDK Development Guide -- [PDF](https://github.com/forcedotcom/SalesforceMobileSDK-Shared/blob/master/doc/mobile_sdk.pdf) [HTML](https://developer.salesforce.com/docs/atlas.en-us.mobile_sdk.meta/mobile_sdk/preface_intro.htm)
* [Mobile SDK Trail](https://trailhead.salesforce.com/trails/mobile_sdk_intro)

Discussion
==

If you would like to make suggestions, have questions, or encounter any issues, we'd love to hear from you.  Post any feedback you have on our [Google+ Community](https://plus.google.com/communities/114225252149514546445).

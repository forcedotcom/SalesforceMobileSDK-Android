| Android API | Build Status |
|-------------|--------------|
| android-21  |[![Build Status](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-21/dev/latest/buildstatus.svg)](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-21/dev/latest/index.html)|
| android-22  |[![Build Status](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-22/dev/latest/buildstatus.svg)](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-22/dev/latest/index.html)|
| android-23  |[![Build Status](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-23/dev/latest/buildstatus.svg)](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-23/dev/latest/index.html)|
| android-24  |[![Build Status](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-24/dev/latest/buildstatus.svg)](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-24/dev/latest/index.html)|
| android-25  |[![Build Status](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-25/dev/latest/buildstatus.svg)](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-25/dev/latest/index.html)|
| android-26  |[![Build Status](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-26/dev/latest/buildstatus.svg)](https://forcedotcom.github.io/SalesforceMobileSDK-TestResults/Android-test-results/Android-26/dev/latest/index.html)|

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

### What's New in 5.3

**OS Version Support**
- Android Oreo (API 26) is fully supported in Mobile SDK 5.3.

**Library Upgrades**
- We've updated SQLCipher to version 3.5.7.

**Kotlin Support**
- We now support Kotlin in native Android apps.
- `forcedroid` adds a new app type: `native_kotlin`.

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

# Salesforce.com Mobile SDK for Android

**Note:** For npm package details, see the [npm.md doc](https://github.com/forcedotcom/SalesforceMobileSDK-Android/blob/unstable20/npm.md).

Installation (do this first - really)
==
After cloning the SalesforceMobileSDK-Android project from github, run the install script from the command line:

`./install.sh`

This pulls submodule dependencies from github.

(Windows users: run `cscript install.vbs` from the command line instead.)

Introduction
==

### What's New in 2.0

**SmartSync library**
- Introducing the SmartSync library (external/shared/libs/smartsync.js), a set of JavaScript tools to allow you to work with higher level data objects from Salesforce.
- New AccountEditor hybrid sample app with User and Group Search, demonstrating the SmartSync functionality in action.

**SmartStore Enhancements**
- SmartStore now supports 'Smart SQL' queries, such as complex aggregate functions, JOINs, and any other SQL-type queries.
- NativeSqlAggregator is a new sample app to demonstrate usage of SmartStore within a native app to run Smart SQL queries, such as aggregate queries.
- SmartStore now supports three data types for index fields - 'string', 'integer', and 'floating'.

**OAuth Enhancements**
- Authentication can now be handled in an on-demand fashion.
- Refresh tokens are now explicitly revoked from the server upon logout.

**Other Technical Improvements**
- Revamped the architecture of the Mobile SDK to make it pluggable and easy to use.
	- Extending `ForceApp` or `ForceAppWithSmartStore` is no longer a requirement.
	- Added `SalesforceListActivity` and `SalesforceExpandableListActivity`, that provide Salesforce wrappers around the standard `ListActivity` and `ExpandableListActivity` respectively.
	- `rest.xml` has been removed and replaced with `bootconfig.xml`. Login options do not have to be supplied through code anymore, they can be specified in `bootconfig.xml`.
	- Bootstrap flow for hybrid apps now occurs on the native side, thereby enabling faster initial load times (`bootstrap.html` is no longer required or used).
- Changed the package name of the SmartStore library, so that it can coexist with other library projects.
- Added support for community users to login.
- Consolidated our Cordova JS plugins and utility code into one file (cordova.force.js).
- Updated `forcetk.js` and renamed to `forcetk.mobilesdk.js`, to pull in the latest functionality from ForceTK and enhance its ability to work with the Mobile SDK authentication process.
- Fixed session state rehydration for Visualforce apps, in the event of session timeouts during JavaScript Remoting calls in Visualforce.

Check http://developer.force.com/mobilesdk for additional articles and tutorials

### Native Applications
The Salesforce Mobile SDK provides essential libraries for quickly building native mobile apps that seamlessly integrate with the Salesforce cloud architecture.  Out of the box, we provide an implementation of OAuth2, abstracting away the complexity of securely storing refresh tokens or fetching a new session ID when a session expires. The SDK also provides Java wrappers for the Salesforce REST API, making it easy to retrieve, store, and manipulate data.

### Hybrid Applications
HTML5 is quickly emerging as dominant technology for developing cross-platform mobile applications. While developers can create sophisticated apps with HTML5 and JavaScript, some limitations remain, specifically: session management, access to the camera and address book, and the inability to distribute apps inside public App Stores. The Salesforce Mobile Container makes possible to combine the ease of web app development with power of the Android platform by wrapping a web app inside a thin native container, producing a hybrid application.

### WARNING: OAuth2 token storage on devices without encryption
The Salesforce Mobile SDK provides PIN-based OAuth token encryption for Android devices that don't provide full storage encryption functionality.  The SDK implementation is **NOT** designed to provide complete security. It's simply offered as an option for temporarily protecting your app from eavesdroppers. Please use caution in your production deployment with sensitive data. **We strongly recommend deploying production apps on the latest generation of Android devices with build-in device encryption.**

Setting up your Development Environment
==

The following steps will help you get started with your development environment, whether you choose to develop native apps or hybrid apps. See the `README` files in the `native/` and `hybrid/` folders for additional notes pertaining to development in those environments.

1. Install the Android SDK (r21 or above): http://developer.android.com/sdk/index.html
2. Install ant 1.8.0 or later: http://ant.apache.org/manual/install.html (in order to build from the command line)
3. Install Eclipse: http://www.eclipse.org/
4. Install the Android Development Tools (ADT) plugin for Eclipse (r21 or above): http://developer.android.com/sdk/eclipse-adt.html
5. Get setup on github: http://help.github.com/

Downloading the Salesforce SDK
==

To pull down the SDK from github, create a new directory and git clone the salesforce SDK repo.
<pre>
git clone git@github.com:forcedotcom/SalesforceMobileSDK-Android.git
</pre>

Documentation
==

* [SalesforceSDK](http://forcedotcom.github.com/SalesforceMobileSDK-Android/index.html)

Discussion
==

If you would like to make suggestions, have questions, or encounter any issues, we'd love to hear from you.  Post any feedback you have to the [Mobile Community Discussion Board](http://boards.developerforce.com/t5/Mobile/bd-p/mobile) on developerforce.com.

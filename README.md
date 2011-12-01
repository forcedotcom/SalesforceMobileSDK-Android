# Salesforce.com Mobile SDK for Android

Installation (do this first - really)
==
After cloning the SalesforceMobileSDK-Android project from github, run the install script from the command line:

`./install.sh`
This pulls submodule dependencies from github.

(Windows users: Run "install.bat" instead.)


Introduction
==
__Version 1.0__
This is the first generally available release of Salesforce Mobile SDK for iOS that can be used to develop native and hybrid applications. The public facing APIs have been finalized. Due to the rapid pace of innovation of mobile operating systems, some of the APIs and modules may change in their implementation details, but should not have a direct impact on the application logic. All updates will be clearly communicated in advanced using github.  

Check http://developer.force.com/mobilesdk for additional articles and tutorials




__Native Mobile Libraries__
The Salesforce Mobile SDK provides essential libraries for quickly building native mobile apps that seamlessly integrate with the Salesforce cloud architecture.  Out of the box, we provide an implementation of OAuth2, abstracting away the complexity of securely storing refresh tokens or fetching a new session ID when a session expires. The SDK also provides Java wrappers for the Salesforce REST API, making it easy to retrieve, store, and manipulate data.

__Salesforce Mobile Container__
HTML5 is quickly emerging as dominant technology for developing cross-platform mobile applications. While developers can create sophisticated apps with HTML5 and JavaScript, some limitations remain, specifically: session management, access to the camera and address book, and the inability to distribute apps inside public App Stores. The Salesforce Mobile Container makes possible to combine the ease of web app development with power of the iOS platform by wrapping a web app inside a thin native container, producing a hybrid application.



# Setting up your development environment

The following steps will help you get started with your development environment, whether you choose to develop native apps or hybrid apps.  See the README files in the native/ and hybrid/ folders for additional notes pertaining to development in those environments.

1. Install the Android SDK (r14 or above): http://developer.android.com/sdk/index.html
2. Install ant 1.8.0 or later: http://ant.apache.org/manual/install.html (in order to build from the command line)
3. Install Eclipse: http://www.eclipse.org/
4. Install the Android Development Tools (ADT) plugin for Eclipse (r14 or above): http://developer.android.com/sdk/eclipse-adt.html
5. Get setup on github: http://help.github.com/

**A word about the Android 4.0 (API 14) SDK:** Starting with the SDK that's deployed with API 14, Google has introduced some fundamental changes in Eclipse project configurations, build configuratons, library project configurations, and other areas.  Because of this, **you will not be able to build any of the projects in this repository without upgrading to API 14**.  Note that updating to r14 will not impact your ability to build for earlier Android platforms.  It's just the toolset changes of the SDK that require the upgrade.

# Discussion

If you would like to make suggestions, have questions, or encounter any issues, we'd love to hear from you.  Post any feedback you have to the [Mobile Community Discussion Board](http://boards.developerforce.com/t5/Mobile/bd-p/mobile) on developerforce.com.

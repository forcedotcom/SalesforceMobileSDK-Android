# Salesforce Mobile SDK for Android Package

The **forcedroid** npm package allows users to create Android mobile applications to interface with the [Salesforce Platform](http://www.salesforce.com/platform/overview/), leveraging the [Salesforce Mobile SDK for Android](https://github.com/forcedotcom/SalesforceMobileSDK-Android).

## Getting Started

If you're new to mobile development, or the force.com platform, you may want to start at the [Mobile SDK landing page](http://wiki.developerforce.com/page/Mobile_SDK).  This page offers a variety of resources to help you determine the best technology path for creating your app, as well as many guides and blog posts detailing how to work with the Mobile SDK.

But assuming you're all read up, here's how to get started with the **forcedroid** package to create the starting point for your mobile application.

## Install the forcedroid Package

Because forcedroid is a command-line utility, we recommend installing it globally, so that it's easily accessible on your path:

        sudo npm install forcedroid -g

You're of course welcome to install it locally as well:

        npm install forcedroid

In this case, you can access the forcedroid app at `[Install Directory]/node_modules/.bin/forcedroid`.

## Using forcedroid

For the rest of this document, we'll assume that `forcedroid` is on your path.

Typing `forcedroid` with no arguments gives you a breakdown of the usage:

        $ forcedroid
        Usage:
        forcedroid create
            --apptype=<Application Type> (native, hybrid_remote, hybrid_local)
            --appname=<Application Name>
            --targetdir=<Target App Folder>
            --packagename=<App Package Identifier> (com.my_company.my_app)
            --startpage=<Path to the remote start page> (/apex/MyPage — Only required/used for 'hybrid_remote')
            [--usesmartstore=<Whether or not to use SmartStore> ('true' or 'false'. false by default)]

        OR

        forcedroid version

**Note:** You can specify any or all of the arguments as command line options as specified in the usage.  If you run `forcedroid create` with missing arguments, it prompts you for each missing option interactively.

Once the creation script completes, you'll have a fully functioning basic application of the type you specified.  The new application will be configured as an Eclipse project in your target directory, alongside the Mobile SDK libraries it consumes.

### forcedroid create options

**App Type:** The type of application you wish to develop:

- **native** — A fully native Android application
- **hybrid\_remote** — A hybrid application, based on the [Cordova](http://cordova.apache.org/) framework, that runs in a native container.  The app contents live in the cloud as a [Visualforce](http://wiki.developerforce.com/page/An_Introduction_to_Visualforce) application
- **hybrid\_local** — A hybrid application, based on the Cordova framework, that runs in a native container.  The app contents are developed locally in the Eclipse project, and are deployed to the device itself when the app is built

**App Name:** The name of your application

**Target App Folder:** The folder where you want your app to be created.  Your app will be contained in a folder underneath this folder, alongside a `forcedroid` folder containing the Mobile SDK libraries that your app is linked to.

**App Package Identifier:** The Java package identifier for your app (e.g. `com.acme.mobile_apps`).  **Note:** Your package name must be formatted as a [valid Java package name](http://docs.oracle.com/javase/tutorial/java/package/namingpkgs.html), or you will receive an error.

**Start Page:** \( *Required for hybrid\_remote apps only* \) The starting page of your application on salesforce.com.  This is the entry point of your remote application, though it's only the path, not the server portion of the URL.  For instance, `/apex/MyVisualforceStartPage`.

**Use SmartStore:** \( *optional* \) Whether to use SmartStore in your app.  The value is `false` by default.  Set this value to `true` if you intend to use SmartStore in your app.

## More information

- After your app has been created, you will see some on-screen instructions for next steps, such as building and running your app, importing the project into an Eclipse workspace, and changing the default Connected App (sample) configuration values to match your own Connected App.  Note that if you intend to work with your app in Eclipse, you are not required to go through the steps to build and run your app from the command line, and vice versa.

- You can find the `forceios` npm package [here](https://npmjs.org/package/forceios), to develop Mobile SDK apps for iOS.

- The Salesforce Mobile SDK for iOS source repository lives [here](https://github.com/forcedotcom/SalesforceMobileSDK-iOS).

- See [our developerforce site](http://wiki.developerforce.com/page/Mobile_SDK) for more information about how you can leverage the Salesforce Mobile SDK with the force.com platform.

- If you would like to make suggestions, have questions, or encounter any issues, we'd love to hear from you.  Post any feedback you have on our [Google+ Community](https://plus.google.com/communities/114225252149514546445).

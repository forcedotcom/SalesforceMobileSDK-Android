# Setting up your development environment

1. Install the Android SDK (r14 or above): http://developer.android.com/sdk/index.html
2. Install ant 1.8.0 or later: http://ant.apache.org/manual/install.html (in order to build from the command line)
3. Install Eclipse: http://www.eclipse.org/
4. Install the Android Development Tools (ADT) plugin for Eclipse (r14 or above): http://developer.android.com/sdk/eclipse-adt.html
5. Get setup on github: http://help.github.com/

# Downloading the Salesforce SDK
To pull down the SDK from github, create a new directory and git clone the salesforce SDK repo.
<pre>
git clone git@github.com:forcedotcom/SalesforceMobileSDK-Android-dev.git
</pre>

For the rest of this document, we assume that you have setup two shell variables:
1. ANDROID_SDK_DIR pointing to the Android SDK directory
2. NATIVE_DIR pointing to the native directory of your clone of the SalesforceMobileSDK-Android-dev
<b>If you haven't, just make sure to replace $ANDROID_SDK_DIR and $NATIVE_DIR in the snippets below with the actual paths.</b>

There are four projects:
1. <b>SalesforceSDK</b>: the Salesforce SDK which provides support for OAuth2 and REST API calls.
2. <b>SalesforceSDKTest</b>: tests for SalesforceSDK
3. <b>RestExplorer</b>: a sample app using SalesforceSDK to try out the REST API calls.
4. <b>RestExplorerTest</b>: tests for RestExplorer

# Building from eclipse
1. Launch eclipse and select $NATIVE_DIR as your workspace directory.
2. Go to Window -> Preferences and pick Android. Enter the the SDK location.
3. Go to File -> Import and select General -> Existing Projects into Workspace.
4. Select $NATIVE_DIR as your root directory and import the four projects.
5. Create a gen folder for the SalesforceSDKTest, RestExplorer and RestExplorerTest projects (right click the project and choose new -> folder).
6. Create a res folder for the SalesforceSDK project.
You are done.

**A word about building in Eclipse**: With the latest version of the Android SDK Tools (v14), there are issues around cleaning your workspace
(Project -> Clean...) and rebuilding it.  Specifically, for projects that are dependent on Android Library projects, they do not properly follow
the build dependency ordering, so when every project is cleaned, these do not pick up the existence of the Library project.  The result is that
if you clean your workspace, all of the non-Library projects will have build errors.

If you would like to rebuild everything, we recommend cleaning/rebuilding the Library (SalesforceSDK) project *by itself* first, followed by
the cleaning and rebuilding of the dependent projects, to avoid these build errors.

## Using the SalesforceSDK
In you Android application project, you can either:
- add SalesforceSDK.jar to the libs directory
- in Project -> Preferences -> Android, click Add... to add a reference to the SalesforceSDK library project.

## Running the RestExplorer
Right-click on the RestExplorer project and choose run as -> Android Application.
To run the tests, right-click on the RestExplorerTest project and choose run as -> Android JUnit Test.

# Building from the command line
Make sure to generate local.properties for each project by doing:
<pre>
cd project_dir
android update project
</pre>

## Using the SalesforceSDK
$ANDROID_SDK_DIR/dist directory contains the SalesforceSDK.jar.
In you Android application project, you can either:
- copy or link dist/SalesforceSDK.jar in the libs directory
- add android.library.reference.1=../SalesforceSDK to project.properties

If you change the SDK you can regenerate the jar by doing
<pre>
and jar
</pre>

To run the SDK tests, first plug in a device or start an emulator then do:
<pre>
cd $FORCE_DOT_COM_DIR/SalesforceSDKTest
ant debug installt test
</pre>

To get code coverage, do:
<pre>
cd $FORCE_DOT_COM_DIR/SalesforceSDKTest
ant emma debug installt test
firefox file:///$NATIVE_DIR/SalesforceSDKTest/coverage/coverage.html
</pre>
Note: Code coverage is only supported on the emulator and rooted devices.


## Running the RestExplorer
The RestExplorer is a sample app that demonstrates how to use SalesforceSDK OAuth and REST API functions. It is also useful to play with the various REST API actions from a Honeycomb tablet.

To compile and deploy the RestExplorer, first plug in a device or start an emulator then do:
<pre>
ant debug installd
</pre>

To run the tests, first plug in a device or start an emulator then do:
<pre>
cd $FORCE_DOT_COM_DIR/RestExplorerTest
ant debug installt test
</pre>


To get code coverage, do:
<pre>
cd $FORCE_DOT_COM_DIR/RestExplorerTest
ant emma debug installt test
firefox file:///$NATIVE_DIR/RestExplorerSDKTest/coverage/coverage.html
</pre>
Note: Code coverage is only supported on the emulator and rooted devices.


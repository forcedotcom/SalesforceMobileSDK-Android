## Upgrade steps from v. 2.2.x to v. 2.3.0 for native apps
- Import the `Cordova` library project in Eclipse by following the steps below:
	- Click File -> Import -> Existing Projects into Workspace.
	- Navigate to the root of the Mobile SDK workspace, and select the `Cordova` project.
- Replace the existing `SalesforceSDK` project in Eclipse with the Mobile SDK 2.3.0 `SalesforceSDK` project.
- If your app uses `SmartStore`, replace the existing `SmartStore` project in Eclipse with the new `SmartStore` project.
- Right click your project and select `Properties`.
- Click the `Android` tab and navigate to the library project section at the bottom.
- Replace the existing `SalesforceSDK` entry with the new `SalesforceSDK` project in your workspace.
- If your app uses `SmartStore`, repeat the previous step for the `SmartStore` project.

## Upgrade steps from v. 2.2.x to v. 2.3.0 for hybrid apps

The 2.3 version of the Mobile SDK uses Cordova 3.5, which represents both a significant upgrade from the previous Cordova 2.3, and a signficant change in how you bootstrap your application.  Please follow the instructions below to migrate your hybrid app to the Cordova 3.5 paradigm.

### Prerequisites
- You will need to install the `cordova` command line tool from [https://www.npmjs.org/package/cordova](https://www.npmjs.org/package/cordova).  The `forcedroid` package depends on the `cordova` tool to create hybrid apps.  Make sure you have version 3.5 or greater installed.
- You will also need to install the `forcedroid` npm package from [https://www.npmjs.org/package/forceidroid](https://www.npmjs.org/package/forcedroid), to create your new hybrid app.

### Create your new hybrid app
Follow the instructions in the [forcedroid package](https://www.npmjs.org/package/forcedroid) to create your new hybrid app.  You'll choose either a `hybrid_remote` or `hybrid_local` app, depending on the type of hybrid app you've developed.

### Migrate your old app artifacts to the new project
1. Once you've created your new app, `cd` into the top level folder of the new app you've created.
2. Run `cordova plugin add [Cordova plugin used in your app]` for every plugin that your app uses.  **Note:** You do not need to do this for the Mobile SDK plugins, as the `forcedroid` app creation process will automatically add those plugins to your app.
3. Remove everything from the `www/` folder, and replace its contents with all of your HTML, CSS, (non-Cordova) JS files, and `bootconfig.json` from your old app.  Basically, copy everything from your old `www/` folder except for the Cordova and Cordova plugin JS files.  Cordova is responsible for pushing all of the Cordova-specific JS files, plugin files, etc., into your `www/` folder when the app is deployed (see below).
4. For any of your HTML pages that reference the Cordova JS file, make sure to change the declaration to `<script src="cordova.js"></script>`, i.e. the generic version of the Cordova JS file.  The Cordova framework now handles the versioning of this file.
5. Remove any `<script src="[Some Plugin JS File]"></script>` references in your HTML files.  Cordova is responsible for handling the inclusion of the proper plugin JS files in your app.
6. Make sure that any calls in your code to `cordova.require()` do not happen before Cordova's `deviceready` event has fired.
7. The naming convention for our Cordova plugins has changed, to reflect the new conventions used in Cordova 3.5.  Specifically, dot separation has replaced '/' separation for namespacing.  For example, if your app previously called `cordova.require('salesforce/util/logger')`, you would now call that via `cordova.require('com.salesforce.util.logger')`.  Generally:
    - Replace `salesforce` with `com.salesforce`.
    - Replace '/' with '.'
8. Run `cordova prepare`, to stage the changes into your app project(s).  Generally speaking, you'll run `cordova prepare` after any changes made to your app code, and Cordova will stage all of the appropriate changes into your app project(s).

Please see the [Mobile SDK Development Guide](https://github.com/forcedotcom/SalesforceMobileSDK-Shared/blob/master/doc/mobile_sdk.pdf?raw=true) for more information about developing hybrid apps with the 2.3 SDK and Cordova 3.5.

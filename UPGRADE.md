## Upgrade steps from v. 2.3.x to v. 3.0.0 for native apps

- Replace the existing `Cordova` project in Eclipse with the Mobile SDK 3.0.0 `Cordova` project.
- Replace the existing `SalesforceSDK` project in Eclipse with the new `SalesforceSDK` project.
- If your app uses `SmartStore`, replace the existing `SmartStore` project in Eclipse with the new `SmartStore` project.
- Right click your project and select `Properties`.
- Click the `Android` tab and navigate to the library project section at the bottom.
- Replace the existing `SalesforceSDK` entry with the new `SalesforceSDK` project in your workspace.
- If your app uses `SmartStore`, repeat the previous step for the `SmartStore` project.
- Ensure that the minSdkVersion used by your app is 17 or higher.

## Upgrade steps from v. 2.3.x to v. 3.0.0 for hybrid apps

- Existing hybrid apps should continue to work with Mobile SDK 3.0.0.

Please see the [Mobile SDK Development Guide](https://github.com/forcedotcom/SalesforceMobileSDK-Shared/blob/master/doc/mobile_sdk.pdf?raw=true) for more information about developing hybrid apps with the 3.0 SDK and Cordova 3.6.4.

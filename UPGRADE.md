## Upgrade steps from v. 3.2.x to v. 3.3 for native apps

- Replace the existing `Cordova` project in Eclipse with the Mobile SDK 3.3 `Cordova` project.
- Replace the existing `SalesforceSDK` project in Eclipse with the new `SalesforceSDK` project.
- If your app uses `SmartStore`, replace the existing `SmartStore` project in Eclipse with the new `SmartStore` project.
- If your app uses `SmartSync`, replace the existing `SmartSync` project in Eclipse with the new `SmartSync` project.
- Right click your project and select `Properties`.
- Click the `Android` tab and navigate to the library project section at the bottom.
- Replace the existing `SalesforceSDK` entry with the new `SalesforceSDK` project in your workspace.
- If your app uses `SmartStore`, repeat the previous step for the `SmartStore` project.
- If your app uses `SmartSync`, repeat the previous step for the `SmartSync` project.

## Upgrade steps from v. 3.2.x to v. 3.3 for hybrid apps

- Existing hybrid apps should continue to work with Mobile SDK 3.3.

See the [Mobile SDK Development Guide](https://github.com/forcedotcom/SalesforceMobileSDK-Shared/blob/master/doc/mobile_sdk.pdf?raw=true) for more information about developing hybrid apps with the 3.3 SDK and Cordova 3.6.4.

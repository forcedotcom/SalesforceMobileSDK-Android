## Upgrade steps from v2.1.x to v2.2.0 for native and hybrid apps
- Replace the existing `SalesforceSDK` project in Eclipse with the Mobile SDK 2.2.0 `SalesforceSDK` project.
- If your app uses `SmartStore`, replace the existing `SmartStore` project in Eclipse with the new `SmartStore` project.
- Right click your project and select `Properties`.
- Click the `Android` tab and navigate to the library project section at the bottom and replace the existing `SalesforceSDK` entry with the new `SalesforceSDK` project in your workspace.
- If your app uses `SmartStore`, repeat the previous step for the `SmartStore` project.

## Upgrade steps from v2.0.x to v2.1.0 for native and hybrid apps
- Replace the existing `SalesforceSDK` project in Eclipse with the Mobile SDK 2.1.0 `SalesforceSDK` project.
- If your app uses `SmartStore`, replace the existing `SmartStore` project in Eclipse with the new `SmartStore` project.
- Right click your project and select `Properties`.
- Click the `Android` tab and navigate to the library project section at the bottom and replace the existing `SalesforceSDK` entry with the new `SalesforceSDK` project in your workspace.
- If your app uses `SmartStore`, repeat the previous step for the `SmartStore` project.
- The Salesforce Mobile SDK Activity and Service declarations have now been moved from the app's `AndroidManifest.xml` file to the `AndroidManifest.xml` file of the `SalesforceSDK` project. These declarations are automatically merged into the app's manifest file if you enable the `manifestmerger` attribute. In order to use this, add `manifestmerger.enabled=true` to your app's `project.properties` file.
	- NOTE:
		- You are required to perform this step to use features that were added in Mobile SDK 2.1.0, such as push notifications.

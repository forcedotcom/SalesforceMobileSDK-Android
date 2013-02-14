## Hybrid 1.4.7 to 1.5 upgrade
- Update your Mobile SDK plugins and artifacts:
    - `SFHybridApp.js`
    - `SFSmartStorePlugin.js`
    - `SFTestRunnerPlugin.js`
    - `SalesforceOAuthPlugin.js`
    - `bootstrap.html` (The only change is the cordova.js reference)
- Cordova: Update your cordova.js to `cordova-2.3.0.js`
- Cordova: `plugins.xml` has changed to `config.xml`

## Native 1.4.7 to 1.5 upgrade
- Swap the existing `SalesforceSDK` project in Eclipse with the new `SalesforceSDK` project
- Right click on your project and go to `Properties`
- Click on the `Android` tab and replace the existing `SalesforceSDK` entry at the bottom (in the library project section) with the new `SalesforceSDK` project in your workspace
- If all your activities extend `NativeMainActivity` then you do not need to perform any additional steps
- If you have any activities that do not extend `NativeMainActivity` perform the following additional steps:
	- Add the following line of code to your member variable declarations:
		- `private TokenRevocationReceiver tokenRevocationReceiver;`
	- Add the following line of code within the `onCreate` method:
		- `tokenRevocationReceiver = new TokenRevocationReceiver(this);`
	- Add the following line of code within the `onResume` method:
		- `registerReceiver(tokenRevocationReceiver, new IntentFilter(ClientManager.ACCESS_TOKEN_REVOKE_INTENT));`
	- Add the following line of code within the `onPause` method:
		- `unregisterReceiver(tokenRevocationReceiver);`
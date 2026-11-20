# Focused Cordova Android runtime verification

`CordovaRuntimeTest` runs a local, unauthenticated page inside the real
`SalesforceDroidGapActivity`, Salesforce WebView engine and Cordova bridge. It
checks SDK-info calls, the first hardware Back event, the AndroidX Back dispatcher,
removing a JavaScript Back override, root-page fallback, activity recreation,
pause/resume, and keyboard behavior in normal, edge-to-edge and fullscreen modes.

Run only the focused class on an explicitly selected, owned emulator:

```bash
ANDROID_SERIAL=<owned-emulator-serial> ./gradlew \
  :libs:SalesforceHybrid:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.salesforce.androidsdk.phonegap.ui.CordovaRuntimeTest
```

Use the repository's normal JDK/Android SDK setup and protected test
configuration. The existing test application initializes test credentials;
this fixture overrides the authentication callback to load controlled local
content and does not log in or validate an OAuth browser return flow.

The tests grant the test application's notification permission so the SDK's
developer-support prompt cannot intercept Back or keyboard input. Keyboard tests
temporarily allow the software keyboard with a hardware keyboard connected.
The fixture also suppresses the fullscreen tutorial during each scenario. Both
system settings are restored in `finally` blocks. The activity is not exported,
and the reporting plugin exists only in the test application.

Check the resulting test report on API36 and a supported lower API. A native
dispatcher test exercises the callback used by gesture navigation; also smoke-test
the device's actual edge-swipe Back gesture, navigation mode changes and rotation.
Keep authenticated startup, remote/server content, optional plugins and media
permission dialogs as separate checks. Passing this class does not establish
those behaviors or replace the complete CI test suite.

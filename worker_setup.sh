cd libs/SalesforceSDK

$ANDROID_SDK/tools/android update project -p .

cd ../../external/cordova/framework

$ANDROID_SDK/tools/android update project -p .

cd ../../../native/SampleApps/FileExplorer

$ANDROID_SDK/tools/android update project -p .

ant debug

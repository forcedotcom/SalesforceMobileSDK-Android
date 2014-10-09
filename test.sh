#!/bin/bash

$TDDIUM_REPO_ROOT/start-emulator.sh

cd libs/test/SmartStoreTest/
mv project.properties project.properties.original
sed s/android-.*/${DEVICE_OS_VERSION}/g project.properties.original >
project.properties

$ANDROID_SDK/tools/android update project --path
../../../external/cordova/framework

cd ../../SalesforceSDK/
$ANDROID_SDK/tools/android update project -p .
ant clean debug

cd ../SmartStore/
$ANDROID_SDK/tools/android update project -p .
ant clean debug

cd ../test/SmartStoreTest/
$ANDROID_SDK/tools/android update test-project -p . -m ../../SmartStore/
ant clean debug

$TDDIUM_REPO_ROOT/wait-for-emulator.sh

ant installt
ant test
ant uninstall

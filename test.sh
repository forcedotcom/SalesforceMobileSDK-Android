#!/bin/bash

$TDDIUM_REPO_ROOT/start-emulator.sh

cd libs/test/SmartStoreTest/
mv project.properties project.properties.original
sed s/android-.*/${DEVICE_OS_VERSION}/g project.properties.original >
project.properties

. android-settings.sh

android update project --path
../../../external/cordova/framework

cd ../../SalesforceSDK/
android update project -p .
ant clean debug

cd ../SmartStore/
android update project -p .
ant clean debug

cd ../test/SmartStoreTest/
android update test-project -p . -m ../../SmartStore/
ant clean debug

$TDDIUM_REPO_ROOT/wait-for-emulator.sh

ant installt
ant test
ant uninstall

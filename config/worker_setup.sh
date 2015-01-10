#!/bin/bash

. config/android-settings.sh


project_libs="libs/SalesforceSDK external/cordova/framework native/SampleApps/FileExplorer native/SampleApps/RestExplorer libs/SmartStore"

for d in $project_libs ; do
	echo "Updating android project $d"
	(cd $d && android update project -p .)
done

tools/sdk.sh -v -b Cordova SmartStore

# FileExplorer
./gradlew :native:SampleApps:FileExplorer:assembleDebug

# RestExplorer
./gradlew :native:SampleApps:RestExplorer:assembleDebug

# SalesforceSDK
./gradlew :libs:SalesforceSDK:assembleDebug
#!/bin/bash

. android-settings.sh


project_libs="libs/SalesforceSDK external/cordova/framework native/SampleApps/FileExplorer native/SampleApps/RestExplorer"

for d in $project_libs ; do
	echo "Updating android project $d"
	(cd $d && android update project -p .)
done

tools/sdk.sh -v -b SalesforceSDK Cordova RestExplorer FileExplorer 


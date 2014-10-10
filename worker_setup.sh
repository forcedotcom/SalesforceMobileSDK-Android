#!/bin/bash

. android-settings.sh

for d in libs/SalesforceSDK external/cordova/framework native/SampleApps/FileExplorer; do
	echo "Updating android project $d"
	(cd $d && android update project -p .)
done

(cd native/SampleApps/FileExplorer && ant debug)

for d in libs/SalesforceSDK external/cordova/framework native/SampleApps/FileExplorer; do
	echo "Updating android project $d"
	(cd $d && $ANDROID_SDK/tools/android update project -t $DEVICE_OS_VERSION -p .)
done

(cd native/SampleApps/FileExplorer && ant debug)

#!/bin/bash
if [ ! -d "external" ]
then
    echo "You must run this tool from the root directory of your repo clone"
else
    javadoc -d doc -author -version -verbose -use -doctitle "SalesforceSDK 11.1 API" -sourcepath "libs/SalesforceAnalytics/src:libs/SalesforceSDK/src:libs/SmartStore/src:libs/MobileSync/src:libs/SalesforceHybrid/src:libs/SalesforceReact/src" -subpackages com
fi

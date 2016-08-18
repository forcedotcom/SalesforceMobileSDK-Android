#!/bin/bash
if [ ! -d "external" ]
then
    echo "You must run this tool from the root directory of your repo clone"
else
    javadoc -d doc -author -version -verbose -use -doctitle "SalesforceSDK 4.3 API" -sourcepath "libs/SalesforceSDK/src:libs/SmartStore/src:libs/SmartSync/src:libs/SalesforceHybrid/src:libs/SalesforceReact/src" -subpackages com
fi

#!/bin/bash
if [ ! -d "external" ]
then
    echo "You must run this tool from the root directory of your repo clone"
else
    javadoc -d doc -author -version -verbose -use -doctitle "SalesforceSDK 4.0 API" -sourcepath "libs/SalesforceSDK/src:libs/SmartStore/src:libs/SmartSync/src" -subpackages com
fi

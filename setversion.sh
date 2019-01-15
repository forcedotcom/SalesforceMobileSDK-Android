#!/bin/bash

#set -x

OPT_VERSION=""
OPT_CODE=""
OPT_IS_DEV="no"
RED='\033[0;31m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

usage ()
{
    echo "Use this script to set Mobile SDK version number in source files"
    echo "Usage: $0 -v <versionName> -c <versionCode> [-d <isDev>]"
    echo "  where: versionName is the version name e.g. 7.1.0"
    echo "         versionCode is the version code e.g. 64"
    echo "         isDev is yes or no (default) to indicate whether it is a dev build"
}

parse_opts ()
{
    while getopts v:c:d: command_line_opt
    do
        case ${command_line_opt} in
            v)  OPT_VERSION=${OPTARG};;
            c)  OPT_CODE=${OPTARG};;
            d)  OPT_IS_DEV=${OPTARG};;
        esac
    done

    if [ "${OPT_VERSION}" == "" ]
    then
        echo -e "${RED}You must specify a value for the version name.${NC}"
        usage
        exit 1
    fi

    if [ "${OPT_CODE}" == "" ]
    then
        echo -e "${RED}You must specify a value for the version code.${NC}"
        usage
        exit 1
    fi
}

# Helper functions
update_package_json ()
{
    local file=$1
    local versionName=$2
    sed -i "s/\"version\":.*\"[^\"]*\"/\"version\": \"${versionName}\"/g" ${file}
}

update_manifest ()
{
    local file=$1
    local versionName=$2
    local versionCode=$3
    sed -i "s/android\:versionCode=\"[^\"]*\"/android:versionCode=\"${versionCode}\"/g" ${file}
    sed -i "s/android\:versionName=\"[^\"]*\"/android:versionName=\"${versionName}\"/g" ${file}
}

update_config_xml ()
{
    local file=$1
    local versionName=$2
    sed -i "s/version.*=.*\"[^\"]*\">/version   = \"${versionName}\">/g" ${file}
}

update_salesforcesdkmanager_java ()
{
    local file=$1
    local versionName=$2
    sed -i "s/SDK_VERSION.*=.*\"[^\"]*\"/SDK_VERSION = \"${versionName}\"/g" ${file}

}

parse_opts "$@"

VERSION_SUFFIXED=""
if [ "$OPT_IS_DEV" == "yes" ]
then
    echo "here 1"
    VERSION_SUFFIXED="${OPT_VERSION}.dev"
else
    echo "here 2"
    VERSION_SUFFIXED=${OPT_VERSION}
fi


echo -e "${YELLOW}*** SETTING VERSION NAME TO ${OPT_VERSION}, VERSION CODE TO ${OPT_CODE}, IS DEV = ${OPT_IS_DEV} ***${NC}"

echo "*** Updating package.json ***"
update_package_json "./package.json" "${OPT_VERSION}"

echo "*** Updating manifests ***"
update_manifest "./libs/SalesforceAnalytics/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/SalesforceSDK/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/SmartStore/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/SmartSync/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/SalesforceHybrid/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/SalesforceReact/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"

echo "*** Updating config.xml files ***"
update_config_xml "./libs/SalesforceHybrid/res/xml/config.xml" "${OPT_VERSION}"
update_config_xml "./libs/test/SalesforceHybridTest/res/xml/config.xml" "${OPT_VERSION}"

echo "*** Updating SalesforceSDKManager.java ***"
update_salesforcesdkmanager_java "./libs/SalesforceSDK/src/com/salesforce/androidsdk/app/SalesforceSDKManager.java" "${VERSION_SUFFIXED}"





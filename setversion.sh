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
    echo "  where: versionName is the version name e.g. 7.2.0"
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
    gsed -i "s/\"version\":.*\"[^\"]*\"/\"version\": \"${versionName}\"/g" ${file}
}

update_top_build_gradle ()
{
    local file=$1
    local versionName=$2
    gsed -i "s/version = '[0-9\.]*'/version = '${versionName}'/g" ${file}
}

update_build_gradle ()
{
    local file=$1
    local versionName=$2
    gsed -i "s/PUBLISH_VERSION = '[0-9\.]*'/PUBLISH_VERSION = '${versionName}'/g" ${file}
}

update_manifest ()
{
    local file=$1
    local versionName=$2
    local versionCode=$3
    gsed -i "s/android\:versionCode=\"[^\"]*\"/android:versionCode=\"${versionCode}\"/g" ${file}
    gsed -i "s/android\:versionName=\"[^\"]*\"/android:versionName=\"${versionName}\"/g" ${file}
}

update_config_xml ()
{
    local file=$1
    local versionName=$2
    gsed -i "s/version.*=.*\"[^\"]*\">/version   = \"${versionName}\">/g" ${file}
}

update_salesforcesdkmanager_java ()
{
    local file=$1
    local versionName=$2
    gsed -i "s/SDK_VERSION.*=.*\"[^\"]*\"/SDK_VERSION = \"${versionName}\"/g" ${file}

}

update_generate_doc ()
{
    local file=$1
    local versionName=$2
    gsed -i "s/SalesforceSDK [0-9\.]* API/SalesforceSDK ${versionName} API/g" ${file}
}

update_react_package_json ()
{
    local file=$1
    local versionName=$2
    local isDev=$3
    local sdkTag=""

    if [ "$OPT_IS_DEV" == "yes" ]
    then
        sdkTag="dev"
    else
        sdkTag="v${versionName}"
    fi

    gsed -i "s/\"version\":.*\"[^\"]*\"/\"version\": \"${versionName}\"/g" ${file}
    gsed -i "s/SalesforceMobileSDK-ReactNative.git\#[^\"]*\"/SalesforceMobileSDK-ReactNative.git\#${sdkTag}\"/g" ${file}
}

update_readme ()
{
    local file=$1
    local version=$2
    gsed -i "s/\#\#\# What's New.*/### What's New in ${version}/g" ${file}
    gsed -i "s/releases\/tag\/.*[)]/releases\/tag\/v${version}\)/g" ${file}
}

parse_opts "$@"

VERSION_SUFFIXED=""
if [ "$OPT_IS_DEV" == "yes" ]
then
    VERSION_SUFFIXED="${OPT_VERSION}.dev"
else
    VERSION_SUFFIXED=${OPT_VERSION}
fi

SHORT_VERSION=`echo ${OPT_VERSION} | cut -d. -f1,2`

echo -e "${YELLOW}*** SETTING VERSION NAME TO ${OPT_VERSION}, VERSION CODE TO ${OPT_CODE}, IS DEV = ${OPT_IS_DEV} ***${NC}"

echo "*** Updating main package.json ***"
update_package_json "./package.json" "${OPT_VERSION}"

echo "*** Updating react package.json ***"
update_react_package_json "./libs/SalesforceReact/package.json" "${OPT_VERSION}" "${OPT_IS_DEV}"

echo "*** Updating top build.gradle file ***"
update_top_build_gradle "./build.gradle" "${OPT_VERSION}"

echo "*** Updating build.gradle files ***"
update_build_gradle "./libs/SalesforceAnalytics/build.gradle" "${OPT_VERSION}"
update_build_gradle "./libs/SalesforceSDK/build.gradle" "${OPT_VERSION}"
update_build_gradle "./libs/SmartStore/build.gradle" "${OPT_VERSION}"
update_build_gradle "./libs/MobileSync/build.gradle" "${OPT_VERSION}"
update_build_gradle "./libs/SalesforceHybrid/build.gradle" "${OPT_VERSION}"
update_build_gradle "./libs/SalesforceReact/build.gradle" "${OPT_VERSION}"

echo "*** Updating manifests ***"
update_manifest "./libs/SalesforceAnalytics/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/SalesforceSDK/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/SmartStore/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/MobileSync/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/SalesforceHybrid/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"
update_manifest "./libs/SalesforceReact/AndroidManifest.xml" "${VERSION_SUFFIXED}" "${OPT_CODE}"

echo "*** Updating config.xml files ***"
update_config_xml "./libs/SalesforceHybrid/res/xml/config.xml" "${OPT_VERSION}"
update_config_xml "./libs/test/SalesforceHybridTest/res/xml/config.xml" "${OPT_VERSION}"

echo "*** Updating SalesforceSDKManager.java ***"
update_salesforcesdkmanager_java "./libs/SalesforceSDK/src/com/salesforce/androidsdk/app/SalesforceSDKManager.java" "${VERSION_SUFFIXED}"

echo "*** Updating generate_doc.sh ***"
update_generate_doc "./tools/generate_doc.sh" "${SHORT_VERSION}"

echo "*** Updating README.md ***"
update_readme "./README.md" "${OPT_VERSION}"

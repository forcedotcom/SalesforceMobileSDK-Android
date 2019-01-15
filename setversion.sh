#!/bin/bash

#set -x

OPT_VERSION_NAME=""
OPT_VERSION_CODE=""
OPT_IS_DEV=""
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

usage ()
{
    echo "Use this script to set Mobile SDK version name/code in source files"
    echo "Usage: $0 -v <versionName e.g. 7.1.0> -c <versionCode e.g. 64> [-d <isDev e.g. yes>]"
}

parse_opts ()
{
    while getopts v:c: command_line_opt
    do
        case ${command_line_opt} in
            v)
                OPT_VERSION_NAME=${OPTARG};;
            c)
                OPT_VERSION_CODE=${OPTARG};;
            ?)
                echo "Unknown option '-${OPTARG}'."
                usage
                exit 1;;
        esac
    done

    if [ "${OPT_VERSION_NAME}" == "" ]
    then
        echo "You must specify a value for the version name."
        usage
        exit 1
    fi

    valid_version_name_regex='^[0-9]+\.[0-9]+\.[0-9]+$'
    if [[ "${OPT_VERSION_NAME}" =~ $valid_version_name_regex ]]
     then
         # No action
            :
     else
        echo "${OPT_VERSION_NAME} is not a valid version name. Should be in the format <integer.integer.interger>"
        exit 2
    fi

    if [ "${OPT_VERSION_CODE}" == "" ]
    then
        echo "You must specify a value for the version code."
        usage
        exit 1
    fi

    valid_version_code_regex='^[0-9]+$'
    if [[ "${OPT_VERSION_CODE}" =~ $valid_version_code_regex ]]
     then
         # No action
            :
     else
        echo "${OPT_VERSION_CODE} is not a valid version code. Should be a number>"
        exit 2
    fi

    if [ "${OPT_IS_DEV}" == "yes" ]
    then
       OPT_IS_DEV=1
    else
       OPT_IS_DEV=0
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

echo -e "${YELLOW}*** SETTING VERSION NAME TO ${OPT_VERSION_NAME}, VERSION CODE TO ${OPT_VERSION_CODE}, IS DEV = ${OPT_IS_DEV} ***${NC}"

echo "*** Updating package.json ***"
update_package_json "./package.json" "${OPT_VERSION_NAME}"

echo "*** Updating manifests ***"
update_manifest "./libs/SalesforceAnalytics/AndroidManifest.xml" "${OPT_VERSION_NAME}" "${OPT_VERSION_CODE}"
update_manifest "./libs/SalesforceSDK/AndroidManifest.xml" "${OPT_VERSION_NAME}" "${OPT_VERSION_CODE}"
update_manifest "./libs/SmartStore/AndroidManifest.xml" "${OPT_VERSION_NAME}" "${OPT_VERSION_CODE}"
update_manifest "./libs/SmartSync/AndroidManifest.xml" "${OPT_VERSION_NAME}" "${OPT_VERSION_CODE}"
update_manifest "./libs/SalesforceHybrid/AndroidManifest.xml" "${OPT_VERSION_NAME}" "${OPT_VERSION_CODE}"
update_manifest "./libs/SalesforceReact/AndroidManifest.xml" "${OPT_VERSION_NAME}" "${OPT_VERSION_CODE}"

echo "*** Updating config xmls ***"
update_config_xml "./libs/SalesforceHybrid/res/xml/config.xml" "${OPT_VERSION_NAME}"
update_config_xml "./libs/test/SalesforceHybridTest/res/xml/config.xml" "${OPT_VERSION_NAME}"

echo "*** Updating salesforce sdk manager java ***"
update_salesforcesdkmanager_java "./libs/SalesforceSDK/src/com/salesforce/androidsdk/app/SalesforceSDKManager.java" "${OPT_VERSION_NAME}"





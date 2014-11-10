#!/bin/bash
TOP=`pwd`
LIBS_TOP=$TOP/libs/
NATIVE_TOP=$TOP/native/
HYBRID_TOP=$TOP/hybrid/
CORDOVA_TOP=$TOP/external/cordova/
TRUE=0
FALSE=1
TARGETS=""
VERBOSE=$FALSE
BUILD_OUTPUT_FILTER='^BUILD '
TEST_OUTPUT_FILTER='Tests run\|OK'

process_args()
{
    if [ $# -eq 0 ] 
    then
        usage
    fi
    while [ $# -gt 0 ]
    do
        case $1 in
            -h) usage ; shift 1 ;;
            -v) verbose ; shift 1 ;;
            -b) TARGETS="$TARGETS build{$2}" ; shift 2 ;;
            -t) TARGETS="$TARGETS test{$2}" ; shift 2 ;;
            *) shift 1 ;;
        esac
    done
}

wrong_directory_usage()
{
    echo "You must run this tool from the root directory of your repo clone"
}

usage ()
{
    echo "./tools/sdk.sh [-b <build_target>] [-t <test_target>] [-h] [-v]"
    echo ""
    echo "   -b target to build that target"
    echo "   -t test_target to run that test_target"
    echo "   -h for help"
    echo "   -v for verbose output"
    echo ""
    echo "    <build_target> can be "
    echo "        all"
    echo "        SalesforceSDK"
    echo "        SmartStore"
    echo "        SmartSync"
    echo "        Cordova"
    echo "        RestExplorer"
    echo "        NativeSqlAggregator"
    echo "        SmartSyncExplorer"
    echo "        FileExplorer"
    echo "        TemplateApp"
    echo "        AccountEditor"
    echo "        ContactExplorer"
    echo "        SimpleSync"
    echo "        UserList"
    echo "        SmartStoreExplorer"
    echo "        VFConnector"
    echo "        SalesforceSDKTest"
    echo "        SmartStoreTest"
    echo "        SmartSyncTest"
    echo "        TemplateAppTest"
    echo "        RestExplorerTest"
    echo "        ForcePluginsTest"
    echo "    <test_target> can be "
    echo "        all"
    echo "        SalesforceSDKTest"
    echo "        SmartStoreTest"
    echo "        SmartSyncTest"
    echo "        RestExplorerTest"
    echo "        TemplateAppTest"
    echo "        ForcePluginsTest"
}

verbose ()
{
    VERBOSE=$TRUE
    BUILD_OUTPUT_FILTER=""
    TEST_OUTPUT_FILTER=""
}

should_do ()
{
    if [[ "$TARGETS" == *$1* ]]
    then
        return $TRUE
    else
        return $FALSE
    fi
}

header () 
{
    if [ $VERBOSE -eq $TRUE ]
    then
        echo "********************************************************************************"
        echo "*                                                                              *"
        echo "* TOP $TOP"
        echo "* $1"
        echo "*                                                                              *"
        echo "********************************************************************************"
    else
        echo "$1"
    fi
}

build_project_if_requested ()
{
    if ( should_do "build{all}" || should_do "build{$1}" )
    then
        header "Building project $1"
        cd $2
        if [ -z $3 ]
        then
            API_VERSION=`cat AndroidManifest.xml | grep minSdkVersion | cut -d"\"" -f2`
        else
            API_VERSION=$3
        fi
        ANDROID_TARGET=`android list target | grep "android-$API_VERSION" | cut -d" "  -f2`
        # echo "API_VERSION=$API_VERSION"
        # echo "ANDROID_TARGET=$ANDROID_TARGET"
        android update project -p . -t "$ANDROID_TARGET" | grep "$BUILD_OUTPUT_FILTER"
        ant clean debug | grep "$BUILD_OUTPUT_FILTER"
        cd $TOP
    fi
}

build_test_project_if_requested ()
{
    if ( should_do "build{all}" || should_do "build{$1}" )
    then
        header "Building test project $1"
        cd $2
        android update test-project -p . -m $3 | grep "$BUILD_OUTPUT_FILTER"
        ant clean debug | grep "$BUILD_OUTPUT_FILTER"
        cd $TOP
    fi
}

run_test_project_if_requested ()
{
    if ( should_do "test{all}" || should_do "test{$1}" )
    then
        header "Running test project $1"
        cd $2
        ant installt | grep "$TEST_OUTPUT_FILTER"
        ant test | grep "$TEST_OUTPUT_FILTER"
        ant uninstall | grep "$TEST_OUTPUT_FILTER"
        cd $TOP
    fi
}

if [ ! -d "external" ]
then
    wrong_directory_usage
else
    process_args $@

    build_project_if_requested "Cordova" $CORDOVA_TOP/framework 19
    build_project_if_requested "SalesforceSDK" $LIBS_TOP/SalesforceSDK 19
    build_project_if_requested "SmartStore" $LIBS_TOP/SmartStore 19
    build_project_if_requested "SmartSync" $LIBS_TOP/SmartSync 19
    build_project_if_requested "TemplateApp" $NATIVE_TOP/TemplateApp 19
    build_project_if_requested "RestExplorer" $NATIVE_TOP/SampleApps/RestExplorer 19
    build_project_if_requested "NativeSqlAggregator" $NATIVE_TOP/SampleApps/NativeSqlAggregator 19
    build_project_if_requested "SmartSyncExplorer" $NATIVE_TOP/SampleApps/SmartSyncExplorer 19
    build_project_if_requested "FileExplorer" $NATIVE_TOP/SampleApps/FileExplorer 19
    build_project_if_requested "AccountEditor" $HYBRID_TOP/SampleApps/AccountEditor 19
    build_project_if_requested "ContactExplorer" $HYBRID_TOP/SampleApps/ContactExplorer 19
    build_project_if_requested "SimpleSync" $HYBRID_TOP/SampleApps/SimpleSync 19
    build_project_if_requested "UserList" $HYBRID_TOP/SampleApps/UserList 19
    build_project_if_requested "SmartStoreExplorer" $HYBRID_TOP/SampleApps/SmartStoreExplorer 19
    build_project_if_requested "VFConnector" $HYBRID_TOP/SampleApps/VFConnector 19

    build_test_project_if_requested "SalesforceSDKTest" $LIBS_TOP/test/SalesforceSDKTest .
    build_test_project_if_requested "SmartStoreTest" $LIBS_TOP/test/SmartStoreTest .
    build_test_project_if_requested "SmartSyncTest" $LIBS_TOP/test/SmartSyncTest .
    build_test_project_if_requested "TemplateAppTest" $NATIVE_TOP/test/TemplateAppTest ../../TemplateApp
    build_test_project_if_requested "RestExplorerTest" $NATIVE_TOP/SampleApps/test/RestExplorerTest ../../RestExplorer
    build_test_project_if_requested "ForcePluginsTest" $HYBRID_TOP/test/ForcePluginsTest .

    run_test_project_if_requested "SalesforceSDKTest" $LIBS_TOP/test/SalesforceSDKTest
    run_test_project_if_requested "SmartStoreTest" $LIBS_TOP/test/SmartStoreTest
    run_test_project_if_requested "SmartSyncTest" $LIBS_TOP/test/SmartSyncTest
    run_test_project_if_requested "TemplateAppTest" $NATIVE_TOP/test/TemplateAppTest
    run_test_project_if_requested "RestExplorerTest" $NATIVE_TOP/SampleApps/test/RestExplorerTest
    run_test_project_if_requested "ForcePluginsTest" $HYBRID_TOP/test/ForcePluginsTest
fi

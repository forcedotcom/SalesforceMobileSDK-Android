#!/bin/bash
TOP=`pwd`
NATIVE_TOP=$TOP/native/
HYBRID_TOP=$TOP/hybrid/
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

wrong_directory_usage ()
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
    echo "        RestExplorer"
    echo "        TemplateApp"
    echo "        CloudTunes"
    echo "        ContactExplorer"
    echo "        VFConnector"
    echo "        SFDCAccounts"
    echo "        SmartStoreExplorer"
    echo "        SalesforceSDKTest"
    echo "        RestExplorerTest"
    echo "        ContactExplorerTest"
    echo "        SmartStorePluginTest"
    echo "        VFConnectorTest"
    echo "    <test_target> can be "
    echo "        all"
    echo "        SalesforceSDKTest"
    echo "        RestExplorerTest"
    echo "        ContactExplorerTest"
    echo "        SmartStorePluginTest"
    echo "        VFConnectorTest"
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
        android update project -p . | grep "$BUILD_OUTPUT_FILTER"
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

if [ ! -d "dist" ]
then
    wrong_directory_usage
else
    process_args $@

    build_project_if_requested "SalesforceSDK" $NATIVE_TOP/SalesforceSDK
    build_project_if_requested "RestExplorer" $NATIVE_TOP/RestExplorer
    build_project_if_requested "TemplateApp" $NATIVE_TOP/TemplateApp
    build_project_if_requested "CloudTunes" $NATIVE_TOP/SampleApps/CloudTunes
    build_project_if_requested "SFDCAccounts" $HYBRID_TOP/SampleApps/SFDCAccounts
    build_project_if_requested "ContactExplorer" $HYBRID_TOP/SampleApps/ContactExplorer
    build_project_if_requested "VFConnector" $HYBRID_TOP/SampleApps/VFConnector
    build_project_if_requested "SmartStoreExplorer" $HYBRID_TOP/SampleApps/SmartStoreExplorer

    build_test_project_if_requested "SalesforceSDKTest" $NATIVE_TOP/SalesforceSDKTest .
    build_test_project_if_requested "RestExplorerTest" $NATIVE_TOP/RestExplorerTest ../RestExplorer
    build_test_project_if_requested "SmartStorePluginTest" $HYBRID_TOP/SmartStorePluginTest .
    build_test_project_if_requested "ContactExplorerTest" $HYBRID_TOP/SampleApps/ContactExplorerTest ../ContactExplorer
    build_test_project_if_requested "VFConnectorTest" $HYBRID_TOP/SampleApps/VFConnectorTest ../VFConnector

    run_test_project_if_requested "SalesforceSDKTest" $NATIVE_TOP/SalesforceSDKTest
    run_test_project_if_requested "RestExplorerTest" $NATIVE_TOP/RestExplorerTest
    run_test_project_if_requested "SmartStorePluginTest" $HYBRID_TOP/SmartStorePluginTest
    run_test_project_if_requested "ContactExplorerTest" $HYBRID_TOP/SampleApps/ContactExplorerTest
    run_test_project_if_requested "VFConnectorTest" $HYBRID_TOP/SampleApps/VFConnectorTest
fi

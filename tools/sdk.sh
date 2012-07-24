#!/bin/bash
TOP=`pwd`
NATIVE_TOP=$TOP/native/
HYBRID_TOP=$TOP/hybrid/
TRUE=0
FALSE=1

TARGETS_TO_BUILD=""
TARGETS_TO_TEST=""

process_args()
{
    while [ $# -gt 0 ] ; do
        case $1 in
            --help) usage ; shift 1 ;;
            -b) TARGETS_TO_BUILD="$TARGET_TO_BUILD $2" ; shift 2 ;;
            -t) TARGETS_TO_TEST="$TARGET_TO_TEST $2" ; shift 2 ;;
            *) shift 1 ;;
        esac
    done
}

usage ()
{
    echo ""
    echo "sdk.sh [-b <build_target>] [-t <test_target>] [--help]"
    echo "    <build_target> can be "
    echo "        SalesforceSDK"
    echo "        RestExplorer"
    echo "        TemplateApp"
    echo "        CloudTunes"
    echo "        SmartStorePluginTest"
    echo "        ContactExplorer"
    echo "        VFConnector"
    echo "        SFDCAccounts"
    echo "    <test_target> can be "
    echo "        SalesforceSDKTest"
    echo "        RestExplorerTest"
    echo "        SmartStorePluginTest"
    echo ""
    echo "When no arguments are passed, everything is built and tested"
}

should_build ()
{
    return [ -z "$TARGET_TO_BUILD" ] || [[ $TARGET_TO_BUILD == *$1* ]]
}

should_test ()
{
    return [ -z "$TARGET_TO_TEST" ] || [[ $TARGET_TO_TEST == *$1* ]]
}

header () 
{
    echo "********************************************************************************"
    echo "*                                                                              *"
    echo "* TOP $TOP"
    echo "* $1"
    echo "*                                                                              *"
    echo "********************************************************************************"
}

build_project ()
{
    if ( should_build $1 )
    then
        header "Building project $1"
        cd $2
        android update project -p .
        ant clean debug
        cd $TOP
    fi
}

build_test_project ()
{
    if ( should_build $1 )
    then
        header "Building test project $1"
        cd $2
        android update test-project -p . -m $3
        ant clean debug
        cd $TOP
    fi
}

run_test_project ()
{
    if ( should_test $1 )
    then
        header "Running test project $1"
        cd $2
        ant installt
        ant test
        ant uninstall
        cd $TOP
    fi
}

process_args $@
build_project "SalesforceSDK" $NATIVE_TOP/SalesforceSDK
build_project "RestExplorer" $NATIVE_TOP/RestExplorer
build_project "TemplateApp" $NATIVE_TOP/TemplateApp
build_project "CloudTunes" $NATIVE_TOP/SampleApps/CloudTunes
build_project "SFDCAccounts" $HYBRID_TOP/SampleApps/SFDCAccounts
build_project "ContactExplorer" $HYBRID_TOP/SampleApps/ContactExplorer
build_project "VFConnector" $HYBRID_TOP/SampleApps/VFConnector
build_test_project "SalesforceSDKTest" $NATIVE_TOP/SalesforceSDKTest .
build_test_project "RestExplorerTest" $NATIVE_TOP/RestExplorerTest ../RestExplorer
build_test_project "SmartStorePluginTest" $HYBRID_TOP/SmartStorePluginTest .
run_test_project "SalesforceSDKTest" $NATIVE_TOP/SalesforceSDKTest
run_test_project "RestExplorerTest" $NATIVE_TOP/RestExplorerTest
run_test_project "SmartStorePluginTest" $HYBRID_TOP/SmartStorePluginTest
    
echo $@

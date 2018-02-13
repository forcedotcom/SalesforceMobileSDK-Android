#!/bin/bash
TOP=`pwd`
TRUE=0
FALSE=1
TARGETS=""
VERBOSE=$FALSE
FAILFAST=$FALSE
BUILD_OUTPUT_FILTER='^BUILD '
TEST_OUTPUT_FILTER='^BUILD '

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
	    -f) failfast ; shift 1 ;;
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
    echo "./tools/sdk.sh [-b <build_target>] [-t <test_target>] [-h] [-v] [-f]"
    echo ""
    echo "   -b target to build that target"
    echo "   -t test_target to run that test_target"
    echo "   -h for help"
    echo "   -v for verbose output"
    echo "   -f to exit immediately on failure"
    echo ""
    echo "    <build_target> can be "
    echo "        all"
    echo "        SalesforceAnalytics"
    echo "        SalesforceSDK"
    echo "        SmartStore"
    echo "        SmartSync"
    echo "        SalesforceHybrid"
    echo "        SalesforceReact"
    echo "        Cordova"
    echo "        AccountEditor"
    echo "        AppConfigurator"
    echo "        ConfiguredApp"
    echo "        RestExplorer"
    echo "        NoteSync"
    echo "        SmartSyncExplorerHybrid"
    echo "        SmartSyncExplorer"
    echo "        SalesforceHybridTest"
    echo "    <test_target> can be "
    echo "        all"
    echo "        RestExplorerTest"
    echo "        SalesforceAnalyticsTest"
    echo "        SalesforceSDKTest"
    echo "        SmartStoreTest"
    echo "        SmartSyncTest"
    echo "        SalesforceHybridTest"
    echo "        SalesforceReactTest"
}

verbose ()
{
    VERBOSE=$TRUE
    BUILD_OUTPUT_FILTER=""
    TEST_OUTPUT_FILTER=""
}

failfast ()
{
    FAILFAST=$TRUE
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

# Run command ($1) optionally piped to 'grep $2'
# If global $FAILFAST is set, exit immediately if command exits with non-zero
# (failure) exit status.
run_with_output_filter ()
{
    cmd=$1
    filter=$2

    if [ "$filter" == "" ]
    then
        $cmd
    else
        ( $cmd | grep $filter ; exit ${PIPESTATUS[0]} )
    fi

    result=$?

    if [ $FAILFAST -eq $TRUE ]
    then
        if [ $result -ne 0 ]
        then
            exit ${result}
        fi
    fi
}

build_project_if_requested ()
{
    if ( should_do "build{$1}" )
    then
        header "Building project $1"
        ./gradlew $2:assembleDebug  | grep "$BUILD_OUTPUT_FILTER"
    fi
}

run_test_project_if_requested ()
{
    if ( should_do "test{$1}" )
    then
        header "Running test project $1"
        ./gradlew $2:connectedAndroidTest  | grep "$TEST_OUTPUT_FILTER"
    fi
}

if [ ! -d "external" ]
then
    wrong_directory_usage
else
    process_args $@

    if ( should_do "build{all}" )
    then
        header "Building all"
        ./gradlew assembleDebug  | grep "$TEST_OUTPUT_FILTER"
    else
        build_project_if_requested    "Cordova"                       :external:cordova:framework
        build_project_if_requested    "SalesforceAnalytics"           :libs:SalesforceAnalytics
        build_project_if_requested    "SalesforceSDK"                 :libs:SalesforceSDK
        build_project_if_requested    "SmartStore"                    :libs:SmartStore
        build_project_if_requested    "SmartSync"                     :libs:SmartSync
        build_project_if_requested    "SalesforceHybrid"              :libs:SalesforceHybrid
        build_project_if_requested    "SalesforceReact"               :libs:SalesforceReact
        build_project_if_requested    "RestExplorer"                  :native:NativeSampleApps:RestExplorer 
        build_project_if_requested    "AppConfigurator"               :native:NativeSampleApps:AppConfigurator
        build_project_if_requested    "ConfiguredApp"                 :native:NativeSampleApps:ConfiguredApp
        build_project_if_requested    "SmartSyncExplorer"             :native:NativeSampleApps:SmartSyncExplorer
        build_project_if_requested    "AccountEditor"                 :hybrid:HybridSampleApps:AccountEditor
        build_project_if_requested    "NoteSync"                      :hybrid:HybridSampleApps:NoteSync
        build_project_if_requested    "SmartSyncExplorerHybrid"       :hybrid:HybridSampleApps:SmartSyncExplorerHybrid
    fi

    if ( should_do "test{all}" )
    then
        header "Testing all"
        ./gradlew connectedAndroidTest  | grep "$TEST_OUTPUT_FILTER"
    else
        run_test_project_if_requested "SalesforceAnalyticsTest" :libs:SalesforceAnalytics
        run_test_project_if_requested "SalesforceSDKTest"       :libs:SalesforceSDK
        run_test_project_if_requested "SmartStoreTest"          :libs:SmartStore
        run_test_project_if_requested "SmartSyncTest"           :libs:SmartSync
        run_test_project_if_requested "SalesforceHybridTest"    :libs:SalesforceHybrid
        run_test_project_if_requested "SalesforceReactTest"     :libs:SalesforceReact
        run_test_project_if_requested "RestExplorerTest"        :native:NativeSampleApps:RestExplorer
    fi
fi

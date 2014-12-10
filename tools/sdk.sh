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
    echo "        AccountEditor"
    echo "        AppConfigurator"
    echo "        ConfiguredApp"
    echo "        ContactExplorer"
    echo "        FileExplorer"
    echo "        HybridFileExplorer"
    echo "        NativeSqlAggregator"
    echo "        RestExplorer"
    echo "        SimpleSync"
    echo "        SmartStoreExplorer"
    echo "        SmartSyncExplorer"
    echo "        TemplateApp"
    echo "        UserList"
    echo "        VFConnector"
    echo "        SalesforceSDKTest"
    echo "        SmartStoreTest"
    echo "        SmartSyncTest"
    echo "        ForcePluginsTest"
    echo "        RestExplorerTest"
    echo "        TemplateAppTest"
    echo "    <test_target> can be "
    echo "        all"
    echo "        ForcePluginsTest"
    echo "        RestExplorerTest"
    echo "        SalesforceSDKTest"
    echo "        SmartStoreTest"
    echo "        SmartSyncTest"
    echo "        TemplateAppTest"
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
        android update project -p . -t "android-$API_VERSION" | grep "$BUILD_OUTPUT_FILTER"

        if [ -z $4 ]
        then
            ant clean debug | grep "$BUILD_OUTPUT_FILTER"
            cd $TOP
        else
            cd $TOP
            ./gradlew $4:assembleDebug  | grep "$BUILD_OUTPUT_FILTER"
        fi
    fi
}

run_test_project_if_requested ()
{
    if ( should_do "test{all}" || should_do "test{$1}" )
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

    build_project_if_requested    "Cordova"             $CORDOVA_TOP/framework                     19
    build_project_if_requested    "SalesforceSDK"       $LIBS_TOP/SalesforceSDK                    21 :libs:SalesforceSDK
    build_project_if_requested    "SmartStore"          $LIBS_TOP/SmartStore                       19 :libs:SmartStore
    build_project_if_requested    "SmartSync"           $LIBS_TOP/SmartSync                        19 :libs:SmartSync
    build_project_if_requested    "TemplateApp"         $NATIVE_TOP/TemplateApp                    19 :native:TemplateApp
    build_project_if_requested    "RestExplorer"        $NATIVE_TOP/SampleApps/RestExplorer        19 :native:SampleApps:RestExplorer 
    build_project_if_requested    "AppConfigurator"     $NATIVE_TOP/SampleApps/AppConfigurator     21 :native:SampleApps:AppConfigurator
    build_project_if_requested    "ConfiguredApp"       $NATIVE_TOP/SampleApps/ConfiguredApp       21 :native:SampleApps:ConfiguredApp
    build_project_if_requested    "NativeSqlAggregator" $NATIVE_TOP/SampleApps/NativeSqlAggregator 19 :native:SampleApps:NativeSqlAggregator
    build_project_if_requested    "SmartSyncExplorer"   $NATIVE_TOP/SampleApps/SmartSyncExplorer   19 :native:SampleApps:SmartSyncExplorer
    build_project_if_requested    "FileExplorer"        $NATIVE_TOP/SampleApps/FileExplorer        19 :native:SampleApps:FileExplorer
    build_project_if_requested    "AccountEditor"       $HYBRID_TOP/SampleApps/AccountEditor       19 :hybrid:SampleApps:AccountEditor
    build_project_if_requested    "ContactExplorer"     $HYBRID_TOP/SampleApps/ContactExplorer     19 :hybrid:SampleApps:ContactExplorer
    build_project_if_requested    "HybridFileExplorer"  $HYBRID_TOP/SampleApps/HybridFileExplorer  19 :hybrid:SampleApps:HybridFileExplorer
    build_project_if_requested    "SimpleSync"          $HYBRID_TOP/SampleApps/SimpleSync          19 :hybrid:SampleApps:SimpleSync
    build_project_if_requested    "UserList"            $HYBRID_TOP/SampleApps/UserList            19 :hybrid:SampleApps:UserList
    build_project_if_requested    "SmartStoreExplorer"  $HYBRID_TOP/SampleApps/SmartStoreExplorer  19 :hybrid:SampleApps:SmartStoreExplorer
    build_project_if_requested    "VFConnector"         $HYBRID_TOP/SampleApps/VFConnector         19 :hybrid:SampleApps:VFConnector

    run_test_project_if_requested "SalesforceSDKTest"   :libs:SalesforceSDK
    run_test_project_if_requested "SmartStoreTest"      :libs:SmartStore
    run_test_project_if_requested "SmartSyncTest"       :libs:SmartSync
    run_test_project_if_requested "TemplateAppTest"     :native:TemplateApp
    run_test_project_if_requested "RestExplorerTest"    :native:SampleApps:RestExplorer
    run_test_project_if_requested "ForcePluginsTest"    :hybrid:test:ForcePluginsTest
fi

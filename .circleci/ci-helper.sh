#!/usr/bin/env bash
# inspired by https://github.com/Originate/guide/blob/master/android/guide/Continuous%20Integration.md

function printTestsToRun {
    if [ -z "$CIRCLE_PULL_REQUEST" ]; then
        echo "Not a PR.  Run everything"
    else
        getAndSetLibsToTest
        if [[ ! -z ${LIBS_TO_TEST} ]]; then
            echo -e "\n\nLibraries to Test-> ${LIBS_TO_TEST//","/", "}."
        else
            echo -e "\n\nNothing to Test."
        fi
    fi
}

function getAndSetLibsToTest {
    LIBS_TO_TEST=$(ruby .circleci/gitChangedLibs.rb)
    echo -e "export LIBS_TO_TEST=${LIBS_TO_TEST}" >> "${BASH_ENV}"
}

# Read from ENV var to determine what AVD to start when we update to use multiple
function startAVD {
    if [ -z "$CIRCLE_PULL_REQUEST" ] || [[ ${LIBS_TO_TEST} == *"${CURRENT_LIB}"* ]]; then
        emulator64-arm -avd test22 -no-audio -no-window -no-boot-anim -gpu off
    else
        echo "No need to start an emulator to test ${CURRENT_LIB} for this PR."
    fi
}

function waitForAVD {
    if [ -z "$CIRCLE_PULL_REQUEST" ] || [[ ${LIBS_TO_TEST} == *"${CURRENT_LIB}"* ]]; then
        local bootanim=""
        export PATH=$(dirname $(dirname $(which android)))/platform-tools:$PATH
        until [[ "$bootanim" =~ "stopped" ]]; do
            sleep 10
            bootanim=$(adb -e shell getprop init.svc.bootanim 2>&1)
            echo "emulator status=$bootanim"
        done
        sleep 30
        echo "Device Booted"
    else
        echo "No need to start an emulator to test ${CURRENT_LIB} for this PR."
    fi
}

function runTests {
    if [ -z "$CIRCLE_PULL_REQUEST" ] || [[ ${LIBS_TO_TEST} == *"${CURRENT_LIB}"* ]]; then
        ./gradlew :libs:${CURRENT_LIB}:connectedAndroidTest --continue --no-daemon --profile --max-workers 2 --stacktrace
    else
        echo "No need to run ${CURRENT_LIB} tests for this PR."
    fi
}

# Use the "-p" flag to run the style Dangerfile for the PR (not the individual library unit test/lint Dangerfile)
function runDanger {
    if [ -z "$CIRCLE_PULL_REQUEST" ] || [[ ${LIBS_TO_TEST} == *"${CURRENT_LIB}"* ]]; then
        if [ "$1" == "-p" ]; then
            DANGER_GITHUB_API_TOKEN="c21349d8a97e1bf9cdd9""301fd949a83db862216b" danger --dangerfile=.circleci/Dangerfile_PR.rb --danger_id=PR-Check --verbose
        else
            if ls libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/*.xml 1> /dev/null 2>&1; then
                mv libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/*.xml libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/test-results.xml
            fi
            DANGER_GITHUB_API_TOKEN="c21349d8a97e1bf9cdd9""301fd949a83db862216b" danger --dangerfile=.circleci/Dangerfile_Lib.rb --danger_id="${CURRENT_LIB}" --verbose
        fi
    else
        echo "No need to run Danger."
    fi
}
#!/usr/bin/env bash
# inspired by https://github.com/Originate/guide/blob/master/android/guide/Continuous%20Integration.md

function printTestsToRun {
    echo "CIRCLE_PULL_REQUEST: $CIRCLE_PULL_REQUEST"
    echo "CIRCLE_PULL_REQUEST with brances: ${CIRCLE_PULL_REQUEST}"
    echo "CIRCLE_PR_USERNAME: ${CIRCLE_PR_USERNAME}"
    echo "CIRCLE_PROJECT_USERNAME: ${CIRCLE_PROJECT_USERNAME}"
    echo "CIRCLE_PR_NUMBER: ${CIRCLE_PR_NUMBER}"
    echo "CIRCLE_PULL_REQUEST: ${CIRCLE_PULL_REQUEST}"
    echo "CIRCLE_PULL_REQUESTS: ${CIRCLE_PULL_REQUESTS}"
    echo "LIBS_TO_TEST: $(ruby .circleci/gitChangedLibs.rb)"

    if [ -z "$CIRCLE_PULL_REQUEST" ]; then
        echo "Not a PR.  Run everything"
    else
        LIBS_TO_TEST=$(ruby .circleci/gitChangedLibs.rb)
        echo -e "export LIBS_TO_TEST=${LIBS_TO_TEST}" >> "${BASH_ENV}"
        echo "Bash LIBS_TO_TEST: ${LIBS_TO_TEST}"
        if [[ ! -z ${LIBS_TO_TEST} ]]; then
            echo -e "\n\nLibraries to Test-> ${LIBS_TO_TEST//","/", "}."
        else
            echo -e "\n\nNothing to Test."
        fi
    fi
}

# Read from ENV var to determine what AVD to start when we update to use multiple
function startAVD {
    printTestsToRun
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
            sleep 5
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
        ./gradlew :libs:${CURRENT_LIB}:connectedAndroidTest --continue --no-daemon --profile --max-workers 2
    else
        echo "No need to run ${CURRENT_LIB} tests for this PR."
    fi
}

function runDangerPR {
    if [[ -n "$CIRCLE_PULL_REQUEST" ]]; then
        DANGER_GITHUB_API_TOKEN="c21349d8a97e1bf9cdd9""301fd949a83db862216b" danger --dangerfile=.circleci/Dangerfile_PR
    else
        echo "Not a PR, no need to run Danger."
    fi
}

function runDangerLib {
    if [[ -n "$CIRCLE_PULL_REQUEST" ]]; then
        if ls libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/*.xml 1> /dev/null 2>&1; then
            mv libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/*.xml libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/test-results.xml
        fi
        DANGER_GITHUB_API_TOKEN="c21349d8a97e1bf9cdd9""301fd949a83db862216b" danger --dangerfile=.circleci/Dangerfile_Lib
    else
        echo "Not a PR, no need to run Danger."
    fi
}
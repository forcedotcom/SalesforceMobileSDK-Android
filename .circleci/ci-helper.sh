#!/usr/bin/env bash
# inspired by https://github.com/Originate/guide/blob/master/android/guide/Continuous%20Integration.md

function printTestsToRun {
    if [ -z "$CIRCLE_PULL_REQUEST" ]; then
        echo "Not a PR.  Run everything"
    else
        LIBS_TO_TEST=$(ruby .circleci/gitChangedLibs.rb)
        if [[ ! -z ${LIBS_TO_TEST} ]]; then
            echo -e "\n\nLibraries to Test-> ${LIBS_TO_TEST//","/", "}."
        else
            echo -e "\n\nNothing to Test."
        fi
    fi
}

# Read from ENV var to determine what AVD to start when we update to use multiple
function startAVD {
    if [ -z "$CIRCLE_PULL_REQUEST" ] || [[ $(ruby .circleci/gitChangedLibs.rb) == *"${CURRENT_LIB}"* ]]; then
        emulator64-arm -avd test22 -no-audio -no-window -no-boot-anim -gpu off
    else
        echo "No need to start an emulator to test ${CURRENT_LIB} for this PR."
    fi
}

function waitForAVD {
    if [ -z "$CIRCLE_PULL_REQUEST" ] || [[ $(ruby .circleci/gitChangedLibs.rb) == *"${CURRENT_LIB}"* ]]; then
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
    if [ -z "$CIRCLE_PULL_REQUEST" ] || [[ $(ruby .circleci/gitChangedLibs.rb) == *"${CURRENT_LIB}"* ]]; then
        ./gradlew :libs:${CURRENT_LIB}:connectedAndroidTest --continue --no-daemon --profile --max-workers 2
    else
        echo "No need to run ${CURRENT_LIB} tests for this PR."
    fi
}

function runDangerPR {
    if [[ -n "$CIRCLE_PULL_REQUEST" ]]; then
        DANGER_GITHUB_API_TOKEN="c21349d8a97e1bf9cdd9""301fd949a83db862216b" danger --dangerfile=.circleci/Dangerfile_PR --danger_id=ci/circleci-setup
    else
        echo "Not a PR, no need to run Danger."
    fi
}

function runDangerLib {
    if [[ -n "$CIRCLE_PULL_REQUEST" ]]; then
        mv libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/*.xml libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/test-results.xml
        DANGER_GITHUB_API_TOKEN="c21349d8a97e1bf9cdd9""301fd949a83db862216b" danger --dangerfile=.circleci/Dangerfile_Lib --danger_id=ci/circleci-${CURRENT_LIB}
    else
        echo "Not a PR, no need to run Danger."
    fi
}
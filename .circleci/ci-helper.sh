#!/usr/bin/env bash

function envSetup {
    sudo npm i npm@latest -g
    sudo npm install -g shelljs@0.7.0
    sudo npm install -g cordova@8.0.0
    cordova telemetry off

    ./install.sh

    gem install bundler
    gem install danger
    gem install danger-junit
    gem install danger-android_lint
    gem install danger-jacoco

    ruby .circleci/setTestCreds.rb 
}

function printTestsToRun {
    if [ -n "$NIGHTLY_TEST" ]; then
        echo -e "\n\nNightly -> Run everything."
    else
        LIBS_TO_TEST=$(ruby .circleci/gitChangedLibs.rb)
        echo -e "export LIBS_TO_TEST=${LIBS_TO_TEST}" >> "${BASH_ENV}"

        # Check if tests should run
        if [[ ${LIBS_TO_TEST} == *"${CURRENT_LIB}"* ]]; then
            echo -e "\n\nLibraries to Test-> ${LIBS_TO_TEST//","/", "}."
        else
            echo -e "\n\nNo need to test ${CURRENT_LIB} for this PR, stopping execution."
            circleci step halt
        fi
    fi
}

function runTests {
    if [[ $CIRCLE_BRANCH == *"pull"* ]]; then
        android_api=27
    else
        # Run API 21 on Mon, 23 on Wed, 25 on Fri
        android_api=$((19 + $(date +"%u")))
    fi

    [[ $android_api < 23 ]] && device="Nexus6" || device="NexusLowRes"

    #
    #  Code coverage has been removed until a client crash issue is fixed by Google
    #  Issue: https://issuetracker.google.com/issues/116075439
    #
    gcloud firebase test android run \
        --project mobile-apps-firebase-test \
        --type instrumentation \
        --app "native/NativeSampleApps/RestExplorer/build/outputs/apk/debug/RestExplorer-debug.apk" \
        --test ${TEST_APK}  \
        --device model=$device,version=$android_api,locale=en,orientation=portrait  \
        --results-dir=${CURRENT_LIB}-${CIRCLE_BUILD_NUM}  \
        --results-history-name=${CURRENT_LIB}  \
        --timeout 10m
}

function runDanger {
    if [[ $CIRCLE_BRANCH == *"pull"* ]]; then
        # These env vars are not set properly on rebuilds
        export CIRCLE_PULL_REQUEST="https://github.com/forcedotcom/SalesforceMobileSDK-Android/${CIRCLE_BRANCH}"
        export CIRCLE_PULL_REQUESTS="https://github.com/forcedotcom/SalesforceMobileSDK-Android/${CIRCLE_BRANCH}"
        export CI_PULL_REQUEST="https://github.com/forcedotcom/SalesforceMobileSDK-Android/${CIRCLE_BRANCH}"
        export CI_PULL_REQUESTS="https://github.com/forcedotcom/SalesforceMobileSDK-Android/${CIRCLE_BRANCH}"

        if [ -z "${CURRENT_LIB}" ]; then
            # This token is completely harmless as it only has public permission and
            # is owned by a bot who isn't a member of hte org.
            DANGER_GITHUB_API_TOKEN="279a29d75427e4178cef""b7b5b2d7646c540f025a" danger --dangerfile=.circleci/Dangerfile_PR.rb --danger_id=PR-Check --verbose
        else
            if ls libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/*.xml 1> /dev/null 2>&1; then
                mv libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/*.xml libs/"${CURRENT_LIB}"/build/outputs/androidTest-results/connected/test-results.xml
            fi
            # This token is completely harmless as it only has public permission and
            # is owned by a bot who isn't a member of hte org.
            DANGER_GITHUB_API_TOKEN="279a29d75427e4178cef""b7b5b2d7646c540f025a" danger --dangerfile=.circleci/Dangerfile_Lib.rb --danger_id="${CURRENT_LIB}" --verbose
        fi
    else
        echo "No need to run Danger."
    fi
}
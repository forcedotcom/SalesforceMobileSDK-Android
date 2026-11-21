#!/usr/bin/env bash

set -euo pipefail

: "${FULL_API_RANGE:?FULL_API_RANGE is required}"
: "${PS16K_MIN_API_VERSION:?PS16K_MIN_API_VERSION is required}"
: "${RUN_NUMBER:?RUN_NUMBER is required}"

if [ "$#" -eq 0 ]; then
  echo "Usage: $0 <test-group> [<test-group>...]" >&2
  exit 2
fi

FAILED_GROUPS=()

for GROUP in "$@"; do
  case "$GROUP" in
    login)
      TEST_TARGETS="notClass com.salesforce.samples.authflowtester.MultiUserLoginTests,notClass com.salesforce.samples.authflowtester.DPoPLoginTests,notClass com.salesforce.samples.authflowtester.LoginWithRestartTests,notClass com.salesforce.samples.authflowtester.RTRLoginTests,notClass com.salesforce.samples.authflowtester.RefreshTokenMigrationTests,notClass com.salesforce.samples.authflowtester.WelcomeLoginTests,notClass com.salesforce.samples.authflowtester.AdvancedAuthBeaconLoginTests,notClass com.salesforce.samples.authflowtester.CommunityLoginTests,notClass com.salesforce.samples.authflowtester.NativeLoginTests"
      FIREBASE_TIMEOUT="60m"
      ;;
    welcome)
      TEST_TARGETS="class com.salesforce.samples.authflowtester.WelcomeLoginTests,class com.salesforce.samples.authflowtester.AdvancedAuthBeaconLoginTests"
      FIREBASE_TIMEOUT="10m"
      ;;
    community)
      TEST_TARGETS="class com.salesforce.samples.authflowtester.CommunityLoginTests"
      FIREBASE_TIMEOUT="45m"
      ;;
    native-login)
      TEST_TARGETS="class com.salesforce.samples.authflowtester.NativeLoginTests"
      FIREBASE_TIMEOUT="45m"
      ;;
    token-lifecycle)
      TEST_TARGETS="class com.salesforce.samples.authflowtester.DPoPLoginTests,class com.salesforce.samples.authflowtester.LoginWithRestartTests,class com.salesforce.samples.authflowtester.RTRLoginTests,class com.salesforce.samples.authflowtester.RefreshTokenMigrationTests"
      FIREBASE_TIMEOUT="60m"
      ;;
    multi-user)
      TEST_TARGETS="class com.salesforce.samples.authflowtester.MultiUserLoginTests"
      FIREBASE_TIMEOUT="60m"
      ;;
    *)
      echo "Unknown AuthFlowTester test group: $GROUP" >&2
      exit 2
      ;;
  esac

  GCLOUD_RESULTS_DIR="authflowtester-nightly-${GROUP}-build-${RUN_NUMBER}"
  DEVICE_ARGS=()
  for LEVEL in $FULL_API_RANGE; do
    DEVICE_MODEL="MediumPhone.arm"
    if [ "$PS16K_MIN_API_VERSION" -le "$LEVEL" ]; then
      DEVICE_MODEL="MediumPhone_ps16k.arm"
    fi
    DEVICE_ARGS+=(--device "model=${DEVICE_MODEL},version=${LEVEL},locale=en,orientation=portrait")
  done

  if ! gcloud firebase test android run \
    --project mobile-apps-firebase-test \
    --type instrumentation \
    --use-orchestrator \
    --environment-variables clearPackageData=true \
    --app "native/NativeSampleApps/AuthFlowTester/build/outputs/apk/debug/AuthFlowTester-debug.apk" \
    --test "native/NativeSampleApps/AuthFlowTester/build/outputs/apk/androidTest/debug/AuthFlowTester-debug-androidTest.apk" \
    --test-targets "$TEST_TARGETS" \
    "${DEVICE_ARGS[@]}" \
    --directories-to-pull=/sdcard \
    --results-dir="$GCLOUD_RESULTS_DIR" \
    --results-history-name=AuthFlowTester \
    --no-performance-metrics \
    --num-flaky-test-attempts=1 \
    --timeout="$FIREBASE_TIMEOUT"; then
    FAILED_GROUPS+=("$GROUP")
  fi
done

if [ "${#FAILED_GROUPS[@]}" -gt 0 ]; then
  echo "Failed AuthFlowTester test groups: ${FAILED_GROUPS[*]}" >&2
  exit 1
fi

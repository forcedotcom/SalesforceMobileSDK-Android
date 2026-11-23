#!/usr/bin/env bash

# Runs one library's existing CI shards against a single Firebase Test Lab API level.
set -euo pipefail

readonly FIREBASE_PROJECT="mobile-apps-firebase-test"
readonly PS16K_MIN_API_LEVEL=36

usage() {
    cat <<'EOF'
Usage: .github/test-shards/run-firebase-sharded-tests.sh <library> <api-level>

Builds the library's instrumentation APK and RestExplorer, then runs the
library's configured Firebase Test Lab shards at the specified API level.

Prerequisites:
  - shared/test/test_credentials.json exists
  - gcloud is authenticated for mobile-apps-firebase-test
  - gcloud's beta component, jq, Java 21, and Android SDK are installed
  - repository dependencies have been initialized with ./install.sh
  - SalesforceHybrid also requires the Cordova CLI
EOF
}

if [[ $# -ne 2 ]]; then
    usage >&2
    exit 1
fi

lib=$1
api_level=$2
repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || {
    echo "Run this script from within the repository." >&2
    exit 1
}
cd "$repo_root"

if [[ ! $api_level =~ ^[0-9]+$ ]] || (( api_level < 31 || api_level > 37 )); then
    echo "API level must be an integer from 31 through 37: $api_level" >&2
    exit 1
fi

case "$api_level" in
    31|32|33|34|35|36|37) ;;
    *)
        echo "API level must be a canonical value from 31 through 37: $api_level" >&2
        exit 1
        ;;
esac

shard_config=".github/test-shards/${lib}.json"
if [[ ! -f $shard_config ]]; then
    echo "No shard configuration found for ${lib}: ${shard_config}" >&2
    exit 1
fi

if [[ ! -f "libs/${lib}/build.gradle.kts" ]]; then
    echo "Unknown library: ${lib}" >&2
    exit 1
fi

if [[ ! -f shared/test/test_credentials.json ]]; then
    echo "Missing shared/test/test_credentials.json. Configure test credentials before running." >&2
    exit 1
fi

echo "Warning: the test APK includes shared/test/test_credentials.json and will be uploaded to ${FIREBASE_PROJECT}." >&2
echo "Use only least-privileged, disposable test-org credentials." >&2

for command in gcloud jq; do
    if ! command -v "$command" >/dev/null; then
        echo "Missing required command: $command" >&2
        exit 1
    fi
done

if ! gcloud auth list --filter=status:ACTIVE --format='value(account)' | grep -q .; then
    echo "No active gcloud account. Authenticate for ${FIREBASE_PROJECT} before running." >&2
    exit 1
fi

if [[ $lib == "SalesforceHybrid" ]] && ! command -v cordova >/dev/null; then
    echo "SalesforceHybrid requires the Cordova CLI. Install it with: npm install -g cordova" >&2
    exit 1
fi

if ! jq -e '
    (.shards | type == "array" and length > 0) and
    ([.shards[] | select(.name == "remaining")] | length == 1) and
    all(
        .shards[];
        (.name | type == "string") and
        (.targets | type == "array" and length > 0) and
        if .name == "remaining" then
            all(.targets[]; startswith("notClass "))
        else
            all(.targets[]; startswith("class "))
        end
    )
' "$shard_config" >/dev/null; then
    echo "Invalid shard config ${shard_config}: require one remaining shard, class targets, and notClass exclusions." >&2
    exit 1
fi

classes=$(jq -r '[.shards[] | select(.name != "remaining") | .targets[] | select(startswith("class ")) | ltrimstr("class ")] | sort | .[]' "$shard_config")
not_classes=$(jq -r '[.shards[] | select(.name == "remaining") | .targets[] | select(startswith("notClass ")) | ltrimstr("notClass ")] | sort | .[]' "$shard_config")
if [[ $classes != "$not_classes" ]]; then
    echo "Shard config mismatch in ${shard_config}: class targets must match remaining-shard notClass targets." >&2
    exit 1
fi

num_shards=$(jq -e '.shards | length' "$shard_config")
if (( num_shards == 0 )); then
    echo "Shard config contains no shards: ${shard_config}" >&2
    exit 1
fi

shard_args=()
for (( index = 0; index < num_shards; index++ )); do
    targets=$(jq -er ".shards[$index].targets | join(\";\")" "$shard_config")
    if [[ -z $targets ]]; then
        echo "Shard ${index} has no targets in ${shard_config}" >&2
        exit 1
    fi
    shard_args+=("--test-targets-for-shard=${targets}")
done

device_model="MediumPhone.arm"
if (( api_level >= PS16K_MIN_API_LEVEL )); then
    device_model="MediumPhone_ps16k.arm"
fi

echo "Building ${lib} test APK and RestExplorer app APK..."
./gradlew "libs:${lib}:assembleAndroidTest"
./gradlew native:NativeSampleApps:RestExplorer:assembleDebug

app_apk="native/NativeSampleApps/RestExplorer/build/outputs/apk/debug/RestExplorer-debug.apk"
test_apk="libs/${lib}/build/outputs/apk/androidTest/debug/${lib}-debug-androidTest.apk"
for apk in "$app_apk" "$test_apk"; do
    if [[ ! -f $apk ]]; then
        echo "Expected APK was not produced: ${apk}" >&2
        exit 1
    fi
done

if ! gcloud beta firebase test android run --help >/dev/null 2>&1; then
    echo "Installing the gcloud beta component..."
    gcloud components install beta --quiet
fi

results_suffix=$(od -An -N4 -tx4 /dev/urandom | tr -d '[:space:]')
results_dir="${lib}-api-${api_level}-local-$(date -u +%Y%m%dT%H%M%SZ)-${results_suffix}"
echo "Submitting ${num_shards} shard(s) to Firebase Test Lab in ${results_dir}..."
gcloud beta firebase test android run \
    --project "$FIREBASE_PROJECT" \
    --type instrumentation \
    --app "$app_apk" \
    --test "$test_apk" \
    --device "model=${device_model},version=${api_level},locale=en,orientation=portrait" \
    --environment-variables 'coverage=true,coverageFile=/sdcard/coverage.ec' \
    --directories-to-pull=/sdcard \
    --results-dir="$results_dir" \
    --results-history-name="$lib" \
    --timeout=30m \
    --no-auto-google-login \
    --no-record-video \
    --no-performance-metrics \
    --num-flaky-test-attempts=1 \
    "${shard_args[@]}"

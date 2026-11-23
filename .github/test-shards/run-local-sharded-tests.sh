#!/usr/bin/env bash

# Runs one library's existing CI shards sequentially on a connected Android device.
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: .github/test-shards/run-local-sharded-tests.sh <library> [device-serial]

Runs every configured test shard sequentially with connectedAndroidTest. When a
device serial is omitted, ANDROID_SERIAL is used; otherwise exactly one ready
ADB device must be connected.

Prerequisites:
  - shared/test/test_credentials.json exists
  - jq, adb, Java 21, and Android SDK are installed
  - repository dependencies have been initialized with ./install.sh
  - the selected emulator or device is booted, unlocked, API level 31+, and
    can reach the configured test org
  - SalesforceHybrid may require the Cordova CLI
EOF
}

if [[ $# -lt 1 || $# -gt 2 ]]; then
    usage >&2
    exit 1
fi

lib=$1
serial=${2:-${ANDROID_SERIAL:-}}
repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || {
    echo "Run this script from within the repository." >&2
    exit 1
}
cd "$repo_root"

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

for command in adb jq; do
    if ! command -v "$command" >/dev/null; then
        echo "Missing required command: $command" >&2
        exit 1
    fi
done

if [[ $lib == "SalesforceHybrid" ]] && ! command -v cordova >/dev/null; then
    echo "SalesforceHybrid may require the Cordova CLI. Install it with: npm install -g cordova" >&2
    exit 1
fi

if ! jq -e '
    (.shards | type == "array" and length > 0) and
    ([.shards[] | select(.name == "remaining")] | length == 1) and
    ([.shards[] | .name] as $names | ($names | unique | length) == ($names | length)) and
    all(
        .shards[];
        (.name | type == "string" and test("^[A-Za-z0-9_-]+$")) and
        (.targets | type == "array" and length > 0) and
        if .name == "remaining" then
            all(.targets[]; startswith("notClass "))
        else
            all(.targets[]; startswith("class "))
        end
    )
' "$shard_config" >/dev/null; then
    echo "Invalid shard config ${shard_config}: require unique names, one remaining shard, class targets, and notClass exclusions." >&2
    exit 1
fi

classes=$(jq -r '[.shards[] | select(.name != "remaining") | .targets[] | ltrimstr("class ")] | sort | .[]' "$shard_config")
not_classes=$(jq -r '[.shards[] | select(.name == "remaining") | .targets[] | ltrimstr("notClass ")] | sort | .[]' "$shard_config")
if [[ $classes != "$not_classes" ]]; then
    echo "Shard config mismatch in ${shard_config}: class targets must match remaining-shard notClass targets." >&2
    exit 1
fi

if [[ -z $serial ]]; then
    ready_devices=()
    adb_devices=$(adb devices) || {
        echo "Unable to list connected devices." >&2
        exit 1
    }
    while IFS=$'\t' read -r device state; do
        [[ $state == "device" ]] && ready_devices+=("$device")
    done <<< "$adb_devices"
    if (( ${#ready_devices[@]} != 1 )); then
        echo "Specify a device serial or set ANDROID_SERIAL; found ${#ready_devices[@]} ready devices." >&2
        adb devices -l >&2
        exit 1
    fi
    serial=${ready_devices[0]}
fi

if [[ $(adb -s "$serial" get-state 2>/dev/null) != "device" ]]; then
    echo "Device ${serial} is not ready. Start, authorize, and boot it before running." >&2
    exit 1
fi

if [[ $(adb -s "$serial" shell getprop sys.boot_completed | tr -d '\r') != "1" ]]; then
    echo "Device ${serial} has not finished booting." >&2
    exit 1
fi

device_api=$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
if [[ ! $device_api =~ ^[0-9]+$ ]] || (( device_api < 31 )); then
    echo "Device ${serial} must run API level 31 or later; found ${device_api:-unknown}." >&2
    exit 1
fi

num_shards=$(jq -r '.shards | length' "$shard_config")
results_dir="build/connected-shard-results/${lib}-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$results_dir"

echo "Running ${num_shards} shard(s) for ${lib} on ${serial} (API ${device_api})."
failed_shards=()
for (( index = 0; index < num_shards; index++ )); do
    shard_name=$(jq -r ".shards[$index].name" "$shard_config")
    target_type=$(jq -r ".shards[$index].targets[0] | split(\" \")[0]" "$shard_config")
    targets=$(jq -r ".shards[$index].targets | map(ltrimstr(\"${target_type} \")) | join(\",\")" "$shard_config")
    shard_dir="${results_dir}/${shard_name}"
    log_file="${shard_dir}/gradle.log"
    mkdir -p "$shard_dir"

    echo "Running shard ${shard_name}..."
    rm -rf "libs/${lib}/build/outputs/androidTest-results/connected"
    rm -rf "libs/${lib}/build/reports/androidTests/connected"
    if ! ANDROID_SERIAL="$serial" ./gradlew ":libs:${lib}:connectedAndroidTest" \
        "-Pandroid.testInstrumentationRunnerArguments.${target_type}=${targets}" \
        2>&1 | tee "$log_file"; then
        failed_shards+=("$shard_name")
    fi

    has_test_results=false
    if [[ -d "libs/${lib}/build/outputs/androidTest-results/connected" ]]; then
        cp -R "libs/${lib}/build/outputs/androidTest-results/connected" "${shard_dir}/results"
        for result_file in "${shard_dir}"/results/*/*.xml; do
            [[ -s $result_file ]] || continue
            result_contents=$(<"$result_file")
            if [[ $result_contents =~ tests=\"[1-9] ]]; then
                has_test_results=true
                break
            fi
        done
    fi
    if [[ -d "libs/${lib}/build/reports/androidTests/connected" ]]; then
        cp -R "libs/${lib}/build/reports/androidTests/connected" "${shard_dir}/reports"
    fi
    if [[ $has_test_results == false ]]; then
        echo "Shard ${shard_name} did not produce a non-empty connected test result." >&2
        failed_shards+=("$shard_name")
    fi
done

if (( ${#failed_shards[@]} > 0 )); then
    echo "Failed shard(s): ${failed_shards[*]}" >&2
    echo "Logs and reports: ${results_dir}" >&2
    exit 1
fi

echo "All shards passed. Logs and reports: ${results_dir}"

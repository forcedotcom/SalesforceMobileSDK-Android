#!/bin/bash
# Running this script will install all dependencies needed for all of the projects

# Run from repo root so relative paths work regardless of invocation directory
cd "$(dirname "$0")"

# ensure that we have the correct version of all submodules
git submodule init
git submodule sync
git submodule update

# Restore bootconfig.json in shared submodule to committed placeholders
git -C external/shared checkout -- samples/mobilesyncexplorer/bootconfig.json samples/accounteditor/bootconfig.json 2>/dev/null || true

# get react native
pushd "libs/SalesforceReact"
rm -rf node_modules
rm yarn.lock
yarn install
./node_modules/.bin/react-native bundle --platform android --dev true --entry-file node_modules/react-native-force/test/alltests.js --bundle-output ../test/SalesforceReactTest/assets/index.android.bundle --assets-dest ../test/SalesforceReactTest/assets/
popd

# Apply bootconfig placeholder substitution. Usage:
#   apply_bootconfig_paths [sample_file] path1 path2 ...
# First arg is sample path (or empty for no sample). If sample is set, copy sample over each path (overwriting if present).
# Then substitute env vars.
apply_bootconfig_paths() {
    local sample_file=""
    [ -n "$1" ] && [ -f "$1" ] && sample_file="$1"
    shift
    while [ $# -gt 0 ]; do
        local bootconfig="$1"
        shift
        if [ -n "$sample_file" ]; then
            mkdir -p "$(dirname "$bootconfig")"
            cp "$sample_file" "$bootconfig"
        fi
        if [ -f "$bootconfig" ]; then
	    # Substitute env vars if set
	    if [ -n "${MSDK_ANDROID_REMOTE_ACCESS_CONSUMER_KEY:-}" ]; then
	              # sed -i.bak works identically on both BSD sed (macOS) and GNU sed (Linux)
                sed -i.bak "s|__CONSUMER_KEY__|${MSDK_ANDROID_REMOTE_ACCESS_CONSUMER_KEY}|g" "$bootconfig" && rm -f "$bootconfig.bak"
            fi
            if [ -n "${MSDK_ANDROID_REMOTE_ACCESS_CALLBACK_URL:-}" ]; then
                sed -i.bak "s|__REDIRECT_URI__|${MSDK_ANDROID_REMOTE_ACCESS_CALLBACK_URL}|g" "$bootconfig" && rm -f "$bootconfig.bak"
            fi
        fi
    done
}

BOOTCONFIG_SAMPLE="shared/bootconfig.xml.sample"
BOOTCONFIG_XML_PATHS=(
    "native/NativeSampleApps/RestExplorer/res/values/bootconfig.xml"
    "native/NativeSampleApps/AuthFlowTester/src/main/res/values/bootconfig.xml"
    "native/NativeSampleApps/ConfiguredApp/res/values/bootconfig.xml"
)
BOOTCONFIG_JSON_PATHS=(
    "external/shared/samples/mobilesyncexplorer/bootconfig.json"
    "external/shared/samples/accounteditor/bootconfig.json"
)

apply_bootconfig_paths "$BOOTCONFIG_SAMPLE" "${BOOTCONFIG_XML_PATHS[@]}"
apply_bootconfig_paths "" "${BOOTCONFIG_JSON_PATHS[@]}"

if [ -z "${MSDK_ANDROID_REMOTE_ACCESS_CONSUMER_KEY:-}" ] || [ -z "${MSDK_ANDROID_REMOTE_ACCESS_CALLBACK_URL:-}" ]; then
    echo ""
    echo "Note: MSDK_ANDROID_REMOTE_ACCESS_CONSUMER_KEY and/or MSDK_ANDROID_REMOTE_ACCESS_CALLBACK_URL are not set."
    echo "To run the sample applications, define these environment variables or ensure bootconfig.xml"
    echo "files exist (created from shared/bootconfig.xml.sample) with remoteAccessConsumerKey and oauthRedirectURI set."
    echo ""
fi

#!/bin/bash
# Running this script will install all dependencies needed for all of the projects 

# ensure that we have the correct version of all submodules
git submodule init
git submodule sync
git submodule update


# get react native
pushd "libs/SalesforceReact"
rm -rf node_modules
npm install
./node_modules/.bin/react-native bundle --platform android --dev true --entry-file node_modules/react-native-force/test/alltests.js --bundle-output ../test/SalesforceReactTest/assets/index.android.bundle --assets-dest ../test/SalesforceReactTest/assets/
popd

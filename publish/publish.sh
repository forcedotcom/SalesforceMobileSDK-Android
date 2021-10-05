#!/bin/bash

# if we are in publish dir cd to root
if [ ! -f gradlew ]; then
  cd ..
fi

./gradlew libs:SalesforceAnalytics:publishReleasePublicationToSonatypeRepository
./gradlew libs:SalesforceSDK:publishReleasePublicationToSonatypeRepository
./gradlew libs:SalesforceHybrid:publishReleasePublicationToSonatypeRepository
./gradlew libs:SalesforceReact:publishReleasePublicationToSonatypeRepository
./gradlew libs:SmartStore:publishReleasePublicationToSonatypeRepository
./gradlew libs:MobileSync:publishReleasePublicationToSonatypeRepository
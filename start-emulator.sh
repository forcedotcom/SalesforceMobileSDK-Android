#!/bin/bash

. android-settings.sh

echo "Starting android emulator..."
emulator -avd test -no-skin -no-audio -no-window -port

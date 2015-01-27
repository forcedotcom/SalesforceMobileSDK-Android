#!/bin/bash

. config/android-settings.sh

echo "Starting android emulator..."
emulator -avd test -no-skin -no-audio -no-window > emulator.log 2>&1 &

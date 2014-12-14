#!/bin/bash
. config/android-settings.sh

pkill -f emulator64 || true

$TDDIUM_CONFIG_ROOT/start-emulator.sh
$TDDIUM_CONFIG_ROOT/wait-for-emulator.sh

$@

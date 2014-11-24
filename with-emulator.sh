#!/bin/bash
. android-settings.sh

pkill -f emulator64 || true

$TDDIUM_REPO_ROOT/start-emulator.sh
$TDDIUM_REPO_ROOT/wait-for-emulator.sh

$@

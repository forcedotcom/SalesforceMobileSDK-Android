#!/bin/bash
. android-settings.sh

$TDDIUM_REPO_ROOT/start-emulator.sh
$TDDIUM_REPO_ROOT/wait-for-emulator.sh

$@

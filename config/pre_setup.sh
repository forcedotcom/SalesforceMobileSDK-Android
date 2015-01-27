#!/bin/bash

sudo apt-get update
sudo apt-get install -y ia32-libs

echo '$TDDIUM_REPO_ROOT is' + $TDDIUM_REPO_ROOT
$TDDIUM_REPO_ROOT/install.sh

(cd ~ && curl http://dl.google.com/android/android-sdk_r23.0.2-linux.tgz | tar zxv)

expect -c '
set timeout -1   ;
spawn ~/android-sdk-linux/tools/android update sdk -u; 
expect { 
    "Do you accept the license" { exp_send "y\r" ; exp_continue }
    eof
}
'

. config/android-settings.sh

echo y | android update sdk --no-ui --all --filter build-tools-21.1.1
echo y | android update sdk --filter sys-img-armeabi-v7a-$DEVICE_OS_VERSION --no-ui --force --all
echo no | android create avd --force -n test -t $DEVICE_OS_VERSION --abi armeabi-v7a


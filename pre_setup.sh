sudo apt-get update
sudo apt-get install -y ia32-libs

./install.sh

cd ~

curl http://dl.google.com/android/android-sdk_r23.0.2-linux.tgz | tar zxv

expect -c '
set timeout -1   ;
spawn ~/android-sdk-linux/tools/android update sdk -u; 
expect { 
    "Do you accept the license" { exp_send "y\r" ; exp_continue }
    eof
}
'

echo y | $ANDROID_SDK/tools/android  update sdk --filter sys-img-armeabi-v7a-$DEVICE_OS_VERSION --no-ui --force --all
echo no | $ANDROID_SDK/tools/android create avd --force -n test -t $DEVICE_OS_VERSION --abi armeabi-v7a


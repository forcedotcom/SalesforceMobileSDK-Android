sudo apt-get update
sudo apt-get install -y ia32-libs

./install.sh

cd ~

curl http://dl.google.com/android/android-sdk_r23.0.2-linux.tgz | tar zxv

yes | $ANDROID_SDK/tools/android  update sdk --filter platform-tools,build-tools-19.0.3,sysimg-17,android-17,extra-android-support --no-ui --force
yes no | $ANDROID_SDK/tools/android create avd --force -n test -t android-17 --abi armeabi-v7a


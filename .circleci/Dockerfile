FROM circleci/android:api-26-alpha

MAINTAINER Brandon Page <bpage@salesforce.com>

# Tools
RUN sudo apt-get update -y
RUN sudo apt-get install libqt5widgets5
RUN curl -sL https://deb.nodesource.com/setup_8.x | sudo -E bash -
RUN sudo apt-get install -y nodejs
RUN sudo npm install -g shelljs
RUN sudo npm install -g junit-merge
RUN sudo npm install -g cordova
RUN cordova telemetry off

RUN sudo apt-get install ruby-full
RUN sudo gem install bundler
RUN sudo gem install danger
RUN sudo gem install danger-junit
RUN sudo gem install danger-android_lint
RUN sudo gem install danger-jacoco

# Install NDK
RUN sudo mkdir /opt/ndk
RUN sudo chown ${USER:=$(/usr/bin/id -run)}:$USER /opt/ndk
RUN cd /opt/ndk \
curl -o ndk.zip https://dl.google.com/android/repository/android-ndk-r10e-linux-x86.zip \
unzip -o -q ndk.zip \
rm ndk.zip
RUN cd /opt/ndk \
curl -o ndk_64.zip https://dl.google.com/android/repository/android-ndk-r10e-linux-x86_64.zip \
unzip -o -q ndk_64.zip \
rm ndk_64.zip

# Android API 22
RUN echo "y" | sdkmanager "system-images;android-22;default;armeabi-v7a"
RUN echo "no" | avdmanager create avd -n test22 -k "system-images;android-22;default;armeabi-v7a"

# Android API 23
#RUN echo "y" | sdkmanager "system-images;android-23;google_apis;armeabi-v7a"
#RUN echo "no" | avdmanager create avd -n test23 -k "system-images;android-23;google_apis;armeabi-v7a"

# Android API 24
#RUN echo "y" | sdkmanager "system-images;android-24;default;arm64-v8a"
#RUN echo "no" | avdmanager create avd -n test24 -k "system-images;android-24;default;arm64-v8a"

# Android API 25
#RUN echo "y" | sdkmanager "system-images;android-25;google_apis;arm64-v8a"
#RUN echo "no" | avdmanager create avd -n test25 -k "system-images;android-25;google_apis;arm64-v8a"

RUN sdkmanager "build-tools;26.0.2"
RUN sdkmanager "extras;android;m2repository"

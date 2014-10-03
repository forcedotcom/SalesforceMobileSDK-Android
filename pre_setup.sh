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
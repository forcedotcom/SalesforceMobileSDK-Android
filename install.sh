#!/bin/bash
# Running this script will install all dependencies needed for all of the projects 

# ensure that we have the correct version of all submodules
git submodule init
git submodule sync
git submodule update

CURRENT_DIR=`pwd`
cd external
bower install ./samples # will bring in shared and its dependencies
rm -rf bower_components/salesforcemobilesdk-* # we have them as submodules already
ln -s ../bower_components ./shared/bower_components   # so that shared uses bower_components under external
ln -s ../bower_components ./samples/bower_components  # so that sampples uses bower_components under external
ln -s ../shared ./bower_components/salesforcemobilesdk-shared # samples shared in bower_components
cd $CURRENT_DIR

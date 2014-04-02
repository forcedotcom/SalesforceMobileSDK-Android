#!/bin/bash
# Running this script will install all dependencies needed for all of the projects 

# ensure that we have the correct version of all submodules
git submodule init
git submodule sync
git submodule update

CURRENT_DIR=`pwd`
cd external
bower install ./samples # will bring in shared and its dependencies
ln -s ../bower_components ./shared/bower_components
ln -s ../bower_components ./samples/bower_components
cd $CURRENT_DIR

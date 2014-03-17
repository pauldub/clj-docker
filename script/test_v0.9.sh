#!/bin/bash
# --------------------------------------
# Runs tests against actual docker v.0.9
#
# Prerequirements: vagrant, ansible
# --------------------------------------

VAGRANT_FOLDER="resource/docker-0.9/"
PREV_FOLDER=$(pwd)

# -- start vagrant
cd $VAGRANT_FOLDER
vagrant up
cd $PREV_FOLDER

# -- run tests written for docker v0.9
lein with-profile dev test docker.test.v09-*

# -- stop vagrant
cd $VAGRANT_FOLDER
vagrant halt
cd $PREV_FOLDER

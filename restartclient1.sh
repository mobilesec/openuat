#!/bin/bash

cd ~/amw/src/java/RelateAuthentication/

kill -9 `cat last.pid`
sudo killall -9 java

./reset.sh

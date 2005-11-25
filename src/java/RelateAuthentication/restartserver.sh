#!/bin/bash

cd ~/amw/src/java/RelateAuthentication/

kill -9 `cat last.pid`
killall -9 java

nohup ./runme.sh server 7 </dev/null >/dev/null 2>&1 &
echo $! > last.pid

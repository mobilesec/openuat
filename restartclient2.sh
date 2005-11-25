#!/bin/bash

cd ~/amw/src/java/RelateAuthentication/
nohup ./experiment.sh 10 </dev/null >/dev/null 2>&1 &
echo $! > last.pid

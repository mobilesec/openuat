#!/bin/bash

i=1
while true; do
  find /tmp -name "exp_*" | xargs rm
  echo ++++++++ Run $i ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  time ./experiment.sh
  echo ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
  echo
  let i=i+1
done

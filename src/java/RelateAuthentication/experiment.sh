#!/bin/bash

r=2
while [ $r -le 43 ]; do 
  i=1
  while [ $i -le 250 ]; do 
    echo -------------------------------------------------
    echo "Rounds: $r Try: $i"
    if [ ! -e "/tmp/exp_${r}_${i}" ]; then
      time ./runme.sh both $r
      ret=$?
      sleep 2s
      # only mark the run as done for either error or success, but else retry
      if [ $ret -eq 0 -o $ret -eq 1 ]; then
        touch "/tmp/exp_${r}_${i}"
      fi
    else
      echo "Already done, skipping."
      ret=0
    fi
    if [ $ret -eq 0 -o $ret -eq 1 ]; then
      let i=i+1
    fi
  done
  let r=r+1
done

#!/bin/bash
r=2
while [ $r -le 43 ]; do 
  i=0
  while [ $i -le 29 ]; do 
    echo -------------------------------------------------
    echo "Rounds: $r Try: $i"
    if [ ! -e "/tmp/exp_${r}_${i}" ]; then
      ./runme.sh client ind022000011.lancs.ac.uk 3 $r
      touch "/tmp/exp_${r}_${i}"
    else
      echo "Already done, skipping."
    fi
    let i=i+1
  done
  let r=r+1
done

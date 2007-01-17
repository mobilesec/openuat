#!/bin/sh
for f in ../../../doc/publications/shake-well-before-use/matlab/shaken-together-*.log; do
  mean=`java -cp src/:lib/jfreechart-1.0.1.jar:lib/log4j-1.2.jar:lib/jcommon-1.0.0.jar:dist/spatial-authentication-core.jar org.openuat.sensors.ParallelPortPWMReader $f | awk '/Coherence mean/ {print $3;}'`
  echo "`basename $f` $mean"
done

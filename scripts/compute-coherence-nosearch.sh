#!/bin/sh
echo "Positive data old (balls)"
for f in /data/shaking/balls/*-left*subj* /data/shaking/balls/*-right*subj* ; do echo -n "`basename $f` "; java -cp bin/:lib/jfreechart-1.0.1.jar:lib/jcommon-1.0.5.jar:lib/log4j-1.2.jar:src org.openuat.sensors.ParallelPortPWMReader $f | awk '/Coherence mean/ {print $3}'; done

echo "Positive data new (xsens)"
for f in /data/shaking/xsens/256hz/positive_* ; do echo -n "`basename $f` "; java -cp bin/:lib/jfreechart-1.0.1.jar:lib/jcommon-1.0.5.jar:lib/log4j-1.2.jar:src org.openuat.sensors.XsensLogReader $f | awk '/Coherence mean/ {print $3}'; done

echo "Negative data old (balls)"
for f in /data/shaking/balls/*pair* ; do echo -n "`basename $f` "; java -cp bin/:lib/jfreechart-1.0.1.jar:lib/jcommon-1.0.5.jar:lib/log4j-1.2.jar:src org.openuat.sensors.ParallelPortPWMReader $f | awk '/Coherence mean/ {print $3}'; done

echo "Negative data new (xsens)"
for f in /data/shaking/xsens/256hz/negative_* ; do echo -n "`basename $f` "; java -cp bin/:lib/jfreechart-1.0.1.jar:lib/jcommon-1.0.5.jar:lib/log4j-1.2.jar:src org.openuat.sensors.XsensLogReader $f | awk '/Coherence mean/ {print $3}'; done


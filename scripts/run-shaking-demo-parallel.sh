#!/bin/sh

# where are we called from?
mydir=`dirname $0`

JAR_PATH=$mydir/../dist/openuat-distbundle.jar:$mydir/../lib/jfreechart-1.0.1.jar:$mydir/../lib/jcommon-1.0.0.jar:$mydir/../lib/linux/swt-3.1.1_gtk.linux.x86.jar

java -cp ${JAR_PATH} -Djava.library.path=$mydir/../nativelib/linux org.openuat.apps.ShakingSinglePCDemonstrator listentcp 56677 &

# wait for the GUI to start and the TCP port to be opened for listening
sleep 2s

# and start the sampling
sudo $mydir/parport-pulsewidth - | nc -q0 localhost 56677

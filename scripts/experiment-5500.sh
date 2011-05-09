#!/bin/bash
ADDR1=00180FA4C997
ADDR2=00180FA3A1D4

if [ $# -ne 1 ]; then
  echo "Error: need log prefix"
  exit 1
fi

if [ -e /tmp/$1 ]; then
  echo "Error: /tmp/$1 already exists"
  exit 2
fi

mkdir /tmp/$1

java -cp `dirname $0`/../dist/openuat-distbundle.jar org.openuat.channel.main.bluetooth.jsr82.BluetoothRFCOMMChannel $ADDR1 2 stream | /srv/debian-unstable-i386/usr/bin/tai64n | /srv/debian-unstable-i386/usr/bin/tai64nlocal > /tmp/$1/1 &

java -cp `dirname $0`/../dist/openuat-distbundle.jar org.openuat.channel.main.bluetooth.jsr82.BluetoothRFCOMMChannel $ADDR2 2 stream | /srv/debian-unstable-i386/usr/bin/tai64n | /srv/debian-unstable-i386/usr/bin/tai64nlocal > /tmp/$1/2 &

echo "Press enter to terminate"
read

killall java

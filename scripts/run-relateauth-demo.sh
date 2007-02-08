#!/bin/bash
if [ x"$1" = x"autorun-once" ]; then
  # detect relate devices and take the first two
  dev1=""
  dev2=""
  # first look for bricks
  for id in `grep "FT232R USB UART" /sys/bus/usb/drivers/usb/*/product | awk -F'/' '{print $7}'`; do
    dev=`basename /sys/bus/usb/drivers/ftdi_sio/$id:1.0/ttyUSB*`
    if [ -z "$dev1" ]; then dev1=$dev; dev1type=2; echo "/dev/$dev1 seems to be a Relate brick"
    elif [ -z "$dev2" ]; then dev2=$dev; dev2type=2; echo "/dev/$dev2 seems to be a Relate brick"
    # ignore others
    else echo "Ignoring device $dev"
    fi
  done
  if [ -n "$dev1" -a -n "$dev2" ]; then
    echo "Found two Relate bricks on /dev/$dev1 and /dev/$dev2, using them"
  else
    # didn't find two bricks, looking for dongles now
    for id in `grep "USB <-> Serial" /sys/bus/usb/drivers/usb/*/product | awk -F'/' '{print $7}'`; do
      dev=`basename /sys/bus/usb/drivers/ftdi_sio/$id:1.0/ttyUSB*`
      if [ -z "$dev1" ]; then dev1=$dev; dev1type=1; echo "/dev/$dev1 seems to be a Relate dongle"
      elif [ -z "$dev2" ]; then dev2=$dev; dev2type=1; echo "/dev/$dev2 seems to be a Relate dongle"
      # ignore others
      else echo "Ignoring device $dev"
      fi
    done
  fi

  if [ -n "$dev1" -a -n "$dev2" ]; then
    echo "Found two Relate devices on /dev/$dev1 (type $dev1type) and /dev/$dev2 (type $dev2type), using them"
    echo
    params="both /dev/$dev1 $dev1type /dev/$dev2 $dev2type 5"
  else
    echo "Could not find two Relate devices, aborting"
    exit 1
  fi
else
  params=$@
fi

# classpath . is just so that log4j.properties is found
java -cp .:dist/openuat-distbundle.jar:lib/relate-2.2-core.jar:lib/relate-2.2-apps.jar:lib/swt-3.1.jar:lib/log4j-1.2.jar -Djava.library.path=../relate/thirdparty/nativelib/linux org.openuat.authentication.relate.RelateAuthenticationProtocol $params

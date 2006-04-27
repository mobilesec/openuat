magic1=15
while [ $magic1 -le 35 ]; do 
  magic2=50
  while [ $magic2 -le 400 ]; do 
    echo -n "$magic1 $magic2 "
    i=0
    while [ $i -lt 50 ]; do 
      time=`java -cp bin:lib/RXTXcomm.jar:lib/log4j-1.2.jar:. \
            -Dgnu.io.rxtx.SerialPorts=/dev/ttyUSB0:/dev/ttyUSB1 \
            -Djava.library.path=nativelib/linux uk.ac.lancs.relate.SerialConnector \
             /dev/ttyUSB0 param-search $magic1 $magic2 2>/dev/null \
             | awk ' /time to get dongle.s attention/ { print $11; }' \
             | tail -n 1 | sed 's/ms//'`
      if [ $? -ne 0 -o -z "$time" ]; then
        echo -n "X "
      else
        echo -n "$time "
        let i=i+1
      fi
      sleep 3s
    done
    echo
    let magic2=magic2+25
  done
  let magic1=magic1+1
done | tee parameter-search-experiment/attention-magic-values.log

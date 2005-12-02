magic1=1
while [ $magic1 -le 30 ]; do 
  magic2=50
  while [ $magic2 -le 400 ]; do 
    echo -n "$magic1 $magic2: "
    i=0
    while [ $i -lt 30 ]; do 
      time=`java -cp bin:lib/RXTXcomm.jar:lib/log4j-1.2.jar:. \
            -Dgnu.io.rxtx.SerialPorts=/dev/ttyUSB0:/dev/ttyUSB1 \
            -Djava.library.path=nativelib/linux uk.ac.lancs.relate.SerialConnector \
             /dev/ttyUSB1 param-search $magic1 $magic2 2>/dev/null \
             | awk ' /time to get dongle.s attention/ { print $11; }' \
             | sed 's/ms//'`
      echo -n "$time "
      let i=i+1
    done
    echo
    let magic2=magic2+25
  done
  let magic1=magic1+1
done | tee parameter-search-experiment/attention-magic-values.log

#!/bin/sh
java -cp bin:/usr/share/java/RXTXcomm.jar:lib/log4j-1.2.jar:. -Dgnu.io.rxtx.SerialPorts=/dev/ttyUSB0:/dev/ttyUSB1 -Djava.library.path=/usr/lib uk.ac.lancs.relate.SerialConnector /dev/ttyUSB0

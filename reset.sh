#!/bin/sh
java -cp bin:lib/RXTXcomm.jar:lib/log4j-1.2.jar:. -Dgnu.io.rxtx.SerialPorts=/dev/ttyUSB0:/dev/ttyUSB1 uk.ac.lancs.relate.SerialConnector /dev/ttyUSB0

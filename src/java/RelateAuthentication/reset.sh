#!/bin/sh
java -cp bin:lib/comm.jar:lib/log4j-1.2.jar:. -Djava.library.path=nativelib/linux/ uk.ac.lancs.relate.SerialConnector /dev/ttyUSB0

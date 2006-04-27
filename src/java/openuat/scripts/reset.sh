#!/bin/sh
java -cp bin:lib/relatecore.jar:. -Djava.library.path=/usr/lib uk.ac.lancs.relate.core.SerialConnector /dev/ttyUSB0

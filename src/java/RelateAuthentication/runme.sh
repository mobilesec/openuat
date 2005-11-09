# classpath . is just so that log4j.properties is found
java -cp bin:extlib/comm.jar:extlib/log4j-1.2.jar:. -Djava.library.path=nativelib/linux/ org.eu.mayrhofer.authentication.RelateAuthenticationProtocol $@

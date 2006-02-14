# classpath . is just so that log4j.properties is found
java -cp .:bin:lib/relate-dist.jar:/usr/lib/java/swt.jar -Djava.library.path=/usr/lib:/usr/lib/jni org.eu.mayrhofer.authentication.RelateAuthenticationProtocol $@

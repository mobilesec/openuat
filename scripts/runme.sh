# classpath . is just so that log4j.properties is found
java -cp .:bin-save:lib/relate-dist.jar:lib/swt-3.1.jar -Djava.library.path=../relate/thirdparty/nativelib/linux org.eu.mayrhofer.authentication.RelateAuthenticationProtocol $@

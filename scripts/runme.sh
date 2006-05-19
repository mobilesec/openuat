# classpath . is just so that log4j.properties is found
java -cp .:dist/openuat-core.jar:lib/relate-dist.jar:lib/swt-3.1.jar:lib/log4j-1.2.jar -Djava.library.path=../relate/thirdparty/nativelib/linux org.eu.mayrhofer.authentication.relate.RelateAuthenticationProtocol $@

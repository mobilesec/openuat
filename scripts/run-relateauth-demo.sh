# classpath . is just so that log4j.properties is found
java -cp .:dist/openuat-distbundle.jar:lib/relate-2.0-core.jar:lib/relate-2.0-apps.jar:lib/swt-3.1.jar:lib/log4j-1.2.jar -Djava.library.path=../relate/thirdparty/nativelib/linux org.eu.mayrhofer.authentication.relate.RelateAuthenticationProtocol $@

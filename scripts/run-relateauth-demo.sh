# classpath . is just so that log4j.properties is found
java -cp .:dist/openuat-distbundle.jar:lib/relate-2.1-core.jar:lib/relate-2.1-apps.jar:lib/swt-3.1.jar:lib/log4j-1.2.jar -Djava.library.path=../relate/thirdparty/nativelib/linux org.openuat.authentication.relate.RelateAuthenticationProtocol $@

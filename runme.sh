# classpath . is just so that log4j.properties is found
java -cp bin:lib/relatecore.jar:. -Djava.library.path=/usr/lib org.eu.mayrhofer.authentication.RelateAuthenticationProtocol $@

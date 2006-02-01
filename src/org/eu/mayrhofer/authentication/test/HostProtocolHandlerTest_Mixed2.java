package org.eu.mayrhofer.authentication.test;

public class HostProtocolHandlerTest_Mixed2 extends HostProtocolHandlerTest {
	public HostProtocolHandlerTest_Mixed2(String s) {
		super(s);
		this.useJSSEClient = false;
		this.useJSSEServer = true;
	}
}

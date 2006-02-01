package org.eu.mayrhofer.authentication.test;

public class HostProtocolHandlerTest_BCAPI extends HostProtocolHandlerTest {
	public HostProtocolHandlerTest_BCAPI(String s) {
		super(s);
		this.useJSSEClient = false;
		this.useJSSEServer = false;
	}
}

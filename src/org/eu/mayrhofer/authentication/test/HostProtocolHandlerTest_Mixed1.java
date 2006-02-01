package org.eu.mayrhofer.authentication.test;

public class HostProtocolHandlerTest_Mixed1 extends HostProtocolHandlerTest {
	public HostProtocolHandlerTest_Mixed1(String s) {
		super(s);
		this.useJSSEClient = true;
		this.useJSSEServer = false;
	}
}

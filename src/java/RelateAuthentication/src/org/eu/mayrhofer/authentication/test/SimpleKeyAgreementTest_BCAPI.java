package org.eu.mayrhofer.authentication.test;

public class SimpleKeyAgreementTest_BCAPI extends SimpleKeyAgreementTest {
	public SimpleKeyAgreementTest_BCAPI(String s) {
		super(s);
		this.useJSSE = false;
		this.useJSSE2 = false;
	}
}

package org.eu.mayrhofer.authentication.test;

public class SimpleKeyAgreementTest_Mixed1 extends SimpleKeyAgreementTest {
	public SimpleKeyAgreementTest_Mixed1(String s) {
		super(s);
		this.useJSSE = true;
		this.useJSSE2 = false;
	}
}

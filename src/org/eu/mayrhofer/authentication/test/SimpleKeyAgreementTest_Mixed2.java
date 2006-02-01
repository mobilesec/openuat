package org.eu.mayrhofer.authentication.test;

public class SimpleKeyAgreementTest_Mixed2 extends SimpleKeyAgreementTest {
	public SimpleKeyAgreementTest_Mixed2(String s) {
		super(s);
		this.useJSSE = false;
		this.useJSSE2 = true;
	}
}

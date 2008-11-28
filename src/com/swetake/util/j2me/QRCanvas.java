/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.swetake.util.j2me;


import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;


/**
 * Simple canvas to quickly display the QR Code result on J2ME. This canvas can be incorporated in a JFrame or JPanel and displayed in a J2SE application.
 * @author Iulia Ion
 *
 */
public class QRCanvas extends Canvas{
	/** The QR Code which is displayed on the canvas */
	boolean [][] qrCode;
	
	public QRCanvas(boolean [][] qrCode){
		this.qrCode = qrCode;
	}
	
	public QRCanvas() {
	}
	
	/**
	 * Automatically adapts to the phone display, to best fit the QR code. 
	 * Leaves a white margin equal to two times a square length in the QR code.
	 */
	public void paint(Graphics g) {

		//display dimensions
		int width = getWidth();
		int heigth = getHeight();
		//the size of the QR code
		int length = qrCode.length;
		//delete what was before
		g.setColor(255, 255, 255);
		g.fillRect(0, 0, width, heigth);
		//redraw
		g.setColor(0, 0, 0);

		int unit = Math.min(width/(length+4), heigth/(length+4));
		
		for (int i=0;i<qrCode.length;i++){
			for (int j=0;j<qrCode.length;j++){
				if (qrCode[j][i]) {
					g.fillRect(j*unit + 2*unit,i*unit +2*unit, unit, unit);
				}
			}
		}

	}
	
	public boolean[][] getQRCode() {
		return qrCode;
	}
	
	public void setQRCode(boolean[][] code) {
		this.qrCode = code;
	}

}
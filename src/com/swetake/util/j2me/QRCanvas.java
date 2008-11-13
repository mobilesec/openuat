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
 * Simple canvas to quickly display the QR Code result on J2SE
 * @author Iulia Ion
 *
 */
public class QRCanvas extends Canvas{
	boolean [][] qrCode;
	public QRCanvas(boolean [][] qrCode){
		this.qrCode = qrCode;
	}
    public QRCanvas() {
		// TODO Auto-generated constructor stub
	}
	public void paint(Graphics g) {
		//
		
	
	int width = getWidth();
	int heigth = getHeight();
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
			    	//System.out.print("X");
			    }
			    //else System.out.print("O");
			}
			//System.out.println();
		    }
    	
    }
	public boolean[][] getS() {
		return qrCode;
	}
	public void setS(boolean[][] s) {
		this.qrCode = s;
	}

}
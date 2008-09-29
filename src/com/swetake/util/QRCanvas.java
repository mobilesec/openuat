package com.swetake.util;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;

/**
 * Simple canvas to quickly display the QR Code result on J2SE
 * @author Iulia Ion
 *
 */
public class QRCanvas extends Canvas{
	boolean [][] s;
	public QRCanvas(boolean [][] qrcode){
		System.out.println("creating canvas");
		this.s = qrcode;
	}

	public void paint(Graphics g) {
		//g.setColor(Color.black);
		
//		System.out.println("paint called");
	    for (int i=0;i<s.length;i++){
			for (int j=0;j<s.length;j++){
			    if (s[j][i]) {
			    	g.fillRect(j*10+40,i*10+40,10,10);
//			    	System.out.println("printing canvas");
			    	//System.out.print("X");
			    }
			    //else System.out.print("O");
			}
			//System.out.println();
		    }
    	
    }
	public boolean[][] getS() {
		return s;
	}
	public void setS(boolean[][] s) {
		this.s = s;
	}

}
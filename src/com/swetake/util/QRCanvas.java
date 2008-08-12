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
		this.s = qrcode;
	}
    public QRCanvas() {
		// TODO Auto-generated constructor stub
	}
	public void paint(Graphics g) {
		//g.setColor(Color.black);
		

	    for (int i=0;i<s.length;i++){
			for (int j=0;j<s.length;j++){
			    if (s[j][i]) {
			    	g.fillRect(j*5+100,i*5+100,5,5);
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
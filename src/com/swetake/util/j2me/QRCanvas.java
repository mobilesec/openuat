package com.swetake.util.j2me;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;


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
		g.setColor(0, 0, 0);
		

	    for (int i=0;i<s.length;i++){
			for (int j=0;j<s.length;j++){
			    if (s[j][i]) {
			    	g.fillRect(j*10,i*10,10,10);
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
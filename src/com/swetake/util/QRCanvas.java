/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.swetake.util;

import java.awt.Canvas;
import java.awt.Graphics;

/**
 * Simple canvas to quickly display the QR Code result on J2SE
 * 
 * @author Iulia Ion
 * 
 */
public class QRCanvas extends Canvas {

	/** The graphical representation of the QR code to be displayed */
	private boolean[][] s;
	
	/** Number of white pixels to be left around the QR code */
	private static int border = 30;

	/** Number of white pixels in a sqare unit of the QR code */
	private static int size = 10;
	
	public QRCanvas(boolean[][] qrcode) {
		this.s = qrcode;
	}

	public void paint(Graphics g) {

		for (int i = 0; i < s.length; i++) {
			for (int j = 0; j < s.length; j++) {
				if (s[j][i]) {
					g.fillRect(j * size + border, i * size + border, size, size);
				}
			}
		}

	}

}
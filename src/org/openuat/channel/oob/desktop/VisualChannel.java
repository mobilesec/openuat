/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob.desktop;

import javax.swing.JPanel;

import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;

import com.swetake.util.QRCanvas;
import com.swetake.util.QRcodeGen;

/**
 * Displays the verification message as a QR barcode on the specified JPanel. Capturing functionality is not implemented.
 * @author Iulia Ion 
 */
public class VisualChannel implements OOBChannel {
	private JPanel pane;
	
	/**
	 * A short description (one to two words) of the channel,
	 * suitable to print on a gui element
	 * e.g. to list several channels to choose from.<br/>
	 * The <code>toString</code> method is overridden and will output this value.
	 */
	protected String shortDescription = "Visual channel";
	
	public VisualChannel() {

	}

	public void close() {
		pane.removeAll();
		pane.repaint();
	}


	public void capture() {
		// not yet implemented

	}

	public void setOOBMessageHandler(OOBMessageHandler handler) {

	}
	
	public String toString() {
		return shortDescription;
	}

/**
 * Generates the QR code and displays it to the user.
 * @param content The content of the QR code.
 * @param panel The panel on which to display it.
 * @return The QR canvas displayed on the panel.
 */
	private static QRCanvas generateAndShowQRCode(byte[] content, JPanel panel) {
		QRCanvas canvas = null;
		int version = 0;

		if (content.length < 15) {
			version = 1;
		} else if (content.length < 27) {
			version = 2;
		} else if (content.length < 43) {
			version = 3;
		} else {
			System.out.println("message length is too big - qr code generator");
		}

		QRcodeGen x = new QRcodeGen();
		x.setQrcodeErrorCorrect('M');
		x.setQrcodeEncodeMode('B');
		x.setQrcodeVersion(version);


		if (content.length > 0 && content.length < 120) {
			boolean[][] s = x.calQrcode(content);
//			System.out.println("calculated qr code");
			canvas = new QRCanvas(s);
			canvas.setSize(500, 500);
			canvas.setBackground(java.awt.Color.white);
			canvas.repaint();
			panel.add(canvas);

			
		}
		panel.repaint();
		return canvas;
	}

	

	public void transmit(byte[] message) {
		generateAndShowQRCode(message, pane);

	}

	public void setPane(JPanel pane) {
		this.pane = pane;
	}

}

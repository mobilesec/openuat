/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import javax.swing.JPanel;

import org.apache.log4j.Logger;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;

import com.swetake.util.QRCanvas;
import com.swetake.util.QRcodeGen;

/**
 * 
 * @author Iulia Ion 
 */
public class VisualChannel implements OOBChannel {

	Logger logger = Logger.getLogger(VisualChannel.class);
	OOBMessageHandler handler;

	public VisualChannel() {

	}

	public void close() {
//		frame.setVisible(false);
//		frame.dispose();
		pane.removeAll();
		pane.repaint();
	}

	//
	// public void handleDecodedText(Result theResult) {
	// // destroyApp(true);
	// stop();
	// handler.handleOOBMessage(OOBChannel.VIDEO_CHANNEL,
	// theResult.getText().getBytes());
	//		
	//		
	//		
	// }

	public void capture() {
		// not yet implemented

	}

	public void setOOBMessageHandler(OOBMessageHandler handler) {
		this.handler = handler;

	}

	// put this in a visual channel class
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

	JPanel pane;

	public void transmit(byte[] message) {
		generateAndShowQRCode(message, pane);

	}

	public void setPane(JPanel pane) {
		this.pane = pane;
	}

}

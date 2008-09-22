/*
 * Copyright 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openuat.channel.oob;

import javax.microedition.midlet.MIDlet;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.log4j.Logger;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;

import com.swetake.util.QRCanvas;
import com.swetake.util.QRcodeGen;

/**
 * <p>The actual reader application {@link MIDlet}.</p>
 *
 * @author Sean Owen (srowen@google.com)
 */
public class VisualChannel implements OOBChannel{

	
	private static JFrame frame;
	
	Logger logger = Logger.getLogger("");
	OOBMessageHandler handler;


	public VisualChannel() {

	}

	

	public void close() {
		frame.setVisible(false);
		frame.dispose();
	}


//
//	public void handleDecodedText(Result theResult) {
////		destroyApp(true);
//		stop();
//		handler.handleOOBMessage(OOBChannel.VIDEO_CHANNEL, theResult.getText().getBytes());
//		
//		
//		
//	}

	public void capture() {
		//not yet implemented

	}

	public void setOOBMessageHandler(OOBMessageHandler handler) {
		this.handler = handler;

	}
	//put this in a visual channel class
	private static void generateAndShowQRCode(byte [] content, JPanel panel) {
		
		int version = 0;
		
		if (content.length <15){
			version = 1;
		}else if (content.length <27){
			version = 2;
		}else if (content.length <43){
			version = 3;
		}else{
			System.out.println("message length is too big - qr code generator");
		}

		QRcodeGen x=new QRcodeGen();
		x.setQrcodeErrorCorrect('M');
		x.setQrcodeEncodeMode('B');
		x.setQrcodeVersion(version);

		System.out.println("hash length: "+content.length);

		if (content.length>0 && content.length <120){
			boolean[][] s = x.calQrcode(content);

			QRCanvas canvas =  new QRCanvas(s);
//			canvas.setSize(1000, 1000);
			canvas.setBackground(java.awt.Color.white);

			panel.add(canvas);
			canvas.repaint();
		}
		panel.repaint();
	}
	
	JPanel pane;
	public void transmit(byte[] message) {
		generateAndShowQRCode(message, pane);
		
	}
	
	public void setPane(JPanel pane) {
		this.pane = pane;
	}
	

}

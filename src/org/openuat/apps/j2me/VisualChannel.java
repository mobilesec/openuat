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

package org.openuat.apps.j2me;

import java.io.IOException;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.VideoControl;
import javax.microedition.midlet.MIDlet;

import org.apache.log4j.Logger;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;

import com.google.zxing.Result;
import com.google.zxing.client.j2me.AdvancedMultimediaManager;
import com.google.zxing.client.j2me.VideoCanvas;
import com.swetake.util.j2me.QRCanvas;
import com.swetake.util.j2me.QRcodeGen;

/**
 * <p>The actual reader application {@link MIDlet}.</p>
 *
 * @author Sean Owen (srowen@google.com)
 */
public class VisualChannel implements OOBChannel{

	protected Canvas canvas;
	protected Player player;
	protected VideoControl videoControl;
	Logger logger = Logger.getLogger("");
	OOBMessageHandler handler;

	private OpenUATmidlet mainProgram;
	Displayable getCanvas() {
		return canvas;
	}

	public Player getPlayer() {
		return player;
	}

	public VideoControl getVideoControl() {
		return videoControl;
	}

	public VisualChannel(OpenUATmidlet mainProgram) {
		this.mainProgram = mainProgram;



	}

	protected static Player createPlayer() throws IOException, MediaException {
		// Try a workaround for Nokias, which want to use capture://image in some cases
		Player player = null;
		String platform = System.getProperty("microedition.platform");
		if (platform != null && platform.indexOf("Nokia") >= 0) {
			try {
				player = Manager.createPlayer("capture://image");
			} catch (MediaException me) {
				// if this fails, just continue with capture://video
			} catch (Error e) {
				// Ugly, but, it seems the Nokia N70 throws "java.lang.Error: 136" here
				// We should still try to continue
			}
		}
		if (player == null) {
			player = Manager.createPlayer("capture://video");
		}
		return player;
	}

	protected void pauseApp() {
		if (player != null) {
			try {
				player.stop();
			} catch (MediaException me) {
				// continue?
				showError(me);
			}
		}
	}

	public void destroyApp(boolean unconditional) {
		if (player != null) {
			videoControl = null;
			try {
				player.stop();
			} catch (MediaException me) {
				// continue
			}
			player.deallocate();
			player.close();
			player = null;
			
		}
	}



	private void showAlert(String title, String text) {
		Alert alert = new Alert(title, text, null, AlertType.INFO);
		alert.setTimeout(Alert.FOREVER);
		showAlert(alert);
	}

	public void showError(Throwable t) {
		String message = t.getMessage();
		if (message != null && message.length() > 0) {
			showError(message);
		} else {
			showError(t.toString());
		}
	}

	public void showError(String message) {
		showAlert(new Alert("Error", message, null, AlertType.ERROR));
	}

	private void showAlert(Alert alert) {
		Display display = Display.getDisplay(mainProgram);
		display.setCurrent(alert, canvas);
	}

	public void handleDecodedText(Result theResult) {
//		destroyApp(true);
		stop();
		handler.handleOOBMessage(OOBChannel.VIDEO_CHANNEL, theResult.getText().getBytes());
		
		
		
	}

	public void capture() {
		try{
			player = createPlayer();
			player.realize();
			AdvancedMultimediaManager.setZoom(player);
			AdvancedMultimediaManager.setExposure(player);
			videoControl = (VideoControl) player.getControl("VideoControl");
			canvas = new VideoCanvas(this);
			canvas.setFullScreenMode(true);
			videoControl.initDisplayMode(VideoControl.USE_DIRECT_VIDEO, canvas);
			videoControl.setDisplayLocation(0, 0);
			videoControl.setDisplaySize(canvas.getWidth(), canvas.getHeight());
			videoControl.setVisible(true);
			player.start();
			Display.getDisplay(mainProgram).setCurrent(canvas);
		}catch(MediaException e){
			logger.error(e);
		} catch (IOException e) {
			logger.error(e);
		}

	}

	public void setOOBMessageHandler(OOBMessageHandler handler) {
		this.handler = handler;

	}

	public void transmit(byte[] message) {
		
		int version = 0;
		
		if (message.length <15){
			version = 1;
		}else if (message.length <27){
			version = 2;
		}else if (message.length <43){
			version = 3;
		}else{
			logger.error("Visual channel - transmit- Message length is too big");
			showError("QR code too big");
			return;
		}
		
		
		logger.info("transmitting");
		QRcodeGen x = new QRcodeGen(mainProgram);
		logger.info("made qr code class");
		x.setQrcodeErrorCorrect('M');
		x.setQrcodeEncodeMode('B');
		x.setQrcodeVersion(version);

		logger.info("computing code - lenght:"+ message.length);
		boolean[][] s = x.calQrcode(message);
		logger.info("done computing");
		if(s==null){logger.warn("qr encode is null");
		
		}else {
			logger.info("computed code");
		}
		try{
		
		QRCanvas qrcanvas = new QRCanvas(s);
		logger.info("showing visual code");
		qrcanvas.repaint();
		qrcanvas.addCommand(mainProgram.back);
		qrcanvas.setCommandListener(mainProgram);
		Display.getDisplay (mainProgram).setCurrent ( qrcanvas );
		}catch(Exception e){
			logger.error(e);
		}

	}

	public void stop() {
		destroyApp(true);
		//Display.getDisplay (mainProgram).setCurrent ( mainProgram.main_list );
	}

}

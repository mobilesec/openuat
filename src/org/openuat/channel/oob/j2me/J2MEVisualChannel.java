/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.openuat.channel.oob.j2me;

import java.io.IOException;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.VideoControl;

import org.apache.log4j.Logger;
import org.openuat.authentication.OOBChannel;
import org.openuat.authentication.OOBMessageHandler;

import com.google.zxing.Result;
import com.google.zxing.client.j2me.DefaultMultimediaManager;
import com.google.zxing.client.j2me.MultimediaManager;
import com.google.zxing.client.j2me.VideoCanvas;
import com.swetake.util.j2me.QRCanvas;
import com.swetake.util.j2me.QRcodeGen;

/**
 * Implements the visual out of band channel for mobile devices.
 * To transmit, it encodes and displays a message as a QR code.
 * To receive, it takes a picture and decodes the QR code displayed by the other device.
 * @author Iulia Ion
 *
 */
public class J2MEVisualChannel implements OOBChannel{

	protected Canvas canvas;
	protected Player player;
	protected VideoControl videoControl;
	private Logger logger = Logger.getLogger("org.openuat.channel.oob.j2me.J2MEVisualChannel");
	private OOBMessageHandler handler;
	
	private Display display;
	private Class appClass;
	private CommandListener appListener;
	private Command appBack, appSuccess, appFailure;

	Displayable getCanvas() {
		return canvas;
	}

	public Player getPlayer() {
		return player;
	}

	public VideoControl getVideoControl() {
		return videoControl;
	}

	public J2MEVisualChannel(Display display, Class appClass,
			CommandListener listener,
			Command back, Command success, Command failure) {
		this.display = display;
		this.appClass = appClass;
		this.appListener = listener;
		this.appBack = back;
		this.appSuccess = success;
		this.appFailure = failure;
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

	public void close(@SuppressWarnings("unused") boolean unconditional) {
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
		display.setCurrent(alert, canvas);
	}

	public void handleDecodedText(Result theResult) {
//		remove the QR from the display if necesary
		stop();
		// inform the handler
		handler.handleOOBMessage(OOBChannel.VIDEO_CHANNEL, theResult.getText().getBytes());
		
		
		
	}

	public void capture() {
		try{
			player = createPlayer();
			player.realize();
		      MultimediaManager multimediaManager = new DefaultMultimediaManager();
		      multimediaManager.setZoom(player);
		      multimediaManager.setExposure(player);
			videoControl = (VideoControl) player.getControl("VideoControl");
			canvas = new VideoCanvas(this);
			canvas.setFullScreenMode(true);
			videoControl.initDisplayMode(VideoControl.USE_DIRECT_VIDEO, canvas);
			videoControl.setDisplayLocation(0, 0);
			videoControl.setDisplaySize(canvas.getWidth(), canvas.getHeight());
			videoControl.setVisible(true);
			player.start();
			display.setCurrent(canvas);
		}catch(MediaException e){
			logger.error(e);
		} catch (IOException e) {
			logger.error(e);
		}

	}

	public void setOOBMessageHandler(OOBMessageHandler handler) {
		this.handler = handler;

	}

	/**
	 * Displays a QR code enconding the message.
	 * @param message The message to be displayed as QR.
	 */
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
		QRcodeGen x = new QRcodeGen(display, appClass);
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
		qrcanvas.addCommand(appBack);
		qrcanvas.addCommand(appSuccess);
		qrcanvas.addCommand(appFailure);
		qrcanvas.setCommandListener(appListener);
		display.setCurrent ( qrcanvas );
		}catch(Exception e){
			logger.error(e);
		}

	}

	public void stop() {
		close(true);
		//Display.getDisplay (mainProgram).setCurrent ( mainProgram.main_list );
	}

}

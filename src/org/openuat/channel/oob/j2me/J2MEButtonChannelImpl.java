/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob.j2me;

import java.io.IOException;
import java.util.Vector;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

import org.openuat.channel.oob.ButtonChannel;
import org.openuat.channel.oob.ButtonChannelImpl;
import org.openuat.channel.oob.ButtonInputHandler;
import org.openuat.log.LogFactory;
import org.openuat.util.RgbColor;

/**
 * This is a J2ME specific implementation of
 * the {@link ButtonChannelImpl} class.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class J2MEButtonChannelImpl extends ButtonChannelImpl {
	
	/**
	 * Main applications <code>Display</code>.
	 */
	protected Display display;
	
	/**
	 * Current gui element displayed on screen.
	 */
	protected Canvas currentScreen;
	
	/**
	 * Allows the user to cancel the currently displayed screen.
	 */
	protected Command abortCommand;
	
	/**
	 * This <code>CommandListener</code> will be invoked whenever
	 * the user aborts the current transmission or capture process.
	 */
	protected CommandListener abortHandler;
	
	/**
	 *  Default font when drawing text on the screen.
	 */
	protected Font defaultFont;

	/**
	 * Creates a new instance.
	 * @param display The main applications <code>Display</code>.
	 * @param abortHandler It will be invoked when the user aborts the
	 * current transmission or capture process. Note: this listener should
	 * only react to events of type <code>Command.STOP</code>.<br/>
	 * Example:
	 * <pre>
	 * 	public void commandAction(Command command, Displayable displayable) {
	 *		if (command.getCommandType() == Command.STOP) {
	 *			// abort current processing...
	 *		}
	 *	}
	 * </pre>
	 */
	public J2MEButtonChannelImpl(Display display, CommandListener abortHandler) {
		transmissionMode	= 0;
		progress			= 0;
		signalCount			= 0;
		showSignal			= false;
		showCount			= false;
		intervalList		= null;
		currentScreen		= null;
		this.display		= display;
		this.abortHandler	= abortHandler;
		logger 			= LogFactory.getLogger("org.openuat.channel.oob.j2me.J2MEButtonChannelImpl");
		abortCommand	= new Command("Abort", Command.STOP, 1);
		defaultFont 	= Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
	}
	
	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#repaint()
	 */
	// @Override
	public void repaint() {
		if (currentScreen != null) {
			currentScreen.repaint();
		}
		else {
			logger.warn("Method repaint(): currentScreen is null.");
		}
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showCaptureGui
	 */
	// @Override
	public void showCaptureGui(String text, ButtonInputHandler inputHandler) {
		signalCount = 0;
		currentScreen = new CaptureGui(text, inputHandler);
		currentScreen.setFullScreenMode(false);
		currentScreen.addCommand(abortCommand);
		currentScreen.setCommandListener(abortHandler);
		
		// make currentScreen the active Displayable
		display.setCurrent(currentScreen);
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showTransmitGui
	 */
	// @Override
	public void showTransmitGui(String text, int type) {
		transmissionMode = type;
		signalCount = 0;
		currentScreen = new TransmitGui(text);
		currentScreen.setFullScreenMode(false);
		currentScreen.addCommand(abortCommand);
		currentScreen.setCommandListener(abortHandler);

		// make currentScreen the active Displayable
		display.setCurrent(currentScreen);
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#vibrate
	 */
	// @Override
	public void vibrate(int milliseconds) {
		display.vibrate(milliseconds);
	}

	
	/* Helper method to split a string to fit on the canvas.
	 * Returns a Vector<String>.
	 */
	private Vector splitStringByFont(String in, Font font, int maxSize) {
		Vector result = new Vector();
		// first split on newline characters
		// always take '\n', since system property line.separator doesn't exist
		Vector lines = new Vector();
		int lower = 0;
		int index = 0;
		while (lower < in.length()) {
			index = in.indexOf('\n', lower);
			if (index == -1) {
				index = in.length();
			}
			lines.addElement(in.substring(lower, index));
			lower = index + 1;
		}
		// now split the lines on space characters (' ')
		for (int i = 0; i < lines.size(); i++) {
			String line = (String)lines.elementAt(i);
			String current = "";
			String sub = "";
			lower = 0;
			index = 0;
			while (lower < line.length()) {
				index = line.indexOf(' ', lower);
				if (index == -1) {
					index = line.length();
				}
				sub = line.substring(lower, index);
				if (font.stringWidth(current + " " + sub) > maxSize) {
					result.addElement(current.trim());
					current = sub;
				}
				else {
					current = current + " " + sub;
				}
				lower = index + 1;
			}
			result.addElement(current.trim());
		}
		return result;
	}
	

	/*
	 * Private helper/wrapper class to launch the capture gui.
	 */
	private class CaptureGui extends Canvas {
		
		/*
		 * Constructor for this class.
		 */
		public CaptureGui(String displayText, ButtonInputHandler handler) {
			super();
			this.displayText	= displayText;
			this.inputHandler	= handler;
			marginLeft	= 10;
			marginTop	= 10;
			
			try {
				buttonPress = Image.createImage("/button_press.png");
			} catch (IOException ioe) {
				buttonPress = null;
				logger.warn("Could not create image: button press", ioe);
			}
		}
		
		/* Text to display on gui */
		private String displayText;
		
		/* Delegate key events to the input handler */
		private ButtonInputHandler inputHandler;
		
		/* Margin values when drawing text on the screen */
		private int marginLeft;
		private int marginTop;
		
		/* An image to visualize the input functionality */
		private Image buttonPress;

		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#paint(Graphics)
		 */
		// @Override
		protected void paint(Graphics g) {
			g.setColor(RgbColor.WHITE);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
			g.setColor(RgbColor.BLACK);
			g.setFont(defaultFont);
			Vector lines = splitStringByFont(displayText, defaultFont, this.getWidth()- 2*marginLeft);
			int mTop = marginTop;
			for (int i = 0; i < lines.size(); i++) {
				String line = (String)lines.elementAt(i);
				g.drawString(line, marginLeft, mTop, Graphics.TOP|Graphics.LEFT);
				mTop += defaultFont.getHeight();
			}
			if (showCount) {
				// draw signal count
				String eventCount = "Button events: " 
					+ signalCount + "/" + ButtonChannel.TOTAL_SIGNAL_COUNT;
				g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
				mTop += defaultFont.getHeight();
				g.drawString(eventCount, marginLeft, mTop, Graphics.TOP|Graphics.LEFT);
				mTop += g.getFont().getHeight();
				
				// draw progress wheel
				g.setColor(RgbColor.BLUE);
				int h = defaultFont.getHeight() * 2;
				int xRef = this.getWidth() / 2 - h;
				int yRef = mTop + defaultFont.getHeight();
				
				int cx = xRef + h;
				int cy = yRef + h;
				double angle = 2 * Math.PI / ButtonChannel.TOTAL_SIGNAL_COUNT;
				double qx =	 Math.sin(angle / 2) * h;
				double qy = -Math.cos(angle / 2) * h;
				double px = -qx;
				double py =  qy;
				
				for (int i = 0; i < signalCount; i++) {
					g.fillTriangle(cx, cy, cx + (int)px, cy + (int)py, cx + (int)qx, cy + (int)qy);
					px = qx;
					py = qy;
					qx = px * Math.cos(angle) - py * Math.sin(angle);
					qy = px * Math.sin(angle) + py * Math.cos(angle);
				}
				
			}
			if (!showCount || signalCount <= 0) {
				int imgHeight = buttonPress != null ? buttonPress.getHeight() : 0;
				int imgMarginTop = (this.getHeight() - mTop - imgHeight) / 2 + mTop;
				g.drawImage(buttonPress, this.getWidth() / 2, imgMarginTop, Graphics.TOP|Graphics.HCENTER);
			}
		}

		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#keyPressed(int)
		 */
		// @Override
		protected void keyPressed(int keyCode) {
			if (this.getGameAction(keyCode) == Canvas.FIRE) {
				inputHandler.buttonPressed(System.currentTimeMillis());
			}
		}

		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#keyReleased(int)
		 */
		// @Override
		protected void keyReleased(int keyCode) {
			if (this.getGameAction(keyCode) == Canvas.FIRE) {
				inputHandler.buttonReleased(System.currentTimeMillis());
			}
		}
	}
	
	
	/*
	 * Private helper/wrapper class to launch the transmit gui.
	 */
	private class TransmitGui extends Canvas {
				
		/* Text to display before transmission starts */
		private String displayText;
		
		/* Margin values when drawing text on the screen */
		private int textMarginLeft;
		private int textMarginTop;
		
		/* Margin value when drawing the signals */
		private int signalMargin;
		private int prepSignalMargin;
		
		/* Margin (left and right) when drawing the progress bar. Bar height. */
		private int proBarMargin;
		private int proBarHeight;
		
		/* Progress bar width and margin values that can be precomputed
		 * and don't need to be computed every time in the paint method anew */		
		private int proBarMarginTop;
		private int proBarWidth;
		
		/* Power bar vertical margin and minimum width */
		private int powBarMarginTop;
		private int powBarMaxWidth;

		/* Power bar height, can be precomputed */
		private int powBarHeight;
		
		/* Traffic light images */
		private Image trafficLightRed;
		private Image trafficLightYellow;
		private Image trafficLightGreen;
		
		/* Transmit image */
		private Image phoneTransmit;
		
		/*
		 * Constructor for this class.
		 */
		public TransmitGui(String displayText) {
			super();
			this.displayText	= displayText;
			textMarginLeft		= 10;
			textMarginTop		= 10;
			signalMargin		= 30;
			prepSignalMargin	= 50;
			proBarMargin		= 5;
			proBarHeight		= 30;
			proBarMarginTop 	= (this.getHeight() - proBarHeight) / 2;
			proBarWidth 		= this.getWidth() - 2 * proBarMargin;
			powBarMarginTop		= 15;
			powBarMaxWidth		= 20;
			powBarHeight		= this.getHeight() - 2 * powBarMarginTop;
			
			// load images
			try {
				trafficLightRed = Image.createImage("/Traffic_lights_dark_red.png");
			} catch (IOException ioe) {
				trafficLightRed = null;
				logger.warn("Could not create image: red traffic light", ioe);
			}
			try {
				trafficLightYellow = Image.createImage("/Traffic_lights_dark_yellow.png");
			} catch (IOException ioe) {
				trafficLightYellow = null;
				logger.warn("Could not create image: yellow traffic light", ioe);
			}
			try {
				trafficLightGreen = Image.createImage("/Traffic_lights_dark_green.png");
			} catch (IOException ioe) {
				trafficLightGreen = null;
				logger.warn("Could not create image: green traffic light", ioe);
			}
			try {
				phoneTransmit = Image.createImage("/phone_sending.png");
			} catch (IOException ioe) {
				phoneTransmit = null;
				logger.warn("Could not create image: sending phone", ioe);
			}
		}
		
		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#paint(javax.microedition.lcdui.Graphics)
		 */
		// @Override
		protected void paint(Graphics g) {
			// clear the screen first
			g.setColor(RgbColor.WHITE);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
			String signalCountText = "Signals sent: " 
				+ signalCount + "/" + ButtonChannel.TOTAL_SIGNAL_COUNT;
			
			if (transmissionMode == ButtonChannelImpl.TRANSMIT_PLAIN) {
				paintPlain(g, signalCountText);
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_SIGNAL) {
				paintSignal(g, signalCountText);
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_TRAFFIC_LIGHT) {
				paintTrafficLight(g, signalCountText);
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_BAR) {
				if (intervalList != null) {
					paintBar(g);
				}
				else {
					logger.warn("Method paint(): 'intervalList' is null.");
				}
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_VERT_BARS) {
				if (intervalList != null) {
					paintVertBars(g);
				}
				else {
					logger.warn("Method paint(): 'intervalList' is null.");
				}
			}
			else {
				logger.warn("Method paint(): Unknown 'transmissionMode': " + transmissionMode);
			}
		}
		
		/* Painting method for transmission mode TRANSMIT_PLAIN */
		private void paintPlain(Graphics g, String signalCountText) {
			g.setColor(RgbColor.BLACK);
			g.setFont(defaultFont);
			Vector lines = splitStringByFont(displayText, defaultFont, this.getWidth()- 2*textMarginLeft);
			int mTop = textMarginTop;
			for (int i = 0; i < lines.size(); i++) {
				String line = (String)lines.elementAt(i);
				g.drawString(line, textMarginLeft, mTop, Graphics.TOP|Graphics.LEFT);
				mTop += defaultFont.getHeight();
			}
			if (showCount) {
				mTop += defaultFont.getHeight();
				g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
				g.drawString(signalCountText, textMarginLeft, mTop, Graphics.TOP|Graphics.LEFT);
				mTop += g.getFont().getHeight();
			}
			int imgHeight = phoneTransmit != null ? phoneTransmit.getHeight() : 0;
			int imgMarginTop = (this.getHeight() - mTop - imgHeight) / 2 + mTop;
			g.drawImage(phoneTransmit, this.getWidth() / 2, imgMarginTop, Graphics.TOP|Graphics.HCENTER);
		}
		
		/* Painting method for transmission mode TRANSMIT_SIGNAL */
		private void paintSignal(Graphics g, String signalCountText) {
			int marginTop = 0;
			if (showCount) {
				marginTop = textMarginTop + defaultFont.getHeight();
				g.setColor(RgbColor.BLACK);
				g.setFont(defaultFont);
				g.drawString(signalCountText, textMarginLeft, textMarginTop, Graphics.TOP|Graphics.LEFT);
			}
			// the 'real' signal has always precedence over the preparatory signal
			if (showSignal) {
				// the signal is just a simple rectangle, painted black
				g.setColor(RgbColor.BLACK);
				int rectWidth = this.getWidth() - 2 * signalMargin;
				int rectHeight = this.getHeight() - 2 * signalMargin - marginTop;
				g.fillRect(signalMargin, signalMargin + marginTop, rectWidth, rectHeight);
			}
			else if (prepareSignal) {
				// the preparatory signal is a smaller rectangle, painted gray
				g.setColor(RgbColor.LIGHT_GRAY);
				int rectWidth = this.getWidth() - 2 * prepSignalMargin;
				int rectHeight = this.getHeight() - 2 * prepSignalMargin - marginTop;
				g.fillRect(prepSignalMargin, prepSignalMargin + marginTop, rectWidth, rectHeight);
				
			}
		}
		
		/* Painting method for transmission mode TRANSMIT_TRAFFIC_LIGHT */
		private void paintTrafficLight(Graphics g, String signalCountText) {
			int marginTop = 0;
			if (showCount) {
				marginTop = textMarginTop + defaultFont.getHeight();
				g.setColor(RgbColor.BLACK);
				g.setFont(defaultFont);
				g.drawString(signalCountText, textMarginLeft, textMarginTop,
						Graphics.TOP|Graphics.LEFT);
			}
			// the 'real' signal has always precedence over the preparatory signal
			// i.e. green light wins over yellow light
			Image currentState = trafficLightRed;
			if (showSignal) {
				currentState = trafficLightGreen;
			}
			else if (prepareSignal) {
				currentState = trafficLightYellow;
			}
			g.drawImage(currentState, this.getWidth() / 2, signalMargin + marginTop,
						Graphics.TOP|Graphics.HCENTER);
		}
		
		/* Painting method for transmission mode TRANSMIT_BAR */
		private void paintBar(Graphics g) {
			int marginLeft = proBarMargin;
			for (int i = 0; i < intervalList.size(); i++) {
				int intervalWidth = (int)((double)intervalList.item(i) 
							/ intervalList.getTotalIntervalLength() * proBarWidth);
				if (i % 2 == 0) {
					g.setColor(RgbColor.LIGHT_GRAY);
				}
				else {
					g.setColor(RgbColor.DARK_GREEN);
				}
				g.fillRect(marginLeft, proBarMarginTop, intervalWidth, proBarHeight);
				marginLeft += intervalWidth;
			}
			g.setColor(RgbColor.BLACK);
			int progressWidth = (int)(proBarWidth / 100.0d * progress);
			g.fillRect(proBarMargin, proBarMarginTop, progressWidth, proBarHeight);
		}
		
		/* Painting method for transmission mode TRANSMIT_VERT_BARS */
		private void paintVertBars(Graphics g) {
			int barCount = (intervalList.size() + 1) / 2;
			int powBarWidth = Math.min(powBarMaxWidth, this.getWidth() / (2*barCount + 1));
			int powBarMargin = Math.max(powBarWidth, 
						(this.getWidth() - (2*barCount - 1) * powBarWidth) / 2);
			int maxDblInterval = 0;
			
			for (int i = 0; i < intervalList.size(); i += 2){
				int interval1 = intervalList.item(i);
				int interval2 = (i+1 < intervalList.size()) ? intervalList.item(i+1) : 0;
				if (interval1 + interval2 > maxDblInterval) {
					maxDblInterval = interval1 + interval2;
				}
			}
			
			// draw the different bars
			int marginLeft = powBarMargin;
			for (int i = 0; i < intervalList.size(); i += 2) {
				int intervalLength = (int)((double)intervalList.item(i) / maxDblInterval * powBarHeight);
				int marginTop = this.getHeight() - powBarMarginTop - intervalLength;
				g.setColor(RgbColor.LIGHT_GRAY);
				g.fillRect(marginLeft, marginTop, powBarWidth, intervalLength);
				
				if (i + 1 < intervalList.size()) {
					intervalLength = (int)((double)intervalList.item(i+1) / maxDblInterval * powBarHeight);
					marginTop -= intervalLength;
					g.setColor(RgbColor.DARK_GREEN);
					g.fillRect(marginLeft, marginTop, powBarWidth, intervalLength);
				}
				
				marginLeft += 2 * powBarWidth;
			}
			
			// draw progress
			marginLeft = powBarMargin;
			g.setColor(RgbColor.BLACK);
			double spacialProgress = 0.0d;
			for (int i = 0; i < intervalList.size(); i += 2) {
				if (progress < spacialProgress) {
					break;
				}
				
				int dblInterval = intervalList.item(i);
				if (i + 1 < intervalList.size()) {
					dblInterval += intervalList.item(i + 1);
				}
				
				int intervalWidth = 0;
				double intervalProgress = (double)dblInterval / intervalList.getTotalIntervalLength() * 100.0d;
				if (spacialProgress + intervalProgress < progress) {
					intervalWidth = (int)((double)dblInterval / maxDblInterval * powBarHeight);
				}
				else if (spacialProgress < progress) {
					double tempInterval = intervalList.getTotalIntervalLength() / 100.d * (progress - spacialProgress); 
					intervalWidth = (int)(tempInterval / maxDblInterval * powBarHeight);
				}
				
				g.fillRect(marginLeft, this.getHeight() - powBarMarginTop - intervalWidth, powBarWidth, intervalWidth);
				
				spacialProgress += intervalProgress;
				marginLeft += 2 * powBarWidth;
			}
		}
			
	}
	
}

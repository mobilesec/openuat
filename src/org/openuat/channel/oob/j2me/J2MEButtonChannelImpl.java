/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob.j2me;

import java.util.Vector;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

import org.openuat.channel.oob.ButtonChannelImpl;
import org.openuat.channel.oob.ButtonInputHandler;
import org.openuat.util.RgbColor;

/**
 * This is a J2ME specific implementation of
 * the {@link ButtonChannelImpl} class.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class J2MEButtonChannelImpl extends ButtonChannelImpl implements CommandListener {

	/**
	 * Creates a new instance.
	 * @param display main applications <code>Display</code>
	 */
	public J2MEButtonChannelImpl(Display display) {
		transmissionMode	= 0;
		progress			= 0;
		showSignal			= false;
		intervalList		= null;
		currentScreen		= null;
		this.display		= display;
		abortCommand	= new Command("Abort", Command.STOP, 1);
		defaultFont 	= Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
	}
	
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
	 *  Default font when drawing text on the screen.
	 */
	protected Font defaultFont;

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#repaint()
	 */
	// @Override
	public void repaint() {
		if (currentScreen != null) {
			currentScreen.repaint();
		}
		else {
			// TODO: log warning
			// logger.warn("Method repaint(): currentScreen is null.");
		}
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showCaptureGui
	 */
	// @Override
	public void showCaptureGui(String text, ButtonInputHandler inputHandler) {
		currentScreen = new CaptureGui(text, inputHandler);
		currentScreen.setFullScreenMode(false);
		currentScreen.addCommand(abortCommand);
		currentScreen.setCommandListener(this);
		
		// make currentScreen the active Displayable
		display.setCurrent(currentScreen);
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showTransmitGui
	 */
	// @Override
	public void showTransmitGui(String text, int type) {
		transmissionMode = type;
		currentScreen = new TransmitGui(text);
		currentScreen.setFullScreenMode(false);
		currentScreen.addCommand(abortCommand);
		currentScreen.setCommandListener(this);

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
	
	/* (non-Javadoc)
	 * @see javax.microedition.lcdui.CommandListener#commandAction(javax.microedition.lcdui.Command, javax.microedition.lcdui.Displayable)
	 */
	// @Override
	public void commandAction(Command command, Displayable displayable) {
		if (command.getCommandType() == Command.STOP) {
			// TODO: abort current processing...
		}
		else {
			// TODO: log warning
			// logger.warn("Command not handled: "+command.getLabel());
		}
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
		}
		
		/* Text to display on gui */
		private String displayText;
		
		/* Delegate key events to the input handler */
		private ButtonInputHandler inputHandler;
		
		/* Margin values when drawing text on the screen */
		private int marginLeft;
		private int marginTop;

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
		}

		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#keyPressed(int)
		 */
		// @Override
		protected void keyPressed(int keyCode) {
			if (this.getGameAction(keyCode) == Canvas.FIRE) {
				inputHandler.buttonPressed();
			}
		}

		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#keyReleased(int)
		 */
		// @Override
		protected void keyReleased(int keyCode) {
			if (this.getGameAction(keyCode) == Canvas.FIRE) {
				inputHandler.buttonReleased();
			}
		}
	}
	
	
	/*
	 * Private helper/wrapper class to launch the transmit gui.
	 */
	private class TransmitGui extends Canvas {
		
		/*
		 * Constructor for this class.
		 */
		public TransmitGui(String displayText) {
			super();
			this.displayText = displayText;
			textMarginLeft	= 10;
			textMarginTop	= 10;
			signalMargin	= 30;
			barMargin		= 10;
			barHeight		= 40;
		}
		
		/* Text to display before transmission starts */
		private String displayText;
		
		/* Margin values when drawing text on the screen */
		private int textMarginLeft;
		private int textMarginTop;
		
		/* Margin value when drawing the signal */
		private int signalMargin;
		
		/* Margin (left and right) when drawing the progress bar. Bar height. */
		private int barMargin;
		private int barHeight;
		
		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#paint(javax.microedition.lcdui.Graphics)
		 */
		// @Override
		protected void paint(Graphics g) {
			// clear the screen first
			g.setColor(RgbColor.WHITE);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
			
			if (transmissionMode == ButtonChannelImpl.TRANSMIT_PLAIN) {
				g.setColor(RgbColor.BLACK);
				g.setFont(defaultFont);
				Vector lines = splitStringByFont(displayText, defaultFont, this.getWidth()- 2*textMarginLeft);
				int mTop = textMarginTop;
				for (int i = 0; i < lines.size(); i++) {
					String line = (String)lines.elementAt(i);
					g.drawString(line, textMarginLeft, mTop, Graphics.TOP|Graphics.LEFT);
					mTop += defaultFont.getHeight();
				}
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_SIGNAL) {
				if (showSignal) {
					// the signal is just a simple rectangle, painted black
					g.setColor(RgbColor.BLACK);
					int rectWidth = this.getWidth() - 2 * signalMargin;
					int rectHeight = this.getHeight() - 2 * signalMargin;
					g.fillRect(signalMargin, signalMargin, rectWidth, rectHeight);
				}
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_BAR) {
				if (intervalList != null) {
					int marginTop = (this.getWidth() - barHeight) / 2;
					int marginLeft = barMargin;
					int barWidth = this.getWidth() - 2 * barMargin;
					for (int i = 0; i < intervalList.size(); i++) {
						int intervalWidth = (int)((double)intervalList.item(i) / (double)intervalList.getTotalIntervalLength() * barWidth);
						if (i == 0) {
							g.setColor(RgbColor.LIGHT_GRAY);
						}
						else if (i % 2 == 0) {
							g.setColor(RgbColor.DARK_RED);
						}
						else {
							g.setColor(RgbColor.LIGHT_RED);
						}
						g.fillRect(marginLeft, marginTop, intervalWidth, barHeight);
						marginLeft += intervalWidth;
					}
					g.setColor(RgbColor.BLACK);
					int progressWidth = (int)((this.getWidth() - 2*barMargin) / 100.0d * progress);
					g.fillRect(barMargin, marginTop, progressWidth, barHeight);
				}
				else {
					// TODO: log warning
					// logger.warn("Method paint(): 'intervalList' is null");
				}
			}
			else {
				// TODO: log warning
				// logger.warn("Method paint(): Unknown 'transmissionMode': " + transmissionMode);
			}
		}
	}
	
}

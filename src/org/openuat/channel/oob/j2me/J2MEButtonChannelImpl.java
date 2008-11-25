/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob.j2me;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Graphics;

import org.openuat.channel.oob.ButtonChannelImpl;
import org.openuat.channel.oob.ButtonInputHandler;

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
	 * 
	 * @param display main applications <code>Display</code>
	 */
	public J2MEButtonChannelImpl(Display display) {
		this.display = display;
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

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#repaint()
	 */
	@Override
	public void repaint() {
		if (currentScreen != null) {
			currentScreen.repaint();
		}
		else {
			// TODO: log warning
			// logger.warn("Call to repaint(), but currentScreen is null.");
		}
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showCaptureGui
	 */
	@Override
	public void showCaptureGui(String text, ButtonInputHandler inputHandler) {
		abortCommand = new Command("Abort", Command.STOP, 1);
		currentScreen = new CaptureGui(text, inputHandler);
		currentScreen.addCommand(abortCommand);
		currentScreen.setCommandListener(this);
		
		// make currentScreen the active Displayable
		display.setCurrent(currentScreen);
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showTransmitGui
	 */
	@Override
	public void showTransmitGui(String text, int type) {
		transmissionMode = type;
		// TODO: show gui
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#vibrate
	 */
	@Override
	public void vibrate(int milliseconds) {
		display.vibrate(milliseconds);
	}
	
	/* (non-Javadoc)
	 * @see javax.microedition.lcdui.CommandListener#commandAction(javax.microedition.lcdui.Command, javax.microedition.lcdui.Displayable)
	 */
	@Override
	public void commandAction(Command command, Displayable displayable) {
		if (command.getCommandType() == Command.STOP) {
			// TODO: abort current processing...
		}
		else {
			// TODO: log warning
			// logger.warn("Command not handled: "+command.getLabel());
		}
	}



	/*
	 * Private helper/wrapper class to launch the capture gui.
	 */
	private class CaptureGui extends Canvas {
		
		/*
		 * Constructor for this class.
		 */
		public CaptureGui(String displayText, ButtonInputHandler handler) {
			this.displayText = displayText;
			this.inputHandler = handler;
			marginLeft	= 10;
			marginTop	= 10;
		}
		
		/* Text to display on gui */
		private String displayText;
		
		/* Delegate key events to the input handler */
		private ButtonInputHandler inputHandler;
		
		/* Margin values when drawing on the screen */
		private int marginLeft;
		private int marginTop;
		
		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#paint(Graphics)
		 */
		@Override
		protected void paint(Graphics g) {
			g.drawString(displayText, marginLeft, marginTop, Graphics.TOP|Graphics.LEFT);
		}

		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#keyPressed(int)
		 */
		@Override
		protected void keyPressed(int keyCode) {
			if (getGameAction(keyCode) == Canvas.FIRE) {
				inputHandler.buttonPressed();
			}
		}

		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#keyReleased(int)
		 */
		@Override
		protected void keyReleased(int keyCode) {
			if (getGameAction(keyCode) == Canvas.FIRE) {
				inputHandler.buttonReleased();
			}
		}
	}
	
	
	private class TransmitGui extends Canvas {
		
		/*
		 * Constructor for this class.
		 */
		public TransmitGui(String displayText, int transmissionMode) {
			this.displayText = displayText;
			this.transmissionMode = transmissionMode;
			marginLeft	= 10;
			marginTop	= 10;
		}
		
		/* Text to display before transmission starts */
		private String displayText;
		
		/* current transmission mode
		 * @see ButtonChannelImpl#transmissionMode
		 */
		private int transmissionMode;
		
		/* Margin values when drawing on the screen */
		private int marginLeft;
		private int marginTop;

		/* (non-Javadoc)
		 * @see javax.microedition.lcdui.Canvas#paint(javax.microedition.lcdui.Graphics)
		 */
		@Override
		protected void paint(Graphics g) {
			if (transmissionMode == ButtonChannelImpl.TRANSMIT_PLAIN) {
				g.drawString(displayText, marginLeft, marginTop, Graphics.LEFT|Graphics.TOP);
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_SIGNAL) {
				// TODO: something...
			}
			
		}
		
		
	}
	
}

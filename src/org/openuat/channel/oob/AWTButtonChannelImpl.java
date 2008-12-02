/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextPane;


/**
 * This is an AWT specific implementation of
 * the {@link ButtonChannelImpl} class.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class AWTButtonChannelImpl extends ButtonChannelImpl implements ActionListener {

	/**
	 * Creates a new instance.
	 * @param parentComponent The parent gui element which will hold gui elements
	 * created by this class.
	 */
	public AWTButtonChannelImpl(JComponent parentComponent) {
		transmissionMode	= 0;
		progress			= 0;
		showSignal			= false;
		intervalList		= null;
		paintableComponent	= null;
		parent				= parentComponent;
		defaultFont = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
		abortButton = new JButton("Abort");
		abortButton.addActionListener(this);
	}
	
	/**
	 * The parent gui component. It serves as a container for gui elements
	 * created by this class.
	 */
	protected JComponent parent;
	
	/**
	 * A gui component that paints to the screen. It it is set, it can be updated
	 * through the <code>repaint</code> method.
	 */
	protected Component paintableComponent;
	
	/**
	 * A button that allows the user to abort the current processing (capture or transmit).
	 */
	protected JButton abortButton;
	
	/**
	 * Default font when displaying text.
	 */
	protected Font defaultFont;

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#repaint()
	 */
	// @Override
	public void repaint() {
		if (paintableComponent != null) {
			paintableComponent.repaint();
		}
		else {
			// TODO: log warning
			// logger.warn("Method repaint(): paintableComponent is null");
		}
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showCaptureGui(java.lang.String, org.openuat.channel.oob.ButtonInputHandler)
	 */
	// @Override
	public void showCaptureGui(String text, ButtonInputHandler inputHandler) {
		final ButtonInputHandler buttonInputHandler = inputHandler;
		JTextPane captureGui = new JTextPane();
		
		KeyListener keyListener = new KeyAdapter() {

			/* (non-Javadoc)
			 * @see java.awt.event.KeyAdapter#keyPressed(java.awt.event.KeyEvent)
			 */
			// @Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_SPACE) {
					buttonInputHandler.buttonPressed();
				}
			}

			/* (non-Javadoc)
			 * @see java.awt.event.KeyAdapter#keyReleased(java.awt.event.KeyEvent)
			 */
			// @Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_SPACE) {
					buttonInputHandler.buttonReleased();
				}
			}
		};
		
		captureGui.addKeyListener(keyListener);
		captureGui.setFont(defaultFont);
		captureGui.setText(text);
		captureGui.setEditable(false);
		
		// display the capture gui
		parent.removeAll();
		parent.add(captureGui);
		parent.add(abortButton);
		parent.repaint();
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showTransmitGui(java.lang.String, int)
	 */
	// @Override
	public void showTransmitGui(String text, int type) {
		transmissionMode = type;
		Canvas transmitGui = new TransmitGui(text);
		
		// display the transmit gui
		parent.removeAll();
		parent.add(transmitGui);
		parent.add(abortButton);
		parent.repaint();
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#vibrate(int)
	 */
	// @Override
	public void vibrate(int milliseconds) {
		// can't be implemented on this platform
		// TODO Logger.warn("Method vibrate(int): Not implemented on J2SE (AWT)");
		
	}

	/* (non-Javadoc)
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	// @Override
	public void actionPerformed(ActionEvent e) {
		if (e.getID() == ActionEvent.ACTION_PERFORMED
				&& e.getActionCommand().equals("Abort")) {
			// TODO: abort current processing...
		}
	}
	
	
	/*
	 * Private helper/wrapper class to launch the transmit gui.
	 */
	private class TransmitGui extends Canvas {
		
		/*
		 * Creates a new Instance.
		 */
		public TransmitGui(String displayText) {
			this.displayText	= displayText;
			textMarginLeft		= 20;
			textMarginTop		= 20;
			signalLength		= 200;
			barMaxWidth			= 400;
			barMinMargin		= 20;
			barHeight			= 25;
		}
		
		/* Display this text before transmission starts. */
		private String displayText;
		
		/* Margins to draw text */
		private int textMarginLeft;
		private int textMarginTop;
		
		/* Length of one side of the signal */
		private int signalLength;
		
		/* Height, maximum width and minimum margin of the progress bar */
		private int barMaxWidth;
		private int barMinMargin;
		private int barHeight;

		/* (non-Javadoc)
		 * @see java.awt.Canvas#paint(java.awt.Graphics)
		 */
		// @Override
		public void paint(Graphics g) {
			// clear painting area
			super.paint(g);
			
			if (transmissionMode == ButtonChannelImpl.TRANSMIT_PLAIN) {
				g.setColor(Color.BLACK);
				g.setFont(defaultFont);
				g.drawString(displayText, textMarginLeft, textMarginTop + defaultFont.getSize());
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_SIGNAL) {
				if (showSignal) {
					g.setColor(Color.BLACK);
					// the signal is just a simple square, painted black
					int marginLeft = (this.getWidth()  - signalLength) / 2;
					int marginTop  = (this.getHeight() - signalLength) / 2;
					g.fillRect(marginLeft, marginTop, signalLength, signalLength);
				}
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_BAR) {
				if (intervalList != null) {
					int barWidth	= Math.min(barMaxWidth, this.getWidth() - 2 * barMinMargin);
					int marginLeft	= (this.getWidth() - barWidth) / 2;
					int marginTop	= (this.getHeight() - barHeight) / 2;
					
					int currentMargin = marginLeft;
					for (int i = 0; i < intervalList.size(); i++) {
						int intervalWidth = (int)((double)intervalList.item(i) / (double)intervalList.getTotalIntervalLength() * barWidth);
						if (i == 0) {
							g.setColor(Color.LIGHT_GRAY);
						}
						else if (i % 2 == 0) {
							g.setColor(Color.RED);
						}
						else {
							g.setColor(Color.ORANGE);
						}
						g.fillRect(currentMargin, marginTop, intervalWidth, barHeight);
						currentMargin += intervalWidth;
					}
					g.setColor(Color.BLACK);
					int progressWidth = (int)((double)intervalList.getTotalIntervalLength() / 100.0d * progress);
					g.fillRect(marginLeft, marginTop, progressWidth, barHeight);
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

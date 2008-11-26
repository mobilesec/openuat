/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JComponent;
import javax.swing.JTextPane;


/**
 * This is an AWT specific implementation of
 * the {@link ButtonChannelImpl} class.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class AWTButtonChannelImpl extends ButtonChannelImpl {

	/**
	 * Creates a new instance.
	 */
	public AWTButtonChannelImpl(JComponent parentComponent) {
		transmissionMode	= 0;
		progress			= 0;
		showSignal			= false;
		buttonInputHandler	= null;
		intervalList		= null;
		currentComponent	= null;
		parent				= parentComponent;
	}
	
	/**
	 * The parent gui component.
	 */
	protected JComponent parent;
	
	/**
	 * The current gui element displayed on screen.
	 */
	protected JComponent currentComponent;
	
	/* Keep the inputHandler as a private member, so an anonymous class can access it */
	private ButtonInputHandler buttonInputHandler;

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#repaint()
	 */
	@Override
	public void repaint() {
		if (currentComponent != null) {
			currentComponent.repaint();
		}
		else {
			// TODO: log warning
			// logger.warn("Method repaint(): currentComponent is null");
		}
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showCaptureGui(java.lang.String, org.openuat.channel.oob.ButtonInputHandler)
	 */
	@Override
	public void showCaptureGui(String text, ButtonInputHandler inputHandler) {
		buttonInputHandler = inputHandler;
		JTextPane captureGui = new JTextPane();
		KeyListener keyListener = new KeyAdapter() {

			/* (non-Javadoc)
			 * @see java.awt.event.KeyAdapter#keyPressed(java.awt.event.KeyEvent)
			 */
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_SPACE) {
					buttonInputHandler.buttonPressed();
				}
			}

			/* (non-Javadoc)
			 * @see java.awt.event.KeyAdapter#keyReleased(java.awt.event.KeyEvent)
			 */
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_SPACE) {
					buttonInputHandler.buttonReleased();
				}
			}
		};
		
		captureGui.addKeyListener(keyListener);
		captureGui.setText(text);
		
		// display currentComponent
		currentComponent = captureGui;
		parent.add(currentComponent);
		parent.repaint();
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showTransmitGui(java.lang.String, int)
	 */
	@Override
	public void showTransmitGui(String text, int type) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#vibrate(int)
	 */
	@Override
	public void vibrate(int milliseconds) {
		// can't be implemented on this platform
		// TODO Logger.warn("method vibrate is not implemented on AWT");
		
	}
	
	


}

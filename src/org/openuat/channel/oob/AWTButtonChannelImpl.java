/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
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
 * the {@link ButtonChannelImpl} class.<br/>
 * Special care must be taken to handle fake key events from  the OS when
 * a key is held down. Key press (and release) events MAY be fired by the OS
 * at the systems (variable) keyboard repeat rate.<br/>
 * <table><tr>
 *   <td>User:</td>
 *   <td>Key press</td>
 * 	 <td>Hold the key</td>
 *   <td>Key release</td>
 * </tr><tr>
 *   <td>Windows:</td>
 *   <td>Key press event</td>
 * 	 <td>Key press events at systems keyboard repeat rate</td>
 * 	 <td>Key release event</td>
 * </tr><tr>
 *   <td>Linux:</td>
 *   <td>Key press event</td>
 * 	 <td>Key release/key press event pairs (in that order, with identical timestamps)
 *       at systems keyboard repeat rate</td>
 * 	 <td>Key release event</td>
 * </tr><tr>
 *   <td>Linux (modifier keys):</td>
 *   <td>Key press event</td>
 * 	 <td>No events</td>
 * 	 <td>Key release event</td>
 * </tr></table>
 * Presumably, the key repeat functionality is activated on most systems. However, it
 * could be deactivated by the user as well (which is the same behavior as with the
 * modifier keys like CTRL, SHIFT, ALT on linux).
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
	public AWTButtonChannelImpl(Container parentComponent) {
		transmissionMode	= 0;
		progress			= 0;
		showSignal			= false;
		intervalList		= null;
		paintableComponent	= null;
		parent				= parentComponent;
		isKeyDown			= false;
		defaultFont = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
		abortButton = new JButton("Abort");
		abortButton.addActionListener(this);
	}
	
	/**
	 * The parent gui component. It serves as a container for gui elements
	 * created by this class.
	 */
	protected Container parent;
	
	/**
	 * A gui component that paints to the screen. If it is set, it can be updated
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
	
	/**
	 * The keyboard key which is used as <i>the</i> button for user input.
	 */
	protected int buttonKey = KeyEvent.VK_SPACE;
	
	/* Keep track of button presses: ignore fake key press events */
	private boolean isKeyDown;
	
	/* Keep track of button presses: ignore fake key release events */
	private long lastKeyDown;

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
		Dimension size = new Dimension(
			parent.getWidth() - 20,
			parent.getHeight() - abortButton.getHeight() - 10
		);
		captureGui.setPreferredSize(size);
		isKeyDown = false;
		lastKeyDown = 0L;
		
		KeyListener keyListener = new KeyAdapter() {
			// @Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == buttonKey) {
					lastKeyDown = e.getWhen();
					if (!isKeyDown) {
						// TODO: log.debug()
						System.out.println("Key pressed.  Event time: " + e.getWhen() + 
										" Captured at: " + System.currentTimeMillis());
						isKeyDown = true;
						buttonInputHandler.buttonPressed(e.getWhen());
					}
				}
			}

			// @Override
			public void keyReleased(final KeyEvent e) {
				if (e.getKeyCode() == buttonKey) {
					if (isKeyDown) {
						/*
						 * This is nasty and only necessary on linux.
						 * The key repeat feature of the OS will fire a pair of fake key events:
						 * key released, followed by key pressed event with identical timestamps.
						 * The two events will be added to the AWT event queue, but not as an atomic
						 * operation. To be able to handle (ignore) those two events, do the following:
						 * When the first event (it's the release event) is dispatched,
						 * wait for 15 milliseconds to give the event source
						 * enough time to put the second event into the queue as well (note: we are in the
						 * AWT event dispatch thread, which is blocked!). Then postpone the handling of the
						 * release event after all other events (especially the press event, if any) have
						 * been processed (through EventQueue.invokeLater()). At this later point it is
						 * possible to decide whether the release event was a real or a faked one and to
						 * react accordingly.
						 * Note1: The delay of 15 milliseconds does NOT guarantee correctness in all cases!
						 * Note2: However, we do not lose accuracy of the timestamps since the time is taken
						 * from the delivered Event itself.
						 */
						try {
							Thread.sleep(15);
						} catch(InterruptedException ie) {
							// TODO: log warning
						}
						java.awt.EventQueue.invokeLater(new Runnable(){
							public void run() {
								if (lastKeyDown != e.getWhen()) {
									// TODO: log.debug()
									System.out.println("Key released. Event time: " + e.getWhen() +
												" Captured at: " + System.currentTimeMillis());
									isKeyDown = false;
									buttonInputHandler.buttonReleased(e.getWhen());
								}
							}
						});
					}
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
		parent.validate();
		parent.repaint();
		
		// Request the focus, works only after all changes to the container are done.
		captureGui.requestFocusInWindow();
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showTransmitGui(java.lang.String, int)
	 */
	// @Override
	public void showTransmitGui(String text, int type) {
		transmissionMode = type;
		JComponent transmitGui = null;
		
		if (transmissionMode == ButtonChannelImpl.TRANSMIT_PLAIN) {
			JTextPane temp = new JTextPane();
			temp.setFont(defaultFont);
			temp.setText(text);
			temp.setEditable(false);
			transmitGui = temp;
		}
		else {
			transmitGui = new TransmitGui(text);
			transmitGui.setBackground(Color.WHITE);
			transmitGui.setDoubleBuffered(true);
			paintableComponent = transmitGui;
		}
		
		Dimension size = new Dimension(
				parent.getWidth() - 20,
				parent.getHeight() - abortButton.getHeight() - 10
			);
		transmitGui.setPreferredSize(size);
		
		// display the transmit gui
		parent.removeAll();
		parent.add(transmitGui);
		parent.add(abortButton);
		parent.validate();
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
	private class TransmitGui extends JComponent {
		// TODO: remove TRANSFER_PLAIN from this class
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
			barHeight			= 40;
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

		// @Override
		public void paint(Graphics g) {
			// clear area
			g.setColor(this.getBackground());
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
			
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
					int progressWidth = (int)(barWidth / 100.0d * progress);
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

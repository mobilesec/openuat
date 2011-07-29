/* Copyright Lukas Huser
 * File created 2008-11-18
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.channel.oob.desktop;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.net.URL;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextPane;

import org.openuat.channel.oob.ButtonChannel;
import org.openuat.channel.oob.ButtonInputHandler;
import org.openuat.log.LogFactory;


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
 * modifier keys like CTRL, SHIFT, ALT on linux).<br/>
 * This class has been tested under Linux (Ubuntu 8.10) and quickly on Windows XP.
 *
 * @author Lukas Huser
 * @version 1.0
 */
public class AWTButtonChannelImpl extends ButtonChannelImpl {

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
	 * A gui component that displays text. It it is set and the <code>repaint</code>
	 * method is called, its content will be updated with the current
	 * <code>signalCount</code>
	 */
	protected JTextPane textComponent;
	
	/**
	 * Text content which will be displayed in the <code>textComponent</code>
	 * gui element.
	 */
	protected String currentText;
	
	/**
	 * A button that allows the user to abort the current processing (capture or transmit).
	 */
	protected JButton abortButton;
	
	/**
	 * Default font when displaying text.
	 */
	protected Font defaultFont;
	
	/**
	 * The keyboard key which is used as <i>the</i> button for user input.<br/>
	 * It is the space bar on J2SE.
	 */
	protected int buttonKey;
	
	
	/* Keep track of button presses: ignore fake key press events */
	private boolean isKeyDown;
	
	/* Keep track of button presses: ignore fake key release events */
	private long lastKeyDown;
	
	
	/**
	 * Creates a new instance.
	 * @param parentComponent The parent gui element which will hold gui elements
	 * created by this class.
	 * @param abortHandler An action listener which will be called when the user
	 * aborts the current transmission or capture process.
	 */
	public AWTButtonChannelImpl(Container parentComponent, ActionListener abortHandler) {
		transmissionMode	= 0;
		progress			= 0;
		signalCount			= 0;
		showSignal			= false;
		showCount			= false;
		intervalList		= null;
		paintableComponent	= null;
		textComponent		= null;
		currentText			= "";
		parent				= parentComponent;
		buttonKey			= KeyEvent.VK_SPACE;
		isKeyDown			= false;
		lastKeyDown			= 0L;
		defaultFont = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
		abortButton = new JButton("Abort");
		abortButton.addActionListener(abortHandler);
		logger = LogFactory.getLogger("org.openuat.channel.oob.AWTButtonChannelImpl");
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#repaint()
	 */
	// @Override
	public void repaint() {
		if (paintableComponent != null) {
			paintableComponent.repaint();
		}
		else if (textComponent != null) {
			String text = currentText;
			if (showCount) {
				text += signalCount + "/" + ButtonChannel.TOTAL_SIGNAL_COUNT;
			}
			textComponent.setText(text);
		}
		else {
			logger.warn("Method repaint(): paintableComponent and textComponent are null");
		}
	}

	/* (non-Javadoc)
	 * @see org.openuat.channel.oob.ButtonChannelImpl#showCaptureGui(java.lang.String, org.openuat.channel.oob.ButtonInputHandler)
	 */
	// @Override
	public void showCaptureGui(String text, final ButtonInputHandler inputHandler) {
		JTextPane captureGui = new JTextPane();
		Dimension size = new Dimension(
			parent.getWidth() - 20,
			parent.getHeight() - abortButton.getHeight() - 40
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
						if (logger.isDebugEnabled()) {
							logger.debug("Key pressed.  Event time: " + e.getWhen() + 
										" Captured at: " + System.currentTimeMillis());
						}
						isKeyDown = true;
						inputHandler.buttonPressed(e.getWhen());
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
							logger.warning("Thread interrupted.", ie);
						}
						java.awt.EventQueue.invokeLater(new Runnable(){
							public void run() {
								if (lastKeyDown != e.getWhen()) {
									if (logger.isDebugEnabled()) {
										logger.debug("Key released. Event time: " + e.getWhen() +
												" Captured at: " + System.currentTimeMillis());
									}
									isKeyDown = false;
									inputHandler.buttonReleased(e.getWhen());
								}
							}
						});
					}
				}
			}
		};
		
		signalCount = 0;
		paintableComponent = null;
		currentText = text;
		if (showCount) {
			currentText += "\n\n" + "Button events: ";
		}
		textComponent = captureGui;
		captureGui.addKeyListener(keyListener);
		captureGui.setFont(defaultFont);
		captureGui.setEditable(false);
		this.repaint();
		
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
		signalCount = 0;
		
		if (transmissionMode == ButtonChannelImpl.TRANSMIT_PLAIN) {
			currentText = text;
			if (showCount) {
				currentText += "\n\n" + "Signals sent: ";
			}
			JTextPane temp = new JTextPane();
			temp.setFont(defaultFont);
			temp.setEditable(false);
			transmitGui = temp;
			paintableComponent = null;
			textComponent = temp;
			this.repaint();
		}
		else {
			transmitGui = new TransmitGui();
			transmitGui.setBackground(Color.WHITE);
			transmitGui.setDoubleBuffered(true);
			paintableComponent = transmitGui;
			textComponent = null;
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
		logger.warning("Method vibrate(int): Not implemented on J2SE (AWT)");
	}
	
	
	/*
	 * Private helper/wrapper class to launch the transmit gui.
	 */
	private class TransmitGui extends JComponent {
		private static final long serialVersionUID = 1L;
		/* Margins to draw text */
		private int textMarginLeft;
		private int textMarginTop;
		
		/* Length of one side of the signals */
		private int signalLength;
		private int prepSignalLength;
		
		/* Height, maximum width and minimum margin of the progress bar */
		private int barMaxWidth;
		private int barMinMargin;
		private int barHeight;
		
		/* Maximum width and height of the power bar */
		private int powBarMaxWidth;
		private int powBarHeight;
		
		/* Traffic light images */
		private Image trafficLightRed;
		private Image trafficLightYellow;
		private Image trafficLightGreen;
		
		/*
		 * Creates a new Instance.
		 */
		public TransmitGui() {
			textMarginLeft		= 20;
			textMarginTop		= 20;
			signalLength		= 200;
			prepSignalLength	= 150;
			barMaxWidth			= 400;
			barMinMargin		= 20;
			barHeight			= 40;
			powBarMaxWidth		= 40;
			powBarHeight		= 400;
			
			// load the traffic light images
			URL imgURL = getClass().getResource("/resources/Traffic_lights_dark_red.png");
			trafficLightRed = imgURL != null
					? Toolkit.getDefaultToolkit().getImage(imgURL)
					: Toolkit.getDefaultToolkit().getImage("resources/Traffic_lights_dark_red.png");
			imgURL = getClass().getResource("/resources/Traffic_lights_dark_yellow.png");
			trafficLightYellow = imgURL != null
					? Toolkit.getDefaultToolkit().getImage(imgURL)
					: Toolkit.getDefaultToolkit().getImage("resources/Traffic_lights_dark_yellow.png");
			imgURL = getClass().getResource("/resources/Traffic_lights_dark_green.png");
			trafficLightGreen = imgURL != null
					? Toolkit.getDefaultToolkit().getImage(imgURL)
					: Toolkit.getDefaultToolkit().getImage("resources/Traffic_lights_dark_green.png");
		}

		// @Override
		public void paint(Graphics g) {
			// clear area
			g.setColor(this.getBackground());
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
			String eventCountText = "Signals sent: " + signalCount
				+ "/" + ButtonChannel.TOTAL_SIGNAL_COUNT;
			
			if (transmissionMode == ButtonChannelImpl.TRANSMIT_SIGNAL) {
				paintSignal(g, eventCountText);
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_TRAFFIC_LIGHT) {
				paintTrafficLight(g, eventCountText);
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_BAR) {
				if (intervalList != null) {
					paintBar(g);
				}
				else {
					logger.warning("Method paint(): 'intervalList' is null");
				}
			}
			else if (transmissionMode == ButtonChannelImpl.TRANSMIT_VERT_BARS) {
				if (intervalList != null) {
					paintVertBars(g);
				}
				else {
					logger.warning("Method paint(): 'intervalList' is null");
				}
			}
			else {
				logger.warning("Method paint(): Unknown 'transmissionMode': " + transmissionMode);
			}
			
		}
		
		/* Painting method for transmission mode TRANSMIT_SIGNAL */
		private void paintSignal(Graphics g, String eventCountText) {
			int marginTopText = 0;
			if (showCount) {
				marginTopText = textMarginTop + defaultFont.getSize();
				g.setColor(Color.BLACK);
				g.setFont(defaultFont);
				g.drawString(eventCountText, textMarginLeft, marginTopText);
			}
			// the 'real' signal has always precedence over the preparatory signal
			if (showSignal) {
				g.setColor(Color.BLACK);
				// the signal is just a simple square, painted black
				int marginLeft = (this.getWidth()  - signalLength) / 2;
				int marginTop  = (this.getHeight() - signalLength) / 2 + marginTopText;
				g.fillRect(marginLeft, marginTop, signalLength, signalLength);
			}
			else if (prepareSignal) {
				g.setColor(Color.LIGHT_GRAY);
				// the preparatory signal is a smaller square, painted gray
				int marginLeft = (this.getWidth()  - prepSignalLength) / 2;
				int marginTop  = (this.getHeight() - prepSignalLength) / 2 + marginTopText;
				g.fillRect(marginLeft, marginTop, prepSignalLength, prepSignalLength);
			}
		}
		
		/* Painting method for transmission mode TRANSMIT_TRAFFIC_LIGHT */
		private void paintTrafficLight(Graphics g, String eventCountText) {
			int marginTopText = 0;
			if (showCount) {
				marginTopText = textMarginTop + defaultFont.getSize();
				g.setColor(Color.BLACK);
				g.setFont(defaultFont);
				g.drawString(eventCountText, textMarginLeft, marginTopText);
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
			int imgX = (this.getWidth() - currentState.getWidth(this)) / 2;
			int imgY = (this.getHeight() - marginTopText - currentState.getHeight(this)) / 2;
			g.drawImage(currentState, imgX, imgY, this);
		}
		
		/* Painting method for transmission mode TRANSMIT_BAR */
		private void paintBar(Graphics g) {
			int barWidth	= Math.min(barMaxWidth, this.getWidth() - 2 * barMinMargin);
			int marginLeft	= (this.getWidth() - barWidth) / 2;
			int marginTop	= (this.getHeight() - barHeight) / 2;
			
			int currentMargin = marginLeft;
			for (int i = 0; i < intervalList.size(); i++) {
				int intervalWidth = (int)((double)intervalList.item(i) / (double)intervalList.getTotalIntervalLength() * barWidth);
				if (i%2 == 0) {
					g.setColor(Color.LIGHT_GRAY);
				}
				else {
					g.setColor(Color.GREEN);
				}
				g.fillRect(currentMargin, marginTop, intervalWidth, barHeight);
				currentMargin += intervalWidth;
			}
			g.setColor(Color.BLACK);
			int progressWidth = (int)(barWidth / 100.0d * progress);
			g.fillRect(marginLeft, marginTop, progressWidth, barHeight);
		}
		
		/* Painting method for transmission mode TRANSMIT_VERT_BARS */
		private void paintVertBars(Graphics g) {
			int barCount = (intervalList.size() + 1) / 2;
			int powBarWidth = Math.min(powBarMaxWidth, this.getWidth() / (2*barCount + 1));
			int powBarMargin = Math.max(powBarWidth, 
						(this.getWidth() - (2*barCount - 1) * powBarWidth) / 2);
			int powBarMarginTop = (this.getHeight() - powBarHeight) / 2;
			
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
				g.setColor(Color.LIGHT_GRAY);
				g.fillRect(marginLeft, marginTop, powBarWidth, intervalLength);
				
				if (i + 1 < intervalList.size()) {
					intervalLength = (int)((double)intervalList.item(i+1) / maxDblInterval * powBarHeight);
					marginTop -= intervalLength;
					g.setColor(Color.GREEN);
					g.fillRect(marginLeft, marginTop, powBarWidth, intervalLength);
				}
				
				marginLeft += 2 * powBarWidth;
			}
			
			// draw progress
			marginLeft = powBarMargin;
			g.setColor(Color.BLACK);
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

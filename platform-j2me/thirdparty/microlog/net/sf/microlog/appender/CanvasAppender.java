/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.microlog.appender;

import java.io.IOException;
import java.util.Vector;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

import net.sf.microlog.Appender;
import net.sf.microlog.Formatter;
import net.sf.microlog.Level;
import net.sf.microlog.format.SimpleFormatter;
import net.sf.microlog.util.PropertiesGetter;

/**
 * An appender which writes entries to a Canvas. Supports scrolling.
 * 
 * @author Ricardo Amores Hernandez
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 */
public class CanvasAppender extends Canvas implements Appender {
	/**
	 * Saves log entries
	 */
	private Vector logStrings = new Vector();
	/**
	 * Text formatter. Uses SimpleFormatter by default.
	 */
	private Formatter formatter;
	/**
	 * True if the log is closed. The log is opened by default.
	 */
	private boolean isOpen;
	/**
	 * Size of the offset for drawing string lines
	 */
	private int lineSize;
	/**
	 * Offset in the screen
	 */
	private int screenOffset;
	/**
	 * Set our logging font
	 */
	private Font loggingFont;

	/**
	 * Create a <code>CanvasAppender</code>. constructor
	 */
	public CanvasAppender() {

		// Set default formatter
		formatter = new SimpleFormatter();

		screenOffset = 0;

		// Create logging font
		loggingFont = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN,
				Font.SIZE_SMALL);

		// Store size of a line writted writted in the screen with this font
		lineSize = loggingFont.getHeight();
	}

	/**
	 * Draws log entries to the screen
	 * 
	 * @param g
	 *            the <code>Graphics</code> object to use for drawing.
	 */
	public void paint(Graphics g) {
		// Erase screen
		g.setColor(255, 255, 255);
		g.fillRect(0, 0, this.getWidth(), this.getHeight());
		g.setColor(0, 0, 0);

		// Draw all lines
		int count = 1;
		for (int index = screenOffset; index < logStrings.size(); index++) {
			g.drawString(logStrings.elementAt(index).toString(), 0, count
					* lineSize, Graphics.BASELINE | Graphics.LEFT);

			count++;
		}
	}

	/**
	 * Called when a key is pressed.
	 * 
	 * @param keyCode
	 *            the key kode of the key that was pressed.
	 */
	protected void keyPressed(int keyCode) {
		switch (this.getGameAction(keyCode)) {
		case UP:
			if (screenOffset > 0) {
				screenOffset--;
			}
			break;

		case DOWN:
			if (lineSize * (logStrings.size() - screenOffset) > this
					.getHeight()) {
				screenOffset++;
			}
			break;
		}

		// Request screen repaint
		repaint();
	}

	/**
	 * Do the logging.
	 * @param level
	 *            the level at which the logging shall be done.
	 * @param message
	 *            the message to log.
	 * @param throwable
	 *            the exception to log.
	 */
	public void doLog(String name, long time, Level level,
			Object message, Throwable throwable) {
		if (isOpen && formatter != null) {
			logStrings.addElement(formatter.format(name, time, level,
					message, throwable));
		}

		// Scroll down screen to show the last line entered when a new line is
		// added
		while (lineSize * (logStrings.size() - screenOffset) > this.getHeight()) {
			screenOffset++;
		}

		// Request screen repaint
		this.repaint();
	}

	/**
	 * Clear the log.
	 * 
	 * @see net.sf.microlog.Appender#clear()
	 */
	public void clear() {
		logStrings.removeAllElements();
	}

	/**
	 * Opens the log.
	 * 
	 * @see net.sf.microlog.Appender#open()
	 */
	public void open() throws IOException {
		clear();
		isOpen = true;
	}

	/**
	 * Closes the log.
	 * 
	 * @see net.sf.microlog.Appender#close()
	 */
	public void close() throws IOException {
		isOpen = false;
	}

	/**
	 * Check if the log is open.
	 * 
	 * @return true if the log is open, false if the log is closed.
	 */
	public boolean isLogOpen() {
		return isOpen;
	}

	/**
	 * Gets the number of entries in the log.
	 * 
	 * @return A long with the number of log entries
	 */
	public long getLogSize() {
		return logStrings.size();
	}

	/**
	 * Sets the format used to represent the log strings
	 * 
	 * @param formatter
	 */
	public void setFormatter(Formatter formatter) {
		if (formatter == null) {
			throw new NullPointerException("newFormatter is invalid");
		}

		this.formatter = formatter;
	}

	/**
	 * Gets the current formatter used to format the log entires.
	 * 
	 * @return the current formatter.
	 */
	public Formatter getFormatter() {
		return formatter;
	}

	/**
	 * Configure the appender.
	 * 
	 * @param properties
	 *            Properties to configure with
	 */
	public void configure(PropertiesGetter properties) {

	}

}
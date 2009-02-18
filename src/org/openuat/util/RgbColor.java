/* Copyright Lukas Huser
 * File created 2008-11-26
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

/**
 * This class represents an opaque RGB color encoded as an <code>int</code>
 * value of the form <code>0xRRGGBB</code>. Additionally it provides
 * some predefined color values.<br/>
 * It's intended to be used mainly within J2ME since J2SE (AWT) brings its own
 * <code>Color</code> class.
 * 
 * @author Lukas Huser
 * @version 1.0
 */
public class RgbColor {
	
	/**
	 * Creates a new color. The default color is black.
	 */
	public RgbColor() {
		this(BLACK);
	}
	
	/**
	 * Creates a new color from a given RGB representation.
	 * @param rgbValue The RGB representation of the new color.
	 */
	public RgbColor(int rgbValue) {
		setRgbValue(rgbValue);
	}
	
	/**
	 * Gets the RGB representation of the color.
	 * @return Returns the RGB representation of the color.
	 */
	public int getRgbValue() {
		return rgbValue;
	}
	
	/**
	 * Sets the RGB representation of the color.
	 * @param rgbValue The RGB representation of the color.
	 */
	public void setRgbValue(int rgbValue) {
		rgbValue = rgbValue & 0x00ffffff;
	}
	
	/*
	 * Internal representation of the color.
	 */
	private int rgbValue;

	
	/* ******************************* *
	   Some predefined color constants
	 * ******************************* */
	
	/**
	 * The color black (<code>0x000000</code>).
	 */
	public static final int BLACK		= 0x000000;
	/**
	 * The color white (<code>0xffffff</code>).
	 */
	public static final int WHITE		= 0xffffff;
	/**
	 * The color red (<code>0xff0000</code>).
	 */
	public static final int RED			= 0xff0000;
	/**
	 * The color green (<code>0x00ff00</code>).
	 */
	public static final int GREEN		= 0x00ff00;
	/**
	 * The color blue (<code>0x0000ff</code>).
	 */
	public static final int BLUE		= 0x0000ff;
	/**
	 * The color yellow (<code>0xffff00</code>).
	 */
	public static final int YELLOW		= 0xffff00;
	/**
	 * The color cyan (<code>0x00ffff</code>).
	 */
	public static final int CYAN		= 0x00ffff;
	/**
	 * The color magenta (<code>0xff00ff</code>).
	 */
	public static final int MAGENTA		= 0xff00ff;
	/**
	 * The color gray (<code>0x808080</code>).
	 */
	public static final int GRAY		= 0x808080;
	/**
	 * The color dark gray (<code>0x404040</code>).
	 */
	public static final int DARK_GRAY	= 0x404040;
	/**
	 * The color light gray (<code>0xc0c0c0</code>).
	 */	
	public static final int LIGHT_GRAY	= 0xc0c0c0;
	/**
	 * The color dark red (<code>0x800000</code>).
	 */
	public static final int DARK_RED	= 0x800000;
	/**
	 * The color light red (<code>0xff3333</code>).
	 */
	public static final int LIGHT_RED	= 0xff3333;
	/**
	 * The color dark green (<code>0x008000</code>).
	 */
	public static final int DARK_GREEN	= 0x008000;
	/**
	 * The color dark blue (<code>0x0000a0</code>).
	 */
	public static final int DARK_BLUE	= 0x0000a0;
}

/* Copyright Rene Mayrhofer
 * File created 2006-04-28
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.sensors;

/** This class represents a possibly multi-dimensional time series of a single
 * sensor. It computes simply statistical values, can distinguish active from
 * passive segments, and offers some convenience methods.
 * 
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class TimeSeries {
	// TODO: implement normalization
	// TODO: keep statistics automatically, both complete since starting and over sliding window
	// TODO: detect active and passive segments automatically and fire off events on transitions when requested
	// TODO: provide default parameter values, but allow to override them
}

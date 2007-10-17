/* Copyright Rene Mayrhofer
 * File created 2006-09-28
 * Initial public release 2007-03-29
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.authentication.accelerometer;

/** This class defines constants for the motion authentication protocols. The 
 * optimal values were found by a full parameter search over an extensive 
 * real-world data set. See
 *   Rene Mayrhofer, Hans Gellersen: "Generating Secret Shared Keys from 
 *   Common Movement Patterns", Pervasive 2007
 * for details.
 *
 * @author Rene Mayrhofer
 * @version 1.0
 */
public class ShakeWellBeforeUseParameters {
	public final static int samplerate = 256; // Hz
	
	public final static int activityDetectionWindowSize = samplerate/2; // 1/2 s
	
	// this is very robust
	public final static float activityVarianceThreshold = 0.045f; // this depends on samplerate, update when changing!
	
	public final static int activityMinimumSegmentSize = 3*samplerate; // 3 s
	
	public final static int coherenceWindowSize = samplerate;
	
	public final static float coherenceWindowOverlapFactor = 7f/8; // 87,5% overlap
	
	public final static int coherenceSegmentSize = 3*samplerate; // 3s segments

	public final static int coherenceCutOffFrequency = 40; // Hz
	
	public final static float coherenceThreshold = 0.5f; //0.72f;
	
	public final static int fftMatchesWindowSize = samplerate;

	public final static int fftMatchesQuantizationLevels = 6;
	
	public final static int fftMatchesCandidatesPerRound = 4;
	
	public final static int fftMatchesCutOffFrequenecy = 20; // Hz
	
	public final static int fftMatchesWindowOverlap = fftMatchesWindowSize/2; // 50% overlap
	
	public final static float fftMatchesThreshold = 0.84f;
}

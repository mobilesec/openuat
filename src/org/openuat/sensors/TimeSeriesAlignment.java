/* Copyright Rene Mayrhofer
 * File created 2008-01-31
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors;

import java.util.BitSet;

import org.apache.log4j.Logger;

/** http://en.wikipedia.org/wiki/Image:Spherical_Coordinates.png
 * http://en.wikipedia.org/wiki/List_of_canonical_coordinate_transformations
 * 
 * x = r sin phi cos theta
 y = r sin phi sin theta
 z = r cos phi
 
 r^2 = x^2+y^2+z^2
 
 cos theta = x / sqrt(x^2 + y^2), sin theta = y / sqrt(x^2 + y^2), tan theta = y/x
 cos phi = z/r, tan phi = sqrt(x^2+y^2) / z

 * @author Rene Mayrhofer
 * @version 1.0
 */
public class TimeSeriesAlignment extends TimeSeriesBundle {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger("org.openuat.sensors.TimeSeriesAlignment" /*TimeSeriesAlignment.class*/);

	private double[] r = null, theta = null, phi = null;
	
	private int index = -1;
	
	/** Constructs all internal buffers and the time series.
	 * 
	 * @param numSeries The number of time series to use, i.e. the dimensionality
	 *                  of the input space.
	 * @param windowSize The time window size to use for detecting active/quiescent
	 *                   segments. This is specified as the number of samples in the
	 *                   window.
	 * @param minSegmentSize The minimum size of an active segment to be regarded
	 *                       significant enough to be sent to listeners.
	 * @param maxSegmentSize If set to something other than -1, specifies the
	 *                       maximum size of an active segments. If an active segment
	 *                       is longer than this number of samples, it will be sent
	 *                       to the listeners as soon as it reaches this length. The
	 *                       remainders of longer segments will be discarded. Set to
	 *                       -1 to disable this functionality, otherwise must be
	 *                       >=minSegmentSize.
	 */
	public TimeSeriesAlignment(int numSeries, int windowSize, int minSegmentSize, int maxSegmentSize) {
		super(numSeries, windowSize, minSegmentSize, maxSegmentSize);
		
		if (numSeries != 2 && numSeries != 3)
			throw new IllegalArgumentException("Number of dimensions must be 2 or 3");
		
		r = new double[windowSize+maxSegmentSize];
		theta = new double[windowSize+maxSegmentSize];
		if (numSeries == 3)
			phi = new double[windowSize+maxSegmentSize];
	}
	
	public TimeSeriesAlignment(double[][] mySide) {
		this(mySide[0].length, 1, 1, mySide.length);
		
		if (mySide.length < 1)
			throw new IllegalArgumentException("Need at least 1 sample");
		if (mySide[0].length != 2 && mySide[0].length != 3)
			throw new IllegalArgumentException("Number of dimensions must be 2 or 3");

		index=0;
		for (int i=0; i<mySide.length; i++)
			newSample(mySide[i]);
	}
	
	public class Alignment {
		public double delta_theta=0, delta_phi=0, error=0;
		public int numSamples=0;
	}
	
	public Alignment alignWith(TimeSeriesAlignment otherSide) {
		if (otherSide == null || otherSide.r.length < 1)
			throw new IllegalArgumentException("Need at least 1 sample");
		if (otherSide.firstStageSeries_Int.length != firstStageSeries_Int.length)
			throw new IllegalArgumentException("Number of dimensions must match for both sides");

		// this is naive optimisation - our own sample is the reference, the other rotated wrt. it
		Alignment al = new Alignment();
		int nTheta=0, nPhi=0;
		BitSet alternativeRotate = new BitSet(index);
		for (int i=0; i<index && i<otherSide.index; i++) {
			/* only count as relevant when both thetas are != 0, because atan2 returns 0
			 * for (0,0), which really has no angle at all */
			boolean validTheta = (theta[i] != 0 && r[i] != 0) || (otherSide.theta[i] != 0 && otherSide.r[i] != 0);
			double d_theta = 0;
			if (validTheta) {
				d_theta = angleWithinPI(otherSide.theta[i]-theta[i]);
				nTheta++;
			}
			
			if (firstStageSeries_Int.length == 3) {
				boolean validPhi = (phi[i] != 0 && r[i] != 0) || (otherSide.phi[i] != 0 && otherSide.r[i] != 0);
				double d_phi = 0; 
				// see al.delta_theta, same here
				if (validPhi) {
					d_phi = angleWithinPI(otherSide.phi[i]-phi[i]);
					nPhi++;
				}
				
				/* in 3D there are multiple options how to rotate with 2 angles -
				 * choose the one that (with the average so far) yields the smaller error
				 * ==> naive, greedy search over what would be 2^(2*n) possibilities
				 */
				double e1=error3(otherSide.theta[i], theta[i], 
						(nTheta>0 ? (al.delta_theta+d_theta)/nTheta : 0), 
						otherSide.phi[i], phi[i], 
						(nPhi>0 ? (al.delta_phi+d_phi)/nPhi : 0),
						otherSide.r[i], r[i]);
				double e2=error3(otherSide.theta[i], angleWithinPI(theta[i]-Math.PI), 
						(nTheta>0 ? (al.delta_theta+angleWithinPI(d_theta-Math.PI))/nTheta : 0), 
						otherSide.phi[i], angleWithinPI(phi[i]-Math.PI), 
						(nPhi>0 ? (al.delta_phi+angleWithinPI(d_phi-Math.PI))/nPhi : 0),
						otherSide.r[i], r[i]);
				if (e1 > e2) {
					d_theta = angleWithinPI(d_theta-Math.PI);
					d_phi = angleWithinPI(d_phi-Math.PI);
					// remember what we figured out so that error will be calculated correctly
					alternativeRotate.set(i);
				}
				
				al.delta_phi += d_phi;
			}
			al.delta_theta += d_theta;
			al.numSamples++;
		}
		if (nTheta > 0)
			al.delta_theta /= nTheta;
		else if (al.delta_theta != 0)
			logger.warn("Delta theta is not equal zero, although we didn't have a single relevant pair to process. This should not happen!");
		if (nPhi > 0)
			al.delta_phi /= nPhi;
		else if (al.delta_phi != 0)
			logger.warn("Delta phi is not equal zero, although we didn't have a single relevant pair to process. This should not happen!");
		
		// calculate error for theta, phi, and length (magnitude)
		for (int i=0; i<index && i<otherSide.index; i++) {
			if (firstStageSeries_Int.length == 3) {
				if (!alternativeRotate.get(i))
					al.error += error3(otherSide.theta[i], theta[i], al.delta_theta, 
						otherSide.phi[i], phi[i], al.delta_phi,
						otherSide.r[i], r[i]);
				else
					al.error += error3(otherSide.theta[i], angleWithinPI(theta[i]-Math.PI), al.delta_theta, 
							otherSide.phi[i], angleWithinPI(phi[i]-Math.PI), al.delta_phi,
							otherSide.r[i], r[i]);
			}
			else
				al.error += error2(otherSide.theta[i], theta[i], al.delta_theta, 
						otherSide.r[i], r[i]);
		}
		
		return al;
	}
	
	/** Makes sure an angle is within ]-PI; PI]; */ 
	private double angleWithinPI(double angle) {
		if (angle <= -Math.PI)
			angle += 2*Math.PI;
		if (angle > Math.PI)
			angle -= 2*Math.PI;
		if (angle <= -Math.PI || angle > Math.PI)
			logger.warn("Unexpected angle: " + angle);
		return angle;
	}
	
	private double error2(double theta1, double theta2, double d_theta, double r1, double r2) {
		return angleError(theta1, theta2, d_theta, r1, r2) + (r1-r2)*(r1-r2);
	}

	private double error3(double theta1, double theta2, double d_theta, 
			double phi1, double phi2, double d_phi, double r1, double r2) {
		return angleError(theta1, theta2, d_theta, r1, r2) + 
			angleError(phi1, phi2, d_phi, r1, r2) + (r1-r2)*(r1-r2);
	}

	private double angleError(double a1, double a2, double delta, double r1, double r2) {
		// again special handling: for (0,0), no error even with a delta
		if ((a1 != 0 && r1 != 0) || (a2 != 0 && r2 != 0))
			return (angleWithinPI(a1-a2-delta))*(angleWithinPI(a1-a2-delta));
		else
			return 0;
	}
	
	public double[][] rotate(double[][] series) {
		// TODO: implement rotation
		return null;
	}
	
	protected void toActiveFirstLine(int numSample) {
		index = 0;
	}

	protected void toQuiescentLastLine(int numSample) {
		// TODO: implement forwarding
	}

//#if cfg.haveFloatSupport
	protected void sampleAddedLine(int lineIndex, double sample, int numSample) {
		newSample(curSample);
	}
//#endif

	protected void sampleAddedLine(int lineIndex, int sample, int numSample) {
	}
	
	private void newSample(double[] coord) {
		if (index >= maxSegmentSize+windowSize) {
			logger.warn("Want to write more active samples than segment size, aborting. This should not happen!");
			return;
		}

		if (coord.length == 2) {
			r[index] = Math.sqrt(coord[0]*coord[0] + coord[1]*coord[1]);
		}
		else if (coord.length == 3) {
			r[index] = Math.sqrt(coord[0]*coord[0] + coord[1]*coord[1] + coord[2]*coord[2]);
			phi[index] = Math.atan2(Math.sqrt(coord[0]*coord[0] + coord[1]*coord[1]),	coord[2]);
			// sanity check
			if (phi[index] < 0 || phi[index] > Math.PI)
				logger.warn("Computed phi out of tange [0; PI] (" + phi[index] + "). This should not happen!");
		}
		else
			throw new IllegalArgumentException("Number of dimensions must be 2 or 3");

		theta[index] = Math.atan2(coord[1], coord[0]);
		// somewhat normalize the angle
		if (theta[index] <= -Math.PI)
			theta[index] += 2*Math.PI;
		// restrict angles to [0;PI[ so that polar representation is unique
/*		if (theta[index] < 0) {
			r[index] = -r[index];
			theta[index] = -theta[index];
		}
		else if (theta[index] == Math.PI) {
			// special case: PI is not within the limits, so invert r and set to 0 so as to be unique
			r[index] = -r[index];
			theta[index] = 0;
		}*/
		// sanity check
		if (theta[index] <= -Math.PI || theta[index] > Math.PI)
			logger.warn("Phi out of range ]-PI; PI]: " + theta[index]);
/*		if (coord[1] < -0.000001 && r [index] >= 0)
			logger.warn("y < 0 but r >= 0. This should not happen.");
		if (coord[1] > 0.000001 && r [index] <= 0)
			logger.warn("y > 0 but r <= 0. This should not happen.");*/
		
		index++;
	}
}

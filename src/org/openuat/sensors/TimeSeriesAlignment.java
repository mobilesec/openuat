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
	
	private double[][] cartesian = null;
	
	private int index = -1;
	
	/** This is a hard-coded table of what the different quadrant rotation 
	 * options mean in terms of multiples of PI/2 around theta and phi. Would 
	 * be nice to actually compute this...
	 * 
	 * The format for 2D is: <multiples of PI/2 rotation>, <swap x and x>, <negate x>
	 * The format for 3D is: <multiples of PI/2 rotation in psi (around Z)> 
	 * 						 <multiples of PI/2 rotation in theta (around X')> 
	 * 						 <multiples of PI/2 rotation in phi (around Z'')>,
	 * 						 <value for permute in toPolar>, <negate x>, <negate y>
	 */
	private static int[][] quadRot_2D = {
			{0, 0, 0},
			{1, 1, 1},
			{2, 0, 1},
			{3, 1, 0},
		};
	private static int[][] quadRot_3D = {
			{0, 0, 0, 0, 0, 0}, // check - V/2
			{1, 0, 0, 1, 0, 1}, // check - V/1
			{2, 0, 0, 0, 1, 1}, // check - V/4
			{3, 0, 0, 1, 1, 0}, // check - V/3
			{0, 1, 0, 2, 0, 0}, // check - IV/3
			{0, 2, 0, 0, 0, 1}, // check - VI/4
			{0, 3, 0, 2, 0, 1}, // check - II/1
			{1, 1, 0, 3, 0, 0}, // check - III/1
			{1, 2, 0, 1, 0, 0}, // check - VI/1
			{1, 3, 0, 3, 0, 1}, // check - I/3
			{2, 1, 0, 2, 1, 0}, // check - II/3
			{2, 2, 0, 0, 1, 0}, // check - VI/2
			{2, 3, 0, 2, 1, 1}, // check - IV/1
			{3, 1, 0, 3, 1, 0}, // check - I/1
			{3, 2, 0, 1, 1, 1}, // check - VI/3
			{3, 3, 0, 3, 1, 1}, // check - III/3
			{0, 1, 1, 4, 0, 1}, // check - IV/2
			{0, 3, 1, 4, 1, 1}, // check - II/4
			{1, 1, 1, 5, 0, 1}, // check - III/4
			{1, 3, 1, 5, 1, 1}, // check - I/2
			{2, 1, 1, 4, 0, 0}, // check - II/2
			{2, 3, 1, 4, 1, 0}, // check - IV/4
			{3, 1, 1, 5, 0, 0}, // check - I/4
			{3, 3, 1, 5, 1, 0}, // check - III/2
		};
	
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
	
		init();
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
		
		/** The numbers of valid rotation deltas. */
		int nTheta=0, nPhi=0;
		/** For each sample, its value can be positive or negative with obvious rotation of PI. */
		BitSet alternativeRotate = new BitSet(index);
		/** For quadrant rotation cases the required added multiples of PI. */
		public int quadrotTheta=0, quadrotPhi=0, quadrotPsi=0;
	}
	
	/** This is naive optimisation - our own sample is the reference, the other rotated wrt. it.
	 * 
	 * @param otherSide A two-dimensional array, the "outer" array representing
	 *                  samples, the "inner" arrays the dimensions for each sample.
	 *                  It is assumed that all samples have the same number of 
	 *                  dimensions. Bad things will happen if they don't... 
	 * @return
	 */
	public Alignment alignWith(double[][] otherSide) {
		if (otherSide == null || otherSide.length < 1)
			throw new IllegalArgumentException("Need at least 1 sample");
		if (otherSide[0].length != firstStageSeries_Int.length)
			throw new IllegalArgumentException("Number of dimensions must match for both sides");

		/* In 2D, only 4 possibilities.
		 * in 3D, there are 24 possibilities for roughly aligning the quadrants:
		 * 6 sides of the cube times 4 possibilities of rotating (by PI/2) it around this base
		 */
		Alignment[] al = new Alignment[(firstStageSeries_Int.length==2 ? quadRot_2D.length : quadRot_3D.length)];
		double[][][] otherPolar = new double[al.length][][];
		
		for (int q=0; q<al.length; q++) {
			al[q] = new Alignment();
			
			// compute all the alignments for the other side
			otherPolar[q] = new double[otherSide.length][];
			for (int i=0; i<otherSide.length; i++)
				if (firstStageSeries_Int.length==2)
					otherPolar[q][i] = toPolar(otherSide[i], quadRot_2D[q][1], quadRot_2D[q][2]==1, false);
				else
					otherPolar[q][i] = toPolar(otherSide[i], quadRot_3D[q][3], quadRot_3D[q][4]==1, quadRot_3D[q][5]==1);

			// TODO: there must also be nicer way of figuring out these angles...
			if (firstStageSeries_Int.length==2)
				al[q].quadrotTheta = quadRot_2D[q][0];
			else {
				al[q].quadrotPsi = quadRot_3D[q][0];
				al[q].quadrotTheta = quadRot_3D[q][1];
				al[q].quadrotPhi = quadRot_3D[q][2];
			}
		}
	
		for (int q=0; q<al.length; q++) {
			for (int i=0; i<index && i<otherSide.length; i++) {
				/* only count as relevant when both thetas are != 0, because atan2 returns 0
				 * for (0,0), which really has no angle at all */
				boolean validTheta = (theta[i] != 0 && r[i] != 0) || (otherPolar[q][i][1] != 0 && otherPolar[q][i][0] != 0);
				double d_theta = 0;
				if (validTheta) {
					d_theta = angleWithinPI(otherPolar[q][i][1]-theta[i]);
					al[q].nTheta++;
				}
			
				if (firstStageSeries_Int.length == 3) {
					boolean validPhi = (phi[i] != 0 && r[i] != 0) || (otherPolar[q][i][2] != 0 && otherPolar[q][i][0] != 0);
					double d_phi = 0; 
					// see al.delta_theta, same here
					if (validPhi) {
						d_phi = angleWithinPI(otherPolar[q][i][2]-phi[i]);
						al[q].nPhi++;
					}
				
					/* in 3D there are multiple options how to rotate with 2 angles -
					 * choose the one that (with the average so far) yields the smaller error
					 * ==> naive, greedy search over what would be 2^(2*n) possibilities
					 */
					double e1=error3(otherPolar[q][i][1], theta[i], 
							(al[q].nTheta>0 ? (al[q].delta_theta+d_theta)/al[q].nTheta : 0), 
							otherPolar[q][i][2], phi[i], 
							(al[q].nPhi>0 ? (al[q].delta_phi+d_phi)/al[q].nPhi : 0),
							otherPolar[q][i][0], r[i]);
					double e2=error3(otherPolar[q][i][1], angleWithinPI(theta[i]-Math.PI), 
							(al[q].nTheta>0 ? (al[q].delta_theta+angleWithinPI(d_theta-Math.PI))/al[q].nTheta : 0), 
							otherPolar[q][i][2], angleWithinPI(phi[i]-Math.PI), 
							(al[q].nPhi>0 ? (al[q].delta_phi+angleWithinPI(d_phi-Math.PI))/al[q].nPhi : 0),
							otherPolar[q][i][0], r[i]);
					if (e1 > e2) {
						d_theta = angleWithinPI(d_theta-Math.PI);
						d_phi = angleWithinPI(d_phi-Math.PI);
						// remember what we figured out so that error will be calculated correctly
						al[q].alternativeRotate.set(i);
					}
				
					al[q].delta_phi += d_phi;
				}
				al[q].delta_theta += d_theta;
				al[q].numSamples++;
			}
			if (al[q].nTheta > 0)
				al[q].delta_theta /= al[q].nTheta;
			else if (al[q].delta_theta != 0)
				logger.warn("Delta theta is not equal zero, although we didn't have a single relevant pair to process. This should not happen!");
			if (al[q].nPhi > 0)
				al[q].delta_phi /= al[q].nPhi;
			else if (al[q].delta_phi != 0)
				logger.warn("Delta phi is not equal zero, although we didn't have a single relevant pair to process. This should not happen!");
			
			// calculate error for theta, phi, and length (magnitude)
			for (int i=0; i<index && i<otherSide.length; i++) {
				if (firstStageSeries_Int.length == 3) {
					if (!al[q].alternativeRotate.get(i))
						al[q].error += error3(otherPolar[q][i][1], theta[i], al[q].delta_theta, 
								otherPolar[q][i][2], phi[i], al[q].delta_phi,
								otherPolar[q][i][0], r[i]);
					else
						al[q].error += error3(otherPolar[q][i][1], angleWithinPI(theta[i]-Math.PI), al[q].delta_theta, 
								otherPolar[q][i][2], angleWithinPI(phi[i]-Math.PI), al[q].delta_phi,
								otherPolar[q][i][0], r[i]);
				}
				else
					al[q].error += error2(otherPolar[q][i][1], theta[i], al[q].delta_theta, 
							otherPolar[q][i][0], r[i]);
			}
			
			// don't forget to add the quadrant rotations now, because this would not be known externally
			al[q].delta_theta += al[q].quadrotTheta * Math.PI/2;
			al[q].delta_phi += al[q].quadrotPhi * Math.PI/2;
		}
		
		// again naive: return the alignment vector with the lowest error
		Alignment almin = al[0];
		for (int q=1; q<al.length; q++)
			// TODO: remove me again when rotations are correct!
			// have a small error margin here, thus use the "simple" quadrant rotations first
			if (al[q].error < almin.error-0.00001)
				almin = al[q];
		
		return almin;
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
	
	/** Returns the polar representation of coordinates given as x/y(/z). 
	 * Depending on the input (2 or 3 dimensions), there will be 1 or 2
	 * angles returned.
	 * @param coord
	 * @param permute For 2 dimensions, either 0 (no change) or 1 (x and y swapped).
	 * 				  For 3 dimensions, a value between 0 and 5 describing the permutation of x, y, and z:
	 * 				  0: x, y, z
	 * 				  1: y, x, z
	 * 				  2: x, z, y
	 * 				  3: y, z, x
	 * 				  4: z, x, y
	 * 				  5: z, y, x
	 * @negY Ignored for 2 dimensions and computed automatically so that only right-handed systems are generated.
	 * @return Polar coordinates. First element is r, second theta, optional third phi.
	 */
	private static double[] toPolar(double[] coord, int permute, boolean negX, boolean negY) {
		if (coord.length != 2 && coord.length != 3)
			throw new IllegalArgumentException("Number of dimensions must be 2 or 3");
		if (coord.length == 2 && (permute < 0 || permute > 1))
			throw new IllegalArgumentException("Only permutations 0 or 1 allowed");
		if (coord.length == 3 && (permute < 0 || permute > 5))
			throw new IllegalArgumentException("Only permutations 0 to 5 allowed");
		
		double[] ret = new double[coord.length];
		double x=0, y=0, z=0;
		boolean neg=false;
		
		if (coord.length == 2) {
			x = permute==0 ? coord[0] : coord[1];
			y = permute==0 ? coord[1] : coord[0];
			neg = permute==1;
		}
		else {
			/** There are certainly shorter formulations, but this is verbose and self-explanatory (i.e. it's hard that I mess it up). */
			switch (permute) {
			case 0: x = coord[0];
					y = coord[1];
					z = coord[2];
					// this is a right-handed coordinate system, no need to negate any axis
					neg = false;
					break;
			case 1: x = coord[1];
					y = coord[0];
					z = coord[2];
					// this is left-handed, need to negate at least one axis to make it right-handed again
					neg = true;
					break;
			case 2: x = coord[0];
					y = coord[2];
					z = coord[1];
					neg = true;
					break;
			case 3: x = coord[1];
					y = coord[2];
					z = coord[0];
					neg = false;
					break;
			case 4: x = coord[2];
					y = coord[0];
					z = coord[1];
					neg = false;
					break;
			case 5: x = coord[2];
					y = coord[1];
					z = coord[0];
					neg = true;
					break;
			}
		}
		
		if (negX) x = -x;
		if (coord.length == 2)
			if (negX ^ neg) x = -y;
		else {
			if (negY) y = -y;
			// we need to negate an even number of axes to stay right-handed
			if (negX ^ negY ^ neg) z = -z;
		}
		
		if (coord.length == 2) {
			ret[0] = Math.sqrt(x*x + y*y);
		}
		else {
			ret[0] = Math.sqrt(x*x + y*y + z*z);
			ret[2] = Math.atan2(Math.sqrt(x*x + y*y), z);
			// sanity check
			if (ret[2] < 0 || ret[2] > Math.PI)
				logger.warn("Computed phi out of tange [0; PI] (" + ret[2] + "). This should not happen!");
		}

		ret[1] = Math.atan2(y, x);
		// somewhat normalize the angle
		if (ret[1] <= -Math.PI)
			ret[1] += 2*Math.PI;
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
		if (ret[1] <= -Math.PI || ret[1] > Math.PI)
			logger.warn("Phi out of range ]-PI; PI]: " + ret[1]);
/*		if (coord[1] < -0.000001 && r [index] >= 0)
			logger.warn("y < 0 but r >= 0. This should not happen.");
		if (coord[1] > 0.000001 && r [index] <= 0)
			logger.warn("y > 0 but r <= 0. This should not happen.");*/
		
		return ret;
	}
	
	private void newSample(double[] coord) {
		if (index >= maxSegmentSize+windowSize) {
			logger.warn("Want to write more active samples than segment size, aborting. This should not happen!");
			return;
		}
	
		double[] polar = toPolar(coord, 0, false, false);
		r[index] = polar[0];
		theta[index] = polar[1];
		if (coord.length == 3)
			phi[index] = polar[2];
		
		// also remember the original values for alignWith()
		cartesian[index] = new double[coord.length];
		for (int i=0; i<coord.length; i++)
			cartesian[index][i] = coord[i];
		
		index++;
	}

	/** Resets the time series to the state as created when freshly constructing it. */
	public void reset() {
		super.reset();
		init();
	}
		
	/** Simply initializes all vectors kept in this class and is called by the constructor and reset(). */ 
	private void init() {
		r = new double[windowSize+maxSegmentSize];
		theta = new double[windowSize+maxSegmentSize];
		if (firstStageSeries_Int.length == 3)
			phi = new double[windowSize+maxSegmentSize];
		
		cartesian = new double[windowSize+maxSegmentSize][];
		index = 0;
	}
	
	/** Returns all dimensions of the active segment in cartesian coordinates.
	 * @return The length will be equal to index.
	 */
	public double[][] getCartesian() {
		double[][] ret = new double[index][];
		for (int i=0; i<index; i++)
			ret[i] = cartesian[i];
		return ret;
	}
}

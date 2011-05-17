/* Copyright Rene Mayrhofer
 * File created 2007-06-06
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.features;

public class Correlation {
	// http://en.wikipedia.org/wiki/Correlation
	/** Calculates the correlation coefficient between vectors a and b. 
	 * @param a The first vector or time series.
	 * @param b The second vector or time series.
	 * @param offset If >0, then it will be added to the index for accessing b, 
	 *               i.e. elements a[i] and b[i+offset] will be compared.
	 *               If <0, then it will be added to the index for accessing a, 
	 *               i.e. elements a[i+offset] and b[i] will be compared.
	 */
	public double correlationCoefficient(int[] a, int[] b, int offset) {
		if (a == null || b == null) 
			throw new IllegalArgumentException("Time series parameters for correlation computation can not be null");
		if (a.length == 0 || b.length == 0)
			throw new IllegalArgumentException("Time series for correlation computation can not be empty");
		if (offset >= 0 && a.length + offset > b.length)
			throw new IllegalArgumentException("Time series b is not long enough (only " + 
					b.length + " but need at minimum " + a.length + " plus offset " + offset + ")");
		if (offset <= 0 && b.length + offset > a.length)
			throw new IllegalArgumentException("Time series a is not long enough (only " + 
					a.length + " but need at minimum " + b.length + " plus offset " + -offset + ")");
	
		int n = (offset >= 0) ? a.length : b.length;
		int offA = (offset < 0) ? -offset : 0; 
		int offB = (offset > 0) ? offset : 0; 
		int sumA=0, sumB=0, sum2A=0, sum2B=0, sumAB=0;
		for (int i=0; i<n; i++) {
			sumA += a[i+offA];
			sumB += b[i+offB];
			sum2A += a[i+offA] * a[i+offA];
			sum2B += b[i+offB] * b[i+offB];
			sumAB += a[i+offA] * b[i+offB];
		}
		
		return (sumAB - sumA*sumB/n) / Math.sqrt((sum2A - sumA+sumA/n) * (sum2B - sumB*sumB/n));
	}
	
	/** Calculates the correlation coefficient between vectors a and b. 
	 * @param a The first vector or time series.
	 * @param b The second vector or time series.
	 * @param offset If >0, then it will be added to the index for accessing b, 
	 *               i.e. elements a[i] and b[i+offset] will be compared.
	 *               If <0, then it will be added to the index for accessing a, 
	 *               i.e. elements a[i+offset] and b[i] will be compared.
	 */
	public double correlationCoefficient(double[] a, double[] b, int offset) {
		if (a == null || b == null) 
			throw new IllegalArgumentException("Time series parameters for correlation computation can not be null");
		if (a.length == 0 || b.length == 0)
			throw new IllegalArgumentException("Time series for correlation computation can not be empty");
		if (offset >= 0 && a.length + offset > b.length)
			throw new IllegalArgumentException("Time series b is not long enough (only " + 
					b.length + " but need at minimum " + a.length + " plus offset " + offset + ")");
		if (offset <= 0 && b.length + offset > a.length)
			throw new IllegalArgumentException("Time series a is not long enough (only " + 
					a.length + " but need at minimum " + b.length + " plus offset " + -offset + ")");
	
		int n = (offset >= 0) ? a.length : b.length;
		int offA = (offset < 0) ? -offset : 0; 
		int offB = (offset > 0) ? offset : 0; 
		double sumA=0, sumB=0, sum2A=0, sum2B=0, sumAB=0;
		for (int i=0; i<n; i++) {
			sumA += a[i+offA];
			sumB += b[i+offB];
			sum2A += a[i+offA] * a[i+offA];
			sum2B += b[i+offB] * b[i+offB];
			sumAB += a[i+offA] * b[i+offB];
		}
		
		return (sumAB - sumA*sumB/n) / Math.sqrt((sum2A - sumA+sumA/n) * (sum2B - sumB*sumB/n));
	}

	/** Not yet implemented.
	 * @param a
	 * @param b
	 * @param maxOffsetSearch  
	 */
	public int maxCorrelationCoefficient(int[] a, int[] b, int maxOffsetSearch) {
		return 0;
	}

	/** Not yet implemented.
	 * @param a
	 * @param b
	 * @param maxOffsetSearch  
	 */
	public double maxCorrelationCoefficient(double[] a, double[] b, int maxOffsetSearch) {
		return 0;
	}
}

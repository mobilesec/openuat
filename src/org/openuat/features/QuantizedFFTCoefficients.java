/* Copyright Rene Mayrhofer
 * File created 2006-10-02
 * Initial public release 2007-03-29
 * 
 * This implementation is based on the "cohere", "pwelch", "hanning", 
 * and "conj" functions in Octave and Octave Forge.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.features;

/** This class computes quantized FFT coefficients from time series / sensor 
 * signals. There are 4 types that can be computed, a combination of:
 * - linear or exponential quantization bands
 * - quantize either FFT coefficients directly or their pairwise sums
 *
 * @author Rene Mayrhofer
 * @version 1.0
*/
public class QuantizedFFTCoefficients {
	/** This is a small helper function just to do the pairwise adding of 
	 * vector elements.
	 */
	private static double[] addPairwise(double[] s, int max_ind) {
		double sums[] = new double[max_ind];
		for (int k=0; k<max_ind; k++) {
			sums[k] = s[k] + s[k+1];
			//System.out.println("k=" + k + ": sum1=" + sums1[k] + " sum2=" + sums2[k]);
		}
		return sums;
	}

	/** This is a small helper function which compares two arrays of arrays
	 * and returns true when any of the arrays in cand1 matches any of the
	 * arrays in cand2, up to a maximum index max_ind, otherwise false.
	 */
	private static boolean compareQuantizedVectors(int[][] cand1, int[][] cand2, int max_ind) {
		for (int i=0; i<cand1.length; i++) {
			for (int j=0; j<cand2.length; j++) {
				boolean equal = true;
				for (int k=0; k<max_ind && equal; k++) {
					if (cand1[i][k] != cand2[j][k])
						equal = false;
				}
				if (equal) {
					//System.out.println("Match at i=" + i + ", j=" + j);
					return true;
				}
			}
		}
		return false;
	}
	
	/** This is the main method of this class, which combines all 4 possible
	 * methods for better performance. That is, when more than one method 
	 * needs to be computed, intermediate data will be re-used.
	 * @param segment The segment to compute quantized FFT coefficients on.
	 * @param offset Elements of segment will be used starting with this index.
	 * @param numFFTPoints The number of FFT points for computing the 
	 *                     coefficients. This is the number of samples used 
	 *                     from segment.
	 * @param numFFTCoeffCompared The number of FFT coefficients to output.
	 * @param numQuantLevels The number of quantization levels to use.
	 * @param numCandidates The number of candidates to generate with 
	 *                      different quantization offsets.
	 * @param doDirect If true, the FFT coefficients will be used directly.
	 * @param doPairwise If true, the FFT coefficients will be added pairwise.
	 * @param doLinear If true, linear quantization bands will be used.
	 * @param doExponential If true, exponential quantization bands will be used.
	 * @return An array of 4 arrays of arrays. Each element of this array 
	 *         contains an array of quantized FFT coefficients, which are the
	 *         multiple candidates. That is, the elements of the returned array
	 *         are arrays of numCandidates arrays of len integers.
	 *         The first element is type 1, i.e. direct linear.
	 *         The first element is type 2, i.e. direct exponential.
	 *         The first element is type 3, i.e. pairwise linear.
	 *         The first element is type 4, i.e. pairwise linear.
	 *         Elements will be null when the respective boolean combination
	 *         has not been set.
	 */
	private static int[][][] computeFFTCoefficientsCandidates(double[] segment, 
			int offset, int numFFTPoints, int numFFTCoeffCompared, int numQuantLevels, 
			int numCandidates, boolean doDirect, boolean doPairwise, 
			boolean doLinear, boolean doExponential) {
		double[] allCoeff = FFT.fftPowerSpectrum(segment, offset, numFFTPoints);

		double[][] toQuantize = new double[2][];
		if (doDirect) {
			// for better performance, only use the first max_ind coefficients since the others will not be compared anyway
			toQuantize[0] = new double[numFFTCoeffCompared];
			System.arraycopy(allCoeff, 0, toQuantize[0], 0, numFFTCoeffCompared);
			
			// HACK HACK HACK: set DC components to 0
			toQuantize[0][0] = 0;
		}
		if (doPairwise) {
			// TODO: also need the hack here?
			
			toQuantize[1] = addPairwise(allCoeff, numFFTCoeffCompared);
		}
		
		int[][][] ret = new int[4][][];
		for (int i=0; i<2; i++) {
			if (toQuantize[i] != null) {
				double max = Quantizer.max(toQuantize[i]);
				if (doLinear)
					ret[2*i] = Quantizer.generateCandidates(toQuantize[i], 0, max, numQuantLevels, false, numCandidates, false);
				if (doExponential)
					ret[2*i+1] = Quantizer.generateCandidates(toQuantize[i], 0, max, numQuantLevels, true, numCandidates, false);
			}
		}
		return ret;
	}

	/** Computes cofficient vector candidates, quantized with different 
	 * offsets.
	 * @param segment The segment to compute quantized FFT coefficients on.
	 * @param offset Elements of segment will be used starting with this index.
	 * @param numFFTPoints The number of FFT points for computing the 
	 *                     coefficients. This is the number of samples used 
	 *                     from segment.
	 * @param numFFTCoeffCompared The number of FFT coefficients to output.
	 * @param numQuantLevels The number of quantization levels to use.
	 * @param numCandidates The number of candidates to generate with 
	 *                      different quantization offsets.
	 * @param doDirect If true, the FFT coefficients will be used directly.
	 * @param doPairwise If true, the FFT coefficients will be added pairwise.
	 * @param doLinear If true, linear quantization bands will be used.
	 * @param doExponential If true, exponential quantization bands will be used.
	 * @return An array of quantized FFT coefficients, which are the
	 *         multiple candidates. That is, the returned array has 
	 *         numCandidates elements, which are arrays of len integers.
	 */
	public static int[][] computeFFTCoefficientsCandidates(double[] segment, 
			int offset, int numFFTPoints, int numFFTCoeffCompared, int numQuantLevels, 
			int numCandidates, boolean addPairwise, boolean exponentialBands) {
		int retInd = (addPairwise ? 2 : 0) + (exponentialBands ? 1 : 0);  
		return computeFFTCoefficientsCandidates(segment, offset, numFFTPoints, 
				numFFTCoeffCompared, numQuantLevels, numCandidates, 
				!addPairwise, addPairwise, 
				!exponentialBands, exponentialBands)[retInd];
	}

	/** This is a helper function to compute and compare all 4 types.
	 * @return An boolean array of 4 elements, true when the respective
	 *         type contains at least one match among the candidates.
	 */
	public static boolean[] quantizeAndCompare(double[] vector1, double[] vector2, int offset, int numFFTPoints,
			int numFFTCoeffCompared, int numQuantLevels, int numCandidates) {
		boolean[] ret = new boolean[4];
		
		int[][][] cand1 = computeFFTCoefficientsCandidates(vector1, offset, 
				numFFTPoints, numFFTCoeffCompared, numQuantLevels, numCandidates, 
				true, true, true, true);
		int[][][] cand2 = computeFFTCoefficientsCandidates(vector2, offset, 
				numFFTPoints, numFFTCoeffCompared, numQuantLevels, numCandidates, 
				true, true, true, true);
		
		/*for (int i=0; i<4; i++) 
			for (int j=0; j<numCandidates; j++) {
				System.out.print("Cand1 type" + i + " cand" + j + ": ");
				for (int k=0; k<len; k++)
					System.out.print(cand1[i][j][k] + " ");
				System.out.println();
				System.out.print("Cand2 type" + i + " cand" + j + ": ");
				for (int k=0; k<len; k++)
					System.out.print(cand2[i][j][k] + " ");
				System.out.println();
			}*/

		for (int i=0; i<4; i++)
			ret[i] = compareQuantizedVectors(cand1[i], cand2[i], numFFTCoeffCompared);
		return ret;
	}
}

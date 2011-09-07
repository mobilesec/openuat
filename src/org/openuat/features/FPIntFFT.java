/* Copyright Rene Mayrhofer
 * File created 2007-05-12
 * 
 * This implementation is based on the public domain Fixed-point in-place 
 * Fast Fourier Transform by Tom Roberts, Malcolm Slaney, and Dimitrios P. Bouras.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.features;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**   All data are fixed-point short integers, in which -32768
  to +32768 represent -1.0 to +1.0 respectively. Integer
  arithmetic is used for speed, instead of the more natural
  floating-point.
  
    For the forward FFT (time -> freq), fixed scaling is
  performed to prevent arithmetic overflow, and to map a 0dB
  sine/cosine wave (i.e. amplitude = 32767) to two -6dB freq
  coefficients. The return value is always 0.
  
    For the inverse FFT (freq -> time), fixed scaling cannot be
  done, as two 0dB coefficients would sum to a peak amplitude
  of 64K, overflowing the 32k range of the fixed-point integers.
  Thus, the fix_fft() routine performs variable scaling, and
  returns a value which is the number of bits LEFT by which
  the output must be shifted to get the actual amplitude
  (i.e. if fix_fft() returns 3, each value of fr[] and fi[]
  must be multiplied by 8 (2**3) for proper scaling.
  Clearly, this cannot be done within fixed-point short
  integers. In practice, if the result is to be used as a
  filter, the scale_shift can usually be ignored, as the
  result will be approximately correctly normalized as is.
  
  @author Tom Roberts  11/8/89, 
          made portable Malcolm Slaney 12/15/94,
          enhanced Dimitrios P. Bouras  14 Jun 2006,
          ported to Java Rene Mayrhofer 2007-05-12 */
public class FPIntFFT {
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger("org.openuat.features.FPIntFFT" /*FPIntFFT.class*/);

	private final static int N_WAVE = 1024; /* full length of Sinewave[] */
	private final static int LOG2_N_WAVE = 10; /* log2(N_WAVE) */
	
	private final static int[] Sinewave = new int[] {
	      0,    201,    402,    603,    804,   1005,   1206,   1406,
	      1607,   1808,   2009,   2209,   2410,   2610,   2811,   3011,
	      3211,   3411,   3611,   3811,   4011,   4210,   4409,   4608,
	      4807,   5006,   5205,   5403,   5601,   5799,   5997,   6195,
	      6392,   6589,   6786,   6982,   7179,   7375,   7571,   7766,
	      7961,   8156,   8351,   8545,   8739,   8932,   9126,   9319,
	      9511,   9703,   9895,  10087,  10278,  10469,  10659,  10849,
	     11038,  11227,  11416,  11604,  11792,  11980,  12166,  12353,
	     12539,  12724,  12909,  13094,  13278,  13462,  13645,  13827,
	     14009,  14191,  14372,  14552,  14732,  14911,  15090,  15268,
	     15446,  15623,  15799,  15975,  16150,  16325,  16499,  16672,
	     16845,  17017,  17189,  17360,  17530,  17699,  17868,  18036,
	     18204,  18371,  18537,  18702,  18867,  19031,  19194,  19357,
	     19519,  19680,  19840,  20000,  20159,  20317,  20474,  20631,
	     20787,  20942,  21096,  21249,  21402,  21554,  21705,  21855,
	     22004,  22153,  22301,  22448,  22594,  22739,  22883,  23027,
	     23169,  23311,  23452,  23592,  23731,  23869,  24006,  24143,
	     24278,  24413,  24546,  24679,  24811,  24942,  25072,  25201,
	     25329,  25456,  25582,  25707,  25831,  25954,  26077,  26198,
	     26318,  26437,  26556,  26673,  26789,  26905,  27019,  27132,
	     27244,  27355,  27466,  27575,  27683,  27790,  27896,  28001,
	     28105,  28208,  28309,  28410,  28510,  28608,  28706,  28802,
	     28897,  28992,  29085,  29177,  29268,  29358,  29446,  29534,
	     29621,  29706,  29790,  29873,  29955,  30036,  30116,  30195,
	     30272,  30349,  30424,  30498,  30571,  30643,  30713,  30783,
	     30851,  30918,  30984,  31049,  31113,  31175,  31236,  31297,
	     31356,  31413,  31470,  31525,  31580,  31633,  31684,  31735,
	     31785,  31833,  31880,  31926,  31970,  32014,  32056,  32097,
	     32137,  32176,  32213,  32249,  32284,  32318,  32350,  32382,
	     32412,  32441,  32468,  32495,  32520,  32544,  32567,  32588,
	     32609,  32628,  32646,  32662,  32678,  32692,  32705,  32717,
	     32727,  32736,  32744,  32751,  32757,  32761,  32764,  32766,
	     32767,  32766,  32764,  32761,  32757,  32751,  32744,  32736,
	     32727,  32717,  32705,  32692,  32678,  32662,  32646,  32628,
	     32609,  32588,  32567,  32544,  32520,  32495,  32468,  32441,
	     32412,  32382,  32350,  32318,  32284,  32249,  32213,  32176,
	     32137,  32097,  32056,  32014,  31970,  31926,  31880,  31833,
	     31785,  31735,  31684,  31633,  31580,  31525,  31470,  31413,
	     31356,  31297,  31236,  31175,  31113,  31049,  30984,  30918,
	     30851,  30783,  30713,  30643,  30571,  30498,  30424,  30349,
	     30272,  30195,  30116,  30036,  29955,  29873,  29790,  29706,
	     29621,  29534,  29446,  29358,  29268,  29177,  29085,  28992,
	     28897,  28802,  28706,  28608,  28510,  28410,  28309,  28208,
	     28105,  28001,  27896,  27790,  27683,  27575,  27466,  27355,
	     27244,  27132,  27019,  26905,  26789,  26673,  26556,  26437,
	     26318,  26198,  26077,  25954,  25831,  25707,  25582,  25456,
	     25329,  25201,  25072,  24942,  24811,  24679,  24546,  24413,
	     24278,  24143,  24006,  23869,  23731,  23592,  23452,  23311,
	     23169,  23027,  22883,  22739,  22594,  22448,  22301,  22153,
	     22004,  21855,  21705,  21554,  21402,  21249,  21096,  20942,
	     20787,  20631,  20474,  20317,  20159,  20000,  19840,  19680,
	     19519,  19357,  19194,  19031,  18867,  18702,  18537,  18371,
	     18204,  18036,  17868,  17699,  17530,  17360,  17189,  17017,
	     16845,  16672,  16499,  16325,  16150,  15975,  15799,  15623,
	     15446,  15268,  15090,  14911,  14732,  14552,  14372,  14191,
	     14009,  13827,  13645,  13462,  13278,  13094,  12909,  12724,
	     12539,  12353,  12166,  11980,  11792,  11604,  11416,  11227,
	     11038,  10849,  10659,  10469,  10278,  10087,   9895,   9703,
	      9511,   9319,   9126,   8932,   8739,   8545,   8351,   8156,
	      7961,   7766,   7571,   7375,   7179,   6982,   6786,   6589,
	      6392,   6195,   5997,   5799,   5601,   5403,   5205,   5006,
	      4807,   4608,   4409,   4210,   4011,   3811,   3611,   3411,
	      3211,   3011,   2811,   2610,   2410,   2209,   2009,   1808,
	      1607,   1406,   1206,   1005,    804,    603,    402,    201,
	         0,   -201,   -402,   -603,   -804,  -1005,  -1206,  -1406,
	     -1607,  -1808,  -2009,  -2209,  -2410,  -2610,  -2811,  -3011,
	     -3211,  -3411,  -3611,  -3811,  -4011,  -4210,  -4409,  -4608,
	     -4807,  -5006,  -5205,  -5403,  -5601,  -5799,  -5997,  -6195,
	     -6392,  -6589,  -6786,  -6982,  -7179,  -7375,  -7571,  -7766,
	     -7961,  -8156,  -8351,  -8545,  -8739,  -8932,  -9126,  -9319,
	     -9511,  -9703,  -9895, -10087, -10278, -10469, -10659, -10849,
	    -11038, -11227, -11416, -11604, -11792, -11980, -12166, -12353,
	    -12539, -12724, -12909, -13094, -13278, -13462, -13645, -13827,
	    -14009, -14191, -14372, -14552, -14732, -14911, -15090, -15268,
	    -15446, -15623, -15799, -15975, -16150, -16325, -16499, -16672,
	    -16845, -17017, -17189, -17360, -17530, -17699, -17868, -18036,
	    -18204, -18371, -18537, -18702, -18867, -19031, -19194, -19357,
	    -19519, -19680, -19840, -20000, -20159, -20317, -20474, -20631,
	    -20787, -20942, -21096, -21249, -21402, -21554, -21705, -21855,
	    -22004, -22153, -22301, -22448, -22594, -22739, -22883, -23027,
	    -23169, -23311, -23452, -23592, -23731, -23869, -24006, -24143,
	    -24278, -24413, -24546, -24679, -24811, -24942, -25072, -25201,
	    -25329, -25456, -25582, -25707, -25831, -25954, -26077, -26198,
	    -26318, -26437, -26556, -26673, -26789, -26905, -27019, -27132,
	    -27244, -27355, -27466, -27575, -27683, -27790, -27896, -28001,
	    -28105, -28208, -28309, -28410, -28510, -28608, -28706, -28802,
	    -28897, -28992, -29085, -29177, -29268, -29358, -29446, -29534,
	    -29621, -29706, -29790, -29873, -29955, -30036, -30116, -30195,
	    -30272, -30349, -30424, -30498, -30571, -30643, -30713, -30783,
	    -30851, -30918, -30984, -31049, -31113, -31175, -31236, -31297,
	    -31356, -31413, -31470, -31525, -31580, -31633, -31684, -31735,
	    -31785, -31833, -31880, -31926, -31970, -32014, -32056, -32097,
	    -32137, -32176, -32213, -32249, -32284, -32318, -32350, -32382,
	    -32412, -32441, -32468, -32495, -32520, -32544, -32567, -32588,
	    -32609, -32628, -32646, -32662, -32678, -32692, -32705, -32717,
	    -32727, -32736, -32744, -32751, -32757, -32761, -32764, -32766,		
	};
	
	/**   FIX_MPY() - fixed-point multiplication & scaling.
  Substitute inline assembly for hardware-specific
  optimization suited to a particluar DSP processor.
  Scaling ensures that result remains 16-bit. */
	private final static short FIX_MPY(short a, short b) {
		/* shift right one less bit (i.e. 15-1) */
		int c = (a * b) >> 14;
		/* last bit shifted out = rounding-bit */
		b = (short) (c & 0x01);
		/* last shift + rounding bit */
		a = (short) ((c >> 1) + b);
		return a;
	}
	
	/**  fix_fft() - perform forward/inverse fast Fourier transform.
  fr[n],fi[n] are real and imaginary arrays, both INPUT AND
  RESULT (in-place FFT), with 0 <= n < 2**m; set inverse to
  0 for forward transform (FFT), or 1 for iFFT.
	 */
	public final static int fix_fft(short fr[], short fi[], short m, int off_fr, int off_fi, boolean inverse) {
		int mr, nn, i, j, l, k, istep, n, scale;
		boolean shift;
		short qr, qi, tr, ti, wr, wi;

		n = 1 << m;
		
		if (fr.length < n+off_fr) {
			logger.error("fr is too short, expecting " + n + " elements, got "
					+ fr.length + " and accessing with offset " + off_fr);
			return -1;
		}
		if (fi.length < n+off_fi) {
			logger.error("fi is too short, expecting " + n + " elements, got "
					+ fi.length + " and accessing with offset " + off_fi);
			return -1;
		}

		/* max FFT size = N_WAVE */
		if (n > N_WAVE) {
			logger.error("Can only compute FFT windows of up to " + N_WAVE + 
					", but requested  + n");
			return -1;
		}

		mr = 0;
		nn = n - 1;
		scale = 0;

		/* decimation in time - re-order data */
		for (m=1; m<=nn; ++m) {
			l = n;
			do {
				l >>= 1;
			} while (mr+l > nn);
			mr = (mr & (l-1)) + l;

			if (mr <= m)
				continue;
			tr = fr[m+off_fr];
			fr[m+off_fr] = fr[mr+off_fr];
			fr[mr+off_fr] = tr;
			ti = fi[m+off_fi];
			fi[m+off_fi] = fi[mr+off_fi];
			fi[mr+off_fi] = ti;
		}

		l = 1;
		k = LOG2_N_WAVE-1;
		while (l < n) {
			if (inverse) {
				/* variable scaling, depending upon data */
				shift = false;
				for (i=0; i<n; ++i) {
					j = fr[i+off_fr];
					if (j < 0)
						j = -j;
					m = fi[i+off_fi];
					if (m < 0)
						m = (short) -m;
					if (j > 16383 || m > 16383) {
						shift = true;
						break;
					}
				}
				if (shift)
					++scale;
			} else {
				/*
				  fixed scaling, for proper normalization --
				  there will be log2(n) passes, so this results
				  in an overall factor of 1/n, distributed to
				  maximize arithmetic accuracy.
				*/
				shift = true;
			}
			/*
			  it may not be obvious, but the shift will be
			  performed on each data point exactly once,
			  during this pass.
			*/
			istep = l << 1;
			for (m=0; m<l; ++m) {
				j = m << k;
				/* 0 <= j < N_WAVE/2 */
				wr = (short) (Sinewave[j+N_WAVE/4]);
				wi = (short) -Sinewave[j];
				if (inverse)
					wi = (short) -wi;
				if (shift) {
					wr >>= 1;
					wi >>= 1;
				}
				for (i=m; i<n; i+=istep) {
					j = i + l;
					tr = (short) (FIX_MPY(wr,fr[j+off_fr]) - FIX_MPY(wi,fi[j+off_fi]));
					ti = (short) (FIX_MPY(wr,fi[j+off_fi]) + FIX_MPY(wi,fr[j+off_fr]));
					qr = fr[i+off_fr];
					qi = fi[i+off_fi];
					if (shift) {
						qr >>= 1;
						qi >>= 1;
					}
					fr[j+off_fr] = (short) (qr - tr);
					fi[j+off_fi] = (short) (qi - ti);
					fr[i+off_fr] = (short) (qr + tr);
					fi[i+off_fi] = (short) (qi + ti);
				}
			}
			--k;
			l = istep;
		}
		return scale;
	}
	
	/** fix_fftr() - forward/inverse FFT on array of real numbers.
  Real FFT/iFFT using half-size complex FFT by distributing
  even/odd samples into real/imaginary arrays respectively.
  In order to save data space (i.e. to avoid two arrays, one
  for real, one for imaginary samples), we proceed in the
  following two steps: a) samples are rearranged in the real
  array so that all even samples are in places 0-(N/2-1) and
  all imaginary samples in places (N/2)-(N-1), and b) fix_fft
  is called with fr and fi pointing to index 0 and index N/2
  respectively in the original array. The above guarantees
  that fix_fft "sees" consecutive real samples as alternating
  real and imaginary samples in the complex array. */
	public final static int fix_fftr(short f[], short m, boolean inverse) {
		m--;
		int i, N = 1<<m, scale = 0, off_fr=0, off_fi=N;
		short tt;
		
		if (f.length != N<<1) {
			logger.error("Length of f does not match m, expected " + (N<<1) +
					", but got " + f.length);
			return -1;
		}

		if (inverse)
			scale = fix_fft(f, f, m, off_fi, off_fr, inverse);
		for (i=1; i<N; i+=2) {
			tt = f[N+i-1];
			f[N+i-1] = f[i];
			f[i] = tt;
		}
		if (! inverse)
			scale = fix_fft(f, f, m, off_fi, off_fr, inverse);
		return scale;
	}
}

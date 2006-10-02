/* File created by Rene Mayrhofer, implementation of FFT by Robert Sedgewick and Kevin Wayne
 * No specific license seems to be distributed with 
 * http://www.cs.princeton.edu/introcs/97data/FFT.java.html, so I assume the original
 * authors put it under public domain. All copyright on the code is theirs, but I hope
 * it is okay to release their implementation with this toolkit under the terms of the
 * GNU LGPL. 
 * File created 2006-05-01
 * 
 * Extended by Rene Mayrhofer to compute powerSpectrum and added correct 
 * Javadoc comments.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.eu.mayrhofer.features;


/** Compute the FFT and inverse FFT of a length N complex sequence.
 *  Bare bones implementation that runs in O(N log N) time. Our goal
 *  is to optimize the clarity of the code, rather than performance.
 *
 *  Limitations
 *  -----------
 *   -  assumes N is a power of 2
 *
 *   -  not the most memory efficient algorithm (because it uses
 *      an object type for representing complex numbers and because
 *      it re-allocates memory for the subarray, instead of doing
 *      in-place or reusing a single temporary array)
 *  
 * @author Adapted by Rene Mayrhofer, very heavily based on code by Robert Sedgewick and Kevin Wayne
 */
public class FFT {
    /** compute the FFT of x[], assuming its length is a power of 2. */
    public static Complex[] fft(Complex[] x) {
        int N = x.length;

        // base case
        if (N == 1) return new Complex[] { x[0] };

        // radix 2 Cooley-Tukey FFT
        if (N % 2 != 0) throw new RuntimeException("N is not a power of 2");

        // fft of even terms
        Complex[] even = new Complex[N/2];
        for (int k = 0; k < N/2; k++) even[k] = x[2*k];
        Complex[] q = fft(even);

        // fft of odd terms
        Complex[] odd  = even;  // reuse the array
        for (int k = 0; k < N/2; k++) odd[k]  = x[2*k + 1];
        Complex[] r = fft(odd);

        // combine
        Complex[] y = new Complex[N];
        for (int k = 0; k < N/2; k++) {
            double kth = -2 * k * Math.PI / N;
            Complex wk = new Complex(Math.cos(kth), Math.sin(kth));
            y[k]       = q[k].plus(wk.times(r[k]));
            y[k + N/2] = q[k].minus(wk.times(r[k]));
        }
        return y;
    }


    /** compute the inverse FFT of x[], assuming its length is a power of 2. */
    public static Complex[] ifft(Complex[] x) {
        int N = x.length;
        Complex[] y = new Complex[N];

        // take conjugate
        for (int i = 0; i < N; i++)
            y[i] = x[i].conjugate();

        // compute forward FFT
        y = fft(y);

        // take conjugate again
        for (int i = 0; i < N; i++)
            y[i] = y[i].conjugate();

        // divide by N
        for (int i = 0; i < N; i++)
            y[i] = y[i].times(1.0 / N);

        return y;

    }

    /** compute the circular convolution of x and y. */
    public static Complex[] cconvolve(Complex[] x, Complex[] y) {

        // should probably pad x and y with 0s so that they have same length
        // and are powers of 2
        if (x.length != y.length) throw new RuntimeException("Dimensions don't agree");

        int N = x.length;

        // compute FFT of each sequence
        Complex[] a = fft(x);
        Complex[] b = fft(y);

        // point-wise multiply
        Complex[] c = new Complex[N];
        for (int i = 0; i < N; i++)
            c[i] = a[i].times(b[i]);

        // compute inverse FFT
        return ifft(c);
    }


    /** compute the linear convolution of x and y. */
    public static Complex[] convolve(Complex[] x, Complex[] y) {
        Complex ZERO = new Complex(0, 0);

        Complex[] a = new Complex[2*x.length];
        for (int i = 0; i < x.length; i++) a[i] = x[i];
        for (int i = x.length; i < 2*x.length; i++) a[i] = ZERO;

        Complex[] b = new Complex[2*y.length];
        for (int i = 0; i < y.length; i++) b[i] = y[i];
        for (int i = y.length; i < 2*y.length; i++) b[i] = ZERO;

        return cconvolve(a, b);
    }
    
    /** Computes the power spectrum of a complex signal, see e.g.
     * http://www.mathworks.com/access/helpdesk/help/techdoc/ref/fft.html
     */
    public static double[] powerSpectrum(Complex[] x) {
    	double[] powspec = new double[x.length];
    	// the definition is Pff = x .* conj(x) / fftpoints
    	// but x * conj(x) just gives us the squared magnitude, so do it quicker
    	for (int i=0; i<x.length; i++)
    		powspec[i] = (x[i].getRe() * x[i].getRe() + x[i].getIm() * x[i].getIm()) / x.length;
    	return powspec;
    }
    
    /** This is a helper function which computes the FFT power spectrum coefficients
     * of a signal in time domain. It first creates a Complex array out of the double
     * array (with imaginary parts set to 0), then computes the complex FFT coefficients
     * and finally returns the power spectrum of these coefficients. 
     * 
     * @param x The input time series.
     * @param offset Values will be taken from the time series starting at this offset.
     * @param len This number of values will be used from the time series.
     */
    public static double[] fftPowerSpectrum(double[] x, int offset, int len) {
    	if (len > x.length)
    		throw new IllegalArgumentException("Length requested is larger than the number of elements in the time series");
    	if (offset < 0 || offset > x.length-len+1)
    		throw new IllegalArgumentException("offset must be >= 0 and <= x.length-len+1");
    	
    	Complex[] x1 = new Complex[len];
    	for (int i=0; i<len; i++)
    		x1[i] = new Complex(x[offset+i], 0);
    	return powerSpectrum(fft(x1));
    }



    //////////////////// testing code begins here //////////////////
    public static void main(String[] args) { 
        int N = Integer.parseInt(args[0]);
        Complex[] x = new Complex[N];

        // original data
        for (int i = 0; i < N; i++) {
            x[i] = new Complex(i, 0);
            x[i] = new Complex(-2*Math.random() + 1, 0);
        }
        for (int i = 0; i < N; i++)
            System.out.println(x[i]);
        System.out.println();

        // FFT of original data
        Complex[] y = fft(x);
        for (int i = 0; i < N; i++)
            System.out.println(y[i]);
        System.out.println();

        // take inverse FFT
        Complex[] z = ifft(y);
        for (int i = 0; i < N; i++)
            System.out.println(z[i]);
        System.out.println();

        // circular convolution of x with itself
        Complex[] c = convolve(x, x);
        for (int i = 0; i < N; i++)
            System.out.println(c[i]);
        System.out.println();

        // linear convolution of x with itself
        Complex[] d = convolve(x, x);
        for (int i = 0; i < d.length; i++)
            System.out.println(d[i]);
        System.out.println();

    }
}

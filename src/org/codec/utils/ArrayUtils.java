package org.codec.utils;/*
 * Copyright 2002 by the authors. All rights reserved.
 *
 * Author: Cristina V Lopes
 */


/**
 * Copyright (c) 2007, Regents of the University of California
 * All rights reserved.
 * ====================================================================
 * Licensed under the BSD License. Text as follows.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   - Neither the name of University of California,Irvine nor the names
 *     of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 * 
 * @author Crista Lopes (lopes at uci dot edu)
 * @version 1.0
 * 
 */


public class ArrayUtils {

    /**
     * Create a new array of length 'length' and fill it from array 'array'
     *
     * @param array  the array from which to take the subsection
     * @param start  the index from which to start the subsection copy
     * @param length the length of the returned array.
     * @return byte[] of length 'length', padded with zeros if array.length is shorter than 'start' + 'length'
     *         <p/>
     *         NOTE! if start + length goes beyond the end of array.length, the returned value will be padded with 0s.
     */
    public static byte[] subarray(byte[] array, int start, int length) {
        byte[] result = new byte[length];
        for (int i = 0; (i < length) && (i + start < array.length); i++) {
            result[i] = array[i + start];
        }
        return result;
    }

    /**
     * Converts the input matrix into a single dimensional array by transposing and concatenating the columns
     *
     * @param input a 2D array whose columns will be concatenated
     * @return the concatenated array
     */
    public static byte[] concatenate(byte[][] input) {
        //sum the lengths of the columns
        int totalLength = 0;
        for (int i = 0; i < input.length; i++) {
            totalLength += input[i].length;
        }
        //create the result array
        byte[] result = new byte[totalLength];

        //populate the result array
        int currentIndex = 0;
        for (int i = 0; i < input.length; i++) {
            for (int j = 0; j < input[i].length; j++) {
                result[currentIndex++] = input[i][j];
            }
        }
        return result;
    }

    /**
     * Concatenates two arrays
     */
    public static byte[] concatenate(byte[] a, byte[] b) {

        int len = a.length + b.length;
        byte[] result = new byte[len];
        int a_len = a.length;
        int b_len = b.length;

        for (int i = 0; i < a_len; i++)
            result[i] = a[i];

        for (int i = 0; i < b_len; i++)
            result[i + a_len] = b[i];

        return result;
    }


    /**
     * @param sequence the array of floats to return as a shifted and clipped array of bytes
     * @return byte[i] = sequence[i] * org.codec.utils.Constants.kFloatToByteShift cast to a byte
     *         Note!: This doesn't handle cast/conversion issues, so don't use this unless you understand the code
     */
    public static byte[] getByteArrayFromDoubleArray(double[] sequence) {
        byte[] result = new byte[sequence.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) ((sequence[i] * Constants.kFloatToByteShift) - 1);
        }
        return result;
    }
}

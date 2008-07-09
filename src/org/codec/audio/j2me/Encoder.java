/**
 * Modified by Iulia Ion
 */

package org.codec.audio.j2me; 

/**
 * Copyright 2002 by the authors. All rights reserved.
 *
 * Author: Cristina V Lopes
 */


import org.codec.utils.ArrayUtils;
import org.codec.utils.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
public class Encoder implements Constants {

    /**
     * encodeStream is the public function of class org.codec.audio.Encoder.
     *
     * @param input  the stream of bytes to encode
     * @param output the stream of audio samples representing the input,
     *               prefixed with an hail signal and a calibration signal
     */
    public static void encodeStream(InputStream input, OutputStream output) throws IOException {
        byte[] zeros = new byte[kSamplesPerDuration];

        //write out the hail and calibration sequences
        output.write(zeros);
        output.write(getHailSequence());
        output.write(getCalibrationSequence());

        //now write the data
        int read = 0;
        byte[] buff = new byte[kBytesPerDuration];
        while ((read = input.read(buff)) == kBytesPerDuration) {
            output.write(Encoder.encodeDuration(buff));
        }
        if (read > 0) {
            System.out.println("CHECK");
            for (int i = read; i < kBytesPerDuration; i++) {
                buff[i] = 0;
            }
            output.write(Encoder.encodeDuration(buff));
            output.write(Encoder.encodeDuration(buff));
        } else
            output.write(Encoder.encodeDuration(buff));
        
    }

    /**
     * @param bitPosition the position in the kBytesPerDuration wide byte array for which you want a frequency
     * @return the frequency in which to sound to indicate a 1 for this bitPosition
     *         NOTE!: This blindly assumes that bitPosition is in the range [0 - 7]
     */
    public static double getFrequency(int bitPosition) {
        return Constants.kFrequencies[bitPosition];
    }

    /**
     * @param input a kBytesPerDuration long array of bytes to encode
     * @return an array of audio samples of type AudioUtil.kDefaultFormat
     */
    private static byte[] encodeDuration(byte[] input) {
        double[] signal = new double[kSamplesPerDuration];
        for (int j = 0; j < kBytesPerDuration; j++) {
            for (int k = 0; k < kBitsPerByte; k++) {
                if (((input[j] >> k) & 0x1) == 0) {
                    //no need to go through encoding a zero
                    continue;
                }
                //add a sinusoid of getFrequency(j), amplitude kAmplitude and duration kDuration
                double innerMultiplier = getFrequency((j * kBitsPerByte) + k)
                                         * (1 / kSamplingFrequency) * 2 * Math.PI;
                for (int l = 0; l < signal.length; l++) {
                    signal[l] = signal[l] + (kAmplitude * Math.cos(innerMultiplier * l));
                }
            }
        }

        return ArrayUtils.getByteArrayFromDoubleArray(smoothWindow(signal));
    }

    /**
     * @return audio samples for a duration of the hail frequency, org.codec.utils.Constants.kHailFrequency
     */
    private static byte[] getHailSequence() {
        double[] signal = new double[kSamplesPerDuration];
        //add a sinusoid of the hail frequency, amplitude kAmplitude and duration kDuration
        double innerMultiplier = Constants.kHailFrequency * (1 / kSamplingFrequency) * 2 * Math.PI;
        for (int l = 0; l < signal.length; l++) {
            signal[l] = /*kAmplitude **/ Math.cos(innerMultiplier * l);
        }
        return ArrayUtils.getByteArrayFromDoubleArray(smoothWindow(signal, 0.3));
    }

    /**
     * @return audio samples (of length 2 * kSamplesPerDuration), used to calibrate the decoding
     */
    private static byte[] getCalibrationSequence() {
        byte[] results = new byte[2 * kSamplesPerDuration];
        byte[] inputBytes1 = new byte[kBytesPerDuration];
        byte[] inputBytes2 = new byte[kBytesPerDuration];
        for (int i = 0; i < kBytesPerDuration; i++) {
            inputBytes1[i] = (byte) 0xAA;
            inputBytes2[i] = (byte) 0x55;
        }

        //encode inputBytes1 and 2 in sequence
        byte[] partialResult = encodeDuration(inputBytes1);
        for (int k = 0; k < kSamplesPerDuration; k++) {
            results[k] = partialResult[k];
        }
        partialResult = encodeDuration(inputBytes2);
        for (int k = 0; k < kSamplesPerDuration; k++) {
            results[k + kSamplesPerDuration] = partialResult[k];
        }

        return results;
    }

    /**
     * About smoothwindow.
     * This is a data set in with the following form:
     * <p/>
     * |
     * 1 |  +-------------------+
     * | /                     \
     * |/                       \
     * +--|-------------------|--+---
     * 0.01              0.09  0.1  time
     * <p/>
     * It is used to smooth the edges of the signal in each duration
     */
    private static double[] smoothWindow(double[] input, double magicScalingNumber) {
        double[] smoothWindow = new double[input.length];
        double minVal = 0;
        double maxVal = 0;
        int peaks = (int) (input.length * 0.2

        );
        double steppingValue = 1 / (double) peaks;
        for (int i = 0; i < smoothWindow.length; i++) {
            if (i < peaks) {
                smoothWindow[i] = input[i] * (steppingValue * i) /* / magicScalingNumber*/;
            } else if (i > input.length - peaks) {
                smoothWindow[i] = input[i] * (steppingValue * (input.length - i - 1)) /* / magicScalingNumber */;
            } else {
                //don't touch the middle values
                smoothWindow[i] = input[i] /* / magicScalingNumber */;
            }
            if (smoothWindow[i] < minVal) {
                minVal = smoothWindow[i];
            }
            if (smoothWindow[i] > maxVal) {
                maxVal = smoothWindow[i];
            }
        }
        return smoothWindow;
    }

    private static double[] smoothWindow(double[] input) {
        double magicScalingNumber = 0.8;
        return smoothWindow(input, magicScalingNumber);
    }

    /**
     * This isn't used at the moment, but it does sound nice
     */
    private static double[] blackmanSmoothWindow(double[] input) {
        double magicScalingNumber = 3.5;
        double[] smoothWindow = new double[input.length];
        double steppingValue = 2 * Math.PI / (input.length - 1);
        double maxVal = 0;
        double minVal = 0;
        for (int i = 0; i < smoothWindow.length; i++) {
            smoothWindow[i] = (input[i] * (0.42 - 0.5 * Math.cos(steppingValue * i) +
                                           0.08 * Math.cos(steppingValue * i))) * 3.5;
            if (smoothWindow[i] < minVal) {
                minVal = smoothWindow[i];
            }
            if (smoothWindow[i] > maxVal) {
                maxVal = smoothWindow[i];
            }
        }
        return smoothWindow;
    }
}

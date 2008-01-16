package org.codec.utils;

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



public interface Constants {
    
  public static final double kLowFrequency = 600; //the lowest frequency used
  public static final double kFrequencyStep = 50; //the distance between frequencies

  public static final int kBytesPerDuration = 1; //how wide is the data stream
  public static final int kBitsPerByte = 8; //unlikely to change, I know

  // Amplitude of each frequency in a frame.
  public static final double kAmplitude = 0.125d; /* (1/8) */

  // Sampling frequency (number of sample values per second)
  // JK. change this to improve robustness
//  public static final double kSamplingFrequency = 22050;
  public static final double kSamplingFrequency = 44100;

  // Sound duration of encoded byte (in seconds)
  public static final double kDuration = 0.1f;

  // Number of samples per duration
  public static final int kSamplesPerDuration = (int)(kSamplingFrequency * kDuration);

  //This is used to convert the floats of the encoding to the bytes of the audio
  public static final int kFloatToByteShift = 128;

  // The length, in durations, of the key sequence
  public static final int kDurationsPerKey = 3; 

  //The frequency used in the initial hail of the key
  public static final int kHailFrequency = 3000;

  //The frequencies we use for each of the 8 bits
  public static final int[] kFrequencies = {1000,                       //1000
					    (int)(1000 * (float)27/24), //1125
					    (int)(1000 * (float)30/24), //1250
					    (int)(1000 * (float)36/24), //1500 
					    (int)(1000 * (float)40/24), //1666
					    (int)(1000 * (float)48/24), //2000
					    (int)(1000 * (float)54/24), //2250
					    (int)(1000 * (float)60/24)};//2500
}

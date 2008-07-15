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
public class StreamDecoder implements Runnable {
    public static String kThreadName = "org.codec.audio.StreamDecoder";

    private Thread myThread = null;
    private final Object runLock = new Object();
    private boolean running = false;

    private AudioBuffer buffer = new AudioBuffer(); // THE buffer where bytes are being put
    private OutputStream out = null;


    /**
     * This creates and starts the decoding Thread
     *
     * @param _out the OutputStream which will receive the decoded data
     */
    public StreamDecoder(OutputStream _out) {
        out = _out;
        myThread = new Thread(this, kThreadName);
//        myThread.setDaemon(true);
        myThread.start();
    }

    public AudioBuffer getAudioBuffer() {
        return buffer;
    }

    public void run() {
        synchronized (runLock) {
            running = true;
        }

        int durationsToRead = Constants.kDurationsPerKey;
        int deletedSamples = 0;
        boolean hasKey = false;
        boolean hasEOF = false;
        double[] startSignals = new double[Constants.kBitsPerByte * Constants.kBytesPerDuration];
        boolean notEnoughSamples = true;
        byte samples[] = null;

        while (running) {
            notEnoughSamples = true;
            while (notEnoughSamples) {
                samples = buffer.read(Constants.kSamplesPerDuration * durationsToRead);
                if (samples != null)
                    notEnoughSamples = false;
                else {
                    synchronized(runLock) {
                        if (end == 1) {
                            end = 2;
                        } else {
                            if (end == 2) {
                                System.out.println("STREAM DECODINF END.");
                                return;
                            }
                            Thread.currentThread().yield();
                        }
                    }
                }
            }

            if (hasKey) { //we found the key, so decode this duration
                byte[] decoded = AudioDecoder.decode(startSignals, samples);
                try {
                    buffer.delete(samples.length);
                    deletedSamples += samples.length;
                    out.write(decoded);
/*
                    if (decoded[0] == 0) { //we are recieving no signal, so go back to key detection mode
                        //out.write("EOF\r\n".getBytes()); //this is for debugging
                        hasKey = false;
                        durationsToRead = Constants.kDurationsPerKey;
                    }
*/
                } catch (IOException e) {
                    System.out.println("Exception while decoding:" + e);
                    break;
                }

                try {
                    //this provides the audio sampling mechanism a chance to maintain continuity
                    myThread.sleep(10);
                } catch (InterruptedException e) {
                    System.out.println("Stream Decoding thread interrupted:" + e);
                    break;
                }
                continue;
            }

            //we don't have the key, so we are in key detection mode from this point on
            int initialGranularity = 400;
            int finalGranularity = 20;
            //System.out.println("Search Start: " + deletedSamples + " End: " + (deletedSamples + samples.length));
            //System.out.println("Search Time: " + ((float)deletedSamples / org.codec.utils.Constants.kSamplingFrequency) + " End: "
            //		       + ((float)(deletedSamples + samples.length) / org.codec.utils.Constants.kSamplingFrequency));
            int startIndex = AudioDecoder.findKeySequence(samples, startSignals, initialGranularity);
            if (startIndex > -1) {
                System.out.println("\nRough Start Index: " + (deletedSamples + startIndex));
                //System.out.println("Rough Start Time: "
                //	   + (deletedSamples + startIndex) / (float)org.codec.utils.Constants.kSamplingFrequency);

                int shiftAmount = startIndex /* - (org.codec.utils.Constants.kSamplesPerDuration)*/;
                if (shiftAmount < 0) {
                    shiftAmount = 0;
                }
                System.out.println("Shift amount: " + shiftAmount);
                try {
                    buffer.delete(shiftAmount);
                } catch (IOException e) {
                }
                deletedSamples += shiftAmount;

                durationsToRead = Constants.kDurationsPerKey;
                notEnoughSamples = true;
                while (notEnoughSamples) {
                    samples = buffer.read(Constants.kSamplesPerDuration * durationsToRead);
                    if (samples != null)
                        notEnoughSamples = false;
                    else Thread.currentThread().yield();
                }

                //System.out.println("Search Start: " + deletedSamples + " End: " + (deletedSamples + samples.length));
                //System.out.println("Search Time: " + ((float)deletedSamples / org.codec.utils.Constants.kSamplesPerDuration) + " End: "
                //		   + ((float)(deletedSamples + samples.length) / org.codec.utils.Constants.kSamplingFrequency));

                startIndex = AudioDecoder.findKeySequence(samples, startSignals, finalGranularity);
                System.out.println("Refined Start Index: " + (deletedSamples + startIndex));
                //System.out.println("Start Time: " +
                //	   (deletedSamples + startIndex) / (float)org.codec.utils.Constants.kSamplingFrequency);
                try {
                    notEnoughSamples = true;
                    while (notEnoughSamples) {
                        samples = buffer.read(startIndex + (Constants.kSamplesPerDuration * Constants.kDurationsPerKey));
                        if (samples != null)
                            notEnoughSamples = false;
                        else Thread.currentThread().yield();
                    }

                    samples = ArrayUtils.subarray(samples, startIndex + Constants.kSamplesPerDuration,
                                                  2 * Constants.kSamplesPerDuration);
                    AudioDecoder.getKeySignalStrengths(samples, startSignals);
                    /*
             System.out.println(" f(0): " + startSignals[0] + " f(1): " + startSignals[1] +
             " f(2): " + startSignals[2] + " f(3): " + startSignals[3] +
             " f(4): " + startSignals[4] + " f(5): " + startSignals[5] +
             " f(6): " + startSignals[6] + " f(7): " + startSignals[7]);
               */

                    buffer.delete(startIndex + (Constants.kSamplesPerDuration * Constants.kDurationsPerKey));
                    deletedSamples += startIndex + (Constants.kSamplesPerDuration * Constants.kDurationsPerKey);
                } catch (IOException e) {
                }
                hasKey = true;
                durationsToRead = 1;
            } else {
                try {
                    buffer.delete(Constants.kSamplesPerDuration);
                    deletedSamples += Constants.kSamplesPerDuration;
                } catch (IOException e) {
                }
            }
        }
    }

    public void quit() {
        synchronized (runLock) {
            running = false;
        }
    }


    public Thread getMyThread() {
        return myThread;
    }

    int end = 0;
    public void end() {
        synchronized(runLock) {
            end = 1;
        }
    }
}

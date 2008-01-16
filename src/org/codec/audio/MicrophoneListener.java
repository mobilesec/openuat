package org.codec.audio; /**
 * Copyright 2002 by the authors. All rights reserved.
 *
 * Author: Cristina V Lopes
 */

import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

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


public class MicrophoneListener implements Runnable {

    public static final String kThreadName = "org.codec.audio.MicrophoneListener";

    private AudioBuffer buffer = null;
    private Thread myThread = null;
    private Object runLock = new Object();
    private boolean running = false;

    /**
     * NOTE: This spawns a thread to do the listening and then returns
     * @param _buffer the org.codec.audio.AudioBuffer into which to write the microphone input
     */
    public MicrophoneListener(AudioBuffer _buffer) {
	buffer = _buffer;
	myThread = new Thread(this, kThreadName);
	myThread.start();
    }

    public void run() {
	synchronized(runLock){
	    running = true;
	}

	try {
	  /**
	   * NOTE: we want buffSize large so that we don't loose samples when the 
	   * org.codec.audio.StreamDecoder thread kicks in. But we want to read a small number of
	   * samples at a time, so that org.codec.audio.StreamDecoder can process them and they get
	   * freed from the buffer as soon as possible.
	   * So there's a fine balance going on here between the two threads, and
	   * if it's not tuned, samples will be lost.
	   */
	    int buffSize = 32000;
	    int buffSizeFraction = 8;
	    TargetDataLine line = AudioUtils.getTargetDataLine(AudioUtils.kDefaultFormat);
	    line.open(AudioUtils.kDefaultFormat, buffSize);
	    System.out.println(Thread.currentThread().getName() + "> bufferSize = " + line.getBufferSize());
	    ByteArrayOutputStream out  = new ByteArrayOutputStream();
	    byte[] data = new byte[line.getBufferSize() / buffSizeFraction];
	    int numBytesRead;
	    line.start();
	    while(running){
		    numBytesRead =  line.read(data, 0, data.length);
		    //		    System.out.println(Thread.currentThread().getName() + "> bytesRead = " + numBytesRead);
		    buffer.write(data, 0, numBytesRead);
	    }
	    line.drain();
	    line.stop();
	    line.close();
	} catch (Exception e){
	    System.out.println(e.toString());
	}
    }

    public void quit(){
	synchronized(runLock){
	    running = false;
	}
    }
}

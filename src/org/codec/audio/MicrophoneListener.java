package org.codec.audio; 

import javax.sound.sampled.TargetDataLine;

import org.codec.audio.common.AudioBuffer;

import java.io.ByteArrayOutputStream;

/**
 * Copyright 2002 by the authors. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author Crista Lopes (lopes at uci dot edu)
 * @version 1.0
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

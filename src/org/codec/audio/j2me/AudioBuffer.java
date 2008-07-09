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

import java.io.*;

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
public class AudioBuffer {
    private ByteArrayOutputStream baos = new ByteArrayOutputStream();
    private Object lock = new Object();

    public AudioBuffer(){};

    /**
     * @param input an array to write to the end of the buffer
     */
    public synchronized void write(byte[] input)
      throws IOException {
      baos.write(input);
    }

    /**
     * @param input the source array
     * @param offset the offset into the array from which to start copying
     * @param length the length to copy
     */
    public synchronized void write(byte[] input, int offset, int length)
      throws IOException {
      baos.write(input, offset, length);
    }
    
    /**
     * @param n the number of bytes to try to read (nondestructively)
     * @return if the buffer.size >= n, return the requested byte array, otherwise null
     *
     * NOTE: THIS DOES NOT REMOVE BYTES FROM THE BUFFER
     */
    public synchronized byte[] read(int n){
      if(baos.size() < n){
	return null;
      }
      byte[] result = ArrayUtils.subarray(baos.toByteArray(), 0, n);
      return result;
    }

    /**
     * @param n the number of bytes to remove from the buffer. 
     * If n > buffer.size, it has the same effect as n = buffer.size.
     */
    public synchronized void delete(int n)
      throws IOException {
      if(n <= 0){
	return;
      }
      if(baos.size() < n){
	baos.reset();
	return;
      }
      byte[] buff = ArrayUtils.subarray(baos.toByteArray(), n - 1, baos.size() - n);
      baos.reset();
      baos.write(buff);
    }

    /**
     * @return the current size of the buffer
     */
    public synchronized int size(){
      int size = 0;
      size = baos.size();
      return size;
    }
}

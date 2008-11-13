/**
 * Modified by Iulia Ion
 */

package org.codec.audio.j2me;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.codec.audio.common.AudioBuffer;
import org.codec.audio.common.AudioEncoder;
import org.codec.audio.common.StreamDecoder;

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

public class AudioUtils {
	/**
	 * input - the bytes of the audio file
	 */
    public static void decodeWavFile(byte[] input, ByteArrayOutputStream out) throws IOException {
        StreamDecoder decoder = new StreamDecoder(out);

        AudioBuffer decoderBuffer = decoder.getAudioBuffer();
        byte [] data ;
        data = WavCodec.decodeWav(input);
        //??
        ByteArrayInputStream audioInputStream = new ByteArrayInputStream(data);
        
       // int bytesPerFrame = audioInputStream.getFormat().getFrameSize();
        int bytesPerFrame = 1;
        
        // Set an arbitrary buffer size of 1024 frames.
        int numBytes = 60000 * bytesPerFrame;
        byte[] audioBytes = new byte[numBytes];
        int numBytesRead = 0;

        while ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {
            decoderBuffer.write(audioBytes, 0, numBytesRead);
        }

        decoder.end();
        try {
            decoder.getMyThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("END DECODE WAW FILE");
        audioInputStream.close();
        decoder.quit();
    }

    public static byte [] encodeFileToWav(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        AudioEncoder.encodeStream(inputStream, baos);

        inputStream.close();
        
        byte data [] = baos.toByteArray();
        
        return WavCodec.encodeToWav(data);

    }
    
}

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

    //the default format for reading and writing audio information
//    public static AudioFormat kDefaultFormat = new AudioFormat((float) Encoder.kSamplingFrequency,
//                                                               (int) 8, (int) 1, true, false);

//    public static void decodeWavFile(File inputFile, OutputStream out) throws UnsupportedAudioFileException, IOException {
//        StreamDecoder decoder = new StreamDecoder(out);
//
//        AudioBuffer decoderBuffer = decoder.getAudioBuffer();
//
//        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(kDefaultFormat,
//                                                                            AudioSystem.getAudioInputStream(
//                                                                                    inputFile
//                                                                            ));
//        int bytesPerFrame = audioInputStream.getFormat().getFrameSize();
//
//        // Set an arbitrary buffer size of 1024 frames.
//        int numBytes = 60000 * bytesPerFrame;
//        byte[] audioBytes = new byte[numBytes];
//        int numBytesRead = 0;
//        
//        // Try to read numBytes bytes from the file and write it to the buffer
////        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        while ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {
//            /*
//               for(int i=0; i < numBytesRead; i++){
//               float val = audioBytes[i] / (float)org.codec.utils.Constants.kFloatToByteShift;
//               //System.out.println("" + val);
//               }
//             */
//            decoderBuffer.write(audioBytes, 0, numBytesRead);
//        }
//        decoder.end();
////        try {
////            decoder.getMyThread().join();
////        } catch (InterruptedException e) {
////            e.printStackTrace();
////        }
//        System.out.println("END DECODE WAW FILE");
////        audioInputStream.close();
////        decoder.quit();
//    }
//
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

        // Try to read numBytes bytes from the file and write it to the buffer
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((numBytesRead = audioInputStream.read(audioBytes)) != -1) {
            /*
               for(int i=0; i < numBytesRead; i++){
               float val = audioBytes[i] / (float)org.codec.utils.Constants.kFloatToByteShift;
               //System.out.println("" + val);
               }
             */
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
//
//
//    public static void writeWav(File file, byte[] data, AudioFormat format) throws IllegalArgumentException, IOException {
//        ByteArrayInputStream bais = new ByteArrayInputStream(data);
//
//        AudioInputStream ais = new AudioInputStream(bais,
//                                                    format,
//                                                    data.length);
//
//        FileOutputStream outputStream = new FileOutputStream(file);
//        AudioSystem.write(ais,
//                          AudioFileFormat.Type.WAVE,
//                          outputStream);
//        outputStream.flush();
//        outputStream.close();
//
//        ais.close();
//    }
//
//    public static byte[] writeWav(byte[] data, AudioFormat format) throws IllegalArgumentException, IOException {
//        ByteArrayInputStream bais = new ByteArrayInputStream(data);
//
//        AudioInputStream ais = new AudioInputStream(bais,
//                                                    format,
//                                                    data.length);
//
//        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//        AudioSystem.write(ais,
//                          AudioFileFormat.Type.WAVE,
//                          outputStream);
//        outputStream.flush();
//        outputStream.close();
//
//        ais.close();
//        return outputStream.toByteArray();
//    }
//
//    public static void displayMixerInfo() {
//        Mixer.Info[] mInfos = AudioSystem.getMixerInfo();
//        if (mInfos == null) {
//            System.out.println("No Mixers found");
//            return;
//        }
//
//        for (int i = 0; i < mInfos.length; i++) {
//            System.out.println("Mixer Info: " + mInfos[i]);
//            Mixer mixer = AudioSystem.getMixer(mInfos[i]);
//            Line.Info[] lines = mixer.getSourceLineInfo();
//            for (int j = 0; j < lines.length; j++) {
//                System.out.println("\tSource: " + lines[j]);
//            }
//            lines = mixer.getTargetLineInfo();
//            for (int j = 0; j < lines.length; j++) {
//                System.out.println("\tTarget: " + lines[j]);
//            }
//        }
//    }
//
//    public static void displayAudioFileTypes() {
//        AudioFileFormat.Type[] types = AudioSystem.getAudioFileTypes();
//        for (int i = 0; i < types.length; i++) {
//            System.out.println("Audio File Type:" + types[i].toString());
//        }
//    }
//
//    //This never returns, which is kind of lame.
//    // NOT USED!! - replaced by org.codec.audio.MicrophoneListener.run()
//    public static void listenToMicrophone(AudioBuffer buff) {
//        try {
//            int buffSize = 4096;
//            TargetDataLine line = getTargetDataLine(kDefaultFormat);
//            line.open(kDefaultFormat, buffSize);
//
//            ByteArrayOutputStream out = new ByteArrayOutputStream();
//            int numBytesRead;
//            byte[] data = new byte[line.getBufferSize() / 5];
//            line.start();
//            while (true) {
//                numBytesRead = line.read(data, 0, data.length);
//                buff.write(data, 0, numBytesRead);
//            }
//            /*
//           line.drain();
//           line.stop();
//           line.close();
//           */
//        } catch (Exception e) {
//            System.out.println(e.toString());
//        }
//    }
//
//    public static void recordToFile(File file, int length) {
//        try {
//            int buffSize = 4096;
//            TargetDataLine line = getTargetDataLine(kDefaultFormat);
//            line.open(kDefaultFormat, buffSize);
//
//            ByteArrayOutputStream out = new ByteArrayOutputStream();
//            int numBytesRead;
//            byte[] data = new byte[line.getBufferSize() / 5];
//            line.start();
//            for (int i = 0; i < length; i++) {
//                numBytesRead = line.read(data, 0, data.length);
//                out.write(data, 0, numBytesRead);
//            }
//            line.drain();
//            line.stop();
//            line.close();
//
//            writeWav(file, out.toByteArray(), kDefaultFormat);
//
//        } catch (Exception e) {
//            System.out.println(e.toString());
//        }
//    }
//
//    public static byte[] recordToByteArray(int length) {
//        try {
//            int buffSize = 4096;
//            TargetDataLine line = getTargetDataLine(kDefaultFormat);
//            line.open(kDefaultFormat, buffSize);
//
//            ByteArrayOutputStream out = new ByteArrayOutputStream();
//            int numBytesRead;
//            byte[] data = new byte[line.getBufferSize() / 5];
//            line.start();
//            for (int i = 0; i < length; i++) {
//                numBytesRead = line.read(data, 0, data.length);
//                out.write(data, 0, numBytesRead);
//            }
//            line.drain();
//            line.stop();
//            line.close();
//
//            return out.toByteArray();
//        } catch (Exception e) {
//            System.out.println(e.toString());
//        }
//        return null;
//    }
//
//    public static TargetDataLine getTargetDataLine(AudioFormat format)
//            throws LineUnavailableException {
//        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
//        if (!AudioSystem.isLineSupported(info)) {
//            throw new LineUnavailableException();
//        }
//        return (TargetDataLine) AudioSystem.getLine(info);
//    }
//
//    public static SourceDataLine getSourceDataLine(AudioFormat format)
//            throws LineUnavailableException {
//        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
//        if (!AudioSystem.isLineSupported(info)) {
//            throw new LineUnavailableException();
//        }
//        return (SourceDataLine) AudioSystem.getLine(info);
//    }
//
//    public static void encodeFileToWav(File inputFile, File outputFile) throws IOException {
//        FileInputStream inputStream = new FileInputStream(inputFile);
//
//        encodeFileToWav(inputStream, outputFile);
//
//        inputStream.close();
//    }

//    public static void encodeFileToWav(InputStream inputStream, File outputFile) throws IOException {
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//
//        Encoder.encodeStream(inputStream, baos);
//
//        inputStream.close();
//
//        writeWav(outputFile,
//                 baos.toByteArray(),
//                 kDefaultFormat);
//    }
  
    
    public static byte [] encodeFileToWav(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        AudioEncoder.encodeStream(inputStream, baos);

        inputStream.close();
        
        byte data [] = baos.toByteArray();
        
        return new WavCodec().encodeToWav(data);

        
//        writeWav(outputFile,
//                 baos.toByteArray(),
//                 kDefaultFormat);
    }
    
    
//    //iulia: what does this do?
//    public static void performData(byte[] data)
//            throws IOException {
//        //For some reason line.write seems to affect the data
//        //to avoid the side effect, we copy it
//        byte[] dataCopy = new byte[data.length];
//        for (int i = 0; i < data.length; i++) {
//            dataCopy[i] = data[i];
//        }
//
//        SourceDataLine line = null;
//        try {
//            line = getSourceDataLine(kDefaultFormat);
//            line.open(kDefaultFormat);
//        } catch (LineUnavailableException ex) {
//            System.out.println("Line Unavailable: " + ex);
//            return;
//        }
//        line.start();
//        line.write(dataCopy, 0, dataCopy.length);
//        line.drain();
//        line.stop();
//        line.close();
//    }
//
//    public static void performFile(File file)
//            throws IOException {
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        Encoder.encodeStream(new FileInputStream(file), baos);
//        performData(baos.toByteArray());
//    }
}

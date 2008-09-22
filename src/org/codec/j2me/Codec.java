package org.codec.j2me;


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
 * @author claudio soriente (csorient at uci dot edu)
 * @version 1.0
 * 
 */
public abstract class Codec {
    public static int UNILATERAL = 0;
    public static int BILATERAL = 1;
    public static int STS = 2;

    protected String curDir = System.getProperty("user.dir");
    //publik-private key pair used in STS
//    protected static File my_pk_file = new File("my_pk.txt");
//    protected static File my_sk_file = new File("my_sk.txt");
//
//    //key to be sent either by the server or by the client in unilateral or bilateral key exchange
//    protected static File my_key_file = new File("my_key.txt");
//    //encoded wav to be played by the server or by the client in unilateral or bilateral key exchange
//    protected static File my_wav_file = new File("my_wav.wav");
//
//    //received key
//    protected static File output_key_file = new File("output_key.txt");
//    //received key and hash
//    protected static File output_data_file = new File("output_data.txt");
//    //received wav
//    protected static File output_wav_file = new File("output_wav.wav");
//
//    //only for debugging: hash to be used to compute the score for the verification phase
//    protected static File hash_file = new File("my_hash.txt");
//    //only for debugging:score to be played during verification phase
//    protected static File score_file = new File("my_score.txt");
//    
    //Provider to be used for cryptographic functions
    protected static String PROVIDER = "BC" /*"IAIK_ECC"*/;


    protected int protocol;
    protected int time;

    public Codec(int protocol) {
        this.protocol = protocol;
//        reset();
    }

//    protected void reset() {
//        my_wav_file.delete();
//        output_key_file.delete();
//        output_data_file.delete();
//        output_wav_file.delete();
//        hash_file.delete();
//        score_file.delete();
//    }

//    public void checkTransmission() {
//// for unilateral and bilateral only
////in the sts protocol, the signature verification is enough
////as a check to avoid useless verification phase
//        switch (protocol) {
//            case 0:
//            case 1:
//                byte[] data_bytes = new byte[20];
//                byte[] hash_bytes = new byte[10];
//                byte[] computed_hash_bytes = new byte[16];
//
//                //read the key and the hash from output_data_file
//                try {
//                    FileInputStream in = new FileInputStream(output_data_file);
//                    int x = in.read(data_bytes);
//                    int y = in.read(hash_bytes);
//
//                    //writes the key to output_key_file
//                    FileOutputStream out = new FileOutputStream(output_key_file);
//                    out.write(data_bytes);
//
//                    //compute the hash and reads the first 10 bytes in hb[]
//                    computed_hash_bytes = messageDigest.hashMD5(data_bytes);
//                    byte[] hb = new byte[10];
//                    for (int i = 0; i < 10; i++)
//                        hb[i] = computed_hash_bytes[i];
//
//                    //if the first 10 bytes of the computed hash differ
//                    //from the 10 bytes of the hash received produces "error"
//                    if (!Arrays.equals(hb, hash_bytes)) {
//                        JOptionPane.showMessageDialog(null, "Recording Error", curDir, JOptionPane.ERROR_MESSAGE);
//                        output_wav_file.delete();
//                        output_data_file.delete();
//                        output_key_file.delete();
//                        System.exit(0);
//                    }
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                }
//                break;
//            //not used for STS    
//            case 2:
//        }
//    }
//
//    public void accept() {
//        String out = "Keyfile: " + output_key_file;
//        JOptionPane.showMessageDialog(null, out, "HAPADEP " + curDir, JOptionPane.INFORMATION_MESSAGE);
//    }
//
//    public void refuse() {
//        JOptionPane.showMessageDialog(null, "Verification unsuccessful", "HAPADEP " + curDir, JOptionPane.ERROR_MESSAGE);
//        output_wav_file.delete();
//        output_data_file.delete();
//        output_key_file.delete();
//    }


    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

}

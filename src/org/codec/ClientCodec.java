package org.codec;

import org.codec.audio.AudioUtils;
import org.codec.audio.WavPlayer;
import org.codec.audio.speech.Synthesizer;
import org.codec.utils.ArrayUtils;
import org.codec.mad.MadLib;

import org.openuat.authentication.exceptions.InternalApplicationException;
import org.openuat.util.Hash;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

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
 * @author claudio soriente) (csorient at uci dot edu)
 * @version 1.0
 * 
 */
public class ClientCodec extends Codec {
	// TODO: remove these - we can't deal with files on J2ME etc.
    //10 bytes challenge. SENT BY the client
    private static File ch_client_file = new File("ch_client.txt");
    //server public key and signature of the challenge. RECEIVED BY the client
    private static File sts_client_file = new File("sts_client.txt");


    /* TODO: dichiaro 4 hash di 16 byte...due vanno modificati in una sola posizione tutto a randomd
     * Scriere su file cosa ho modificato...
     *
     *
     */



    public ClientCodec(int protocol) {
        super(protocol);
    }


    public void sendData() {
        switch (protocol) {
        
//in case of unilateral or bilateral key exchange
            case 0:
            case 1:
                byte[] bytes = new byte[20];
                byte[] hbytes = new byte[16];

//reads the key from my_key_file,
//appends 10 bytes of the hash
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    FileInputStream in = new FileInputStream(my_key_file);

                    in.read(bytes);

                    hbytes = Hash.doubleSHA256(bytes, false);

                    out.write(bytes);
                    out.write(hbytes, 0, 10);

                    in.close();
                    out.close();
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                } catch (InternalApplicationException e1) {
                    e1.printStackTrace();
                }

//encodes key||hash and plays it
                try {
                    AudioUtils.encodeFileToWav(new ByteArrayInputStream(out.toByteArray()), my_wav_file);
                    WavPlayer.PlayWav(my_wav_file);
                } catch (Exception ioex) {
                    ioex.printStackTrace();
                }
                break;
            case 2:
//in case of STS, encode and send the challenge found in ch_client.txt            	
                try {
                    AudioUtils.encodeFileToWav(ch_client_file,
                                               my_wav_file);
                    WavPlayer.PlayWav(my_wav_file);
                } catch (IOException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
        }
    }

    public void receiveData() {
        File output_data = null;
        switch (protocol) {
            case 0:
            case 1:
                output_data = output_data_file;
                break;
            case 2:
                output_data = sts_client_file;
                break;

        }

// var "time" must be tuned for each protocol. this is possibile looking at the play time (var "start")
        try {
            byte[] recorded_sample = AudioUtils.recordToByteArray(time);

            byte[] result = AudioUtils.writeWav(recorded_sample,
                                                AudioUtils.kDefaultFormat);

            FileOutputStream out = new FileOutputStream(output_data);
            AudioUtils.decodeWavFile2(result, out);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void verify() {
// reads the challenge form ch_client.txt and the pk and 
// the signature from sts_client.txt
    	byte[] ch = null;
        try {
            FileInputStream inch = new FileInputStream(ch_client_file);
            ch = new byte[10];
            inch.read(ch);
            inch.close();

            FileInputStream in = new FileInputStream(sts_client_file);
            byte[] pk = new byte[75];
            in.read(pk);

            byte[] sign = new byte[55];
            in.read(sign);
            in.close();

// creates pk from specification
            System.out.println("Public Key");
            for (int i = 0; i < pk.length; i++) {
                System.out.println(pk[i]);
            }

            System.out.println("SIGN");
            for (int i = 0; i < sign.length; i++) {
                System.out.println(sign[i]);
            }

            X509EncodedKeySpec x509Spec = new X509EncodedKeySpec(pk);
            KeyFactory keyFact = KeyFactory.getInstance("ECDSA", PROVIDER);
            PublicKey pubKey = keyFact.generatePublic(x509Spec);

// verifies the signature
            Signature signature = Signature.getInstance("ECDSA", PROVIDER);
            signature.initVerify(pubKey);
            signature.update(ch);

            if (!signature.verify(sign)) {
                throw new SignatureException("Segnatura non valida");
//                JOptionPane.showMessageDialog(null, "Signature not valid", "HAPADEP " + curDir, JOptionPane.ERROR_MESSAGE);
//                System.exit(0);
            } else
                System.out.println("SIGNATURE OK");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            try {
                System.out.println("PROVA A 56 :");
                FileInputStream in = new FileInputStream(sts_client_file);
                byte[] pk = new byte[75];
                in.read(pk);

                byte[] sign = new byte[56];
                in.read(sign);
                in.close();

                // creates pk from specification
                System.out.println("Private Key");
                for (int i = 0; i < pk.length; i++) {
                    System.out.println(pk[i]);
                }

                System.out.println("SIGN");
                for (int i = 0; i < sign.length; i++) {
                    System.out.println(sign[i]);
                }

                X509EncodedKeySpec x509Spec = new X509EncodedKeySpec(pk);
                KeyFactory keyFact = KeyFactory.getInstance("ECDSA", PROVIDER);
                PublicKey pubKey = keyFact.generatePublic(x509Spec);

                // verifies the signature
                Signature signature = Signature.getInstance("ECDSA", PROVIDER);
                signature.initVerify(pubKey);
                signature.update(ch);

                if (signature.verify(sign)) {
                    System.out.println("SIGNATURE OK");
                    return;
                }
            } catch (Exception e1) {
                e1.printStackTrace();
            }

            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Signature not valid", "HAPADEP " + curDir, JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        }

    }

    public void playScore() {
        try {
            byte[] hbytes = new byte[16];
            byte[] bytes1 = new byte[20];
            byte[] bytes2 = new byte[20];

//in case of unilateral transmission
            if (protocol == 0) {
                // TODO prendere iteratiamente i 4 esce
                // TODO: don't use files
                FileInputStream in = new FileInputStream(output_key_file);

                in.read(bytes1);
                hbytes = Hash.doubleSHA256(bytes1, false);
                // TODO: don't use files
                FileOutputStream out = new FileOutputStream(hash_file);
                out.write(hbytes);
            }

//in case of bilateral transmission 
            if (protocol == 1) {
                FileInputStream myin = new FileInputStream(my_key_file);
                myin.read(bytes1);

                FileInputStream otherin = new FileInputStream(output_key_file);
                otherin.read(bytes2);

                byte[] bytes = new byte[40];
                //the hash is computed on client_key||server_key
                bytes = ArrayUtils.concatenate(bytes1, bytes2);

                hbytes = Hash.doubleSHA256(bytes, false);
                // TODO: don't use files
                FileOutputStream out = new FileOutputStream(hash_file);
                out.write(hbytes);
            }
            
//in case of STS 
            if (protocol == 2) {
                hbytes = Hash.doubleSHA256(playScoreSts(), false);
                // TODO: don't use files
                FileOutputStream out = new FileOutputStream(hash_file);
                out.write(hbytes);
            }

            MadLib madLib = new MadLib();
            String text = madLib.GenerateMadLib(hbytes, 0, 5);
            //System.out.printf("text : " + text);

            Synthesizer.speakPlainText(text);

//Encodes the hash in a score, plays it and writes it to score_file
//            String score = PlayerPiano.MakeInput(hbytes);
//            PlayerPiano.PlayerPiano(score);
//            FileOutputStream scoreOut = new FileOutputStream(score_file);
//            PrintStream p;
//            p = new PrintStream(scoreOut);
//            p.print(score);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } 
    }


    protected void reset() {
        super.reset();
        sts_client_file.delete();
    }

    private byte[] playScoreSts() {
 //reads  75byte key from sts_client
        try {
            FileInputStream in = new FileInputStream(sts_client_file);
            byte[] pk = new byte[75];
            in.read(pk);
            return pk;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

}

package org.codec.j2me;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

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
public class ServerCodec extends Codec {
    //10 bytes challenge. RECEIVED BY the server
//    private static File ch_server_file = new File("ch_server.txt");
//    //server public key and signature of the received challenge. SENT BY the server
//    private static File sts_server_file = new File("sts_server.txt");

    private static boolean generated_geys = false;

//    private static KeyPairGenerator keyGen;

//    private static PublicKey publicKey;
//    private static PrivateKey privateKey;
//


    public ServerCodec(int protocol) {
        super(protocol);
//        genKeys();
    }


    public byte[] sendData() throws Exception{
    	byte [] tosend = null;
    	  ByteArrayOutputStream out = new ByteArrayOutputStream();
        switch (protocol) {
//in case of unilateral or bilateral key exchange        
            case 0:
            case 1:
                byte[] bytes = new byte[20];
                byte[] hbytes = new byte[16];

                //reads the key from my_key_file,
                //appends 10 bytes of the hash
              
//                try {
                    //FileInputStream in = new FileInputStream(my_key_file);
                	 InputStream in = getClass().getResourceAsStream("my_key.txt");
                	 System.out.println("in: "+ in);
                	 InputStream inhash = getClass().getResourceAsStream("my_hashfile.txt");
                	 System.out.println("inhash: "+ inhash);
                    in.read(bytes);

                    inhash.read(hbytes);

                    out.write(bytes);
                    out.write(hbytes, 0, 10);
                    tosend= out.toByteArray();
//                    for (int i = 0; i < tosend.length; i++) {
//						System.out.println(tosend[i]);
//					}
                    in.close();
                    out.close();
//                } catch (IOException e1) {
//                    // TODO Auto-generated catch block
//                    e1.printStackTrace();
//
//                }
                //encodes key||hash and plays it
                
               
//                try {
//                   // AudioUtils.encodeFileToWav(new ByteArrayInputStream(out.toByteArray()), my_wav_file);
//                   // WavPlayer.PlayWav(my_wav_file);
//                	
//                	byte [] wav = AudioUtils.encodeFileToWav(new ByteArrayInputStream(out.toByteArray()));
//                } catch (Exception ioex) {
//                    ioex.printStackTrace();
//                }
                break;
//            case 2: //bilateral, leave for now
////in case of STS, encode and send the challenge found in ch_client.txt
//                try {
//                    AudioUtils.encodeFileToWav(sts_server_file, my_wav_file);
//                    WavPlayer.PlayWav(my_wav_file);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
        }
        return tosend;
    }

//    public void receiveData() {
//        File output_data = null;
//        switch (protocol) {
//            case 0:
//            case 1:
//                output_data = output_data_file;
//                break;
//            case 2:
//                output_data = ch_server_file;
//                break;
//
//        }
//
//// var "time" must be tuned for each protocol. this is possibile looking at the play time (var "start")
//        try {
//            byte[] recorded_sample = AudioUtils.recordToByteArray(time);
//
//            byte[] bytes = AudioUtils.writeWav(recorded_sample,
//                                               AudioUtils.kDefaultFormat);
//
//            FileOutputStream out = new FileOutputStream(output_data);
//            AudioUtils.decodeWavFile2(bytes, out);
//
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//    public void verify() {
//        try {
//        	//reads the challenge from ch_server_file
//            FileInputStream in = new FileInputStream(ch_server_file);
//            byte[] bytes = new byte[10];
//            in.read(bytes);
//            in.close();
//
//            //signs the challenge
//            Signature signature = Signature.getInstance("ECDSA", PROVIDER);
//            signature.initSign(privateKey);
//            signature.update(bytes);
//            byte[] sigBytes = signature.sign();
//
//            // writes pk and the signature of the challenge in sts_server_file
//            FileOutputStream stsServerOut = new FileOutputStream(sts_server_file);
//            byte[] bts = publicKey.getEncoded();
//
//            System.out.println("Public KEY");
//            for (int i = 0; i < bts.length; i++) {
//                System.out.println(bts[i]);
//            }
//
//            System.out.println("SIG");
//            for (int i = 0; i < sigBytes.length; i++) {
//                System.out.println(sigBytes[i]);
//            }
//
//            stsServerOut.write(bts);
//            stsServerOut.write(sigBytes);
//
//
//            stsServerOut.flush();
//            stsServerOut.close();
//
//            System.out.println("PK " + publicKey.getEncoded().length);
//            System.out.println("FIRMA " + sigBytes.length);
//        } catch (IOException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        } catch (NoSuchProviderException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        } catch (InvalidKeyException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        } catch (SignatureException e) {
//            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//        }
//
//    }
//
//    public void playScore() {
//        try {
//            byte[] hbytes = new byte[16];
//            byte[] bytes1 = new byte[20];
//            byte[] bytes2 = new byte[20];
//
//            //in case of unilateral transmission
//            //just focus on 0, unilateral
////            if (protocol == 0) {
//               // FileInputStream in = new FileInputStream(my_key_file);
//            InputStream in = getClass().getResourceAsStream("my_key.txt");
//                in.read(bytes1);
//                //iulia
//                hbytes = messageDigest.hashMD5(bytes1);
//              //  hbytes = messageDigest.hashMD5(bytes1, hash_file);
////            }
////
////            //in case of bilateral transmission 
////            if (protocol == 1) {
////                FileInputStream myin = new FileInputStream(my_key_file);
////                myin.read(bytes1);
////
////                FileInputStream otherin = new FileInputStream(output_key_file);
////                otherin.read(bytes2);
////
////                byte[] bytes = new byte[40];
////                //the hash is computed on client_key||server_key
////                bytes = ArrayUtils.concatenate(bytes2, bytes1);
//////iulia: I assume that the hash_file is never used, never read back
////                //hbytes = messageDigest.hashMD5(bytes, hash_file);
////                hbytes = messageDigest.hashMD5(bytes);
////
////            }
////
////            if (protocol == 2) {
////                //hbytes = messageDigest.hashMD5(playScoreSts(), hash_file);
////            	hbytes = messageDigest.hashMD5(playScoreSts());
////            }
//
//            //Encodes the hash in a score, plays it and writes it to score_file
//            //iulia: don't know what to do with this
////            String score = PlayerPiano.MakeInput(hbytes);
////            PlayerPiano.PlayerPiano(score);
////            FileOutputStream scoreOut = new FileOutputStream(score_file);
//           
////            PrintStream p;
////            p = new PrintStream(scoreOut);
////            p.print(score);
//            //--------------
//        } catch (Exception e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }
//
//
//    protected void reset() {
//        super.reset();
//        sts_server_file.delete();
//    }
//
//    private byte[] playScoreSts() {
//       try {
//            FileInputStream in = new FileInputStream(my_pk_file);
//            byte[] pk = new byte[75];
//            in.read(pk);
//            return pk;
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return new byte[0];
//    }
//
//    private synchronized boolean genKeys() {
//        if (!generated_geys) {
//            generated_geys = true;
//
//            //Checks if keypair have already been created
//            if (my_pk_file.exists() && my_sk_file.exists()) {
//                try {
//                    // Load public key
//                    FileInputStream in = new FileInputStream(my_pk_file);
//                    byte [] pk = new byte[in.available()];
//                    in.read(pk);
//                    in.close();
//
//                    //only for debug: creates pk from specification X509 using the encoded key read from file
//                    KeyFactory keyFact = KeyFactory.getInstance("ECDSA",PROVIDER);
//                    EncodedKeySpec x509Spec = new X509EncodedKeySpec(pk);
//                    publicKey = keyFact.generatePublic(x509Spec);
//
//                    // Load private key
//                    in = new FileInputStream(my_sk_file);
//                    pk = new byte[in.available()];
//                    in.read(pk);
//                    in.close();
//
//                    EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(pk);
//                    privateKey = keyFact.generatePrivate(privateKeySpec);
//
//                } catch (Exception e) {
//                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//                }
//            } else {
// 
//                //Creates keypair
//                try {
//                    keyGen = KeyPairGenerator.getInstance("ECDSA",PROVIDER);
//                    ECGenParameterSpec ecSpec = new ECGenParameterSpec("prime192v1");
//                    keyGen.initialize(ecSpec);
//                    KeyPair keyPair = keyGen.generateKeyPair();
//
//                    publicKey = keyPair.getPublic();
//                    privateKey = keyPair.getPrivate();
//
//                    FileOutputStream outPk = new FileOutputStream(my_pk_file);
//                    outPk.write(keyPair.getPublic().getEncoded());
//                    outPk.flush();
//                    outPk.close();
//
//                    FileOutputStream outSk = new FileOutputStream(my_sk_file);
//                    outSk.write(keyPair.getPrivate().getEncoded());
//                    outSk.flush();
//                    outSk.close();
//
//                } catch (NoSuchProviderException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                } catch (InvalidAlgorithmParameterException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//    /*		} catch (InvalidKeySpecException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//    */
//                } catch (IOException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                } catch (NoSuchAlgorithmException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//            }
//        }
//        return true;
//    }


}

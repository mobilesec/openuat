package org.codec.audio;

import org.bouncycastle.crypto.digests.MD5Digest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class messageDigest {

	/*
	 Generate a Message Digest
	*/


	    
	    public static byte[] hashMD5(byte[] in, File out) throws IOException, NoSuchAlgorithmException {

						
			//read for the key file
	    	byte[] plainText = new byte[20];
	    	byte[] digest    = new byte[16];
			plainText= in;
			
			MD5Digest md5 = new MD5Digest();
			
			md5.reset();
			md5.update(plainText, 0, 20);
			md5.doFinal(digest, 0);
			
	        //MessageDigest org.codec.audio.messageDigest = MessageDigest.getInstance("MD5");
	        
	        //org.codec.audio.messageDigest.update(plainText);
	        System.out.println( "\nDigest: " );
	        System.out.println( new String( digest, "UTF8") );
	    	if(out!=null){
	    		FileOutputStream outputFile = new FileOutputStream(out);
	    		outputFile.write(digest);
	    	}
			return digest;
	    }
}


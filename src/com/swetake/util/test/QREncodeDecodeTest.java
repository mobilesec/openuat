/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package com.swetake.util.test;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.google.zxing.MonochromeBitmapSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageMonochromeBitmapSource;
import com.swetake.util.QRcodeGen;
/**
 * JUnit tests for encoding and decoding QR codes
 * @author Iulia Ion
 *
 */
public class QREncodeDecodeTest extends TestCase{

	public void testEncodeDecode() throws ReaderException {
		String []message = new String[]{"my message",
										"Zurich is optimal",
										"let's try 1234 tests"};
		BufferedImage code;
		String result ;
		for (int i = 0; i < message.length; i++) {
			code = encodeQrcode(message[i]);
			result = decodeQrcode(code);
			System.out.println(result);
			Assert.assertEquals(message[i], result);
		}
		
	}
	
	public static BufferedImage encodeQrcode(String qrcodeData){
		QRcodeGen x=new QRcodeGen();
		x.setQrcodeErrorCorrect('M');
		x.setQrcodeEncodeMode('B');
		x.setQrcodeVersion(2);
		BufferedImage result = new BufferedImage(400, 400, BufferedImage.TYPE_3BYTE_BGR );
		byte[] d =qrcodeData.getBytes();
		if (d.length>0 && d.length <120){
			boolean[][] s = x.calQrcode(d);
			Graphics g = result.getGraphics();
			
			g.setColor(Color.white);
			g.fillRect(0,0,400,400);
			g.setColor(Color.black);
			for (int i=0;i<s.length;i++){
				for (int j=0;j<s.length;j++){
					if (s[j][i]) {
						g.fillRect(j*10 +30,i*10+30,10,10);
					}
				}
			}
			return result;
		}
		return null;
	}
	
	public static String decodeQrcode(BufferedImage image) throws ReaderException{
	    MonochromeBitmapSource source = new BufferedImageMonochromeBitmapSource(image);
	    Result result;
	      result = new MultiFormatReader().decode(source);
	    return result.getText();
	}
	
}

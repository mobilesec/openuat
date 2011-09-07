/* Copyright The Relate project team, Lancaster University
 * File created 2006-06
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.ip;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.FileNameMap;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLConnection;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPSocket  implements Runnable{
	/** Our logger. */
	private static Logger logger = LoggerFactory.getLogger(HTTPSocket.class.getName());

	public static String RELATE_ATTRIBUTE_FILENAME = "X-Relate-Filename";
	public static String RELATE_ATTRIBUTE_ID = "X-Relate-Id";
	private int port;
	private InetAddress ip;
	private File fileToSend;
	private String destId;
	private Thread thread;
	

	public HTTPSocket() {
		this(HTTPServer.DEFAULT_PORT);
	}

	public HTTPSocket(int port) {
		this.port = port;
		thread = new Thread(this);
	}

	public static void main(String[] args) {
		if (args.length <2 ){
			System.out.println("Usage: ip filename");
			System.exit(0);
		}
		String host = args[0];
		File file = new File(args[1]);
		int port = 8080;
		boolean transfer = false;

		HTTPSocket myclient = new HTTPSocket(port);
		
		
		try {
			transfer =myclient.sendFile(InetAddress.getByName(host),
					new FileTransferEvent(""+27, file ));
		} catch (UnknownHostException e) {
			System.out.println(" unknown host exception ");
		}
		System.out.println("file transfer was :"+transfer);

	}

	/**
	 * to send a file: a socket connection will be open to the 
	 * ip address you handle over. afterwards a http POST header is 
	 * created and send. afterwards the file is send to the destination 
	 * addresss. connection is afterwards closed.
	 * @param ip
	 * @param fts
	 * @return boolean value: true if the server send a http ok, which is equal 
	 * 		to yes, file transfer was succesful.
	 */
	public boolean sendFile(InetAddress ip, FileTransferEvent fts) {
		logger.debug(" begin of sending file to "+ip.getHostAddress());
		this.ip=ip;
		this.destId= fts.getSource().toString();
		fileToSend=fts.getTmpFile();
		if (!fileToSend.isFile()){
			logger.warn("file does not exist.");
			return false;
		}
		logger.debug("create thread");
		thread = new Thread(this);
		thread.start();
		return true;
	}

	

	private void sendFileContentToHost(OutputStream out, File file)
			throws FileNotFoundException, IOException {
		int B_SIZE =4096;
		FileInputStream fis = new FileInputStream(file);
		byte[] bucket = new byte[B_SIZE];
		long loops = file.length()/bucket.length;
		int saftey=0;
		for (int i=0; i<loops && saftey!=-1; i++ ){
			saftey =fis.read(bucket);
			out.write(bucket);
			out.flush();
		}
		int rest = (int) (file.length() -loops*B_SIZE);
		bucket = new byte [rest];
		saftey =fis.read(bucket);
		out.write(bucket);
		out.flush();
		
	}

	private void writeHTTPHeader(InetAddress ip, OutputStream out, File file, String id)
			throws IOException {
		String filename = file.getName().replace(' ', '_');
		FileNameMap fileNameMap = URLConnection.getFileNameMap();
	    String mimeType = fileNameMap.getContentTypeFor(filename);
	    logger.debug("MIME TYPE: "+ mimeType);
		String header = "POST / HTTP/1.1\r\n";
		header += RELATE_ATTRIBUTE_FILENAME+": " + filename + "\r\n";
		header += RELATE_ATTRIBUTE_ID + ": " + id + "\r\n";
		
		header += "Content-Type: "+ mimeType+ "\r\n";
		header += "Connection: close\r\n";
		header += "User-Agent: RelateClient\r\n";
		header += "Host: " + ip.getHostAddress() + ":" + port + "\r\n";
		header += "Content-Length: " + file.length() + "\r\n";
		header += "\r\n";
		out.write(header.getBytes());
	}

	public void run() {
		logger.debug("in run ");
		OutputStream out=null;
		try {
			Socket myClient = null;
			 myClient = new Socket(ip.getHostAddress(), port);
			 out = myClient.getOutputStream();
			writeHTTPHeader(ip, out, fileToSend, destId);
			sendFileContentToHost(out, fileToSend);
		} catch (UnknownHostException e) {
			logger.warn(" unknown Host Exception: " + ip.getHostAddress()
					+ " is unknown host");
		} catch (IOException e) {
			logger.warn("file " + fileToSend.getAbsolutePath()+
					" not found in your System");
		}
		if (out != null ){
			try {
				out.close();
			} catch (IOException e) {
				logger.warn(" closing the output stream throw a IO Exception.");
			}
		}
		
	}

}

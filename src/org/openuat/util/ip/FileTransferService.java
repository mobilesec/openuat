/* Copyright The Relate project team, Lancaster University
 * File created 2006-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.ip;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.Vector;

import org.apache.log4j.Logger;

/*
 * file transfer is realised over http protocol. to 
 * be able to receive a file, each participant has
 * a open http socket.
 * each participant. to receive the file, you just register
 * to FileTransferService, indicates where the transfered 
 * Files should be placed and than that you would like 
 * be informed so.
 * 
 * The FileTransferService has also a slim implemented client which 
 * makes possible to sent a file to another participant over http post
 * in order to do that, you need the ip/id of the receiver so the client
 * can open a connection, transfer the file and close the connection afterwords.
 */

public class FileTransferService {
	/** Our log4j logger. */
	private static Logger logger = Logger.getLogger(FileTransferService.class);

	private static FileTransferService instance;

	private HTTPServer httpServer;
	private PDAHTTPServer  pdaServer;

	private FileTransferListener listener;

	private Vector ftListener;

	/**
	 * default port where you http service
	 * is started.
	 */
	public static int DEFAULT_PORT = 8080;

	/**
	 * starts the http server. this one is listening to the address 
	 * of the InetAddress. the default port, where the relate http 
	 * server is started is DEFAULT_PORT.
	 * @param ia
	 * @return FileTransferService
	 */
	public static FileTransferService getFileTransferService(InetAddress ia) {
		return FileTransferService.getFileTransferService(DEFAULT_PORT, ia);
	}

	/**
	 * starts the http server. this one is listening to the address 
	 * of the InetAddress. the default port, where the relate http 
	 * server is started is the parameter port.
	 * @param port to which http server is listening
	 * @param ia address where the server is bind to.
	 * @return FileTransferService
	 */
	public static FileTransferService getFileTransferService(int port,
			InetAddress ia) {
		if (instance == null) {
			instance = new FileTransferService(ia, port);

		}
		return instance;
	}
	
	public static FileTransferService getFileTransferService(){
		if (instance == null){
			throw new IllegalArgumentException("you have to bind the File transfer " +
					"service to a InetAddress First, basically you have to use the" +
					" staticMethod with a Inetaddress first");
		}
		return instance;
	}
	

	public FileTransferListener getListener() {
		return listener;
	}

	private FileTransferService(InetAddress ia, int port) {
		listener = new FileTransferHandler();
		ftListener = new Vector();
		//FIXME pda does not work with simple os.
		/* to make simple work, the j9 needs the jclmax 
		 * extension. i was unable to get that zip file 
		 * downloaded here at uni
		 */
		if (System.getProperty("os.name").startsWith("Windows CE")){
			try {
				pdaServer =new PDAHTTPServer(port,false, ia);
				pdaServer.addFileTransferListener(listener);
			} catch (IOException ioe) {
				logger.error("Couldn't start server:\n");
				System.exit(-1);
			}
		}else {
				httpServer = new HTTPServer(ia, port);
		}
		
	}

	class FileTransferHandler implements FileTransferListener {
		public FileTransferHandler() {
		}
		public void receivedFile(FileTransferEvent fts) {
			logger.debug(" file received: "
					+ fts.getTmpFile().getAbsolutePath());
			FileTransferService.this.fireFileToListeners(fts);
		}
	}

	public void close() {
		if (httpServer!= null) 
			httpServer.close();
		else if (pdaServer!=null ){
			pdaServer.close();
		}
		
	}
	private void fireFileToListeners(FileTransferEvent fts) {
		Iterator iter = ftListener.iterator();
		while (iter.hasNext()){
			FileTransferListener t =(FileTransferListener)iter.next();
			t.receivedFile(fts);
		}
	}
	
	public void addFileReceivedListener(FileTransferListener l){
		ftListener.add(l);
	}
	
	public void removeFileReceivedListener(FileTransferListener l){
		ftListener.remove(l);
	}

}

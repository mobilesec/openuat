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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

import java.net.ServerSocket;
import java.net.UnknownHostException;

import java.util.logging.Logger;

import simple.http.load.MapperEngine;
import simple.http.serve.CacheContext;

/**
 * This class starts a HTTP server based on the implementation
 * of the simple server (http://www.simpleframework.org/)
 * To make make it run, you need to include the simple.jar (containing
 * the implementation) and the kxml lib (http://kxml.objectweb.org/)
 * you need as well to configure the server over a xml file (called 
 * mapper.xml)
 * There you specify which service is mapped to which request. at the 
 * moment all the request are mapped to the same service class. if
 * the request is a filetransfer over post, the file will be extracted
 * by the PostFileTransfer.class otherwise this class gives a simple 
 * html hello worl file as answer.
 * the service is configured that the mapper.xml should be in the same 
 * directory where the programm is started (at the moment in the src 
 * file).
 * @author rose gostner
 *
 */
public class HTTPServer {
	private static Logger logger = Logger.getLogger(HTTPServer.class);

	private ServerSocket server;

	private File mapper;

	static int DEFAULT_PORT = 8080;

	private static String SERVER_CONFIG_FILE = "mapper.xml";

	private static String SERVER_CONFIG_DIR = System
			.getProperty("file.separator");

	public HTTPServer() throws UnknownHostException {
		this(InetAddress.getLocalHost());
	}

	public HTTPServer(InetAddress ia) {
		this(ia, DEFAULT_PORT);
	}

	public HTTPServer(InetAddress ia, int port) {
		String tmpDirectory= System.getProperty("java.io.tmpdir")+SERVER_CONFIG_DIR;
		
		File file = new File(tmpDirectory);
		/*
		 * the configuration file for the server is in the jar. to keep realte 
		 * easy to configure, i decided to read out the file from the jar, than
		 * to wite it to the application root directory. there, the server will 
		 * read the configuration and afterwards the file will be deleted.
		 * best would be, if the server finds the file by it own, but until 
		 * now i couldn't figure out how to do that. 
		 */

		InputStream is = getClass().getResourceAsStream(
				SERVER_CONFIG_DIR + SERVER_CONFIG_FILE);
		mapper = new File(tmpDirectory + SERVER_CONFIG_FILE);
		FileOutputStream outstream = null;
		File out = new File(tmpDirectory + SERVER_CONFIG_FILE);
		logger.info("file for cache context is here: " + out.getAbsolutePath());
		try {
			outstream = new FileOutputStream(out);
			for (int i = is.read(); i != -1; i = is.read()) {
				outstream.write(i);
			}
			outstream.close();
		} catch (FileNotFoundException e1) {
			logger.warning("File not found Exception.");
		} catch (IOException e) {
			logger.warning("IO Exception.");
		}

		CacheContext context = new CacheContext(file);
		logger.info("context is looking for mapper file at :"
				+ context.getBasePath());
		MapperEngine engine;
		try {
			engine = new MapperEngine(context);
			// TODO: need to replace with more general code
/*			RelateProtocolHandler handler = new RelateProtocolHandler(
					ProtocolHandlerFactory.getInstance(engine));
			Connection connection = ConnectionFactory.getConnection(handler);
			server = new ServerSocket();
			server = new ServerSocket(port, 50, ia);
			connection.connect(server);*/
			logger.finer("server is listening to "
					+ server.getInetAddress().getHostName());
			logger.finer("server is listening to "
					+ server.getInetAddress().getHostAddress());
			logger
					.debug("server is listening on port "
							+ server.getLocalPort());
			if (mapper != null) {
				mapper.delete();
			}

		} catch (IOException e) {
			logger.finer("Http Server throw a IOException");
		} catch (Exception e) {
			logger.finer("other exception: ");
		}
	}

	public static void main(String[] args) {
		try {
			new HTTPServer();
		} catch (UnknownHostException e) {
			logger.finer("unknown host exception");
		}
	}

	public void close() {
		try {
			if (server != null)
				server.close();
		} catch (IOException e) {
			logger.finer(" IOException");
		}
	}

}

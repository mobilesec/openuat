/* Copyright The Relate project team, Lancaster University
 * File created 2006-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.ip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import java.util.logging.Logger;

import simple.http.Request;
import simple.http.Response;
import simple.http.serve.Context;


public class PostFileTransfer extends simple.http.load.Service {
	private static Logger logger = Logger.getLogger(PostFileTransfer.class.getName());
	private Vector listener;
	private File path = new File("");
	
	public PostFileTransfer(Context context) {
        super(context);
        listener= new Vector();
        /*
         * register a listener here. it seems not to be the right way to do it. 
         * but have no better idea at the moment.
         * this service is used by the server to fetch the POST sended file.
         * as soon as the file comes in, the listeners are informed. 
         * to make sure, that the right listener is informed, this one is 
         * taken from the FileTransferListener.
         */
        listener.add(FileTransferService.getFileTransferService().getListener());
        
     }

     public void process(Request req, Response resp) throws Exception {
    	 if (req.getMethod().compareToIgnoreCase("post")!= 0){
    		 resp.setCode(501);
    		 sendAnswer("You did not post a file", resp );
    		 return;
    	 }
    	 if (req.getContentLength() <=0){
    		 resp.setCode(HttpURLConnection.HTTP_NOT_IMPLEMENTED);
    		 sendAnswer(" the ContentLenght is "+req.getContentLength()+" no" +
    		 		"file transfer will be done", resp);
    		 return;
    	 }
    	 
    	 long temp = System.currentTimeMillis();
    	 String filename =temp+"."+req.getContentType().getSecondary();
    	 File output = new File (path+filename);
    	 FileOutputStream fos = new FileOutputStream (output);
    	 BufferedOutputStream bos = new BufferedOutputStream (fos);
    	 InputStream is = req.getInputStream();
    	 BufferedInputStream bis = new BufferedInputStream(is);
    	 int b =bis.read();
    	 while (b != -1){
    		 bos.write(b);
    		 b=bis.read();
    	 }
    	 bos.close();
    	 bis.close();
    	 String transFileName = getTransmittedFilename(req.toString());
    	 String source =getTransmittedId(req.toString());
    	 if (transFileName != null ){
    		 File move = new File(path+transFileName);
    		 output.renameTo(move);
    		 output=move;
    	 }
    	 
    	 fireEventToListener(new FileTransferEvent (source, output));
    	 sendAnswer("file transfer was successfull", resp);
    	 logger.finer("file stored in "+ output.getAbsolutePath());
    	
       
     }
     
     private String getTransmittedFilename(String header){
    	 String result =null;
    	 StringTokenizer token = new StringTokenizer(header, "\r\n");
    	 while (token.hasMoreTokens()){
    		 String nextToken= token.nextToken();
			StringTokenizer parameter = new StringTokenizer(nextToken, ": ");
			if (parameter.nextToken().compareToIgnoreCase(HTTPSocket.RELATE_ATTRIBUTE_FILENAME)==0){
				logger.finer(" file name found");
				result= parameter.nextToken();
			}
    	 }
    	 return result;
     }
     
     private String getTransmittedId(String header){
    	 String result =null;
    	 StringTokenizer token = new StringTokenizer(header, "\r\n");
    	 while (token.hasMoreTokens()){
    		 String nextToken= token.nextToken();
			StringTokenizer parameter = new StringTokenizer(nextToken, ": ");
			if (parameter.nextToken().compareToIgnoreCase(HTTPSocket.RELATE_ATTRIBUTE_ID)==0){
				logger.finer("id found");
				result= parameter.nextToken();
			}
    	 }
    	 return result;
     }
     
     private void sendAnswer(String string, Response resp) throws IOException{
    	 PrintStream out = resp.getPrintStream();
         resp.set("Content-Type", "text/html");
         out.println("<html>");
         out.println("<head>");
         out.println("<title> HTTP RELATE Server </title>");
         out.println("</head>");      
         out.println("<body>");
         out.println(string);
         out.println("</body>");
         out.println("</html>");
     }
     
    public void addFileTransferListener(FileTransferListener l){
 		listener.add(l);
 	}
 	public void removeFileTransferListener(FileTransferListener l){
 		listener.remove(l);
 	}
 	
 	 private void fireEventToListener(FileTransferEvent fte) {
 		Iterator iter= listener.iterator();
 		while(iter !=null && iter.hasNext()){
 			FileTransferListener l = (FileTransferListener)iter.next();
 			l.receivedFile(fte);
 		}
 	}
     
     
     
     

	

}

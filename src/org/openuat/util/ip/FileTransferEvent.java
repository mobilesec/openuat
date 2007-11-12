
/* Copyright The Relate project team, Lancaster University
 * File created 2006-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.ip;

import java.io.File;
import java.util.EventObject;

public class FileTransferEvent extends EventObject {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * temp file name
	 */
	private File file;
	
	private String dest;
	
	public FileTransferEvent(Object source, File tmp){
		super(source);
		this.file=tmp;
	}
	
	
	public FileTransferEvent(Object source, String filepath) {
		this(source, filepath, null);
	}
	
	public FileTransferEvent(Object source, String filepath, String dest) {
		super(source);
		this.file= new File (filepath);
		this.dest=dest;
	}


	public File getTmpFile(){
		return file;
	}


	public String getDestination() {
		return dest;
	}
	
	public void setSource(int id){
		this.source=id+"";
	}

}

/* Copyright The Relate project team, Lancaster University
 * File created 2006-01
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util.ip;

import java.util.EventListener;

public interface FileTransferListener extends EventListener {
	
	/*
	 * reference to the new file;
	 */
	public void receivedFile(FileTransferEvent fts);

}

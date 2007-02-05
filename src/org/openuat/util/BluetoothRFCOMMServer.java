/* Copyright Rene Mayrhofer
 * File created 2007-01-25
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.util;

public class BluetoothRFCOMMServer {
	/*
// assuming the service UID has been retrieved
String serviceURL =
    "btspp://localhost:"+serviceUID.toString());
// more explicitly:
String ServiceURL =
    "btspp://localhost:10203040607040A1B1C1DE100;name=SPP 
        Server1";
try {
    // create a server connection
    StreamConnectionNotifier notifier =
       (StreamConnectionNotifier) Connector.open(serviceURL);
    // accept client connections
    StreamConnection connection = notifier.acceptAndOpen();
    // prepare to send/receive data
    byte buffer[] = new byte[100];
    String msg = "hello there, client";
    InputStream is = connection.openInputStream();
    OutputStream os = connection.openOutputStream();
    // send data to the client
    os.write(msg.getBytes());
    // read data from client
    is.read(buffer);
    connection.close();
} catch(IOException e) {
  e.printStackTrace();
}
...	 */
	
/*
// lets name our variables
StreamConnectionNotifier notifier = null;
StreamConnection sconn = null;
LocalDevice localdevice = null;
ServiceRecord servicerecord = null;

// step #1
// the String url will already be defined with the 
// correct url parameters
notifier = (StreamConnectionNotifier)Connector.open(url);

// step #2
// we will get the LocalDevice if not already done so
localdevice = LocalDevice.getLocalDevice();
servicerecord = localdevice.getRecord(notifier);

// step #3 is optional

// step #4
// this step will block the current thread until
// a client responds this step will also cause the
// service record to be stored in the SDDB
notifier.acceptAndOpen();

// step #5
// just wait...
// assume the client has connected and you are ready to exit

// step #6
// this causes the service record to be removed 
// from the SDDB
notifier.close(); */
}

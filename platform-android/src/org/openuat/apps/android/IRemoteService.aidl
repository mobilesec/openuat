// IRemoteService.aidl
package org.openuat.apps.android;

/** Service interface */
interface IRemoteService {
    /** Send data to other device */
    boolean send(String data);
    
    /** receive data from other device */
    String receive();
    
}
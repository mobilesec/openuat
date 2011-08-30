// IRemoteService.aidl
package at.fhooe.mc.test;

/** Service interface */
interface IRemoteService {
    /** Send data to other device */
    boolean send(String data);
    
    /** receive data from other device */
    String receive();
    
}
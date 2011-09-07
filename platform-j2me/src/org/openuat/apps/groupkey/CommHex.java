package org.openuat.apps.groupkey;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.StringTokenizer;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.openuat.groupkey.GroupKeyMessageHandler;
import org.openuat.groupkey.StringEventListener;

/**
 * @author Christoph Egger
 * 
 * Must be run as a thread!!! 
 * Example:
 *  comm = new Comm();
 *	Thread commThread = new Thread(comm);
 *	commThread.start();
 *
 *  Additionally, the calling object must also be run as thread!
 *  In the run() method of the calling object, "this.wait()" has to be called, and
 *  in the "handleStringEvent" method, "this.notify()" has to be called, which 
 *  stops the waiting in the run() method. Further work has to be done triggered 
 *  by the run() method.
 */
public class CommHex implements GroupKeyMessageHandler {

	private String serverLocation = "http://www.ibk.tuwien.ac.at/~lwallentin/UAT/";
	private int whatToDo = 0; //1=send, 2=get, 3=getSince
	private final int SEND = 1;
	private final int GET = 2;
	private final int GETSINCE = 3;
	
	//private final int SENDNORET = 4;
	//Variables used for communication
	private String folder;
	private String message;
	private int time;
	private StringEventListener guest;
	private String log;
	/**
	 * Standard Constructor
	 */
	public CommHex(){
		log=new String();
	}
	
	public String getLog()
	{
		return log;
	}
	/**
	 * Constructor updating the location of the server
	 * @param _serverLocation location of the server where the put.php and get.php scripts lie
	 */
	public CommHex(String _serverLocation){
		serverLocation = _serverLocation;
	}
	/**
	 * Updates the location of the server
	 * @param _serverLocation location of the server where the put.php and get.php scripts lie
	 */
	public void setServerLocation(String _serverLocation){
		serverLocation = _serverLocation;
	}
	/**
	 * Triggers the reception of all message from a specific folder.
	 * <p>
	 * implementation of ifComm
	 * 
	 * @param _folder folder name where the desired messages are
	 * @param _guest calling application thread
	 * @see GroupKeyMessageHandler
	 */
	public void getMsg(String _folder, StringEventListener _guest) {
		synchronized (this){
			log+="getMsg - ";
			folder = _folder;
			guest = _guest;
			whatToDo = GET;
			this.notify();
		}
	}
	/**
	 * Called by the run method when the reception of all message is triggered by getMsg
	 * @see Comm#run()
	 * @see Comm#getMsg(String, StringEventListener)
	 */
	private synchronized void get() {
		String URL1 = serverLocation + "get.php?folder="+folder;
		String retString = "";
		String tmpString = "";
		//get Messages from Server
		try {
			tmpString = new String(requestUsingGET(URL1).toCharArray());
		} catch (Exception e) {
			guest.handleStringEvent(e.getMessage(), false);  
		}
		StringTokenizer st = new StringTokenizer(tmpString, "|");
		while(st.hasMoreTokens()){
			StringTokenizer tmpST = new StringTokenizer(st.nextToken(), "-");
			String timestamp = tmpST.nextToken();
			String tmpRetString = "";
			try {
				tmpRetString = new String(Hex.decodeHex(tmpST.nextToken().toCharArray()));
			} catch (DecoderException e) {
				guest.handleStringEvent(e.getMessage(), false);  
			}
			retString += timestamp+"-"+tmpRetString+"|";
		}
		whatToDo=0;
		guest.handleStringEvent(retString, true);

	}
	/**
	 * Triggers the reception of all messages which were sent starting at a specific time untul now from a specific folder. 
	 * <p>
	 * implementation of ifComm
	 * 
	 * @param _folder folder name where the desired messages are
	 * @param _time starting time, oldest wanted message
	 * @param _guest calling application thread
	 * @see GroupKeyMessageHandler
	 */
	public void getMsgSince(String _folder, int _time, StringEventListener _guest) {
		synchronized (this){
			log+="getMsgSince - ";
			folder = _folder;
			time = _time;
			guest = _guest;
			whatToDo = GETSINCE;
			this.notify();
		}
	}
	/**
	 * Called by the run method when the reception of messages, sent at a specific time until now is triggered by getMsgSince
	 * @see Comm#run()
	 * @see Comm#getMsgSince(String, int , StringEventListener)
	 */
	public synchronized void getSince() {
		String URL1 = serverLocation + "get.php?folder="+folder+"&time="+time;
		String retString = "";
		String tmpString = "";
		//get Messages from Server
		try {
			tmpString = new String(requestUsingGET(URL1).toCharArray());
		} catch (Exception e) {
			guest.handleStringEvent(e.getMessage(), false);  
		}
		StringTokenizer st = new StringTokenizer(tmpString, "|");
		while(st.hasMoreTokens()){
			StringTokenizer tmpST = new StringTokenizer(st.nextToken(), "-");
			String timestamp = tmpST.nextToken();
			String tmpRetString = "";
			try {
				tmpRetString = new String(Hex.decodeHex(tmpST.nextToken().toCharArray()));
			} catch (DecoderException e) {
				guest.handleStringEvent(e.getMessage(), false);  
			}
			retString += timestamp+"-"+tmpRetString+"|";
		}
		whatToDo=0;
		guest.handleStringEvent(retString, true);

	}
	/**
	 * Triggers sending of a message
	 * @param _folder folder name where the message shall be placed
	 * @param _msg message to send
	 * @param _guest calling application thread
	 */
	public  void sendMsg(String _folder, String _msg, StringEventListener _guest) {
		synchronized (this){
			log+="sendMsg - ";
			folder = _folder;
			message = new String(Hex.encodeHex(_msg.getBytes()));
			guest = _guest;
			whatToDo = SEND;
			this.notify();
			
		}
	}
	/**
	 * Called by the run method when sending of a message, is triggered by sendMsg
	 * @see Comm#run()
	 * @see Comm#sendMsg(String, String, StringEventListener)
	 */
	public synchronized void send() {
		
		String URL1 = serverLocation + "put.php?folder="+folder+"&msg="+message;
		try {
			requestUsingGET(URL1);
		} catch (IOException e) {
			guest.handleStringEvent(e.getMessage(), false);  
		}
		whatToDo=0;
		guest.handleStringEvent("Message sent!", true);
	}
	/*
	 * Triggers sending of a message
	 * @param _folder folder name where the message shall be placed
	 * @param _msg message to send
	 */
/*
 	public void sendMsgNoReturn(String _folder, String _msg){
		folder = _folder;
		message = _msg;
		whatToDo = SENDNORET;
		synchronized (this){
			this.notify();
		}
	}
*/
	/*
	 * Called by the run method when sending of a message, is triggered by sendMsg
	 * @see Comm#run()
	 * @see Comm#sendMsg(String, String, ifListener)
	 */
/*
	public void sendNoReturn() {
		String URL1 = serverLocation + "put.php?folder="+folder+"&msg="+message;
		try {
			requestUsingGET(URL1);
		} catch (IOException e) {
			//can not do anything here
		}
	}
*/
	/**
	 * Sends a HTTP request using GET.
	 * @param URLString The URL where the request shall be sent
	 * @return answer from the server
	 * @throws IOException when the request failed
	 */
	private synchronized String requestUsingGET(String URLString) throws IOException {
		HttpConnection hpc = null;
		DataInputStream dis = null;
		boolean newline = false;
		String content = "";
		hpc = (HttpConnection) Connector.open(URLString);
		dis = new DataInputStream(hpc.openInputStream());
		int character;

		while ((character = dis.read()) != -1) {
			if ((char) character == '\\') {
				newline = true;
				continue;
			} else {
				if ((char) character == 'n' && newline) {
					content += "\n";
					newline = false;
				} else if (newline) {
					content += "\\" + (char) character;
					newline = false;
				} else {
					content += (char) character;
					newline = false;
				}
			}
		}
		if (hpc != null)
			hpc.close();
		if (dis != null)
			dis.close();

		return content;
	}
	/**
	 * Run method of the Thread.
	 * <p>
	 * calls the specific methods when a notify() is send by another thread.
	 */
	public void run(){
		while (true){
			synchronized(this){
			switch (whatToDo){
			case SEND:
				log+="run Send - ";
				send();
				break;
			case GET:
				log+="run get - ";
				get();
				break;
			case GETSINCE:
				log+="run getsince - ";
				getSince();
				break;
			/*case SENDNORET:
				sendNoReturn();
				break;*/
			default:
				log+="run default - ";
				break;
			}
			
				try{
					this.wait();
				}
				catch(InterruptedException e){
				}
			}
		}
	}
}

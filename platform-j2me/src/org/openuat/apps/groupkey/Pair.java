/* Copyright Lukas Wallentin
 * File created 2009-01-12
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.openuat.apps.groupkey;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import net.mypapit.java.StringTokenizer;

import org.apache.commons.codec.binary.Hex;
import org.openuat.authentication.SimpleKeyAgreement;
import org.openuat.authentication.exceptions.KeyAgreementProtocolException;
import org.openuat.util.SimpleBlockCipher;

/**
 * @author Lukas Wallentin
 * 
 * Must be run as a thread!!! 
 * Example:
 *  comm = new Comm();
 *	Thread commThread = new Thread(comm);
 *	commThread.start();
 *
 * This Class provides basic key management functions. 
 * In the background it uses the Stored keys to generate 
 * additional keys between its two hop neighbors.
 */

public class Pair implements ifPair 
{
	private Hashtable pairKeyTable; //Tables for keys from one hop neighbors
	private Hashtable tmpKeyTable; //Table for keys from one and two hop neighbors
	private Hashtable keyGeneration;
	private String name;
	private ifComm comm;
	private String log;
	private Vector workspace;
	private boolean init;
	private boolean lock;
	private SimpleBlockCipher cipher;
	private String magicWord;
	private	int last;
	
    /**
	* Standard Constructor
	*/
	public Pair()
	{
		lock=false;
		pairKeyTable=new Hashtable();
		tmpKeyTable=new Hashtable();
		keyGeneration=new Hashtable();
		name=new String("");
		workspace= new Vector();
		last=0;
		magicWord = new String("KlaatuBaradaNikto");
		log="init - ";
		init=true;
		comm = new CommPlain();
		Thread commThread = new Thread(comm);
		commThread.start();	
		cipher = new SimpleBlockCipher(true);			
	}
	/**
	 * Capsulation of the encrypt function
	 * @param _plainText Plaintext to encrypt
	 * @param _key key
	 * @return String in the form $crypttext+"-"+$size or -1 if there is a failure
	 */
	private String encypt(String _plainText, byte[] _key)
	{
		byte[] pText = _plainText.getBytes();
		int tSize = pText.length*8;
		String result = new String("");
		try 
		{
			byte[] cText = cipher.encrypt(pText, tSize, _key);
			result+= new String(Hex.encodeHex(cText))+"-"+tSize;
		} catch (Exception e) {
			log+=e.toString();
			result+="-1";
		}
		return result;
		//return _plainText+"-"+tSize; //use this result for debugging
	}
	
	/**
	 * decryption function
	 * @param _cypherText Crypted text
	 * @param _key Key
	 * @param _size Size of the original Text
	 * @return Plaintext or -1 if there is a failure
	 */
	private String decrypt(String _cypherText, byte[] _key, int _size)
	{
		String result = new String("");
		try 
		{
			byte[] cText = Hex.decodeHex(_cypherText.toCharArray());
			byte[] pText = cipher.decrypt(cText,_size, _key);
			result+= new String(pText);
		} catch (Exception e) {
			log+=e.toString();
			result+="-1";
		}
		return result;
		//return _cypherText; //use this result for debugging
	}
	
	/**
	 * Init function to trigger two hop neighbour key keneration mechanism
	 * @param _remote name of he neighbour
	 */
	public void init(String _remote)
	{
		if (init)
		{	
			init=false;
			workspace.addElement(new StringTokenizer("0-NULL-START*"+_remote+"-0","-")); //Add startpoint to workspace
			log=log+"add START to workspace - ";
			workOnSpace();
		}
	}
	
	/**
	 * @see org.openuat.apps.groupkey.ifPair#getLog()
	 */
	public String getLog()
	{
		return log;
	}
	
	/**
	 * @see org.openuat.apps.groupkey.ifPair#getKey(java.lang.String)
	 */
	public byte[] getKey(String _bob) 
	{
		if ( tmpKeyTable.containsKey(_bob))
		{
			return (byte[]) tmpKeyTable.get(_bob);
		}
		return null;
	}

	/**
	 * @see org.openuat.apps.groupkey.ifPair#listKeys()
	 */
	public Hashtable listKeys() 
	{
		return tmpKeyTable;
	}

   /**
    * @see org.openuat.apps.groupkey.ifPair#setKeyPair(java.lang.String, byte[])
    */
	public void setKeyPair(String _remote, byte[] _key) 
	{
		log=log+"Set Keypair: " + _remote + " - ";
		pairKeyTable.put( _remote, _key );
		tmpKeyTable.put(_remote, _key);
		init(_remote);
	}
	
	/**
	 * @see org.openuat.apps.groupkey.ifPair#setName(java.lang.String)
	 */
	public void setName( String _name)
	{
		log=log+"Set name: " + _name + " - ";
		name= _name;		
	}

	/**
	 * Eventhandler called after the successfull sending of a message
	 * or if a message was received.
	 * If there is a new message, the message is stored in the vector 
	 * for later usage
	 * 
	 * @see org.openuat.apps.groupkey.ifListener#handleStringEvent(java.lang.String, boolean)
	 */
	public void handleStringEvent(String _data, boolean _success) 
	{
		log=log+ "Stringevent: "+_data+" - ";
		if (_success)
		{
			if (_data.compareTo("Message sent!")!=0)
			{
				StringTokenizer fileStringTokenizer = new StringTokenizer(_data,"|");
				while (fileStringTokenizer.hasMoreElements())
				{
					StringTokenizer tempStringTokenizer = new StringTokenizer(fileStringTokenizer.nextToken(),"-");
					if( tempStringTokenizer.countTokens()>2)
					{
						workspace.addElement(tempStringTokenizer);
					}
					last = Integer.parseInt(tempStringTokenizer.peek())+1;
				}
			}
			else
			{
				lock=false;
			}
		}
	}

/**
 * function which works on the vector with the stored messages.
 * if the message has a specific structure, it is possible to decrypt the message
 * and extract the command. If it is a valid command, the function performs the command.
 * Structure: Timestamp - src (for key) - [CryptedCommand] - size of the command
 * [CryptedCommand]: Command*Parameter1*Parameter2..
 * Commands: 
 *    START*Remote  // Perform starting procedure with pair "Remote" //intern
 *    GETPAIRS*Remote // Send pairinfo to "Remote"
 *    PAIRS*Remote*part1*part2.... //List of pairs
 *    MAKEKEY*Remote*Pair  //inter, to generate a new pair
 *    FORWARD*remote*pair*forwarder*newcommand*Parameter //to forward messages to two hop neighbours
 *    KEYREQUEST*forwarder*pair*pubkey //key request to trigger simple key agreement function
 *    
 */
	private void workOnSpace() 
	{
		log=log+"WorkOnSpace Size:"+workspace.size()+" - ";
		if (!lock)
		{
			StringTokenizer strTok;
			StringTokenizer CommandTok;
			try
			{
				strTok = (StringTokenizer)workspace.firstElement();
			}catch(Exception e){
				return;
			}
			if (strTok==null)
			{
				return;
			}
			strTok.nextToken(); //Time
			String src = strTok.nextToken();
			String command = strTok.nextToken();
			String tSize = strTok.nextToken();
			if (src.compareTo("NULL")!=0)
			{
				byte[] key = getKey(src);
				if (key==null) //no key -> not for me
				{
					workspace.removeElementAt(0);
					return;
				}
				CommandTok=new StringTokenizer(decrypt(command, key, Integer.parseInt(tSize)),"*");
			}
			else //not encrypted
			{
				CommandTok=new StringTokenizer(command,"*");
			}
			log=log+"Command:"+CommandTok.peek()+" - ";
		
			//Work on Commands
			if(((CommandTok.peek()).compareTo("START")==0)&&(src.compareTo("NULL")==0))
			{
				log=log+"in Start - ";
				CommandTok.nextToken(); //abolish command
				String pair =CommandTok.nextToken();
				//Ask for all Pairs
				comm.sendMsg("PairOb"+pair, name+"-"+encypt("GETPAIRS*"+name+"*"+magicWord,getKey(pair)), this);
				lock=true;
				workspace.removeElementAt(0);
			}
			else if(((CommandTok.peek()).compareTo("GETPAIRS")==0)&&(src.compareTo("NULL")!=0))
			{
				CommandTok.nextToken();
				log=log+"in Getpairs - ";
				String pair =CommandTok.nextToken();
				comm.sendMsg("PairOb"+pair,name+"-"+encypt("PAIRS*"+name+getTenPairs()+"*"+magicWord,getKey(pair)), this);
				lock=true;
				workspace.removeElementAt(0);
			}
			else if(((CommandTok.peek()).compareTo("PAIRS")==0)&&(src.compareTo("NULL")!=0))
			{
				log=log+"in PAIRS ";
				CommandTok.nextToken(); //remove command;
				String remote = CommandTok.nextToken();			
				while (CommandTok.hasMoreElements())
				{
					if( (name.compareTo(CommandTok.peek())!=0) && (magicWord.compareTo(CommandTok.peek())!=0))
					{
						try 
						{
							SimpleKeyAgreement ka= new SimpleKeyAgreement(true);
							ka.init(true);
							keyGeneration.put(CommandTok.peek(), ka);
							workspace.addElement(new StringTokenizer("0-NULL-MAKEKEY*"+remote+"*"+CommandTok.nextToken()+"-0","-"));
						} catch (Exception e) {
							log+=e.toString();
						}
					}
					else
					{
						CommandTok.next();
					}
				}
				workspace.removeElementAt(0);
				workOnSpace();
			}
			else if(((CommandTok.peek()).compareTo("MAKEKEY")==0)&&(src.compareTo("NULL")==0))
			{
				log=log+"in MAKEKEY ";
				CommandTok.nextToken(); //remove command;
				String remote = CommandTok.nextToken();	
				String target = CommandTok.nextToken();
				SimpleKeyAgreement ka= (SimpleKeyAgreement) keyGeneration.get(target);
				try 
				{
					comm.sendMsg("PairOb"+remote,name+"-"+encypt("FORWARD*"+name+"*"+target+"*"+remote+"*KEYREQUEST*"+new String(Hex.encodeHex(ka.getPublicKey())),getKey(remote)), this);
					lock=true;			
					workspace.removeElementAt(0);
				} catch (KeyAgreementProtocolException e) {
					log+=e.toString();
				}
		
			}
			else if(((CommandTok.peek()).compareTo("FORWARD")==0)&&(src.compareTo("NULL")!=0))
			{
				log=log+"in FORWARD ";
				CommandTok.nextToken(); //remove command;
				String source = CommandTok.nextToken();	
				String target = CommandTok.nextToken();
				String mm = CommandTok.nextToken();
				String type = CommandTok.nextToken();
				String param =CommandTok.nextToken();
				comm.sendMsg("PairOb"+target,name+"-"+encypt(type+"*"+mm+"*"+source+"*"+param,getKey(target)), this);
				lock=true;			
				workspace.removeElementAt(0);
			}	
			else if(((CommandTok.peek()).compareTo("KEYREQUEST")==0)&&(src.compareTo("NULL")!=0))
			{
				log=log+"in KEYREQUEST ";
				CommandTok.nextToken(); //remove command;
				String mm = CommandTok.nextToken();
				String pair = CommandTok.nextToken();
				String key =CommandTok.nextToken();
				if (keyGeneration.containsKey(pair))
				{
					SimpleKeyAgreement ka= (SimpleKeyAgreement) keyGeneration.get(pair);	
					try 
					{
						ka.addRemotePublicKey(Hex.decodeHex(key.toCharArray()));
						tmpKeyTable.put(pair, ka.getSessionKey());
					} catch (Exception e) {
						log+=e.toString();
					}
					keyGeneration.remove(pair);
					workspace.removeElementAt(0);
					log+="**KEYFINISHED-nosend Key: "+new String(Hex.encodeHex((byte[])tmpKeyTable.get(pair)))+"**";
					workOnSpace();
				}
				else
				{
					try 
					{
						SimpleKeyAgreement ka= new SimpleKeyAgreement(true);
						ka.init(true);
						comm.sendMsg("PairOb"+mm,name+"-"+encypt("FORWARD*"+name+"*"+pair+"*"+mm+"*KEYREQUEST*"+new String(Hex.encodeHex(ka.getPublicKey())),getKey(mm)), this);
						ka.addRemotePublicKey(key.getBytes());
						tmpKeyTable.put(pair, ka.getSessionKey());
						lock=true;
						log+="**KEYFINISHED-send-Key: "+new String(Hex.encodeHex((byte[])tmpKeyTable.get(pair)))+"**";
					} catch (Exception e) {
						log+=e.toString();
					}
					workspace.removeElementAt(0);
				}			
			}
			else
			{
				log+="Removed unknown message";
				workspace.removeElementAt(0);
			}
		}
	}
	
   /**
    * Returns a list with maximum 10 pair nodes
    * @return string with the names of the pairs ('*' is the seperator)
    */
	private String getTenPairs() 
	{
		String out= new String();
		int i = 0;
		Enumeration e = tmpKeyTable.keys();
		while(( e.hasMoreElements() ) && (i<10))
		{
			i++;
			out=out+"*"+e.nextElement();
		}
		return out;
	}
   
	/**
    * @see java.lang.Runnable#run()
    */
	public void run() 
	{
		log=log+"Run - ";
		while (true)
		{
			try
			{
				//happy polling :)
				if ((name.compareTo("")!=0) && (workspace.isEmpty()) && (!init))
				{
					log=log+"Check for updates "+last+" - ";	
					comm.getMsgSince("PairOb"+name,last, this);
				}
				else
				{
					workOnSpace();
					log=log+"Don't check for updates - "+(name.compareTo("")!=0)+(workspace.isEmpty())+(!init)+" - ";	
				}
			}catch(Exception e){
				log=log+e.toString()+" - ";
			}
			synchronized(this)
			{
				try
				{
					this.wait(10000);
				}catch(Exception e){
				//log=log+e.toString()+" - ";
				}
			}
		}
	}
	
	/**
	 * @see org.openuat.apps.groupkey.ifPair#resetLog()
	 */
	public void resetLog()
	{
		log="";
	}
}

/* Copyright Martijn Sack
 * File created 2009-01-18
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.openuat.apps.groupkey;

import java.io.NotSerializableException;

import sun.security.provider.DSAPrivateKey;
import tgdh.*;
import tgdh.comm.*;


import org.openuat.channel.http.*;
import org.openuat.util.ifListener;

import tgdh.crypto.*;
import tgdh.tree.*;
import junit.framework.TestCase;
import junit.textui.TestRunner;

/**
 * @author Martijn Sack
 *
 * Application stub to GroupKey (tgdh) stub
 * 
 * Basic tree creation and mutation TGDH tree funtions are implemented in this class. 
 * org.openuat.channel.http is used as communicator instead of the tgdh.comm class.
 */

public class App2tgdh  implements ifListener {
	
	private static BasicTree testTree;
	private static LeafNode newNode;
	private static CommPlain communicator;
	private static byte[] privateTestKey = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
	private static byte[] publicTestKey = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
	
	
	public static BasicTree createTree(String _uniqueId){
		/**
		 * @return New created tree
		 */
		//Create LeafNode
		LeafNode M1 = new LeafNode(_uniqueId);
		//Just one Node
		Node[] node = new Node[]{M1};
		
		
		TreeInfo treeinfo = new TreeInfo(node, TreeInfo.PREORDER, _uniqueId);

		//Two types of trees can be created
//		Tree newTree = new Tree(treeinfo);
		BasicTree newTree = new BasicTree(treeinfo);
		return newTree;
	}
	
	public static void joinTree(BasicTree _basicTree, String _uniqueID, String _treeGroupName) throws Exception{
		/**
		 * Sends join message to the server
		 * @param _uniqueID Creates newNode from,  
		 * @param _basicTree Existing tree
		 * @param _treeGroupName The group name the new node want to join to
		 */
		newNode = new LeafNode(_uniqueID);


		/**
		 * HOW TO DO THISSS?
		 */

//		TgdhPrivateKey privateKey = new TgdhPrivateKey();
//		TgdhPublicKey publicKey = new TgdhPublicKey();
		
//		newNode.setKeys(privateKey , publicKey);

		JoinMessage joinMessage = new JoinMessage(_treeGroupName,newNode);
		
		/**
		 * Creates new communicator threat
		 */


		/**
		 * Why ifListener ifListen = null; ?
		 */
		ifListener ifListen = null;
		communicator.sendMsg("tree", joinMessage.toString(), ifListen);

		

		
		
	}
	
	public static void main(String[] args) {
		/**
		 * Test runs...
		 */
		String uniqueId = "12345";
		String uniqueId2 = "6789";
		
		communicator = new CommPlain();
		Thread commThread = new Thread(communicator);
		commThread.start();		

		
		testTree = createTree(uniqueId);
		try {
			joinTree(testTree, uniqueId2, "testTree");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println(testTree.basicTreeInfo());
	}

	@Override
	public void handleStringEvent(String _data, boolean _success) {
		// TODO Auto-generated method stub

		/**
		 * JOIN step 2
		 * Let's say _data is join message (how to detect this!?)
		 * 
		 * TODO updates key tree
		 * TODO get all nodes from SponsorNode to Root and remove all there keys
		 */

		
		LeafNode[] nodesToRoot = testTree.leafNodes();
		LeafNode sponsor = null;
		try {
			sponsor = (LeafNode) testTree.join(newNode);
		} catch (TgdhException e) {
			e.printStackTrace();
		}

	
		ifListener ifListen = null;
		//Broadcast new tree
		communicator.sendMsg("tree", testTree.toString(), ifListen);
		communicator.sendMsg("tree", sponsor.toString(), ifListen);

		/**
		 * JOIN step 3
		 * Let's say _data is a new tree after joining (how to detect this!?)
		 * 
		 * TODO compute new group key using the new tree
		 */		
		

		
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}	
	
}

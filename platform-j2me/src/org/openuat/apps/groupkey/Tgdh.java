/** Copyright Martijn Sack
 * File created 2009-01-27
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.apps.groupkey;

import org.openuat.apps.groupkey.*;

import tgdh.tree.BasicTree;

public class Tgdh {

	/**
	 * @param args
	 */
	public static App2tgdh app2tgdh;
	private static CommPlain communicator;
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		 app2tgdh = new App2tgdh();
		 Thread tgdhThread = new Thread(app2tgdh);
		 tgdhThread.start();
		
		 testJoinTree();
		
	}
	
	public static void testJoinTree(){

		BasicTree aTree = app2tgdh.createTree("M1");
		
		//expected tree before join
		String tree = "Preorder: <0,0>(M1)";
		
		
		String uniqueId2 = "M2";
		BasicTree joinedTree = null;
		try {
			joinedTree = app2tgdh.joinTree(aTree, uniqueId2, "testTree");
		} catch (Exception e) {
			e.printStackTrace();
		}

	
	}

}

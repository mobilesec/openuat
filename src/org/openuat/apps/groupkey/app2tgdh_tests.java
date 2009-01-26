package org.openuat.apps.groupkey;

import com.sun.corba.se.impl.orb.ParserTable.TestBadServerIdHandler;

import tgdh.tree.BasicTree;
import junit.framework.TestCase;

public class app2tgdh_tests extends TestCase{

	
	public static App2tgdh app2tgdh;
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		app2tgdh = new App2tgdh();
		
		testBasicTree();
		testJoinTree();
		
		
	}

	public static void testBasicTree(){
		BasicTree newTree = app2tgdh.createTree("12345");
		
		//expected tree
		String expectedTree = "Preorder: <0,0>(12345)";
		assertEquals("Tree information", expectedTree, newTree.toString());
		
	}
	
	public static void testJoinTree(){

		BasicTree aTree = app2tgdh.createTree("12345");
		
		//expected tree before join
		String tree = "Preorder: <0,0>(12345)";
		assertEquals("Tree information before join", tree, aTree.toString());		
		
		
		String uniqueId2 = "6789";
		BasicTree joinedTree = null;
		try {
			joinedTree = app2tgdh.joinTree(aTree, uniqueId2, "testTree");
		} catch (Exception e) {
			e.printStackTrace();
		}

		//expected tree after join
		String expectedTree = "Preorder: <0,0>, <1,0>(12345), <1,1>(6789)";
		assertEquals("Tree information after join", expectedTree, joinedTree.toString());			
		
		
		
		
	}
	
	
	
}

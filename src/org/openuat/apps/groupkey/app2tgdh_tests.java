package org.openuat.apps.groupkey;

import com.sun.corba.se.impl.orb.ParserTable.TestBadServerIdHandler;

import tgdh.tree.BasicTree;
import tgdh.tree.LeafNode;
import tgdh.tree.Node;
import junit.framework.TestCase;

public class app2tgdh_tests extends TestCase{

	
	public static App2tgdh app2tgdh;
	public static App2tgdh app2tgdh2;
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		app2tgdh = new App2tgdh();
		app2tgdh2 = new App2tgdh();
		
		testBasicTree();
		testJoinTree();
		testLeaveTree();
		
		
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
	
	public static void testLeaveTree(){
		String uniqueId2 = "6789";
		String uniqueId3 = "345";
		BasicTree aTree = app2tgdh.createTree("12345");
		BasicTree joinedTree = null;
		try {
			joinedTree = app2tgdh.joinTree(aTree, uniqueId2, "testTree");
			joinedTree = app2tgdh.joinTree(aTree, uniqueId3, "testTree");
		} catch (Exception e) {
			e.printStackTrace();
		}

		//expected tree before leaving
		String beforeLeavingTree = "Preorder: <0,0>, <1,0>, <2,0>(12345), <2,1>(6789), <1,1>(345)";
		assertEquals("Tree information before leaving", beforeLeavingTree, joinedTree.toString());	
		
		LeafNode leavingNode = joinedTree.leafNode("345");
		BasicTree newTree = app2tgdh.leaveTree(joinedTree, leavingNode); 
		
		//expected tree before leaving
		String expectedTree = "Preorder: <0,0>, <1,0>(12345), <1,1>(6789)";
		assertEquals("Tree information after leaving", expectedTree, newTree.toString());		
	}
	
	public static void testMergeTrees(){
		BasicTree tree1 = app2tgdh.createTree("M1");
		BasicTree tree2 = app2tgdh2.createTree("N1");

		BasicTree newTree = null; 

		try {
			tree1 = app2tgdh.joinTree(tree1, "M2", "testTree1");
			tree1 = app2tgdh.joinTree(tree1, "M3", "testTree1");
			tree1 = app2tgdh.joinTree(tree1, "M4", "testTree1");

			tree2 = app2tgdh2.joinTree(tree2, "N2", "testTree2");
			tree2 = app2tgdh2.joinTree(tree2, "N3", "testTree2");
			tree2 = app2tgdh2.joinTree(tree2, "N4", "testTree2");

		} catch (Exception e) {
			e.printStackTrace();
		}	

		//expected tree1 before merging
		String beforeMergingTree1 = "Preorder: <0,0>, <1,0>, <2,0>(M1), <2,1>(M2), <1,1>, <2,2>(M3), <2,3>(M4)";
		assertEquals("Tree1 information before merging", beforeMergingTree1, tree1.toString());		
		
		//expected tree2 before merging
		String beforeMergingTree2 = "Preorder: <0,0>, <1,0>, <2,0>(N1), <2,1>(N2), <1,1>, <2,2>(N3), <2,3>(N4)";
		assertEquals("Tree2 information before merging", beforeMergingTree2, tree2.toString());	
		
		newTree = app2tgdh.mergeTrees(tree1, tree2);
		
		//expected tree after merging
		String afterMergingTrees = "Preorder: <0,0>, <1,0>, <2,0>, <3,0>(M1), <3,1>(M2), <2,1>, <3,2>(M3), <3,3>(M4), <1,1>, <2,2>, <3,4>(N1), <3,5>(N2), <2,3>, <3,6>(N3), <3,7>(N4)";
		assertEquals("Tree information after merging", afterMergingTrees, newTree.toString());			
		
	}
	
	
}

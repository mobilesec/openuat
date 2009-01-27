/** Copyright Martijn Sack
 * File created 2009-01-26
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */

package org.openuat.apps.groupkey;

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
		testBasicPartitioning();
		
	}

	public static void testBasicTree(){
		BasicTree newTree = app2tgdh.createTree("M1");
		
		//expected tree
		String expectedTree = "Preorder: <0,0>(M1)";
		assertEquals("Tree information", expectedTree, newTree.toString());
		
	}
	
	public static void testJoinTree(){

		BasicTree aTree = app2tgdh.createTree("M1");
		
		//expected tree before join
		String tree = "Preorder: <0,0>(M1)";
		assertEquals("Tree information before join", tree, aTree.toString());		
		
		
		String uniqueId2 = "M2";
		BasicTree joinedTree = null;
		try {
			joinedTree = app2tgdh.joinTree(aTree, uniqueId2, "testTree");
		} catch (Exception e) {
			e.printStackTrace();
		}

		//expected tree after join
		String expectedTree = "Preorder: <0,0>, <1,0>(M1), <1,1>(M2)";
		assertEquals("Tree information after join", expectedTree, joinedTree.toString());
		assertTrue("Does contain node M2", expectedTree.toString().contains("M2"));
	
	}
	
	public static void testLeaveTree(){
		String uniqueId2 = "M2";
		String uniqueId3 = "M3";
		BasicTree aTree = app2tgdh.createTree("M1");
		BasicTree joinedTree = null;
		try {
			joinedTree = app2tgdh.joinTree(aTree, uniqueId2, "testTree");
			joinedTree = app2tgdh.joinTree(aTree, uniqueId3, "testTree");
		} catch (Exception e) {
			e.printStackTrace();
		}

		//expected tree before leaving
		String beforeLeavingTree = "Preorder: <0,0>, <1,0>, <2,0>(M1), <2,1>(M2), <1,1>(M3)";
		assertEquals("Tree information before leaving", beforeLeavingTree, joinedTree.toString());	
		
		LeafNode leavingNode = joinedTree.leafNode("M3");
		BasicTree newTree = app2tgdh.leaveTree(joinedTree, leavingNode); 
		
		//expected tree before leaving
		String expectedTree = "Preorder: <0,0>, <1,0>(M1), <1,1>(M2)";
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
	
	public static void testBasicPartitioning(){
		BasicTree tree1 = app2tgdh.createTree("M1");
		BasicTree newTree = null;
		
		try {
			tree1 = app2tgdh.joinTree(tree1, "M2", "testTree1");
			tree1 = app2tgdh.joinTree(tree1, "M3", "testTree1");
			tree1 = app2tgdh.joinTree(tree1, "M4", "testTree1");
			tree1 = app2tgdh.joinTree(tree1, "M5", "testTree1");
			tree1 = app2tgdh.joinTree(tree1, "M6", "testTree1");
			tree1 = app2tgdh.joinTree(tree1, "M7", "testTree1");

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//expected tree1 before partitioning
		String beforePartitioning = "Preorder: <0,0>, <1,0>, <2,0>, <3,0>(M1), <3,1>(M2), <2,1>, <3,2>(M3), <3,3>(M4), <1,1>, <2,2>(M5), <2,3>, <3,6>(M6), <3,7>(M7)";
		assertEquals("Tree information before partitioning", beforePartitioning, tree1.toString());			
		
		Node[] nodesToLeave = new Node[]{tree1.leafNode("M1"),tree1.leafNode("M4")};

		newTree = app2tgdh.partitionTree(tree1, nodesToLeave);
		
		//expected tree after partitioning
		String afterPartitioning = "Preorder: <0,0>, <1,0>, <2,0>(M2), <2,1>(M3), <1,1>, <2,2>(M5), <2,3>, <3,6>(M6), <3,7>(M7)";
		assertEquals("Tree information before partitioning", afterPartitioning, newTree.toString());
		assertFalse("Does not contain node M1", newTree.toString().contains("M1"));
		assertFalse("Does not contain node M4", newTree.toString().contains("M4"));
		
	}
}

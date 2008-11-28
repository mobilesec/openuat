/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.codec.audio.common;

/**
 * Auxiliary class for the AudioEncoder and AudioDecoder classes, to boost performance. This class creates a list of cached sinus and cosinus values.
 * @author Iulia Ion
 *
 */
public class SinCos {

	static int cache = 10;
	//I estimate 10 values for u, but to be on the safe side we use 20.
	static double u_values [] = new double [cache];  
	static double last_u ;
	static float sincos_values[][][] = new float [cache][4410][2];
	//static double sincos_values[][] = new double [4410][2];
	//static List sincos_valuess = new LinkedList();
	
	public static float [][] getSinCosValues(double u){
		//see if we have already u
		int i;
		boolean found = false;
		for (i = 0; i < u_values.length ; i++) {
			if (u_values[i]==0){//we reached the end, and need to add another u value
				break;
			}
			if (u_values[i] == u) {//we found u cached at index i;
				found = true;
				break;
			}
		}
		if(found )//we found a u
		{
			return sincos_values [i];
		}
		else {//add 
			if(i<u_values.length){
				u_values[i] = u;
				computeSinCos(u, i);
				return sincos_values [i];
			}
			//too many u values
			//System.out.println("error, too many u values");
			//TODO copy to another array 
			
		}
		return computeSinCos(u);
	}

	/**
	 * computes an array of sin and cos values for multiple of u, from 0 to 4410
	 * @param u 
	 * @param index the index where to start storing the values in the sincos array
	 */
	private static void computeSinCos(double u, int index) {
		double g = 0;
		for (int i = 0; i < 4410; i++) {
			
			g = i*u;
			sincos_values[index][i][0]= (float) Math.sin(g);
			sincos_values[index][i][1]= (float) Math.cos(g);
			cosinv ++;
		}
	}

	public static int cosinv = 0;

	private static float[][] computeSinCos(double u) {
		double g = 0;
		float [][] sincos_values = new float [4410][2];
		for (int i = 0; i < 4410; i++) {
			
			g = i*u;
			sincos_values[i][0]= (float) Math.sin(g);
			sincos_values[i][1]= (float) Math.cos(g);
			cosinv ++;
		}
		return sincos_values;
	}
}

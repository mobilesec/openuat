/* Copyright Rene Mayrhofer
 * File created 2008-03-04
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.openuat.sensors.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;
import java.util.zip.GZIPInputStream;

public class PositiveNegativeTestsHelper {
	/** This is a small helper to get all *.gz files from a directory. */
	public static String[] getTestFiles(String directory) {
		File dir = new File(directory);
		String[] testFiles = dir.list(new FilenameFilter() { 
			public boolean accept(File dir, String name) {
				return name.endsWith(".gz");
			}
		});
		return testFiles;
	}

	/** This helper function returns the length of the data set in seconds. */ 
	public static int determineDataSetLength(String filename) throws FileNotFoundException, IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename))));
		int firstTimestamp = -1, lastTimestamp = -1;
		String line = in.readLine();
		while (line != null) {
			StringTokenizer st = new StringTokenizer(line, " .", false);
			int timestampSecs = Integer.parseInt(st.nextToken());
			if (firstTimestamp == -1)
				firstTimestamp = timestampSecs;
			if (timestampSecs > lastTimestamp)
				lastTimestamp = timestampSecs;
			line = in.readLine();
		}
		return lastTimestamp - firstTimestamp;
	}
}

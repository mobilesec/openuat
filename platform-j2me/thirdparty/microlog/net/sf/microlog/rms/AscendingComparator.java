/*
 * Copyright 2008 The Microlog project @sourceforge.net
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.microlog.rms;

import javax.microedition.rms.RecordComparator;

/**
 * An ascending RecordComparator, based on the timestamp.
 * 
 * @author Darius Katz
 * @author Johan Karlsson
 */
public class AscendingComparator implements RecordComparator {

	/**
	 * The compare() implementation, ascending based on the timestamp
	 */
	public int compare(byte[] entry1, byte[] entry2) {

		// Sort based on the timestamp which is the first long in the
		// data/stream
		long timestamp1 = ((long) (entry1[0] & 0xFF)) << 56
				| ((long) (entry1[1] & 0xFF)) << 48
				| ((long) (entry1[2] & 0xFF)) << 40
				| ((long) (entry1[3] & 0xFF)) << 32
				| ((long) (entry1[4] & 0xFF)) << 24
				| ((long) (entry1[5] & 0xFF)) << 16
				| ((long) (entry1[6] & 0xFF)) << 8 | (long) (entry1[7] & 0xFF);
		long timestamp2 = ((long) (entry2[0] & 0xFF)) << 56
				| ((long) (entry2[1] & 0xFF)) << 48
				| ((long) (entry2[2] & 0xFF)) << 40
				| ((long) (entry2[3] & 0xFF)) << 32
				| ((long) (entry2[4] & 0xFF)) << 24
				| ((long) (entry2[5] & 0xFF)) << 16
				| ((long) (entry2[6] & 0xFF)) << 8 | (long) (entry2[7] & 0xFF);

		if (timestamp1 < timestamp2) {
			return RecordComparator.PRECEDES;
		} else if (timestamp1 > timestamp2) {
			return RecordComparator.FOLLOWS;
		} else {
			return RecordComparator.EQUIVALENT;
		}
	}

}

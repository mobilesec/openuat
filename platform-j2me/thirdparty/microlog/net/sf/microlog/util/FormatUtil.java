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
package net.sf.microlog.util;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * A utility class for Microlog.
 * @author Darius Katz
 */
public class FormatUtil {

	/**
	 * Creates a new instance of FormatUtil
	 */
	public FormatUtil() {
	}

	/**
	 * Convert a <code>long</code> timestamp to a string
	 * 
	 * @param timestamp
	 *            the timestamp.
	 * @return String the formatted timestamp as a string.
	 */
	public static final String toTimestampString(long timestamp) {
		StringBuffer ts = new StringBuffer(32);
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		calendar.setTime(new Date(timestamp));

		// year
		int temp = calendar.get(Calendar.YEAR);
		ts.append(temp);

		// month
		temp = calendar.get(Calendar.MONTH) + 1;
		if (temp < 10) {
			ts.append("-0");
		} else {
			ts.append('-');
		}
		ts.append(temp);

		// day
		temp = calendar.get(Calendar.DAY_OF_MONTH);
		if (temp < 10) {
			ts.append("-0");
		} else {
			ts.append('-');
		}
		ts.append(temp);

		// hour
		temp = calendar.get(Calendar.HOUR_OF_DAY);
		if (temp < 10) {
			ts.append(" 0");
		} else {
			ts.append(' ');
		}
		ts.append(temp);

		// minute
		temp = calendar.get(Calendar.MINUTE);
		if (temp < 10) {
			ts.append(":0");
		} else {
			ts.append(':');
		}
		ts.append(temp);

		// second
		temp = calendar.get(Calendar.SECOND);
		if (temp < 10) {
			ts.append(":0");
		} else {
			ts.append(':');
		}
		ts.append(temp);

		// millisecond
		temp = calendar.get(Calendar.MILLISECOND);
		ts.append('.');
		ts.append(temp);

		return ts.toString();
	}

}

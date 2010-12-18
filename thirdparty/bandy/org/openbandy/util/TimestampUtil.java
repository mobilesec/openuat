/**
 *  Filename: TimestampUtil.java (in org.openbandy.util)
 *  This file is part of the OpenBandy project.
 * 
 *  OpenBandy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  OpenBandy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with OpenBandy. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * (c) Copyright Philipp Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 *  www.openbandy.org
 */

package org.openbandy.util;

import java.util.Calendar;
import java.util.Date;


/**
 * This class provides static helper methods to be used with timestamps and
 * dates respectively and methods for the conversion of such.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.0
 */
public class TimestampUtil {

	/**
	 * Compare two timestamps (given in milliseconds) to the accuracy of one
	 * second.
	 * 
	 * @param t1
	 *            The Time in milliseconds since midnight, January 1, 1970 UTC
	 * @param t2
	 *            The Time to compare with
	 * @return true if t1 and t2 are equal to the second
	 */
	public static boolean areEqualToTheSecond(long t1, long t2) {
		t1 = (t1 / 1000) * 1000;
		t2 = (t2 / 1000) * 1000;
		return (t1 == t2);
	}

	/**
	 * Returns a string representing the actual date and time according to the
	 * format 'yyyyMMdd HH:mm:ss'
	 * 
	 * @return Formated string
	 */
	public static String getActualFormatedDate() {
		Calendar calendar = Calendar.getInstance();
		return format(calendar);
	}

	/**
	 * Returns a string representing the actual date and time according to the
	 * format 'yyyyMMdd HH:mm:ss'
	 * 
	 * @param timeMillis
	 *            The time in milliseconds since midnight, January 1, 1970 UTC
	 * @return Formated string
	 */
	public static String getFormatedDate(long timeMillis) {
		Calendar calendar = Calendar.getInstance();
		Date date = new Date(timeMillis);
		calendar.setTime(date);
		return format(calendar);
	}

	/**
	 * Returns a timestamp in milliseconds since midnight, January 1, 1970 UTC
	 * corresponding to the string parameter which is formated according to
	 * NMEA-0183.
	 * 
	 * @param nmea
	 *            Date formated according to NMEA-0183 (e.g.
	 *            '10:22:33/22.11.06')
	 * @return Converted timestamp in milliseconds
	 */
	public static long getTimestampFromNMEAFormatedString(String nmea) {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(nmea.substring(0, 2)));
		calendar.set(Calendar.MINUTE, Integer.parseInt(nmea.substring(3, 5)));
		calendar.set(Calendar.SECOND, Integer.parseInt(nmea.substring(6, 8)));
		calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(nmea.substring(9, 11)));
		calendar.set(Calendar.MONTH, Integer.parseInt(nmea.substring(12, 14)));
		int year = Integer.parseInt(nmea.substring(15, 17));
		if (year < 2000) {
			year = year + 2000;
		}
		calendar.set(Calendar.YEAR, year);
		return calendar.getTime().getTime();
	}

	/* ******************** Helper Methods ******************** */

	private static String format(Calendar calendar) {
		String date = Integer.toString(calendar.get(Calendar.YEAR));
		date = date + doubleDigit(calendar.get(Calendar.MONTH));
		date = date + doubleDigit(calendar.get(Calendar.DATE));
		date = date + " ";
		date = date + doubleDigit(calendar.get(Calendar.HOUR_OF_DAY));
		date = date + ":";
		date = date + doubleDigit(calendar.get(Calendar.MINUTE));
		date = date + ":";
		date = date + doubleDigit(calendar.get(Calendar.SECOND));
		return date;
	}

	private static String doubleDigit(int in) {
		String inString = Integer.toString(in);
		if (inString.length() == 1) {
			return "0" + inString;
		}
		return inString;
	}

}

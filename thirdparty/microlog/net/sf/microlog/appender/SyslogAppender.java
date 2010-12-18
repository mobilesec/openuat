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

package net.sf.microlog.appender;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import net.sf.microlog.Level;

/**
 * This <code>Appender</code> is used for sending the log messages to a syslog
 * daemon. It is basically an UDP Datagram that is sent on port 514. The format
 * of the mssage is described in rfc 3164. Each time a message is logged, the
 * message is sent directly to the server. A <code>DatagramConnection</code>
 * is used, which requires MIDP 2.0.
 * 
 * This has been tested with the Kiwi syslog daemon for Windows (freeware
 * edition). For more information: http://www.kiwisyslog.com/ Note: the Kiwi
 * syslog daemon does not parse the header field by default (v 8.3.30). The
 * <code>SyslogAppender</code> has the possibility to switch the header
 * generation on/off. This is done with the <code>setHeader()</code> method.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 * @since 0.6
 * 
 */
public class SyslogAppender extends DatagramAppender {

	/**
	 * The default port for the syslog protocol.
	 */
	public final static int DEFAULT_SYSLOG_PORT = 514;

	public final static byte FACILITY_KERNAL_MESSAGE = 0;

	public final static byte FACILITY_USER_LEVEL_MESSAGE = 1;

	public final static byte FACILITY_MAIL_SYSTEM = 2;

	public final static byte FACILITY_SYSTEM_DAEMONS = 3;

	public final static byte FACILITY_SECURITY_MESSAGE = 4;

	public final static byte FACILITY_LOG_AUDIT = 13;

	public final static byte FACILITY_LOG_ALERT = 14;

	public final static byte FACILITY_LOCAL_USE_0 = 16;

	public final static byte FACILITY_LOCAL_USE_1 = 17;

	public final static byte FACILITY_LOCAL_USE_2 = 18;

	public final static byte FACILITY_LOCAL_USE_3 = 19;

	public final static byte FACILITY_LOCAL_USE_4 = 20;

	public final static byte FACILITY_LOCAL_USE_5 = 21;

	public final static byte FACILITY_LOCAL_USE_6 = 22;

	public final static byte FACILITY_LOCAL_USE_7 = 23;

	public final static byte SEVERITY_EMERGENCY = 0;

	public final static byte SEVERITY_ALERT = 1;

	public final static byte SEVERITY_CRITICAL = 2;

	public final static byte SEVERITY_ERROR = 3;

	public final static byte SEVERITY_WARNING = 4;

	public final static byte SEVERITY_NOTICE = 5;

	public final static byte SEVERITY_INFORMATIONAL = 6;

	public final static byte SEVERITY_DEBUG = 7;

	public final static String DEFAULT_HOSTNAME = "127.0.0.1";

	private final static String[] MONTHS = { "Jan", "Feb", "Mar", "Apr", "May",
			"Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };

	private byte facility = FACILITY_USER_LEVEL_MESSAGE;

	private byte severity = SEVERITY_DEBUG;

	private boolean header;

	private final StringBuffer messageStringBuffer;

	private final Calendar calendar = Calendar.getInstance(TimeZone
			.getTimeZone("GMT"));

	private String hostname = DEFAULT_HOSTNAME;

	private String tag = "microlog";

	/**
	 * Create a <code>SyslogAppender</code> with the default port for syslog
	 * (514).
	 */
	public SyslogAppender() {
		super.setPort(DEFAULT_SYSLOG_PORT);
		super.setDatagramSize(512);
		messageStringBuffer = new StringBuffer(128);

		String hostNameProperty = System.getProperty("microedition.hostname");
		if (hostNameProperty != null && hostNameProperty.length() > 0) {
			hostname = hostNameProperty;
		}
	}

	/**
	 * Do the logging.
	 * @param level
	 *            the level to use for the logging.
	 * @param message
	 *            the message to log.
	 * @param t
	 *            the exception to log.
	 */
	public void doLog(String name, long time, Level level, Object message, Throwable t) {
		if (logOpen && formatter != null) {
			messageStringBuffer.delete(0, messageStringBuffer.length());

			// Create the PRI part
			messageStringBuffer.append('<');
			int priority = facility * 8 + severity;
			messageStringBuffer.append(priority);
			messageStringBuffer.append('>');

			// Create the HEADER part.
			if (header) {
				// Add the TIMESTAMP field of the HEADER
				// Time format is "Mmm dd hh:mm:ss". For more info see rfc3164.
				long currentTime = System.currentTimeMillis();
				calendar.setTime(new Date(currentTime));

				messageStringBuffer
						.append(MONTHS[calendar.get(Calendar.MONTH)]);
				messageStringBuffer.append(' ');

				int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
				if (dayOfMonth < 10) {
					messageStringBuffer.append('0');
				}
				messageStringBuffer.append(dayOfMonth);
				messageStringBuffer.append(' ');

				int hour = calendar.get(Calendar.HOUR_OF_DAY);
				if (hour < 10) {
					messageStringBuffer.append('0');
				}
				messageStringBuffer.append(hour);
				messageStringBuffer.append(':');

				int minute = calendar.get(Calendar.MINUTE);
				if (minute < 10) {
					messageStringBuffer.append('0');
				}
				messageStringBuffer.append(minute);
				messageStringBuffer.append(':');

				int second = calendar.get(Calendar.SECOND);
				if (second < 10) {
					messageStringBuffer.append('0');
				}
				messageStringBuffer.append(second);
				messageStringBuffer.append(' ');

				// Add the HOSTNAME part of the message
				messageStringBuffer.append(hostname);
			}

			// Create the MSG part.
			messageStringBuffer.append(' ');
			messageStringBuffer.append(tag);
			messageStringBuffer.append(": ");
			messageStringBuffer.append(formatter
					.format(name, time, level, message, t));

			sendMessage(messageStringBuffer.toString());
		}
	}

	/**
	 * Get the facility that is used when sending message.
	 * 
	 * @return the facility
	 */
	public byte getFacility() {
		return facility;
	}

	/**
	 * Set the facility that is used when sending message.
	 * 
	 * @param facility
	 *            the facility to set
	 * @throws IllegalArgumentException
	 *             if the facility is not a valid one.
	 */
	public void setFacility(byte facility) {
		if (facility < FACILITY_KERNAL_MESSAGE
				|| facility > FACILITY_LOCAL_USE_7) {
			throw new IllegalArgumentException("Not a valid facility.");
		}

		this.facility = facility;
	}

	/**
	 * Get the severity at which the message shall be sent.
	 * 
	 * @return the severity
	 */
	public byte getSeverity() {
		return severity;
	}

	/**
	 * Get the severity at which the message shall be sent.
	 * 
	 * @param severity
	 *            the severity to set
	 * @throws IllegalArgumentException
	 *             if the severity is not a valid severity.
	 */
	public void setSeverity(byte severity) throws IllegalArgumentException {
		if (severity < SEVERITY_EMERGENCY || severity > SEVERITY_DEBUG) {
			throw new IllegalArgumentException("Not a valid severity.");
		}

		this.severity = severity;
	}

	/**
	 * Indicates whether the HEADER part of the message. If this is true, the
	 * HEADER part is created.
	 * 
	 * @param header
	 *            the addHeader to set
	 */
	public void setHeader(boolean header) {
		this.header = header;
	}

	/**
	 * Set the hostname to use for the HOSTNAME field of the syslog message.
	 * 
	 * @param hostname
	 *            the hostname to set
	 * @throws IllegalArgumentException
	 *             if the <code>hostname</code> is <code>null</code> or the
	 *             length is less than 1
	 */
	public void setHostname(String hostname) throws IllegalArgumentException {
		if (hostname == null || (hostname != null && hostname.length() < 1)) {
			throw new IllegalArgumentException("The hostname must not be null.");
		}

		this.hostname = hostname;

	}

	/**
	 * Set the tag that is used for the TAG field in the MSG part of the
	 * message. The TAG length must not exceed 32 chars.
	 * 
	 * @param tag
	 *            the tag to set
	 * @throws IllegalArgumentException
	 *             if the tag is null or the length is incorrect.
	 */
	public void setTag(String tag) throws IllegalArgumentException {
		if (tag == null
				|| (tag != null && (tag.length() < 1 || tag.length() > 32))) {
			throw new IllegalArgumentException(
					"The tag must not be null, the length between 1..32");
		}

		this.tag = tag;

	}

}

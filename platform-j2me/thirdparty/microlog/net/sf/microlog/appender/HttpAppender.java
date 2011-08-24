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

import java.io.IOException;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

import net.sf.microlog.Appender;
import net.sf.microlog.Level;
import net.sf.microlog.util.PropertiesGetter;

/**
 * This class uses the HTTP protocol to post the log messages to a server.
 * 
 * @author Johan Karlsson (johan.karlsson@jayway.se)
 */
public class HttpAppender extends AbstractAppender {

	private String postURL;

	/**
	 * Set the URL that is used for posting the messages to the server.
	 * 
	 * @param postURL
	 *            the postURL to set
	 * @throws IllegalArgumentException
	 *             if the <code>postURl</code> is <code>null</code>.
	 */
	public void setPostURL(String postURL) throws IllegalArgumentException {
		if (postURL == null) {
			throw new IllegalArgumentException("The postURL must not be null.");
		}

		this.postURL = postURL;
	}

	/**
	 * Clear the log, i.e. do nothing since this is not applicable.
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#clear()
	 */
	public void clear() {
		// Do nothing
	}

	/**
	 * Open the log.
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#open()
	 */
	public void open() throws IOException {
		if (postURL != null) {
			HttpConnection connection = (HttpConnection) Connector
					.open(postURL);
		}
	}

	/**
	 * Do the actual logging.
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#doLog(String,
	 *      long, net.sf.microlog.Level, java.lang.Object, java.lang.Throwable)
	 */
	public void doLog(String name, long time, Level level, Object message, Throwable t) {
		if (logOpen && formatter != null) {
			doPost(formatter.format(name, time, level, message, t));
		}
	}

	/**
	 * Post the specified <code>message</code> to the server.
	 * 
	 * @param message
	 *            the message to post.
	 */
	public void doPost(String message) {

	}

	/**
	 * Close the log.
	 * 
	 * @see net.sf.microlog.appender.AbstractAppender#close()
	 */
	public void close() throws IOException {
		// TODO Auto-generated method stub

	}

	/**
	 * Get the log size, which always is <code>Appender.SIZE_UNDEFINED</code>.
	 * 
	 * @see net.sf.microlog.Appender#getLogSize()
	 */
	public long getLogSize() {
		return Appender.SIZE_UNDEFINED;
	}

	/**
	 * Configure the <code>HttpAppender</code> with the supplied
	 * <code>PropertiesGetter</code>.
	 * 
	 * @see net.sf.microlog.Appender#configure(net.sf.microlog.util.PropertiesGetter)
	 */
	public void configure(PropertiesGetter properties) {
		// TODO Auto-generated method stub

	}
}

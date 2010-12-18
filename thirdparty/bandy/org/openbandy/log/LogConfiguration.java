/**
 *  Filename: LogConfiguration.java (in org.openbandy.log)
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

package org.openbandy.log;

import org.openbandy.pref.Preferences;


/**
 * The LogConfiguration contains presets, defaults and preference values used to
 * configure the behaviour of the Log.
 * 
 * It provides two standard sets of configuration: the default log screen config
 * and the persistent log viewer config that is suitable if the log is just used
 * to display persistenly stored log messages in a auxiliary midlet.
 * 
 * Presets the user is well aware of such as the log level or the log modes are
 * stored as preference.
 * 
 * <br>
 * <br>
 * (c) Copyright P. Bolliger 2007, ALL RIGHTS RESERVED.
 * 
 * @author Philipp Bolliger (philipp@bolliger.name)
 * @version 1.1
 */
public class LogConfiguration {

	/**
	 * The name of the log file that is sent to the remote device or stored in
	 * the local file system
	 */
	public static final String LOG_FILE_NAME = "BandyLog.txt";

	/** The current log level */
	private int logLevel = LogLevel.DEFAULT;

	/** Array index for log mode canvas */
	protected static final int MODE_CANVAS = 0;

	/** Array index for log mode persistent */
	protected static final int MODE_PERSISTENT = 1;

	/** Array index for log mode file */
	protected static final int MODE_FILE = 2;

	/** Array index for log mode remote */
	protected static final int MODE_REMOTE = 3;

	/** Indicator array for the different log modes. */
	protected boolean[] modes = new boolean[4];

	/** Array for the log modes' labels */
	protected static String[] modeLabels = new String[4];
	/* initialize mode labels */
	static {
		modeLabels[MODE_CANVAS] = "Canvas";
		modeLabels[MODE_PERSISTENT] = "Persistent";
		modeLabels[MODE_FILE] = "File";
		modeLabels[MODE_REMOTE] = "Remote (not implemented yet)";
	}

	/**
	 * The current filter levels (only messages of this type are shown). Stored
	 * as boolean array (log level -> selected | not selected) A
	 * <code>true</code> value indicates that messages of the given level are
	 * shown (i.e. it will not be filtered). No filter is applied by default.
	 */
	protected boolean[] filterLevels = new boolean[LogLevel.NUMBER_OF_LEVELS];

	/** Name of the preference for log mode canvas */
	private final static String PREF_LOGMODE_CANVAS = "LogModeCanvas";

	/** Name of the preference for log mode persistent */
	private final static String PREF_LOGMODE_PERSISTENT = "LogModePersistent";

	/** Name of the preference for log mode file */
	private final static String PREF_LOGMODE_FILE = "LogModeFile";

	/** Name of the preference for log mode remote */
	private final static String PREF_LOGMODE_REMOTE = "LogModeRemote";

	/** Name of the preference used to persistently store the log level */
	private final static String PREF_LOGLEVEL = "LogLevel";

	/**
	 * Base name of the preference used to persistently store the filters,
	 * completed by the log level number
	 */
	private final static String PREF_LOGFILTER = "LogFilter";

	/** Indicates whether the log canvas is shown in full screen mode */
	protected boolean fullScreen = false;

	/** Indicates whether the scroll bars are displayed */
	protected boolean showScrollBars = false;

	/**
	 * Indicates whether back command is displayed. Note that either the back or
	 * the exit command must be present, thus if
	 * <code>showCommandSelectLogLevel</code> is false, the exit command will
	 * be shown.
	 */
	protected boolean showCommandBack = false;

	/** Indicates whether log level select command is displayed. */
	protected boolean showCommandSelectLogLevel = false;

	/** Indicates whether filter select command is displayed. */
	protected boolean showCommandSelectFilter = false;

	/**
	 * Indicates whether command to clear all entries in the shown on canvas is
	 * displayed.
	 */
	protected boolean showCommandClearCanvas = false;

	/**
	 * Indicates whether command to clear all persistently stored log entries is
	 * displayed.
	 */
	protected boolean showCommandClearLogStorage = false;

	/** Indicates whether command to select the modes of logging is displayed. */
	protected boolean showCommandSelectMode = false;

	/** Indicates whether the command to send the log via OBEX push is displayed. */
	protected boolean showCommandSendLog = false;

	/** Indicates whether the command to enable/disable auto scroll is displayed. */
	protected boolean showCommandAutoScroll = false;

	/** Indicates whether the command to enable/disable beeping is displayed. */
	protected boolean showCommandBeep = false;

	/** Indicates whether the log level status box is displayed. */
	protected boolean showStatusBox = true;

	/** Indicates whether the log is set to be run as viewer */
	protected boolean runningAsViewer = false;

	/**
	 * A default maximum number of log entries to keep
	 */
	private static final int MAX_ENTRIES = 200;

	/**
	 * The current maximum number of log entries that are displayed on the log
	 * canvas. If more entries are added, the oldest entry is deleted an the new
	 * one added.
	 */
	protected int maxCanvasLogEntries = MAX_ENTRIES;

	/**
	 * Returns a configuration of the log suitable for use as an application
	 * screen within a MIdlet.
	 * 
	 * @param loadPreferences
	 *            If true, preference values are loaded if available
	 * @return The LogConfiguration
	 */
	public static LogConfiguration getLogAsAppScreenConfig(boolean loadPreferences) {
		LogConfiguration config = new LogConfiguration();

		config.fullScreen = false;
		config.showCommandSelectLogLevel = true;
		config.showScrollBars = true;
		config.showCommandBack = true;
		config.showCommandSelectFilter = true;
		config.showCommandClearCanvas = true;
		config.showCommandClearLogStorage = false;
		config.showCommandSelectMode = true;
		config.showCommandSendLog = true;
		config.showCommandAutoScroll = true;
		config.showCommandBeep = true;
		config.showStatusBox = true;

		/* Enable logging to canvas */
		config.modes[MODE_CANVAS] = true;

		/* Disable logging to log storage (RMS) */
		config.modes[MODE_PERSISTENT] = false;

		/* Disable logging to file */
		config.modes[MODE_FILE] = false;

		/* Disable logging to remote machine */
		config.modes[MODE_REMOTE] = false;

		/* By default, no filter is applied */
		for (int i = 0; i < config.filterLevels.length; i++) {
			config.filterLevels[i] = true;
		}

		/* try to assign preference values */
		readPreferences(config);

		return config;
	}

	/**
	 * Returns a configuration suitable when using the log canvas as single
	 * screen to show persistently stored log messages. This configuration turns
	 * off all logging.
	 * 
	 * @param loadPreferences
	 *            If true, preference values are loaded if available
	 * @return The LogConfiguration
	 */
	public static LogConfiguration getPersistentLogViewerConfig(boolean loadPreferences) {
		LogConfiguration config = new LogConfiguration();
		config.fullScreen = false;
		config.showCommandSelectLogLevel = false;
		config.showScrollBars = true;
		config.showCommandBack = false;
		config.showCommandSelectFilter = true;
		config.showCommandClearCanvas = false;
		config.showCommandClearLogStorage = true;
		config.showCommandSelectMode = false;
		config.showCommandSendLog = true;
		config.showCommandAutoScroll = false;
		config.showCommandBeep = false;
		config.showStatusBox = true;

		config.runningAsViewer = true;

		/* Disable all logging */
		config.modes[MODE_CANVAS] = false;
		config.modes[MODE_PERSISTENT] = false;
		config.modes[MODE_FILE] = false;
		config.modes[MODE_REMOTE] = false;

		/* By default, no filter is applied */
		for (int i = 0; i < config.filterLevels.length; i++) {
			config.filterLevels[i] = true;
		}

		return config;
	}

	/* ************** Getter and Setter ************** */

	/**
	 * Set the log level to <code>logLevel</code> and store it as a
	 * preference.
	 * 
	 * @see LogLevel
	 * @param logLevel
	 *            log level to set
	 */
	public void setLogLevel(int logLevel) throws Exception {
		Preferences.setIntValue(PREF_LOGLEVEL, logLevel);
		this.logLevel = logLevel;
	}

	/**
	 * Get log level currently set.
	 * 
	 * @return The currently set log level
	 */
	public int getLogLevel() {
		return this.logLevel;
	}

	/**
	 * Enable and disable respectively the modes[mode] and store it as a
	 * preference.
	 * 
	 * @param mode
	 *            Mode indicator (MODE_CANVAS | MODE_PERSISTENT | MODE_FILE |
	 *            MODE_REMOTE)
	 * @param enabled
	 *            Enabled if true, disabled else
	 */
	public void setLogMode(int mode, boolean enabled) throws Exception {
		switch (mode) {
		case MODE_CANVAS:
			Preferences.setBooleanValue(PREF_LOGMODE_CANVAS, enabled);
			break;
		case MODE_PERSISTENT:
			Preferences.setBooleanValue(PREF_LOGMODE_PERSISTENT, enabled);
			break;
		case MODE_FILE:
			Preferences.setBooleanValue(PREF_LOGMODE_FILE, enabled);
			break;
		case MODE_REMOTE:
			Preferences.setBooleanValue(PREF_LOGMODE_REMOTE, enabled);
			break;
		}
		this.modes[mode] = enabled;
	}

	/**
	 * Enable and disable respectively the filter for a given log level and
	 * store it as a preference.
	 * 
	 * @param level
	 *            Log level to which the filter applies
	 * @param enabled
	 *            Enabled if true, disabled else
	 */
	public void setFilterLevel(int level, boolean enabled) throws Exception {
		Preferences.setBooleanValue(PREF_LOGFILTER + level, enabled);
		this.filterLevels[level] = enabled;
	}

	/**
	 * Set the maximum number of entries that are displayed on the log canvas.
	 * 
	 * @param maxLogEntries
	 *            Maximum entries on log canvas
	 */
	public void setMaxCanvasLogEntries(int maxLogEntries) {
		this.maxCanvasLogEntries = maxLogEntries;
	}

	/**
	 * Checks whether the log messages should be sent to the canvas.
	 * 
	 * @return True if canvas mode is enabled
	 */
	public boolean logModeCanvasEnabled() {
		return this.modes[MODE_CANVAS];
	}

	/**
	 * Checks whether the log messages should be sent to the RMS.
	 * 
	 * @return True if persistent mode is enabled
	 */
	public boolean logModePersistentEnabled() {
		return this.modes[MODE_PERSISTENT];
	}

	/**
	 * Checks whether the log messages should be sent to the file
	 * <code>LOG_FILE_NAME</code>.
	 * 
	 * @return True if file mode is enabled
	 */
	public boolean logModeFileEnabled() {
		//return this.modes[MODE_FILE];
		return true;
	}

	/**
	 * Checks whether the log messages should be sent to a remote machine.
	 * 
	 * @return True if remote mode is enabled
	 */
	public boolean logModeRemoteEnabled() {
		return this.modes[MODE_REMOTE];
	}

	/* *************** Helper Methods **************** */

	/**
	 * best effort, ignore all exceptions
	 * 
	 * must catch exception seperatly for every trial
	 */
	private static void readPreferences(LogConfiguration config) {
		/* read log level pref */
		try {
			config.logLevel = Preferences.getIntValue(PREF_LOGLEVEL);
		}
		catch (Exception e) {
		}

		/* read log mode canvas pref */
		try {
			config.modes[MODE_CANVAS] = Preferences.getBooleanValue(PREF_LOGMODE_CANVAS);
		}
		catch (Exception e) {
		}

		/* read log mode persistent pref */
		try {
			config.modes[MODE_PERSISTENT] = Preferences.getBooleanValue(PREF_LOGMODE_PERSISTENT);
		}
		catch (Exception e) {
		}

		/* read log mode file pref */
		try {
			config.modes[MODE_FILE] = Preferences.getBooleanValue(PREF_LOGMODE_FILE);
		}
		catch (Exception e) {
		}

		/* read log mode remote pref */
		try {
			config.modes[MODE_REMOTE] = Preferences.getBooleanValue(PREF_LOGMODE_REMOTE);
		}
		catch (Exception e) {
		}

		/* read log filter prefs */
		for (int i = 0; i < config.filterLevels.length; i++) {
			try {
				config.filterLevels[i] = Preferences.getBooleanValue(PREF_LOGFILTER + i);
			}
			catch (Exception e) {
			}
		}

	}

}

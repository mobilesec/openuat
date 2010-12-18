package net.sf.microlog.ui;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;

/**
 * A predefined Form that is used for logging via the FormAppender.
 * 
 * @author Johan Karlsson
 */
public class LogForm extends Form {

	private Command clearLogCommand = new ClearLogCommand();

	private Command previuosScreenCommand = new PreviousScreenCommand();

	private Display display;

	private Displayable previousScreen;

	/**
	 * Create a LogForm.
	 */
	public LogForm() {
		super("MicroLog");
		this.addCommand(clearLogCommand);
		this.addCommand(previuosScreenCommand);
		this.setCommandListener(new CommandHandler());
	}

	/**
	 * Create a LogForm with the specified title.
	 * 
	 * @param title
	 *            the title.
	 */
	public LogForm(String title) {
		super(title);
		this.addCommand(clearLogCommand);
		this.addCommand(previuosScreenCommand);
		this.setCommandListener(new CommandHandler());
	}

	/**
	 * The display to set.
	 * 
	 * @param display
	 *            The display to set.
	 */
	public final void setDisplay(Display display) {
		this.display = display;
	}

	/**
	 * The previous screen that shall be used when selecting the "Back" command.
	 * 
	 * @param previousScreen
	 *            The previousScreen to set.
	 */
	public final void setPreviousScreen(Displayable previousScreen) {
		this.previousScreen = previousScreen;
	}

	/**
	 * A class that listen for our Commands.
	 * 
	 * @author Johan Karlsson
	 */
	private class CommandHandler implements CommandListener {

		/**
		 * Implementation of the CommandListener interface.
		 * 
		 * @param cmd
		 *            the command.
		 * @param displayable
		 *            the displayable that generated the command action.
		 */
		public void commandAction(Command cmd, Displayable displayable) {
			AbstractCommand myCommand = (AbstractCommand) cmd;
			myCommand.execute();
		}
	}

	/**
	 * The super class for all the Command objects.
	 * 
	 * @author Johan Karlsson
	 */
	protected abstract class AbstractCommand extends Command {

		/**
		 * Create an AbstractCommand.
		 * 
		 * @param label
		 *            the label to use.
		 * @param commandType
		 *            the type of <code>Command</code>.
		 * @param priority
		 *            the priority of the <code>Command</code>.
		 */
		public AbstractCommand(String label, int commandType, int priority) {
			super(label, commandType, priority);
		}

		/**
		 * Create an AbstractCommand.
		 * 
		 * @param shortLabel
		 *            the short label to use.
		 * @param longLabel
		 *            the long label to use.
		 * @param commandType
		 *            the type of <code>Command</code>.
		 * @param priority
		 *            the priority of the <code>Command</code>.
		 */
		public AbstractCommand(String shortLabel, String longLabel,
				int commandType, int priority) {
			super(shortLabel, longLabel, commandType, priority);
		}

		/**
		 * Execute the command.
		 */
		public abstract void execute();
	}

	/**
	 * A command that clears the log.
	 * 
	 * @author Johan Karlsson
	 */
	protected class ClearLogCommand extends AbstractCommand {

		/**
		 * Create a ClearLogCommand.
		 */
		public ClearLogCommand() {
			super("Clear Log", Command.SCREEN, 2);
		}

		/**
		 * Execute the command.
		 * 
		 * @see net.sf.microlog.ui.RecordStoreLogViewer.AbstractCommand#execute()
		 */
		public void execute() {
			deleteAll();
		}

	}

	/**
	 * @author Johan Karlsson
	 */
	protected class PreviousScreenCommand extends AbstractCommand {

		/**
		 * Create a PreviousScreenCommand.
		 */
		public PreviousScreenCommand() {
			super("Back", Command.BACK, 1);
		}

		/**
		 * Execute the Command.
		 */
		public void execute() {
			if (display != null && previousScreen != null) {
				display.setCurrent(previousScreen);
			} else if (display != null && previousScreen == null) {
				Alert alert = new Alert("No previous screen is set.");
				display.setCurrent(alert, display.getCurrent());
			}
		}
	}

}

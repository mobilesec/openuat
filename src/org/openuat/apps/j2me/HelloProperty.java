package org.openuat.apps.j2me;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.midlet.MIDlet;

public class HelloProperty extends MIDlet implements CommandListener {
     private Command exitCommand;
     private Form form;

     public HelloProperty() {
         exitCommand = new Command("Exit", Command.EXIT, 1);

         form = new Form("Hello World: My Properties");
         form.addCommand(exitCommand);
         form.setCommandListener(this);
         form.append("Current platform: "
                   + System.getProperty( "microedition.platform") + "\n");
         form.append(
                   "Used encoding: " + System.getProperty("microedition.encoding")
                   + "\n");
         form.append("J2ME configuration: "
                   + System.getProperty( "microedition.configuration") + "\n");
         form.append("Supported profiles: "
                   + System.getProperty( "microedition.profiles")+ "\n");
     }

     protected void startApp() {
         Display.getDisplay(this).setCurrent(form);
     }

     protected void pauseApp() {}

     protected void destroyApp(boolean bool) {}

     public void commandAction(Command cmd, Displayable disp) {
         if (cmd == exitCommand) {
             destroyApp(false);
             notifyDestroyed();
         }
     }
}

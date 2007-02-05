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
         form.append("Bluetooth API version: "
                 + System.getProperty( "bluetooth.api.version")+ "\n");
         form.append("Bluetooth receiveMTU max: "
                 + System.getProperty( "bluetooth.l2cap.reveiveMTU.max")+ "\n");
         form.append("Bluetooth max connected dev: "
                 + System.getProperty( "bluetooth.connected.devices.max")+ "\n");
         form.append("Bluetooth inquiry during connection: "
                 + System.getProperty( "bluetooth.connected.inquiry")+ "\n");
         form.append("Bluetooth page during connection: "
                 + System.getProperty( "bluetooth.connected.page")+ "\n");
         form.append("Bluetooth inquiry scan during connection: "
                 + System.getProperty( "bluetooth.connected.inquiry.scan")+ "\n");
         form.append("Bluetooth page scan during connection: "
                 + System.getProperty( "bluetooth.connected.page.scan")+ "\n");
         form.append("Bluetooth master/slave switch: "
                 + System.getProperty( "bluetooth.master.switch")+ "\n");
         form.append("Bluetooth max concurrent service disc: "
                 + System.getProperty( "bluetooth.sd.trans.max")+ "\n");
         form.append("Bluetooth max service attr: "
                 + System.getProperty( "bluetooth.sd.attr.retrievable.max")+ "\n");
     }

     protected void startApp() {
         Display.getDisplay(this).setCurrent(form);
     }

     protected void pauseApp() {
    	 // don't need to implement
     }

     protected void destroyApp(boolean bool) {
    	 // don't need to implement
     }

     public void commandAction(Command cmd, Displayable disp) {
         if (cmd == exitCommand) {
             destroyApp(false);
             notifyDestroyed();
         }
     }
}

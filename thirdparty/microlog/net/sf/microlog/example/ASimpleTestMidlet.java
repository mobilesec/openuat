/*
 * ASimpleTestMidlet.java
 *
 * Created on den 20 oktober 2005, 15:49
 */

package net.sf.microlog.example;

import javax.microedition.midlet.*;

import net.sf.microlog.Logger;
import net.sf.microlog.util.GlobalProperties;
import net.sf.microlog.util.Properties;


/**
 * A simple MIDlet to test MicroLog
 *
 * @author  Darius Katz
 * @version
 */
public class ASimpleTestMidlet extends MIDlet {
    
    Properties properties;
    
    Logger log = Logger.getLogger();
    
    public void startApp() {
        
        //Test of Properties
        GlobalProperties.init(this);
        GlobalProperties props = GlobalProperties.getInstance();
                
        System.out.println("microlog.level = "+props.getString("microlog.level"));
        System.out.println("microlog.appender = "+props.getString("microlog.appender"));
        System.out.println("microlog.appender.RecordStoreAppender.maxLogEntries = " +
                props.getString("microlog.appender.RecordStoreAppender.maxLogEntries"));
        System.out.println("microlog.formatter = "+props.getString("microlog.formatter"));
        System.out.println("test.defaultvalue = "+props.getString("test.defaultvalue"));
        System.out.println("test.propertyfile = "+props.getString("test.propertyfile"));        
        System.out.println("test.app.property = "+props.getString("test.app.property"));        
        System.out.println("test.numapp.property = "+props.getString("test.numapp.property"));        

        //Test of Logger
        log.configure(props.getProperties());

        //End of tests
        
        properties = new Properties(this);
        
        log.info("startApp()");
        System.out.println("ASimpleTestMidlet...");

        log.debug("Debug line 1");
        log.debug("Debug line 2");
        log.debug("Debug line 3");
        log.debug("Debug line 4");
        log.debug("Debug line 5");
        log.debug("Debug line 6");
}
    
    public void pauseApp() {
        log.info("pauseApp()");
    }
    
    public void destroyApp(boolean unconditional) {
        log.info("destroyApp()");
        notifyDestroyed();
    }
}

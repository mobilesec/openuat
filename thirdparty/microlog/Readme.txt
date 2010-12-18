MicroLog
========

Introduction
------------

Features/benefits

* Compatible with Log4j. A Logger class similiar to the Logger class found in Log4j.
* Small
* Scaleable
* Many different Appenders:
  -ConsoleAppender
  -FileAppender (via FileConnection API , JSR-75)
  -FormAppender (MIDP 1.0 & 2.0)
  -RecordStoreAppender  (MIDP 1.0 & 2.0)
* Configuration via the application descriptor or a property file.
* Customizable formatting
* Quality Assurance with
  -Unit tests (J2MEUnit)
  -Code checks (CheckStyle)

Limitations
* Only one Logger instance, i.e. no hierachy of loggers.

Examples
--------
See the example MIDlets in the package net.sf.microlog.example

Installation
------------
Unzip the files into your source directory. Compile or build the class files.

FAQs
----
Q: Is it possible to read the output from ConsoleAppender on a mobile phone.
A: On some mobile phones, such as SonyEricsson P800/P9xx, it is possible to 
   install an application the redirects the output from System.out & 
   System.err. One such application is the Redirector. More info can be 
   found in the "Tips & Tricks" section.
   
Q: Why should I use the MicroLog package?
A: See the "Features/benefits" section.

Tips & Tricks
-------------
1. Redirecting output from the ConsoleAppender (i.e. System.out & System.err)
   This trick is for the SonyEricsson P800/P9xx mobile phones.
   
   a) Install the Redirector application.
   Download link: http://developer.sonyericsson.com/getDocument.do?docId=64984
   
   b) Start the redirector application.
   
   c) Set which output you should use. The console should be sufficient for 
      most applications.
      
   d) Start the application which you want to monitor.
   
   e) Switch back.
   

Todo & Open Issues
------------------

* BluetoothAppender
It would be nice to log via a bluetooth connection.
* Implement hierarchial loggers?

Contact
-------
Please use the forums for bug reports, feature requests or questions about
MicroLog.







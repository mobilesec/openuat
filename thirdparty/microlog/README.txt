Microlog
========

Introduction
------------

Features/benefits
* Similar to Log4j. The Logger class could be found here.
* Easy to setup. Configuration via the application descriptor, dependency injection or a property file.
* Small
* Fast
* Many different Appenders

  On-device appenders
  ===================
  -ConsoleAppender - Appends to the console, e.g. System.out.'
  -RecordStoreAppender - Appends to the RecordStore.
  -FileAppender  -  Appends to a file using a FileConnection.
  -CanvasAppender - Appends to a Canvas.
  -FormAppender - Appends to a Form.
  
  Off-device appenders
  ====================
  -BluetoothSerialAppender - Appends to a Bluetooth serial connection (btspp).
  -SerialAppender - Appends to a serial port (CommConnection).
  -SmsAppender - Appends to a cyclic buffer and send the buffer as an SMS.
  -MmsAppender - Appends to a cyclic buffer and send the buffer as an MMS and/or e-mail.
  -DatagramAppender - Appends to a datagram and send it using UDP.
  -SyslogAppender - Appends to syslog server.
  -SocketAppender - Appends to a socket connection (also SSL).
  -S3FileAppender - Appends to a file, as in the FileAppender, and stores the file on Amazon S3.
  -S3BufferAppender - Appends to a cyclic buffer and stores it as a file on Amazon S3.
  
* Customizable formatting with PatternFormatter.
* Quality Assurance with
  -Unit tests (CLDCUnit http://snapshot.pyx4me.com/pyx4me-cldcunit/)
  -Code checks (FindBugs & Lint4j)

Limitations
* Only one Logger instance, i.e. no hierarchy of loggers.

Examples
--------
See the example MIDlets in the package net.sf.microlog.example

Installation
------------

 a) Maven 2 must be installed: 
    Download link: http://maven.apache.org/download.html
 
 b) Sun WTK must be installed, set WTK_HOME, e.g. WTK_HOME=C:\WTK2.5.1
    Download Link: http://java.sun.com/products/sjwtoolkit/
 
 c) Package Microlog with "mvn clean install" on the command line
    See the resulting artifacts under the "target" directory

Note: If you are behind a firewall and must use a proxy for HTTP access 
you must configure Maven to use the proxy:

http://maven.apache.org/guides/mini/guide-proxies.html

FAQs
----
Q: Is it possible to read the output from ConsoleAppender on a mobile phone.
A: On some mobile phones, such as SonyEricsson P800/P9xx, it is possible to 
   install an application the redirects the output from System.out & 
   System.err. One such application is the Redirector. More info can be 
   found in the "Tips & Tricks" section.
   
Q: Why should I use the Microlog package?
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
   

Todos & Open Issues
------------------
* Implement hierarchical loggers?

Credits
-------

We would like to thank the following people for contributing with their code
Ricardo Amores Hern�ndez - CanvasAppender.
Marius de Beer - DatagramAppender formerly known as GPRSAppender.

Contact
-------
Please use the forums for bug reports, feature requests or questions about
Microlog: http://sourceforge.net/projects/microlog/

The Microlog team consists of:
Johan Karlsson  - project admin, developer & initiator
Darius Katz     - project admin, developer & graphics artist
Karsten Ohme    - developer & Maven specialist








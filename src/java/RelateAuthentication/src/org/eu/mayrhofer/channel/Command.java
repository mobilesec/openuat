/*****************************************************************************
 *  Project: Gibraltar-Webinterface
 *  Description: webinterface for the firewall gibraltar
 *  Filename: $RCSfile$
 *  Author: Andreas W�ckl
 *  Copyright: Andreas W�ckl 2001
 *  Project version tag: $Name$
 *  File version: $Revision: 2806 $
 *  Last changed at: $Date: 2004-11-04 12:48:15 +0100 (Don, 04 Nov 2004) $
 *  Last changed by: $Author: rleit $
 *****************************************************************************/

package org.eu.mayrhofer.channel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * The class <code>Command</code> holds a name and a macAdress of an
 * ethernet interface
 */
public class Command {

	/**
	 * executes a system Command and returns the output of the system command
	 *
	 * @param systemCommand the command to execute
	 * @param outputString	A String to set to the output of the process
	 *
	 * @return  the output of the command
	 */
	public static String executeCommand(String[] systemCommand, String outputString) throws ExitCodeException, IOException {
			Runtime r = Runtime.getRuntime();
			Process proc = r.exec(systemCommand);
			// if outputString is not null -> write!
			if (outputString!=null) {			
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
				out.write(outputString);
				out.close();
			}

			/**check whether the reading of the syscommand should be after the proc.waitFor()*/
			BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line = "";
			String temp;
			temp = in.readLine();
			while (temp!=null) {
				line += temp + "\n";
				temp = in.readLine();
			}
			in.close();

			BufferedReader err = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
			String errLine = "";
			temp = err.readLine();
			while (temp!=null) {
				errLine += temp + "\n";
				temp = err.readLine();
			}
			err.close();
			
			int result;
			try {
				result = proc.waitFor();
			} catch (InterruptedException ex) {
				throw new ExitCodeException("Interrupted");				
			}
			
			//ErrorLog.log("*************executed the command ");
			/*for (int i=0; i<systemCommand.length; i++) {
				//ErrorLog.log("param: " + systemCommand[i]);
			}*/
			//ErrorLog.log(" with exit value " + result);
			if (result!=0) {
				throw new ExitCodeException("Exited with non-zero exit code (" + result + "), output was: '" + line + "', error stream was: '" + errLine + "'");
			} else {
				return line;
			}
	}
}
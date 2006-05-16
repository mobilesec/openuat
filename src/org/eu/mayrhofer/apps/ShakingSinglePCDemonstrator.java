package org.eu.mayrhofer.apps;

import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;

public class ShakingSinglePCDemonstrator {

	private Shell sShell = null;  //  @jve:decl-index=0:visual-constraint="10,10"
	private Composite coherenceField = null;
	private Composite matchingField = null;
	private Label coherence = null;
	private Label matching = null;

	/**
	 * This method initializes composite	
	 *
	 */
	private void createComposite() {
		coherenceField = new Composite(sShell, SWT.NONE);
		coherenceField.setBackground(new Color(Display.getCurrent(), 227, 227, 255));
		coherenceField.setBounds(new org.eclipse.swt.graphics.Rectangle(18,18,370,446));
		coherence = new Label(coherenceField, SWT.NONE);
		coherence.setBounds(new org.eclipse.swt.graphics.Rectangle(17,15,334,36));
		coherence.setFont(new Font(Display.getDefault(), "Sans", 24, SWT.NORMAL));
		coherence.setText("0");
	}

	/**
	 * This method initializes composite1	
	 *
	 */
	private void createComposite1() {
		matchingField = new Composite(sShell, SWT.NONE);
		matchingField.setBackground(new Color(Display.getCurrent(), 227, 227, 255));
		matchingField.setBounds(new org.eclipse.swt.graphics.Rectangle(394,18,377,445));
		matching = new Label(matchingField, SWT.NONE);
		matching.setBounds(new org.eclipse.swt.graphics.Rectangle(15,15,323,36));
		matching.setFont(new Font(Display.getDefault(), "Sans", 24, SWT.NORMAL));
		matching.setText("0");
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		/* Before this is run, be sure to set up the launch configuration (Arguments->VM Arguments)
		 * for the correct SWT library path in order to run with the SWT dlls. 
		 * The dlls are located in the SWT plugin jar.  
		 * For example, on Windows the Eclipse SWT 3.1 plugin jar is:
		 *       installation_directory\plugins\org.eclipse.swt.win32_3.1.0.jar
		 */
		Display display = Display.getDefault();
		ShakingSinglePCDemonstrator thisClass = new ShakingSinglePCDemonstrator();
		thisClass.createSShell();
		thisClass.sShell.open();

		while (!thisClass.sShell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
	}

	/**
	 * This method initializes sShell
	 */
	private void createSShell() {
		sShell = new Shell();
		sShell.setText("Shell");
		createComposite();
		createComposite1();
		sShell.setSize(new org.eclipse.swt.graphics.Point(786,515));
	}

}

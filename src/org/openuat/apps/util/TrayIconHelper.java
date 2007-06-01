package org.openuat.apps.util;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.Timer;
import java.util.TimerTask;

public class TrayIconHelper extends TimerTask implements ActionListener {
	
	/*private static final int WIDTH = 16;
	private static final int HEIGHT = 16;*/
	
	private boolean isBlue;
	private TrayIcon trayIcon;
	private Dimension iconSize;
	private BufferedImage image;
	
	public TrayIconHelper() {
		isBlue = true;
		SystemTray systemTray = SystemTray.getSystemTray();
		iconSize = systemTray.getTrayIconSize();
		image = new BufferedImage(iconSize.width, iconSize.height, BufferedImage.TYPE_INT_RGB);
		trayIcon = new TrayIcon(image, "double-click to exit");
		trayIcon.addActionListener(this);
		run();
		try {
			systemTray.add(trayIcon);
		} catch (AWTException e) {
			e.printStackTrace();
		}
		Timer timer = new Timer();
		timer.schedule(this, 1000, 1000);
	}
	
	@Override
	public void run() {
		Graphics g = image.getGraphics();
		g.setColor(Color.white);
		g.fillRect(0, 0, iconSize.width, iconSize.height);
		g.setColor(isBlue ? Color.blue : Color.black);
		g.fillArc(0, 0, iconSize.width - 1, iconSize.height - 1, 0, 360);
		isBlue = ! isBlue;
		trayIcon.setImage(image);
	}

	public static void main(String[] args) {
		if (SystemTray.isSupported())
			new TrayIconHelper();
	}
	
	public void actionPerformed(ActionEvent e) {
		System.exit(0);
	}
}

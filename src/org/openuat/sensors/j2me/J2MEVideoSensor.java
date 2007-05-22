package org.openuat.sensors.j2me;

import java.io.IOException;
import javax.microedition.lcdui.*;
import javax.microedition.media.*;
import javax.microedition.media.control.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.media.control.VideoControl;

public class VideoMIDlet extends MIDlet implements CommandListener {
    
    private Display display;
    private Form form;
    private Command exit,back,capture,camera;
    private Player player;
    private VideoControl videoControl;
    private Video video;
    
    public VideoMIDlet() {
        
        exit = new Command("Exit", Command.EXIT, 0);
        camera = new Command("Camera", Command.SCREEN, 0);
        back = new Command("Back", Command.BACK, 0);
        capture = new Command("Capture", Command.SCREEN, 0);
        
        form = new Form("Capture Video");
        form.addCommand(camera);
        form.setCommandListener(this);
    }
    
    public void startApp() {
        display = Display.getDisplay(this);
        display.setCurrent(form);
    }
    
    public void pauseApp() {}
    
    public void destroyApp(boolean unconditional) {}
    
    public void commandAction(Command c, Displayable s) {
        if (c == exit) {
            destroyApp(true);
            notifyDestroyed();
        } else if (c == camera) {
            showCamera();
        } else if (c == back)
            display.setCurrent(form);
        else if (c == capture) {
            video = new Video(this);
            video.start();
        }
    }
    
    public void showCamera() {
        try {
            player = Manager.createPlayer("capture://video");
            player.realize();
            
            videoControl = (VideoControl)player.getControl("VideoControl");
            Canvas canvas = new VideoCanvas(this, videoControl);
            canvas.addCommand(back);
            canvas.addCommand(capture);
            canvas.setCommandListener(this);
            display.setCurrent(canvas);
            player.start();
        } catch (IOException ioe) {} catch (MediaException me) {}
    }
    
    class Video extends Thread {
        videoMIDlet midlet;
        public Video(VideoMIDlet midlet) {
            this.midlet = midlet;
        }
        
        public void run() {
            captureVideo();
            
        }
        
        public void captureVideo() {
            try {
                byte[] raw = videoControl.getSnapshot(null);
                Image image = Image.createImage(raw, 0, raw.length);
                form.append(image);
                display.setCurrent(form);
                
                player.close();
                player = null;
                videoControl = null;
            } catch (MediaException me) { }
        }
    };
}

import javax.microedition.lcdui.*;
import javax.microedition.media.MediaException;
import javax.microedition.media.control.VideoControl;

public class VideoCanvas extends Canvas {
    private VideoMIDlet midlet;
    
    public VideoCanvas(VideoMIDlet midlet, VideoControl videoControl) {
        int width = getWidth();
        int height = getHeight();
        this.midlet = midlet;
        
        videoControl.initDisplayMode(VideoControl.USE_DIRECT_VIDEO, this);
        try {
            videoControl.setDisplayLocation(2, 2);
            videoControl.setDisplaySize(width - 4, height - 4);
        } catch (MediaException me) {}
        videoControl.setVisible(true);
    }
    
    public void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        
        g.setColor(0x00ff00);
        g.drawRect(0, 0, width - 1, height - 1);
        g.drawRect(1, 1, width - 3, height - 3);
    }
    
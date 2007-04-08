package org.openuat.apps.j2me;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.util.*;

public class TimerDemo extends MIDlet {

    Display    display;
    StarField  field = new StarField();
    FieldMover mover = new FieldMover();
    Timer      timer = new Timer();

    public TimerDemo() {
        display = Display.getDisplay( this );
    }

    protected void destroyApp( boolean unconditional ) {
    }

    protected void startApp() {
        display.setCurrent( field );
        timer.schedule( mover, 100, 100 );
    }

    protected void pauseApp() {
    }

    public void exit(){
        timer.cancel(); // stop scrolling
        destroyApp( true );
        notifyDestroyed();
    }

    class FieldMover extends TimerTask {
        public void run(){
            field.scroll();
        }
    }

    class StarField extends Canvas {
        int        height;
        int        width;
        int[]      stars;
        Random     generator = new Random();
        boolean    painting = false;

        public StarField(){
            height      = getHeight();
            width       = getWidth();
            stars       = new int[ height ];

            for( int i = 0; i < height; ++i ){
                stars[i] = -1;
            }
        }

        public void scroll() {
            if( painting ) return;

            for( int i = height-1; i > 0; --i ){
                stars[i] = stars[i-1];
            }

            stars[0] = ( generator.nextInt() %
                                ( 3 * width ) ) / 2;
            if( stars[0] >= width ){
                stars[0] = -1;
            }

            repaint();
        }

        protected void paint(Graphics g ){
            painting = true;

            g.setColor( 0, 0, 0 );
            g.fillRect( 0, 0, width, height );

            g.setColor( 255, 255, 255 );

            for( int y = 0; y < height; ++y ){
                int x = stars[y];
                if( x == -1 ) continue;
                g.drawLine( x, y, x, y );
            }

            painting = false;
        }

        protected void keypressed( int keycode ){
            exit();
        }
    }
}

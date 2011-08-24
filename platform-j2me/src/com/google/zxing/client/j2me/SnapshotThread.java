/*
 * Copyright 2007 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.j2me;

import com.google.zxing.MonochromeBitmapSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;

import javax.microedition.lcdui.Image;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.control.VideoControl;

import org.openuat.channel.oob.j2me.J2MEVisualChannel;

/**
 * Thread which does the work of capturing a frame and decoding it.
 *
 * @author Sean Owen (srowen@google.com)
 */
final class SnapshotThread implements Runnable {

  private final J2MEVisualChannel zXingMIDlet;
  private final Object waitLock;
  private volatile boolean done;
  private final MultimediaManager multimediaManager;

  SnapshotThread(J2MEVisualChannel zXingMIDlet) {
    this.zXingMIDlet = zXingMIDlet;
    waitLock = new Object();
    done = false;
    multimediaManager = new DefaultMultimediaManager();
  }

  void continueRun() {
    synchronized (waitLock) {
      waitLock.notifyAll();
    }
  }

  private void waitForSignal() {
    synchronized (waitLock) {
      try {
        waitLock.wait();
      } catch (InterruptedException ie) {
        // continue
      }
    }
  }

  void stop() {
    done = true;
    continueRun();
  }

  public void run() {
	    Player player = zXingMIDlet.getPlayer();
	    Result result = null;
	    do {
	      waitForSignal();
	      
	      try {
	        multimediaManager.setFocus(player);
	        byte[] snapshot = takeSnapshot();
	        Image capturedImage = Image.createImage(snapshot, 0, snapshot.length);
	        MonochromeBitmapSource source = new LCDUIImageMonochromeBitmapSource(capturedImage);
	        Reader reader = new MultiFormatReader();
	        result = reader.decode(source);
	        if(result != null) {done = true; break;} 
	      } catch (ReaderException re) {
	        // Show a friendlier message on a mere failure to read the barcode
	        zXingMIDlet.showError("Sorry, no barcode was found.");
	      } catch (MediaException me) {
	        zXingMIDlet.showError(me);
	      } catch (RuntimeException re) {
	        zXingMIDlet.showError(re);
	      }
	      
	    } while (!done);
	    
	    if(result != null) zXingMIDlet.handleDecodedText(result);
	  }

  private byte[] takeSnapshot() throws MediaException {
    VideoControl videoControl = zXingMIDlet.getVideoControl();
    byte[] snapshot = null;
    try {
      snapshot = videoControl.getSnapshot(null);
    } catch (MediaException me) {
    }
    if (snapshot == null) {
      // Fall back on JPEG; seems that some cameras default to PNG even
      // when PNG isn't supported!
      snapshot = videoControl.getSnapshot("encoding=jpeg");
      if (snapshot == null) {
        throw new MediaException("Can't obtain a snapshot");
      }
    }
    return snapshot;
  }

}

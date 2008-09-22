package org.codec.ui;

import info.clearthought.layout.TableLayout;
import org.codec.ui.client.ClientCodecPanel;
import org.codec.ui.server.ServerCodecPanel;

import javax.swing.*;
import java.awt.*;

/**
 * Copyright (c) 2007, Regents of the University of California
 * All rights reserved.
 * ====================================================================
 * Licensed under the BSD License. Text as follows.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   - Neither the name of University of California,Irvine nor the names
 *     of its contributors may be used to endorse or promote products 
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 * 
 * @author claudio soriente (csorient at uci dot edu)
 * @version 1.0
 * 
 */



//HERE'S THE MAIN CLASS!!!
public class Hapadep {
    private JFrame frame;

    public Hapadep() {
    }

    private void setup() {
        frame = new JFrame("HAPADEP " + System.getProperty("user.dir"));
        frame.setSize(320, 200);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        Container container = frame.getContentPane();
        container.setLayout(new TableLayout(new double[][]{{-1},{-1}}));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Client", null, initClientCodec());
        tabbedPane.addTab("Server", null, initServerCodec());

        container.add(tabbedPane, "0,0");
        frame.setLocation(320,200);
    }

    private void start() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                frame.setVisible(true);
            }
        });
    }

    private Component initClientCodec() {
        return new ClientCodecPanel();
    }

    private Component initServerCodec() {
        return new ServerCodecPanel();
    }

    public static void main(String[] args) {
        Hapadep hapadep = new Hapadep();
        hapadep.setup();
        hapadep.start();
    }

}

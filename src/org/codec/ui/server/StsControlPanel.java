package org.codec.ui.server;

import info.clearthought.layout.TableLayout;
import org.codec.ServerCodec;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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

public class StsControlPanel extends JPanel implements ActionListener {
    private ServerCodec serverCodec;

    public StsControlPanel() {
        serverCodec = new ServerCodec(ServerCodec.STS);

        setLayout(new TableLayout(new double[][]{{-1}, {-1}}));

        JPanel mainPanel = new JPanel(new TableLayout(new double[][]{{5, -1, 5, -1, 5}, {5, -1, 5}}));

        JButton sts = new JButton("Sts");
        sts.setActionCommand("Sts");
        sts.addActionListener(this);

        JButton verification = new JButton("Verification");
        verification.setActionCommand("Verification");
        verification.addActionListener(this);

        mainPanel.add(sts, "1,1");
        mainPanel.add(verification, "3,1");

        add(mainPanel, "0,0");
    }

    public void actionPerformed(ActionEvent e) {
        System.out.println("SERVER");
        String actionCommand = e.getActionCommand();
        if ("Sts".equals(actionCommand)) {
            long start = 0;//System.currentTimeMillis();

            //receives the challenge and decodes in ch_server_file
            System.out.println("START RECORDING CH " + (System.currentTimeMillis() - start));
            serverCodec.setTime(120);
            serverCodec.receiveData();
            System.out.println("STOP RECORDING CH " + (System.currentTimeMillis() - start));

            try {
                Thread.currentThread().sleep(2000);
            } catch (InterruptedException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            serverCodec.verify();

            //encodes and plays sts_server_file
            System.out.println("START PLAYING CH" + (System.currentTimeMillis() - start));
            serverCodec.sendData();
            System.out.println("STOP PLAYING CH" + (System.currentTimeMillis() - start));
        } else if ("Verification".equals(actionCommand)) {
            //checks the transmission
            serverCodec.checkTransmission();
            serverCodec.playScore();
        } else if ("Yes".equals(actionCommand)) {
            serverCodec.accept();
            System.exit(0);
        } else if ("No".equals(actionCommand)) {
            serverCodec.refuse();
            System.exit(0);
        }
    }
}

package org.codec.ui.client;

import info.clearthought.layout.TableLayout;
import org.codec.ClientCodec;

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
    private ClientCodec clientCodec;

    public StsControlPanel() {
        this.clientCodec = new ClientCodec(ClientCodec.STS);

        setLayout(new TableLayout(new double[][]{{-1}, {-1, 5, 20}}));

        JPanel mainPanel = new JPanel(new TableLayout(new double[][]{{5, -1, 5, -1, 5}, {5, -1, 5}}));

        JButton sts = new JButton("Sts");
        sts.setActionCommand("Sts");
        sts.addActionListener(this);

        JButton verification = new JButton("Verification");
        verification.setActionCommand("Verification");
        verification.addActionListener(this);

        mainPanel.add(sts, "1,1");
        mainPanel.add(verification, "3,1");


        JPanel buttonPanel = new JPanel(new TableLayout(new double[][]{{-1, 60, 5, 60, 2}, {20}}));

        JButton yes = new JButton("Yes");
        yes.setActionCommand("Yes");
        yes.addActionListener(this);

        JButton no = new JButton("No");
        no.setActionCommand("No");
        no.addActionListener(this);

        buttonPanel.add(yes, "1,0");
        buttonPanel.add(no, "3,0");

        add(mainPanel, "0,0");
        add(buttonPanel, "0,2");
    }

    public void actionPerformed(ActionEvent e) {
        System.out.println("CLIENT");
        String actionCommand = e.getActionCommand();
        if ("Sts".equals(actionCommand)) {
            long start = 0;//System.currentTimeMillis();

            //sends the challenge
            System.out.println("START PLAYING CH" + (System.currentTimeMillis() - start));
            clientCodec.sendData();
            System.out.println("STOP PLAYING CH" + (System.currentTimeMillis() - start));

            //receives the public key and the signature of the challenge
            System.out.println("START RECORDING STS_CLIENT" + (System.currentTimeMillis() - start));
            clientCodec.setTime(1100);
            clientCodec.receiveData();
            System.out.println("STOP RECORDING STS_CLIENT" + (System.currentTimeMillis() - start));
        } else if ("Verification".equals(actionCommand)) {
            clientCodec.verify();
            //checks the transmission
            clientCodec.checkTransmission();
            clientCodec.playScore();
        } else if ("Yes".equals(actionCommand)) {
            clientCodec.accept();

            System.exit(0);
        } else if ("No".equals(actionCommand)) {
            clientCodec.refuse();
            
            System.exit(0);
        }
    }
}

package org.codec.ui.client;

import info.clearthought.layout.TableLayout;
import org.codec.ClientCodec;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
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

public class ClientCodecPanel extends JPanel implements ActionListener {
    private static final String[] PROTOCOLS = {"Unilateral",
                                               "Bilateral",
                                               "Sts"};

    private ClientCodec clientCodec;

    private JPanel protocolPanel;

    private Component unilateralControlPanel;
    private Component bilateralControlPanel;
    private Component stsControlPanel;

    public ClientCodecPanel() {
        initComponents();
    }

    private void initComponents() {
        initControlPanels();

        setLayout(new TableLayout(new double[][]{{-1}, {25, -1}}));
        add(protocolPanel = initProtocolPanel(), "0,1");
        add(initProtocolsPanel(), "0,0");
    }

    private void initControlPanels() {
        this.unilateralControlPanel = new UnilateralControlPanel();
        this.bilateralControlPanel = new BilateralControlPanel();
        this.stsControlPanel = new StsControlPanel();
    }

    private JPanel initProtocolsPanel() {
        JPanel protocolsPanel = new JPanel(new TableLayout(new double[][]{{TableLayout.PREFERRED, 5, -1}, {-1}}));

        JLabel protocolLabel = new JLabel("Protocol :");
        JComboBox protocols = new JComboBox(PROTOCOLS);
        protocols.setActionCommand("protocols");
        protocols.addActionListener(this);

        protocolsPanel.add(protocolLabel, "0,0");
        protocolsPanel.add(protocols, "2,0");

        protocols.setSelectedIndex(0);

        return protocolsPanel;
    }

    private JPanel initProtocolPanel() {
        JPanel panel = new JPanel(new TableLayout(new double[][]{{-1}, {-1}}));
        panel.setBorder(new TitledBorder("Protocol Interface"));
        return panel;
    }

    private void setProcotolControl(Component protocolControlCmp) {
        protocolPanel.removeAll();
        protocolPanel.add(protocolControlCmp, "0,0");
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                protocolPanel.revalidate();
                protocolPanel.repaint();
            }
        });
    }


    public void actionPerformed(ActionEvent e) {
        String actionCommand = e.getActionCommand();
        if ("protocols".equals(actionCommand)) {
            JComboBox protocols = (JComboBox) e.getSource();
            String protocol = (String) protocols.getSelectedItem();
            if (PROTOCOLS[0].equals(protocol)) {
                setProcotolControl(unilateralControlPanel);
            } else if (PROTOCOLS[1].equals(protocol)) {
                setProcotolControl(bilateralControlPanel);
            } else if (PROTOCOLS[2].equals(protocol)) {
                setProcotolControl(stsControlPanel);
            }
        }
    }
}

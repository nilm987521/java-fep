package com.fep.jmeter.gui;

import com.fep.jmeter.config.EchoServerElement;
import org.apache.jmeter.config.gui.AbstractConfigGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import java.awt.*;

/**
 * GUI for EchoServerElement.
 */
public class EchoServerElementGui extends AbstractConfigGui {

    private static final long serialVersionUID = 1L;

    private JTextField portField;
    private JCheckBox enabledCheckbox;

    public EchoServerElementGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());
        add(makeTitlePanel(), BorderLayout.NORTH);
        add(createMainPanel(), BorderLayout.CENTER);
    }

    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Echo Server Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Enabled checkbox
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        enabledCheckbox = new JCheckBox("Enable Echo Server");
        enabledCheckbox.setSelected(true);
        panel.add(enabledCheckbox, gbc);

        // Port
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(new JLabel("Port:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        portField = new JTextField("9999", 10);
        panel.add(portField, gbc);

        // Description
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JTextArea description = new JTextArea(
            "Echo Server will start when the test begins and stop when the test ends.\n" +
            "It echoes back any data it receives - useful for testing client samplers."
        );
        description.setEditable(false);
        description.setBackground(panel.getBackground());
        description.setFont(description.getFont().deriveFont(Font.ITALIC));
        description.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        panel.add(description, gbc);

        // Spacer
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    @Override
    public String getStaticLabel() {
        return "Echo Server";
    }

    @Override
    public String getLabelResource() {
        return "echo_server";
    }

    @Override
    public TestElement createTestElement() {
        EchoServerElement element = new EchoServerElement();
        modifyTestElement(element);
        return element;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        configureTestElement(element);
        if (element instanceof EchoServerElement echoServer) {
            echoServer.setPort(Integer.parseInt(portField.getText().trim()));
            echoServer.setEnabled(enabledCheckbox.isSelected());
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (element instanceof EchoServerElement echoServer) {
            portField.setText(String.valueOf(echoServer.getPort()));
            enabledCheckbox.setSelected(echoServer.isEnabled());
        }
    }

    @Override
    public void clearGui() {
        super.clearGui();
        portField.setText("9999");
        enabledCheckbox.setSelected(true);
    }
}

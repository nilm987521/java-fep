package com.fep.jmeter.gui;

import com.fep.jmeter.sampler.FiscSampler;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Custom GUI for FiscSampler.
 *
 * <p>This provides an alternative GUI to the TestBean-based BeanInfo approach.
 * Use this class if you need more control over the GUI layout.
 */
public class FiscSamplerGui extends AbstractSamplerGui {

    private static final long serialVersionUID = 1L;

    // Connection fields
    private JTextField hostField;
    private JTextField portField;
    private JTextField connectionTimeoutField;
    private JTextField readTimeoutField;

    // Transaction fields
    private JComboBox<String> transactionTypeCombo;
    private JTextField institutionIdField;
    private JTextField terminalIdField;

    // Message fields
    private JTextField panField;
    private JTextField processingCodeField;
    private JTextField amountField;
    private JTextArea customFieldsArea;

    public FiscSamplerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        Box verticalBox = Box.createVerticalBox();
        verticalBox.add(makeTitlePanel());
        verticalBox.add(createConnectionPanel());
        verticalBox.add(createTransactionPanel());
        verticalBox.add(createMessagePanel());

        add(verticalBox, BorderLayout.NORTH);
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new VerticalPanel();
        panel.setBorder(new TitledBorder("Connection Settings"));

        hostField = new JTextField(20);
        portField = new JTextField(6);
        connectionTimeoutField = new JTextField(8);
        readTimeoutField = new JTextField(8);

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Host:"));
        row1.add(hostField);
        row1.add(new JLabel("Port:"));
        row1.add(portField);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Connection Timeout (ms):"));
        row2.add(connectionTimeoutField);
        row2.add(new JLabel("Read Timeout (ms):"));
        row2.add(readTimeoutField);

        panel.add(row1);
        panel.add(row2);

        return panel;
    }

    private JPanel createTransactionPanel() {
        JPanel panel = new VerticalPanel();
        panel.setBorder(new TitledBorder("Transaction Settings"));

        String[] txnTypes = {
            FiscSampler.TXN_ECHO_TEST,
            FiscSampler.TXN_SIGN_ON,
            FiscSampler.TXN_SIGN_OFF,
            FiscSampler.TXN_WITHDRAWAL,
            FiscSampler.TXN_TRANSFER,
            FiscSampler.TXN_BALANCE_INQUIRY,
            FiscSampler.TXN_BILL_PAYMENT
        };
        transactionTypeCombo = new JComboBox<>(txnTypes);
        institutionIdField = new JTextField(15);
        terminalIdField = new JTextField(15);

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Transaction Type:"));
        row1.add(transactionTypeCombo);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Institution ID:"));
        row2.add(institutionIdField);
        row2.add(new JLabel("Terminal ID:"));
        row2.add(terminalIdField);

        panel.add(row1);
        panel.add(row2);

        return panel;
    }

    private JPanel createMessagePanel() {
        JPanel panel = new VerticalPanel();
        panel.setBorder(new TitledBorder("Message Fields"));

        panField = new JTextField(20);
        processingCodeField = new JTextField(8);
        amountField = new JTextField(15);
        customFieldsArea = new JTextArea(3, 40);
        customFieldsArea.setLineWrap(true);

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("PAN:"));
        row1.add(panField);
        row1.add(new JLabel("Processing Code:"));
        row1.add(processingCodeField);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Amount (cents):"));
        row2.add(amountField);

        JPanel row3 = new JPanel(new BorderLayout());
        row3.add(new JLabel("Custom Fields (format: field:value;field:value):"), BorderLayout.NORTH);
        row3.add(new JScrollPane(customFieldsArea), BorderLayout.CENTER);

        panel.add(row1);
        panel.add(row2);
        panel.add(row3);

        return panel;
    }

    @Override
    public String getStaticLabel() {
        return "FISC ISO 8583 Sampler";
    }

    @Override
    public String getLabelResource() {
        return "fisc_sampler_title";
    }

    @Override
    public TestElement createTestElement() {
        FiscSampler sampler = new FiscSampler();
        modifyTestElement(sampler);
        return sampler;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);

        if (element instanceof FiscSampler sampler) {
            sampler.setHost(hostField.getText());
            sampler.setPort(parseIntSafe(portField.getText(), 9000));
            sampler.setConnectionTimeout(parseIntSafe(connectionTimeoutField.getText(), 10000));
            sampler.setReadTimeout(parseIntSafe(readTimeoutField.getText(), 30000));
            sampler.setTransactionType((String) transactionTypeCombo.getSelectedItem());
            sampler.setInstitutionId(institutionIdField.getText());
            sampler.setTerminalId(terminalIdField.getText());
            sampler.setPan(panField.getText());
            sampler.setProcessingCode(processingCodeField.getText());
            sampler.setAmount(amountField.getText());
            sampler.setCustomFields(customFieldsArea.getText());
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);

        if (element instanceof FiscSampler sampler) {
            hostField.setText(sampler.getHost());
            portField.setText(String.valueOf(sampler.getPort()));
            connectionTimeoutField.setText(String.valueOf(sampler.getConnectionTimeout()));
            readTimeoutField.setText(String.valueOf(sampler.getReadTimeout()));
            transactionTypeCombo.setSelectedItem(sampler.getTransactionType());
            institutionIdField.setText(sampler.getInstitutionId());
            terminalIdField.setText(sampler.getTerminalId());
            panField.setText(sampler.getPan());
            processingCodeField.setText(sampler.getProcessingCode());
            amountField.setText(sampler.getAmount());
            customFieldsArea.setText(sampler.getCustomFields());
        }
    }

    @Override
    public void clearGui() {
        super.clearGui();
        hostField.setText("localhost");
        portField.setText("9000");
        connectionTimeoutField.setText("10000");
        readTimeoutField.setText("30000");
        transactionTypeCombo.setSelectedIndex(0);
        institutionIdField.setText("");
        terminalIdField.setText("");
        panField.setText("");
        processingCodeField.setText("");
        amountField.setText("");
        customFieldsArea.setText("");
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

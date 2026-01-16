package com.fep.jmeter.gui;

import com.fep.jmeter.config.SchemaConfigElement;
import com.fep.jmeter.sampler.AtmSimulatorSampler;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import com.fep.message.interfaces.SchemaSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom GUI for AtmSimulatorSampler.
 *
 * <p>This GUI provides schema selection from SchemaConfigElement.
 * Schema file path is configured in SchemaConfigElement, and each
 * sampler independently selects which schema to use.
 *
 * <p>Both request and response schemas are loaded from the same JSON file.
 */
@Slf4j
public class AtmSimulatorSamplerGui extends AbstractSamplerGui implements SchemaSubscriber {

    private static final long serialVersionUID = 1L;

    // Connection fields
    private JTextField hostField;
    private JTextField portField;
    private JTextField connectionTimeoutField;
    private JTextField readTimeoutField;
    private JCheckBox expectResponseCheckbox;

    // Schema fields
    private JComboBox<String> selectedSchemaCombo;
    private JTextArea fieldValuesArea;

    // Response Schema fields
    private JCheckBox useDifferentResponseSchemaCheckbox;
    private JComboBox<String> responseSelectedSchemaCombo;
    private JPanel responseSelectedSchemaPanel;

    public AtmSimulatorSamplerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        JPanel mainPanel = new VerticalPanel();
        mainPanel.add(makeTitlePanel());
        mainPanel.add(createConnectionPanel());
        mainPanel.add(createSchemaSelectionPanel());
        mainPanel.add(createResponseSchemaSettingsPanel());
        mainPanel.add(createFieldValuesPanel());

        add(mainPanel, BorderLayout.CENTER);

        // Initialize
        updateResponseSchemaPanelVisibility();
        JsonSchemaLoader.registerSubscriber(this);

        // Unregister when a component is removed from the hierarchy
        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                // Re-register when added back to the hierarchy
                JsonSchemaLoader.registerSubscriber(AtmSimulatorSamplerGui.this);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                // Unregister when removed from hierarchy to prevent memory leak
                JsonSchemaLoader.unregisterSubscriber(AtmSimulatorSamplerGui.this);
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
                // No action needed
            }
        });
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

    private JPanel createSchemaSelectionPanel() {
        JPanel panel = new VerticalPanel();
        panel.setBorder(new TitledBorder("Request Schema"));

        // Selected Schema
        selectedSchemaCombo = new JComboBox<>();

        JPanel schemaRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        schemaRow.add(new JLabel("Selected Schema:"));
        schemaRow.add(selectedSchemaCombo);

        JLabel helpLabel = new JLabel(
            "<html><i>Schema file is configured in Schema Configuration element.</i></html>");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));

        JPanel helpRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        helpRow.add(helpLabel);

        panel.add(schemaRow);
        panel.add(helpRow);

        return panel;
    }

    private JPanel createResponseSchemaSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Response Settings"));

        JPanel mainContent = new VerticalPanel();

        // Expect Response checkbox
        expectResponseCheckbox = new JCheckBox("Expect Response", true);

        // Checkbox to enable different response schema
        useDifferentResponseSchemaCheckbox = new JCheckBox("Use different schema for response");
        useDifferentResponseSchemaCheckbox.addActionListener(e -> updateResponseSchemaPanelVisibility());

        JPanel checkboxRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        checkboxRow.add(expectResponseCheckbox);
        checkboxRow.add(Box.createHorizontalStrut(20));
        checkboxRow.add(useDifferentResponseSchemaCheckbox);
        mainContent.add(checkboxRow);

        // Response Selected Schema (from the same file)
        responseSelectedSchemaCombo = new JComboBox<>();

        responseSelectedSchemaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        responseSelectedSchemaPanel.add(new JLabel("Response Schema:"));
        responseSelectedSchemaPanel.add(responseSelectedSchemaCombo);
        mainContent.add(responseSelectedSchemaPanel);

        panel.add(mainContent, BorderLayout.CENTER);
        return panel;
    }

    private void updateResponseSchemaPanelVisibility() {
        boolean useDifferent = useDifferentResponseSchemaCheckbox.isSelected();
        responseSelectedSchemaPanel.setVisible(useDifferent);
        revalidate();
        repaint();
    }

    private JPanel createFieldValuesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Field Values (JSON)"));

        fieldValuesArea = new JTextArea(6, 60);
        fieldValuesArea.setLineWrap(true);
        fieldValuesArea.setWrapStyleWord(true);
        fieldValuesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(fieldValuesArea);
        scrollPane.setPreferredSize(new Dimension(600, 120));

        JLabel helpLabel = new JLabel(
            "<html>Example: {\"mti\": \"0200\", \"processingCode\": \"010000\", \"amount\": \"000000100000\"}<br/>" +
            "Auto-generated variables: ${stan}, ${time}, ${date}, ${datetime}, ${rrn}</html>"
        );
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));

        panel.add(helpLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    @Override
    public String getStaticLabel() {
        return "ATM Simulator";
    }

    @Override
    public String getLabelResource() {
        return "atm_simulator_title";
    }

    @Override
    public TestElement createTestElement() {
        AtmSimulatorSampler sampler = new AtmSimulatorSampler();
        modifyTestElement(sampler);
        return sampler;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);

        if (element instanceof AtmSimulatorSampler sampler) {
            // Connection settings with validation
            String host = hostField.getText().trim();
            if (host.isEmpty()) {
                host = "localhost";
            }
            sampler.setFepHost(host);

            int port = parseIntSafe(portField.getText(), 8080);
            if (port < 1 || port > 65535) {
                port = 8080;
                log.warn("Invalid port value, using default: {}", port);
            }
            sampler.setFepPort(port);

            int connTimeout = parseIntSafe(connectionTimeoutField.getText(), 10000);
            if (connTimeout < 0) {
                connTimeout = 10000;
            }
            sampler.setConnectionTimeout(connTimeout);

            int readTimeout = parseIntSafe(readTimeoutField.getText(), 30000);
            if (readTimeout < 0) {
                readTimeout = 30000;
            }
            sampler.setReadTimeout(readTimeout);

            sampler.setExpectResponse(expectResponseCheckbox.isSelected());

            // Schema selection
            String selectedSchema = (String) selectedSchemaCombo.getSelectedItem();
            sampler.setSelectedSchema(selectedSchema != null ? selectedSchema : "FISC ATM Format");
            sampler.setFieldValues(fieldValuesArea.getText());

            // Response Schema settings
            sampler.setUseDifferentResponseSchema(useDifferentResponseSchemaCheckbox.isSelected());
            String responseSelectedSchema = (String) responseSelectedSchemaCombo.getSelectedItem();
            sampler.setResponseSelectedSchema(responseSelectedSchema != null ? responseSelectedSchema : "FISC ATM Format");
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);

        if (element instanceof AtmSimulatorSampler sampler) {
            // Connection settings
            hostField.setText(sampler.getFepHost());
            portField.setText(String.valueOf(sampler.getFepPort()));
            connectionTimeoutField.setText(String.valueOf(sampler.getConnectionTimeout()));
            readTimeoutField.setText(String.valueOf(sampler.getReadTimeout()));
            expectResponseCheckbox.setSelected(sampler.isExpectResponse());

            // Schema selection - try to load and set selection
            selectedSchemaCombo.setSelectedItem(sampler.getSelectedSchema());

            fieldValuesArea.setText(sampler.getFieldValues());

            // Response Schema settings
            useDifferentResponseSchemaCheckbox.setSelected(sampler.isUseDifferentResponseSchema());

            String savedResponseSchema = sampler.getResponseSelectedSchema();
            if (savedResponseSchema != null && !savedResponseSchema.isEmpty()) {
                if (responseSelectedSchemaCombo.getItemCount() == 0 ||
                    !containsItem(responseSelectedSchemaCombo, savedResponseSchema)) {
                    responseSelectedSchemaCombo.addItem(savedResponseSchema);
                }
                responseSelectedSchemaCombo.setSelectedItem(savedResponseSchema);
            }

            // Update visibility
            updateResponseSchemaPanelVisibility();
        }
    }

    private boolean containsItem(JComboBox<String> combo, String item) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (item.equals(combo.getItemAt(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clearGui() {
        super.clearGui();

        // Connection settings
        hostField.setText("localhost");
        portField.setText("8080");
        connectionTimeoutField.setText("10000");
        readTimeoutField.setText("30000");
        expectResponseCheckbox.setSelected(true);

        // Schema settings
        selectedSchemaCombo.removeAllItems();
        fieldValuesArea.setText("");

        // Response Schema settings
        useDifferentResponseSchemaCheckbox.setSelected(false);
        responseSelectedSchemaCombo.removeAllItems();

        // Update visibility
        updateResponseSchemaPanelVisibility();
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Called when schema map is updated (subscriber callback).
     * Updates both request and response schema combo boxes while preserving selections.
     * This method is thread-safe and ensures UI updates happen on EDT.
     *
     * @param schemaMap the updated schema map
     */
    @Override
    public void updateSchemaMap(Map<String, MessageSchema> schemaMap) {
        SwingUtilities.invokeLater(() -> {
            // Preserve current selections
            String currentRequestSchema = (String) selectedSchemaCombo.getSelectedItem();
            String currentResponseSchema = (String) responseSelectedSchemaCombo.getSelectedItem();

            // Get schema names as list for easier checking
            List<String> schemaNames = new ArrayList<>(schemaMap.keySet());

            // Update request schema combo
            selectedSchemaCombo.removeAllItems();
            for (String name : schemaNames) {
                selectedSchemaCombo.addItem(name);
            }

            // Restore request schema selection
            if (currentRequestSchema != null && schemaNames.contains(currentRequestSchema)) {
                selectedSchemaCombo.setSelectedItem(currentRequestSchema);
            } else if (!schemaNames.isEmpty()) {
                selectedSchemaCombo.setSelectedIndex(0);
            }

            // Update response schema combo
            responseSelectedSchemaCombo.removeAllItems();
            for (String name : schemaNames) {
                responseSelectedSchemaCombo.addItem(name);
            }

            // Restore response schema selection
            if (currentResponseSchema != null && schemaNames.contains(currentResponseSchema)) {
                responseSelectedSchemaCombo.setSelectedItem(currentResponseSchema);
            } else if (!schemaNames.isEmpty()) {
                responseSelectedSchemaCombo.setSelectedIndex(0);
            }

            log.debug("Schema combo boxes updated with {} schemas", schemaNames.size());
        });
    }
}

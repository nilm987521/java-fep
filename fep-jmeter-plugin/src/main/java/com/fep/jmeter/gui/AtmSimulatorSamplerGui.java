package com.fep.jmeter.gui;

import com.fep.jmeter.sampler.AtmSimulatorSampler;
import com.fep.jmeter.sampler.PresetSchema;
import com.fep.jmeter.sampler.SchemaSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

/**
 * Custom GUI for AtmSimulatorSampler with Schema Preview functionality.
 *
 * <p>This GUI provides real-time schema preview when selecting preset schemas,
 * allowing users to see the complete JSON schema definition.
 *
 * <p>Thread safety: Schema loading is performed on a background thread using
 * SwingWorker to prevent GUI freezing during I/O operations.
 */
@Slf4j
public class AtmSimulatorSamplerGui extends AbstractSamplerGui {

    private static final long serialVersionUID = 1L;

    // Connection fields
    private JTextField hostField;
    private JTextField portField;
    private JTextField connectionTimeoutField;
    private JTextField readTimeoutField;
    private JCheckBox expectResponseCheckbox;

    // Schema fields
    private JComboBox<String> schemaSourceCombo;
    private JComboBox<String> presetSchemaCombo;
    private JTextField schemaFileField;
    private JTextArea schemaContentArea;
    private JTextArea schemaPreviewArea;
    private JTextArea fieldValuesArea;

    // Panels for dynamic visibility
    private JPanel presetSchemaPanel;
    private JPanel schemaFilePanel;
    private JPanel schemaContentPanel;
    private JPanel schemaPreviewPanel;

    public AtmSimulatorSamplerGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        JPanel mainPanel = new VerticalPanel();
        mainPanel.add(makeTitlePanel());
        mainPanel.add(createConnectionPanel());
        mainPanel.add(createSchemaSettingsPanel());
        mainPanel.add(createSchemaPreviewPanel());
        mainPanel.add(createFieldValuesPanel());

        add(mainPanel, BorderLayout.CENTER);

        // Initialize schema preview
        updatePanelVisibility();
        updateSchemaPreview();
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new VerticalPanel();
        panel.setBorder(new TitledBorder("Connection Settings"));

        hostField = new JTextField(20);
        portField = new JTextField(6);
        connectionTimeoutField = new JTextField(8);
        readTimeoutField = new JTextField(8);
        expectResponseCheckbox = new JCheckBox("Expect Response", true);

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

        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(expectResponseCheckbox);

        panel.add(row1);
        panel.add(row2);
        panel.add(row3);

        return panel;
    }

    private JPanel createSchemaSettingsPanel() {
        JPanel panel = new VerticalPanel();
        panel.setBorder(new TitledBorder("Schema Settings"));

        // Schema Source
        schemaSourceCombo = new JComboBox<>(SchemaSource.names());
        schemaSourceCombo.addActionListener(e -> {
            try {
                updatePanelVisibility();
                updateSchemaPreview();
            } catch (Exception ex) {
                log.error("Error updating schema source", ex);
            }
        });

        JPanel sourceRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sourceRow.add(new JLabel("Schema Source:"));
        sourceRow.add(schemaSourceCombo);

        // Preset Schema
        presetSchemaCombo = new JComboBox<>(PresetSchema.names());
        presetSchemaCombo.addActionListener(e -> {
            try {
                updateSchemaPreview();
            } catch (Exception ex) {
                log.error("Error updating preset schema", ex);
            }
        });

        presetSchemaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        presetSchemaPanel.add(new JLabel("Preset Schema:"));
        presetSchemaPanel.add(presetSchemaCombo);

        // Schema File
        schemaFileField = new JTextField(40);
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseSchemaFile());

        schemaFilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        schemaFilePanel.add(new JLabel("Schema File:"));
        schemaFilePanel.add(schemaFileField);
        schemaFilePanel.add(browseButton);

        // Schema Content (for INLINE mode)
        schemaContentArea = new JTextArea(8, 60);
        schemaContentArea.setLineWrap(true);
        schemaContentArea.setWrapStyleWord(true);
        schemaContentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        schemaContentPanel = new JPanel(new BorderLayout());
        schemaContentPanel.add(new JLabel("Inline Schema (JSON):"), BorderLayout.NORTH);
        schemaContentPanel.add(new JScrollPane(schemaContentArea), BorderLayout.CENTER);

        panel.add(sourceRow);
        panel.add(presetSchemaPanel);
        panel.add(schemaFilePanel);
        panel.add(schemaContentPanel);

        return panel;
    }

    private JPanel createSchemaPreviewPanel() {
        schemaPreviewPanel = new JPanel(new BorderLayout());
        schemaPreviewPanel.setBorder(new TitledBorder("Schema Preview (Read-only)"));

        schemaPreviewArea = new JTextArea(12, 60);
        schemaPreviewArea.setEditable(false);
        schemaPreviewArea.setLineWrap(true);
        schemaPreviewArea.setWrapStyleWord(true);
        schemaPreviewArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        schemaPreviewArea.setBackground(new Color(245, 245, 245));

        JScrollPane scrollPane = new JScrollPane(schemaPreviewArea);
        scrollPane.setPreferredSize(new Dimension(600, 200));

        schemaPreviewPanel.add(scrollPane, BorderLayout.CENTER);

        return schemaPreviewPanel;
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

    private void updatePanelVisibility() {
        String source = (String) schemaSourceCombo.getSelectedItem();
        SchemaSource schemaSource = SchemaSource.fromString(source);

        presetSchemaPanel.setVisible(schemaSource == SchemaSource.PRESET);
        schemaFilePanel.setVisible(schemaSource == SchemaSource.FILE);
        schemaContentPanel.setVisible(schemaSource == SchemaSource.INLINE);
        schemaPreviewPanel.setVisible(schemaSource == SchemaSource.PRESET);

        revalidate();
        repaint();
    }

    /**
     * Update schema preview using SwingWorker to avoid GUI freezing.
     * Schema loading is performed on a background thread.
     */
    private void updateSchemaPreview() {
        String source = (String) schemaSourceCombo.getSelectedItem();
        SchemaSource schemaSource = SchemaSource.fromString(source);

        if (schemaSource == SchemaSource.PRESET) {
            schemaPreviewArea.setText("Loading schema...");
            log.debug("Loading schema preview for: {}", presetSchemaCombo.getSelectedItem());

            // Perform I/O on background thread to prevent GUI freezing
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    String selected = (String) presetSchemaCombo.getSelectedItem();
                    PresetSchema preset = PresetSchema.fromString(selected);
                    return preset.loadSchemaContent();
                }

                @Override
                protected void done() {
                    try {
                        String content = get();
                        schemaPreviewArea.setText(content);
                        schemaPreviewArea.setCaretPosition(0);
                        log.debug("Schema preview loaded successfully");
                    } catch (Exception e) {
                        log.error("Error loading schema preview", e);
                        schemaPreviewArea.setText("// Error loading schema: " + e.getMessage());
                    }
                }
            };
            worker.execute();
        } else {
            schemaPreviewArea.setText("// Schema preview is only available in PRESET mode");
        }
    }

    private static final long MAX_SCHEMA_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private void browseSchemaFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            // Validate file exists
            if (!selectedFile.exists() || !selectedFile.isFile()) {
                JOptionPane.showMessageDialog(this,
                    "Selected path is not a valid file",
                    "Invalid Selection",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Validate file size
            if (selectedFile.length() > MAX_SCHEMA_FILE_SIZE) {
                JOptionPane.showMessageDialog(this,
                    "File too large (max 10MB)",
                    "File Too Large",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Warn if not .json extension
            if (!selectedFile.getName().toLowerCase().endsWith(".json")) {
                int confirm = JOptionPane.showConfirmDialog(this,
                    "Selected file doesn't have .json extension. Continue?",
                    "Confirm",
                    JOptionPane.YES_NO_OPTION);
                if (confirm != JOptionPane.YES_OPTION) {
                    return;
                }
            }

            schemaFileField.setText(selectedFile.getAbsolutePath());
            log.debug("Schema file selected: {}", selectedFile.getAbsolutePath());
        }
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

            // Schema settings
            sampler.setSchemaSource((String) schemaSourceCombo.getSelectedItem());
            sampler.setPresetSchema((String) presetSchemaCombo.getSelectedItem());
            sampler.setSchemaFile(schemaFileField.getText().trim());
            sampler.setSchemaContent(schemaContentArea.getText());
            sampler.setFieldValues(fieldValuesArea.getText());
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

            // Schema settings
            schemaSourceCombo.setSelectedItem(sampler.getSchemaSource());
            presetSchemaCombo.setSelectedItem(sampler.getPresetSchema());
            schemaFileField.setText(sampler.getSchemaFile());
            schemaContentArea.setText(sampler.getSchemaContent());
            fieldValuesArea.setText(sampler.getFieldValues());

            // Update visibility and preview
            updatePanelVisibility();
            updateSchemaPreview();
        }
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
        schemaSourceCombo.setSelectedItem(SchemaSource.PRESET.name());
        presetSchemaCombo.setSelectedItem(PresetSchema.FISC_ATM.name());
        schemaFileField.setText("");
        schemaContentArea.setText("");
        fieldValuesArea.setText("");

        // Update visibility and preview
        updatePanelVisibility();
        updateSchemaPreview();
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

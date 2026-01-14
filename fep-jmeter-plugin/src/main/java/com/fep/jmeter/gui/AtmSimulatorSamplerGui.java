package com.fep.jmeter.gui;

import com.fep.jmeter.gui.schema.SchemaTableCellRenderer;
import com.fep.jmeter.gui.schema.SchemaTableModel;
import com.fep.jmeter.sampler.AtmSimulatorSampler;
import com.fep.jmeter.sampler.PresetSchema;
import com.fep.jmeter.sampler.ResponseSchemaSource;
import com.fep.jmeter.sampler.SchemaSource;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.samplers.gui.AbstractSamplerGui;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableColumn;
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
    private JTable schemaTable;
    private SchemaTableModel schemaTableModel;
    private JLabel schemaStatusLabel;
    private JTextArea fieldValuesArea;

    // Panels for dynamic visibility
    private JPanel presetSchemaPanel;
    private JPanel schemaFilePanel;
    private JPanel schemaContentPanel;
    private JPanel schemaPreviewPanel;

    // Response Schema fields
    private JCheckBox useDifferentResponseSchemaCheckbox;
    private JComboBox<String> responseSchemaSourceCombo;
    private JComboBox<String> responsePresetSchemaCombo;
    private JTextField responseSchemaFileField;
    private JTextArea responseSchemaContentArea;
    private JTable responseSchemaTable;
    private SchemaTableModel responseSchemaTableModel;
    private JLabel responseSchemaStatusLabel;

    // Response Schema panels
    private JPanel responseSchemaSettingsPanel;
    private JPanel responsePresetSchemaPanel;
    private JPanel responseSchemaFilePanel;
    private JPanel responseSchemaContentPanel;
    private JPanel responseSchemaPreviewPanel;

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
        mainPanel.add(createResponseSchemaSettingsPanel());
        mainPanel.add(createFieldValuesPanel());

        add(mainPanel, BorderLayout.CENTER);

        // Initialize schema preview
        updatePanelVisibility();
        updateSchemaPreview();
        updateResponseSchemaPanelVisibility();
        updateResponseSchemaPreview();
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
        schemaPreviewPanel = new JPanel(new BorderLayout(0, 5));
        schemaPreviewPanel.setBorder(new TitledBorder("Schema Preview"));

        // Create table model and table
        schemaTableModel = new SchemaTableModel();
        schemaTable = new JTable(schemaTableModel);
        schemaTable.setRowHeight(22);
        schemaTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        schemaTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        schemaTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        schemaTable.setFillsViewportHeight(true);

        // Set custom cell renderer
        SchemaTableCellRenderer renderer = new SchemaTableCellRenderer(schemaTableModel);
        for (int i = 0; i < schemaTable.getColumnCount(); i++) {
            schemaTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Set column widths
        setColumnWidths();

        JScrollPane scrollPane = new JScrollPane(schemaTable);
        scrollPane.setPreferredSize(new Dimension(900, 250));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Status label for loading indicator
        schemaStatusLabel = new JLabel(" ");
        schemaStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));

        // Legend panel
        JPanel legendPanel = createLegendPanel();

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(schemaStatusLabel, BorderLayout.WEST);
        bottomPanel.add(legendPanel, BorderLayout.EAST);

        schemaPreviewPanel.add(scrollPane, BorderLayout.CENTER);
        schemaPreviewPanel.add(bottomPanel, BorderLayout.SOUTH);

        return schemaPreviewPanel;
    }

    private void setColumnWidths() {
        int[] widths = {120, 160, 100, 60, 80, 70, 45, 45, 80, 100};
        for (int i = 0; i < widths.length && i < schemaTable.getColumnCount(); i++) {
            TableColumn column = schemaTable.getColumnModel().getColumn(i);
            column.setPreferredWidth(widths[i]);
        }
    }

    private JPanel createLegendPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panel.add(createLegendItem(new Color(230, 255, 230), "BITMAP"));
        panel.add(createLegendItem(new Color(255, 250, 230), "由bitmap控制"));
        panel.add(createLegendItem(new Color(230, 240, 255), "COMPOSITE"));
        panel.add(createLegendItem(new Color(240, 248, 255), "子欄位"));
        return panel;
    }

    private JPanel createLegendItem(Color color, String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        JLabel colorBox = new JLabel("  ");
        colorBox.setOpaque(true);
        colorBox.setBackground(color);
        colorBox.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        item.add(colorBox);
        item.add(new JLabel(text));
        return item;
    }

    private JPanel createResponseSchemaSettingsPanel() {
        responseSchemaSettingsPanel = new JPanel(new BorderLayout());
        responseSchemaSettingsPanel.setBorder(new TitledBorder("Response Schema"));

        JPanel mainContent = new VerticalPanel();

        // Checkbox to enable different response schema
        useDifferentResponseSchemaCheckbox = new JCheckBox("Use different schema for response");
        useDifferentResponseSchemaCheckbox.addActionListener(e -> {
            updateResponseSchemaPanelVisibility();
            updateResponseSchemaPreview();
        });

        JPanel checkboxRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        checkboxRow.add(useDifferentResponseSchemaCheckbox);
        mainContent.add(checkboxRow);

        // Response Schema Source
        String[] responseSources = {
            ResponseSchemaSource.SAME_AS_REQUEST.name(),
            ResponseSchemaSource.PRESET.name(),
            ResponseSchemaSource.FILE.name(),
            ResponseSchemaSource.INLINE.name()
        };
        responseSchemaSourceCombo = new JComboBox<>(responseSources);
        responseSchemaSourceCombo.addActionListener(e -> {
            updateResponseSchemaPanelVisibility();
            updateResponseSchemaPreview();
        });

        JPanel sourceRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sourceRow.add(new JLabel("Schema Source:"));
        sourceRow.add(responseSchemaSourceCombo);
        mainContent.add(sourceRow);

        // Response Preset Schema
        responsePresetSchemaCombo = new JComboBox<>(PresetSchema.names());
        responsePresetSchemaCombo.addActionListener(e -> updateResponseSchemaPreview());

        responsePresetSchemaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        responsePresetSchemaPanel.add(new JLabel("Preset Schema:"));
        responsePresetSchemaPanel.add(responsePresetSchemaCombo);
        mainContent.add(responsePresetSchemaPanel);

        // Response Schema File
        responseSchemaFileField = new JTextField(40);
        JButton responseBrowseButton = new JButton("Browse...");
        responseBrowseButton.addActionListener(e -> browseResponseSchemaFile());

        responseSchemaFilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        responseSchemaFilePanel.add(new JLabel("Schema File:"));
        responseSchemaFilePanel.add(responseSchemaFileField);
        responseSchemaFilePanel.add(responseBrowseButton);
        mainContent.add(responseSchemaFilePanel);

        // Response Schema Content (for INLINE mode)
        responseSchemaContentArea = new JTextArea(6, 60);
        responseSchemaContentArea.setLineWrap(true);
        responseSchemaContentArea.setWrapStyleWord(true);
        responseSchemaContentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        responseSchemaContentPanel = new JPanel(new BorderLayout());
        responseSchemaContentPanel.add(new JLabel("Inline Schema (JSON):"), BorderLayout.NORTH);
        responseSchemaContentPanel.add(new JScrollPane(responseSchemaContentArea), BorderLayout.CENTER);
        mainContent.add(responseSchemaContentPanel);

        // Response Schema Preview
        responseSchemaPreviewPanel = createResponseSchemaPreviewPanel();
        mainContent.add(responseSchemaPreviewPanel);

        responseSchemaSettingsPanel.add(mainContent, BorderLayout.CENTER);
        return responseSchemaSettingsPanel;
    }

    private JPanel createResponseSchemaPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));

        // Create table model and table
        responseSchemaTableModel = new SchemaTableModel();
        responseSchemaTable = new JTable(responseSchemaTableModel);
        responseSchemaTable.setRowHeight(22);
        responseSchemaTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        responseSchemaTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        responseSchemaTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        responseSchemaTable.setFillsViewportHeight(true);

        // Set custom cell renderer
        SchemaTableCellRenderer renderer = new SchemaTableCellRenderer(responseSchemaTableModel);
        for (int i = 0; i < responseSchemaTable.getColumnCount(); i++) {
            responseSchemaTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Set column widths
        setResponseSchemaColumnWidths();

        JScrollPane scrollPane = new JScrollPane(responseSchemaTable);
        scrollPane.setPreferredSize(new Dimension(900, 180));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Status label
        responseSchemaStatusLabel = new JLabel(" ");
        responseSchemaStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(responseSchemaStatusLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void setResponseSchemaColumnWidths() {
        int[] widths = {120, 160, 100, 60, 80, 70, 45, 45, 80, 100};
        for (int i = 0; i < widths.length && i < responseSchemaTable.getColumnCount(); i++) {
            TableColumn column = responseSchemaTable.getColumnModel().getColumn(i);
            column.setPreferredWidth(widths[i]);
        }
    }

    private void browseResponseSchemaFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            if (!selectedFile.exists() || !selectedFile.isFile()) {
                JOptionPane.showMessageDialog(this,
                    "Selected path is not a valid file",
                    "Invalid Selection",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (selectedFile.length() > MAX_SCHEMA_FILE_SIZE) {
                JOptionPane.showMessageDialog(this,
                    "File too large (max 10MB)",
                    "File Too Large",
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            responseSchemaFileField.setText(selectedFile.getAbsolutePath());
            log.debug("Response schema file selected: {}", selectedFile.getAbsolutePath());
        }
    }

    private void updateResponseSchemaPanelVisibility() {
        boolean useDifferent = useDifferentResponseSchemaCheckbox.isSelected();

        // Hide all response schema fields if not using different schema
        responseSchemaSourceCombo.getParent().setVisible(useDifferent);
        responsePresetSchemaPanel.setVisible(false);
        responseSchemaFilePanel.setVisible(false);
        responseSchemaContentPanel.setVisible(false);
        responseSchemaPreviewPanel.setVisible(false);

        if (useDifferent) {
            String source = (String) responseSchemaSourceCombo.getSelectedItem();
            ResponseSchemaSource schemaSource = ResponseSchemaSource.fromString(source);

            boolean showPreview = false;
            switch (schemaSource) {
                case SAME_AS_REQUEST:
                    // No additional fields needed
                    break;
                case PRESET:
                    responsePresetSchemaPanel.setVisible(true);
                    responseSchemaPreviewPanel.setVisible(true);
                    showPreview = true;
                    break;
                case FILE:
                    responseSchemaFilePanel.setVisible(true);
                    break;
                case INLINE:
                    responseSchemaContentPanel.setVisible(true);
                    break;
            }
        }

        revalidate();
        repaint();
    }

    private void updateResponseSchemaPreview() {
        if (!useDifferentResponseSchemaCheckbox.isSelected()) {
            responseSchemaTableModel.clear();
            responseSchemaStatusLabel.setText(" ");
            return;
        }

        String source = (String) responseSchemaSourceCombo.getSelectedItem();
        ResponseSchemaSource schemaSource = ResponseSchemaSource.fromString(source);

        if (schemaSource == ResponseSchemaSource.PRESET) {
            responseSchemaStatusLabel.setText("Loading schema...");
            responseSchemaTableModel.clear();

            SwingWorker<MessageSchema, Void> worker = new SwingWorker<>() {
                @Override
                protected MessageSchema doInBackground() {
                    String selected = (String) responsePresetSchemaCombo.getSelectedItem();
                    PresetSchema preset = PresetSchema.fromString(selected);
                    String jsonContent = preset.loadSchemaContent();
                    return JsonSchemaLoader.fromJson(jsonContent);
                }

                @Override
                protected void done() {
                    try {
                        MessageSchema schema = get();
                        responseSchemaTableModel.loadSchema(schema);
                        setResponseSchemaColumnWidths();
                        responseSchemaStatusLabel.setText(String.format("%s - %d fields",
                            schema.getName(), responseSchemaTableModel.getRowCount()));
                    } catch (Exception e) {
                        log.error("Error loading response schema preview", e);
                        responseSchemaStatusLabel.setText("Error: " + e.getMessage());
                        responseSchemaTableModel.clear();
                    }
                }
            };
            worker.execute();
        } else {
            responseSchemaTableModel.clear();
            responseSchemaStatusLabel.setText(" ");
        }
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
            schemaStatusLabel.setText("Loading schema...");
            schemaTableModel.clear();
            log.debug("Loading schema preview for: {}", presetSchemaCombo.getSelectedItem());

            // Perform I/O on background thread to prevent GUI freezing
            SwingWorker<MessageSchema, Void> worker = new SwingWorker<>() {
                @Override
                protected MessageSchema doInBackground() {
                    String selected = (String) presetSchemaCombo.getSelectedItem();
                    PresetSchema preset = PresetSchema.fromString(selected);
                    String jsonContent = preset.loadSchemaContent();
                    return JsonSchemaLoader.fromJson(jsonContent);
                }

                @Override
                protected void done() {
                    try {
                        MessageSchema schema = get();
                        schemaTableModel.loadSchema(schema);
                        setColumnWidths(); // Re-apply column widths after data load
                        schemaStatusLabel.setText(String.format("%s - %d fields",
                            schema.getName(), schemaTableModel.getRowCount()));
                        log.debug("Schema preview loaded successfully with {} rows",
                            schemaTableModel.getRowCount());
                    } catch (Exception e) {
                        log.error("Error loading schema preview", e);
                        schemaStatusLabel.setText("Error: " + e.getMessage());
                        schemaTableModel.clear();
                    }
                }
            };
            worker.execute();
        } else {
            schemaTableModel.clear();
            schemaStatusLabel.setText("Schema preview only available in PRESET mode");
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

            // Response Schema settings
            sampler.setUseDifferentResponseSchema(useDifferentResponseSchemaCheckbox.isSelected());
            sampler.setResponseSchemaSource((String) responseSchemaSourceCombo.getSelectedItem());
            sampler.setResponsePresetSchema((String) responsePresetSchemaCombo.getSelectedItem());
            sampler.setResponseSchemaFile(responseSchemaFileField.getText().trim());
            sampler.setResponseSchemaContent(responseSchemaContentArea.getText());
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

            // Response Schema settings
            useDifferentResponseSchemaCheckbox.setSelected(sampler.isUseDifferentResponseSchema());
            responseSchemaSourceCombo.setSelectedItem(sampler.getResponseSchemaSource());
            responsePresetSchemaCombo.setSelectedItem(sampler.getResponsePresetSchema());
            responseSchemaFileField.setText(sampler.getResponseSchemaFile());
            responseSchemaContentArea.setText(sampler.getResponseSchemaContent());

            // Update visibility and preview
            updatePanelVisibility();
            updateSchemaPreview();
            updateResponseSchemaPanelVisibility();
            updateResponseSchemaPreview();
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

        // Response Schema settings
        useDifferentResponseSchemaCheckbox.setSelected(false);
        responseSchemaSourceCombo.setSelectedItem(ResponseSchemaSource.SAME_AS_REQUEST.name());
        responsePresetSchemaCombo.setSelectedItem(PresetSchema.FISC_ATM.name());
        responseSchemaFileField.setText("");
        responseSchemaContentArea.setText("");

        // Update visibility and preview
        updatePanelVisibility();
        updateSchemaPreview();
        updateResponseSchemaPanelVisibility();
        updateResponseSchemaPreview();
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

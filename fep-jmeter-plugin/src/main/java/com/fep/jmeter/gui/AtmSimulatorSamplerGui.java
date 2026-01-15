package com.fep.jmeter.gui;

import com.fep.jmeter.gui.schema.SchemaTableCellRenderer;
import com.fep.jmeter.gui.schema.SchemaTableModel;
import com.fep.jmeter.sampler.AtmSimulatorSampler;
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
import java.nio.file.Path;
import java.util.List;

/**
 * Custom GUI for AtmSimulatorSampler with Schema Preview functionality.
 *
 * <p>This GUI provides schema selection from a collection file and real-time
 * schema preview, allowing users to see the complete schema definition.
 *
 * <p>Schema file path defaults to ${user.dir}/schemas/atm-schemas.json
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
    private JTextField schemaFileField;
    private JComboBox<String> selectedSchemaCombo;
    private JTable schemaTable;
    private SchemaTableModel schemaTableModel;
    private JLabel schemaStatusLabel;
    private JTextArea fieldValuesArea;

    // Panels
    private JPanel schemaPreviewPanel;

    // Response Schema fields
    private JCheckBox useDifferentResponseSchemaCheckbox;
    private JComboBox<String> responseSchemaSourceCombo;
    private JTextField responseSchemaFileField;
    private JComboBox<String> responseSelectedSchemaCombo;
    private JTable responseSchemaTable;
    private SchemaTableModel responseSchemaTableModel;
    private JLabel responseSchemaStatusLabel;

    // Response Schema panels
    private JPanel responseSchemaSettingsPanel;
    private JPanel responseSchemaFilePanel;
    private JPanel responseSelectedSchemaPanel;
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
        loadSchemaNames();
        updateSchemaPreview();
        updateResponseSchemaPanelVisibility();
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

    private JPanel createSchemaSettingsPanel() {
        JPanel panel = new VerticalPanel();
        panel.setBorder(new TitledBorder("Schema Settings"));

        // Schema File
        schemaFileField = new JTextField(40);
        schemaFileField.setText(SchemaSource.getDefaultSchemaPath());
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseSchemaFile());
        JButton reloadButton = new JButton("Reload");
        reloadButton.addActionListener(e -> {
            loadSchemaNames();
            updateSchemaPreview();
        });

        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileRow.add(new JLabel("Schema File:"));
        fileRow.add(schemaFileField);
        fileRow.add(browseButton);
        fileRow.add(reloadButton);

        // Selected Schema
        selectedSchemaCombo = new JComboBox<>();
        selectedSchemaCombo.addActionListener(e -> {
            try {
                updateSchemaPreview();
            } catch (Exception ex) {
                log.error("Error updating schema preview", ex);
            }
        });

        JPanel schemaRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        schemaRow.add(new JLabel("Selected Schema:"));
        schemaRow.add(selectedSchemaCombo);

        panel.add(fileRow);
        panel.add(schemaRow);

        return panel;
    }

    private JPanel createSchemaPreviewPanel() {
        schemaPreviewPanel = new JPanel(new BorderLayout(0, 5));
        schemaPreviewPanel.setBorder(new TitledBorder("Request Schema Preview"));

        // Create a table model and table
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
        responseSchemaSettingsPanel.setBorder(new TitledBorder("Response Settings"));

        JPanel mainContent = new VerticalPanel();

        // Expect Response checkbox
        expectResponseCheckbox = new JCheckBox("Expect Response", true);

        // Checkbox to enable different response schema
        useDifferentResponseSchemaCheckbox = new JCheckBox("Use different schema for response");
        useDifferentResponseSchemaCheckbox.addActionListener(e -> {
            updateResponseSchemaPanelVisibility();
            updateResponseSchemaPreview();
        });

        JPanel checkboxRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        checkboxRow.add(expectResponseCheckbox);
        checkboxRow.add(Box.createHorizontalStrut(20));
        checkboxRow.add(useDifferentResponseSchemaCheckbox);
        mainContent.add(checkboxRow);

        // Response Schema Source
        String[] responseSources = {
            ResponseSchemaSource.SAME_AS_REQUEST.name(),
            ResponseSchemaSource.FILE.name()
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

        // Response Schema File
        responseSchemaFileField = new JTextField(40);
        responseSchemaFileField.setText(SchemaSource.getDefaultSchemaPath());
        JButton responseBrowseButton = new JButton("Browse...");
        responseBrowseButton.addActionListener(e -> browseResponseSchemaFile());
        JButton responseReloadButton = new JButton("Reload");
        responseReloadButton.addActionListener(e -> {
            loadResponseSchemaNames();
            updateResponseSchemaPreview();
        });

        responseSchemaFilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        responseSchemaFilePanel.add(new JLabel("Schema File:"));
        responseSchemaFilePanel.add(responseSchemaFileField);
        responseSchemaFilePanel.add(responseBrowseButton);
        responseSchemaFilePanel.add(responseReloadButton);
        mainContent.add(responseSchemaFilePanel);

        // Response Selected Schema
        responseSelectedSchemaCombo = new JComboBox<>();
        responseSelectedSchemaCombo.addActionListener(e -> updateResponseSchemaPreview());

        responseSelectedSchemaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        responseSelectedSchemaPanel.add(new JLabel("Selected Schema:"));
        responseSelectedSchemaPanel.add(responseSelectedSchemaCombo);
        mainContent.add(responseSelectedSchemaPanel);

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

    private static final long MAX_SCHEMA_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private void browseSchemaFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));

        // Start from current schema file directory
        String currentPath = schemaFileField.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                fileChooser.setCurrentDirectory(currentFile.getParentFile());
            }
        }

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

            schemaFileField.setText(selectedFile.getAbsolutePath());
            loadSchemaNames();
            updateSchemaPreview();
            log.debug("Schema file selected: {}", selectedFile.getAbsolutePath());
        }
    }

    private void browseResponseSchemaFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));

        String currentPath = responseSchemaFileField.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                fileChooser.setCurrentDirectory(currentFile.getParentFile());
            }
        }

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
            loadResponseSchemaNames();
            updateResponseSchemaPreview();
            log.debug("Response schema file selected: {}", selectedFile.getAbsolutePath());
        }
    }

    private void loadSchemaNames() {
        String schemaFile = schemaFileField.getText();
        if (schemaFile == null || schemaFile.isBlank()) {
            schemaFile = SchemaSource.getDefaultSchemaPath();
        }

        try {
            Path path = Path.of(schemaFile);
            if (path.toFile().exists()) {
                List<String> names = JsonSchemaLoader.getSchemaNames(path);
                selectedSchemaCombo.removeAllItems();
                for (String name : names) {
                    selectedSchemaCombo.addItem(name);
                }
                if (!names.isEmpty()) {
                    selectedSchemaCombo.setSelectedIndex(0);
                }
            } else {
                selectedSchemaCombo.removeAllItems();
                schemaStatusLabel.setText("Schema file not found: " + schemaFile);
            }
        } catch (Exception e) {
            log.error("Error loading schema names", e);
            selectedSchemaCombo.removeAllItems();
            schemaStatusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void loadResponseSchemaNames() {
        String schemaFile = responseSchemaFileField.getText();
        if (schemaFile == null || schemaFile.isBlank()) {
            schemaFile = SchemaSource.getDefaultSchemaPath();
        }

        try {
            Path path = Path.of(schemaFile);
            if (path.toFile().exists()) {
                List<String> names = JsonSchemaLoader.getSchemaNames(path);
                responseSelectedSchemaCombo.removeAllItems();
                for (String name : names) {
                    responseSelectedSchemaCombo.addItem(name);
                }
                if (!names.isEmpty()) {
                    responseSelectedSchemaCombo.setSelectedIndex(0);
                }
            } else {
                responseSelectedSchemaCombo.removeAllItems();
                responseSchemaStatusLabel.setText("Schema file not found: " + schemaFile);
            }
        } catch (Exception e) {
            log.error("Error loading response schema names", e);
            responseSelectedSchemaCombo.removeAllItems();
            responseSchemaStatusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void updateResponseSchemaPanelVisibility() {
        boolean useDifferent = useDifferentResponseSchemaCheckbox.isSelected();

        // Hide all response schema fields if not using different schema
        responseSchemaSourceCombo.getParent().setVisible(useDifferent);
        responseSchemaFilePanel.setVisible(false);
        responseSelectedSchemaPanel.setVisible(false);
        responseSchemaPreviewPanel.setVisible(false);

        if (useDifferent) {
            String source = (String) responseSchemaSourceCombo.getSelectedItem();
            ResponseSchemaSource schemaSource = ResponseSchemaSource.fromString(source);

            switch (schemaSource) {
                case SAME_AS_REQUEST:
                    // No additional fields needed
                    break;
                case FILE:
                    responseSchemaFilePanel.setVisible(true);
                    responseSelectedSchemaPanel.setVisible(true);
                    responseSchemaPreviewPanel.setVisible(true);
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

        if (schemaSource == ResponseSchemaSource.FILE) {
            String schemaFile = responseSchemaFileField.getText();
            String selectedSchema = (String) responseSelectedSchemaCombo.getSelectedItem();

            if (schemaFile == null || schemaFile.isBlank() || selectedSchema == null) {
                responseSchemaTableModel.clear();
                responseSchemaStatusLabel.setText("Please select a schema file and schema");
                return;
            }

            responseSchemaStatusLabel.setText("Loading schema...");
            responseSchemaTableModel.clear();

            SwingWorker<MessageSchema, Void> worker = new SwingWorker<>() {
                @Override
                protected MessageSchema doInBackground() {
                    return JsonSchemaLoader.fromCollectionFile(Path.of(schemaFile), selectedSchema);
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

    /**
     * Update schema preview using SwingWorker to avoid GUI freezing.
     * Schema loading is performed on a background thread.
     */
    private void updateSchemaPreview() {
        String schemaFile = schemaFileField.getText();
        String selectedSchema = (String) selectedSchemaCombo.getSelectedItem();

        if (schemaFile == null || schemaFile.isBlank() || selectedSchema == null) {
            schemaTableModel.clear();
            schemaStatusLabel.setText("Please select a schema file and schema");
            return;
        }

        schemaStatusLabel.setText("Loading schema...");
        schemaTableModel.clear();
        log.debug("Loading schema preview for: {} from {}", selectedSchema, schemaFile);

        // Perform I/O on background thread to prevent GUI freezing
        SwingWorker<MessageSchema, Void> worker = new SwingWorker<>() {
            @Override
            protected MessageSchema doInBackground() {
                return JsonSchemaLoader.fromCollectionFile(Path.of(schemaFile), selectedSchema);
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
            sampler.setSchemaFile(schemaFileField.getText().trim());
            String selectedSchema = (String) selectedSchemaCombo.getSelectedItem();
            sampler.setSelectedSchema(selectedSchema != null ? selectedSchema : "FISC ATM Format");
            sampler.setFieldValues(fieldValuesArea.getText());

            // Response Schema settings
            sampler.setUseDifferentResponseSchema(useDifferentResponseSchemaCheckbox.isSelected());
            sampler.setResponseSchemaSource((String) responseSchemaSourceCombo.getSelectedItem());
            sampler.setResponseSchemaFile(responseSchemaFileField.getText().trim());
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

            // Schema settings
            schemaFileField.setText(sampler.getSchemaFile());
            loadSchemaNames();
            selectedSchemaCombo.setSelectedItem(sampler.getSelectedSchema());
            fieldValuesArea.setText(sampler.getFieldValues());

            // Response Schema settings
            useDifferentResponseSchemaCheckbox.setSelected(sampler.isUseDifferentResponseSchema());
            responseSchemaSourceCombo.setSelectedItem(sampler.getResponseSchemaSource());
            responseSchemaFileField.setText(sampler.getResponseSchemaFile());
            loadResponseSchemaNames();
            responseSelectedSchemaCombo.setSelectedItem(sampler.getResponseSelectedSchema());

            // Update visibility and preview
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
        schemaFileField.setText(SchemaSource.getDefaultSchemaPath());
        loadSchemaNames();
        fieldValuesArea.setText("");

        // Response Schema settings
        useDifferentResponseSchemaCheckbox.setSelected(false);
        responseSchemaSourceCombo.setSelectedItem(ResponseSchemaSource.SAME_AS_REQUEST.name());
        responseSchemaFileField.setText(SchemaSource.getDefaultSchemaPath());
        loadResponseSchemaNames();

        // Update visibility and preview
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

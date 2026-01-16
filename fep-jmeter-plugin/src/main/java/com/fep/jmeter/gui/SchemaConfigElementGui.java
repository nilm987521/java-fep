package com.fep.jmeter.gui;

import com.fep.jmeter.config.SchemaConfigElement;
import com.fep.jmeter.gui.schema.SchemaTableCellRenderer;
import com.fep.jmeter.gui.schema.SchemaTableModel;
import com.fep.jmeter.sampler.SchemaSource;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.config.gui.AbstractConfigGui;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * GUI for SchemaConfigElement.
 *
 * <p>This GUI provides configuration for a single schema file path that can be
 * shared by multiple samplers. Includes schema preview functionality to view
 * the contents of the selected schema.
 *
 * <p>Both request and response schemas are loaded from the same JSON file.
 */
@Slf4j
public class SchemaConfigElementGui extends AbstractConfigGui {

    private static final long serialVersionUID = 1L;
    private static final long MAX_SCHEMA_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    // Schema file field
    private JTextField jsonFileField;

    // Schema preview fields
    private JComboBox<String> schemaCombo;
    private JTable schemaTable;
    private SchemaTableModel schemaTableModel;
    private JLabel schemaStatusLabel;

    public SchemaConfigElementGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        JPanel mainPanel = new VerticalPanel();
        mainPanel.add(makeTitlePanel());
        mainPanel.add(createSchemaFilePanel());
        mainPanel.add(createSchemaPreviewPanel());

        add(mainPanel, BorderLayout.CENTER);

        // Initialize schema preview
        loadSchemaNames();
    }

    private JPanel createSchemaFilePanel() {
        JPanel panel = new VerticalPanel();
        panel.setBorder(new TitledBorder("Schema File"));

        jsonFileField = new JTextField(50);
        jsonFileField.setText(SchemaSource.getDefaultSchemaPath());
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseSchemaFile());
        JButton reloadButton = new JButton("Reload");
        reloadButton.addActionListener(e -> {
            loadSchemaNames();
            updateSchemaPreview();
        });

        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileRow.add(new JLabel("Schema File:"));
        fileRow.add(jsonFileField);
        fileRow.add(browseButton);
        fileRow.add(reloadButton);

        JLabel helpLabel = new JLabel(
            "<html>This file contains all schemas for request and response messages.<br/>" +
            "Each sampler can select which schema to use from this file.</html>");
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.ITALIC, 11f));

        JPanel helpRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        helpRow.add(helpLabel);

        // Schema selection for preview
        schemaCombo = new JComboBox<>();
        schemaCombo.addActionListener(e -> updateSchemaPreview());

        JPanel schemaRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        schemaRow.add(new JLabel("Preview Schema:"));
        schemaRow.add(schemaCombo);

        panel.add(fileRow);
        panel.add(helpRow);
        panel.add(schemaRow);

        return panel;
    }

    private JPanel createSchemaPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(new TitledBorder("Schema Preview"));

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
        scrollPane.setPreferredSize(new Dimension(900, 300));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // Status label
        schemaStatusLabel = new JLabel(" ");
        schemaStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));

        // Legend panel
        JPanel legendPanel = createLegendPanel();

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(schemaStatusLabel, BorderLayout.WEST);
        bottomPanel.add(legendPanel, BorderLayout.EAST);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
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

    private void browseSchemaFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));

        String currentPath = jsonFileField.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                fileChooser.setCurrentDirectory(currentFile.getParentFile());
            } else {
                fileChooser.setCurrentDirectory(new File("."));
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

            jsonFileField.setText(selectedFile.getAbsolutePath());
            loadSchemaNames();
            updateSchemaPreview();
            log.debug("Schema file selected: {}", selectedFile.getAbsolutePath());
        }
    }

    private void loadSchemaNames() {
        String schemaFile = jsonFileField.getText();
        if (schemaFile == null || schemaFile.isBlank()) {
            schemaFile = SchemaSource.getDefaultSchemaPath();
        }

        try {
            Path path = Path.of(schemaFile);
            if (path.toFile().exists()) {
                JsonSchemaLoader.reloadFromFilePath(schemaFile);
                List<String> names = JsonSchemaLoader.getSchemaMap().keySet().stream().toList();
                String currentSelection = (String) schemaCombo.getSelectedItem();

                schemaCombo.removeAllItems();
                for (String name : names) {
                    schemaCombo.addItem(name);
                }

                // Restore previous selection if available
                if (currentSelection != null && names.contains(currentSelection)) {
                    schemaCombo.setSelectedItem(currentSelection);
                } else if (!names.isEmpty()) {
                    schemaCombo.setSelectedIndex(0);
                }

                schemaStatusLabel.setText("Found " + names.size() + " schemas");
            } else {
                schemaCombo.removeAllItems();
                schemaStatusLabel.setText("Schema file not found: " + schemaFile);
            }
        } catch (Exception e) {
            log.error("Error loading schema names", e);
            schemaCombo.removeAllItems();
            schemaStatusLabel.setText("Error: " + e.getMessage());
        }
    }

    private void updateSchemaPreview() {
        String schemaFile = jsonFileField.getText();
        String selectedSchema = (String) schemaCombo.getSelectedItem();

        if (schemaFile == null || schemaFile.isBlank() || selectedSchema == null) {
            schemaTableModel.clear();
            schemaStatusLabel.setText("Please select a schema file and schema");
            return;
        }

        schemaStatusLabel.setText("Loading schema...");
        schemaTableModel.clear();
        log.debug("Loading schema preview for: {} from {}", selectedSchema, schemaFile);

        // Perform I/O on the background thread to prevent GUI freezing
        SwingWorker<Map<String, MessageSchema>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<String, MessageSchema> doInBackground() {
                JsonSchemaLoader.reloadFromFilePath(schemaFile);
                return JsonSchemaLoader.getSchemaMap();
            }

            @Override
            protected void done() {
                try {
                    Map<String, MessageSchema> schemaMap = get();
                    if (schemaMap.size() > 0) {
                        MessageSchema firstSchema = schemaMap.entrySet().stream().findFirst().get().getValue();
                        schemaTableModel.loadSchema(firstSchema);
                        setColumnWidths(); // Re-apply column widths after data load
                        schemaStatusLabel.setText(String.format("%s - %d fields",
                                firstSchema.getName(), schemaTableModel.getRowCount()));
                        log.debug("Schema preview loaded successfully with {} rows",
                                schemaTableModel.getRowCount());
                    }
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
        return "Schema Configuration";
    }

    @Override
    public String getLabelResource() {
        return "schema_config";
    }

    @Override
    public TestElement createTestElement() {
        SchemaConfigElement element = new SchemaConfigElement();
        modifyTestElement(element);
        return element;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        configureTestElement(element);

        if (element instanceof SchemaConfigElement config) {
            config.setSchemaFile(jsonFileField.getText().trim());
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);

        if (element instanceof SchemaConfigElement config) {
            jsonFileField.setText(config.getSchemaFile());
            loadSchemaNames();
            updateSchemaPreview();
        }
    }

    @Override
    public void clearGui() {
        super.clearGui();
        Path currentRelativePath = Paths.get("");
        jsonFileField.setText(currentRelativePath.toAbsolutePath() + "/schemas.json");
        loadSchemaNames();
        updateSchemaPreview();
    }
}

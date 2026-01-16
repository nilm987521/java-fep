package com.fep.jmeter.gui;

import com.fep.jmeter.assertion.GenericMessageAssertion;
import com.fep.jmeter.sampler.SchemaSource;
import com.fep.message.generic.schema.JsonSchemaLoader;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.assertions.gui.AbstractAssertionGui;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * GUI for GenericMessageAssertion.
 *
 * <p>Provides schema selection and expected values configuration for validating
 * Generic Message responses from ATM Simulator or similar samplers.
 */
@Slf4j
public class GenericMessageAssertionGui extends AbstractAssertionGui {

    private static final long serialVersionUID = 1L;
    private static final long MAX_SCHEMA_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    // Schema selection fields
    private JTextField schemaFileField;
    private JComboBox<String> selectedSchemaCombo;
    private JLabel schemaStatusLabel;

    // Expected values field
    private JTextArea expectedValuesArea;

    public GenericMessageAssertionGui() {
        init();
    }

    private void init() {
        setLayout(new BorderLayout(0, 5));
        setBorder(makeBorder());

        JPanel mainPanel = new VerticalPanel();
        mainPanel.add(makeTitlePanel());
        mainPanel.add(createSchemaSettingsPanel());
        mainPanel.add(createExpectedValuesPanel());

        add(mainPanel, BorderLayout.CENTER);

        // Initialize schema list
        loadSchemaNames();
    }

    private JPanel createSchemaSettingsPanel() {
        JPanel panel = new VerticalPanel();
        panel.setBorder(new TitledBorder("Schema Settings"));

        // Schema File row
        schemaFileField = new JTextField(40);
        schemaFileField.setText(SchemaSource.getDefaultSchemaPath());

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browseSchemaFile());

        JButton reloadButton = new JButton("Reload");
        reloadButton.addActionListener(e -> loadSchemaNames());

        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileRow.add(new JLabel("Schema File:"));
        fileRow.add(schemaFileField);
        fileRow.add(browseButton);
        fileRow.add(reloadButton);

        // Selected Schema row
        selectedSchemaCombo = new JComboBox<>();

        JPanel schemaRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        schemaRow.add(new JLabel("Selected Schema:"));
        schemaRow.add(selectedSchemaCombo);

        // Status label
        schemaStatusLabel = new JLabel(" ");
        schemaStatusLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));

        panel.add(fileRow);
        panel.add(schemaRow);
        panel.add(schemaStatusLabel);

        return panel;
    }

    private JPanel createExpectedValuesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Expected Values (JSON)"));

        // Help text
        JLabel helpLabel = new JLabel(
            "<html>" +
            "<b>Simple match:</b> {\"responseCode\": \"00\", \"mti\": \"0210\"}<br/>" +
            "<b>Operators:</b> $eq, $ne, $regex, $contains, $startsWith, $endsWith<br/>" +
            "<b>Example:</b> {\"amount\": {\"$regex\": \"^\\\\d{12}$\"}, \"terminalId\": {\"$contains\": \"ATM\"}}" +
            "</html>"
        );
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.PLAIN, 11f));
        helpLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        // Text area
        expectedValuesArea = new JTextArea(8, 60);
        expectedValuesArea.setLineWrap(true);
        expectedValuesArea.setWrapStyleWord(true);
        expectedValuesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(expectedValuesArea);
        scrollPane.setPreferredSize(new Dimension(600, 150));

        panel.add(helpLabel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

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
            log.debug("Schema file selected: {}", selectedFile.getAbsolutePath());
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
                schemaStatusLabel.setText("Loaded " + names.size() + " schema(s)");
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

    @Override
    public String getStaticLabel() {
        return "Generic Message Assertion";
    }

    @Override
    public String getLabelResource() {
        return "generic_message_assertion_title";
    }

    @Override
    public TestElement createTestElement() {
        GenericMessageAssertion assertion = new GenericMessageAssertion();
        modifyTestElement(assertion);
        return assertion;
    }

    @Override
    public void modifyTestElement(TestElement element) {
        super.configureTestElement(element);

        if (element instanceof GenericMessageAssertion assertion) {
            assertion.setSchemaFile(schemaFileField.getText().trim());

            String selectedSchema = (String) selectedSchemaCombo.getSelectedItem();
            assertion.setSelectedSchema(selectedSchema != null ? selectedSchema : "FISC ATM Format");

            assertion.setExpectedValues(expectedValuesArea.getText());
        }
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);

        if (element instanceof GenericMessageAssertion assertion) {
            schemaFileField.setText(assertion.getSchemaFile());
            loadSchemaNames();
            selectedSchemaCombo.setSelectedItem(assertion.getSelectedSchema());
            expectedValuesArea.setText(assertion.getExpectedValues());
        }
    }

    @Override
    public void clearGui() {
        super.clearGui();

        schemaFileField.setText(SchemaSource.getDefaultSchemaPath());
        loadSchemaNames();
        expectedValuesArea.setText("");
    }
}

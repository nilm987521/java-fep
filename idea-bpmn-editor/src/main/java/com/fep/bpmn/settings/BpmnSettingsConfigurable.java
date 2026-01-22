package com.fep.bpmn.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Settings page for the FEP BPMN Editor plugin.
 * Accessible via Settings > Tools > FEP BPMN Editor
 */
public class BpmnSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private JBTextArea scanPatternsField;
    private JBTextField annotationsField;
    private JBTextField interfaceField;
    private JBCheckBox autoScanCheckbox;
    private JBCheckBox showMinimapCheckbox;
    private JBCheckBox showPaletteCheckbox;
    private JBCheckBox showPropertiesCheckbox;
    private JBCheckBox gridSnappingCheckbox;
    private JSpinner gridSizeSpinner;
    private JComboBox<String> themeComboBox;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "FEP BPMN Editor";
    }

    @Override
    public @Nullable JComponent createComponent() {
        // Create form components
        scanPatternsField = new JBTextArea(5, 40);
        scanPatternsField.setLineWrap(false);
        JScrollPane scanPatternsScroll = new JScrollPane(scanPatternsField);
        scanPatternsScroll.setPreferredSize(new Dimension(400, 100));

        annotationsField = new JBTextField();
        interfaceField = new JBTextField();
        autoScanCheckbox = new JBCheckBox("Auto-scan delegates on project open");
        showMinimapCheckbox = new JBCheckBox("Show minimap in editor");
        showPaletteCheckbox = new JBCheckBox("Show component palette by default");
        showPropertiesCheckbox = new JBCheckBox("Show properties panel by default");
        gridSnappingCheckbox = new JBCheckBox("Enable grid snapping");
        gridSizeSpinner = new JSpinner(new SpinnerNumberModel(10, 5, 50, 5));
        themeComboBox = new JComboBox<>(new String[]{"auto", "light", "dark"});

        // Build the form
        mainPanel = FormBuilder.createFormBuilder()
                // Delegate Scanning Section
                .addComponent(createSectionHeader("Delegate Scanning"))
                .addLabeledComponent(new JBLabel("Scan patterns (one per line):"), scanPatternsScroll, 1, false)
                .addTooltip("Glob patterns for finding Java delegate files. Use ** for any path, * for any filename part.")
                .addLabeledComponent(new JBLabel("Required annotations:"), annotationsField, 1, false)
                .addTooltip("Comma-separated list of annotations (e.g., @Component, @Service)")
                .addLabeledComponent(new JBLabel("Delegate interface:"), interfaceField, 1, false)
                .addTooltip("Interface that delegates must implement (e.g., JavaDelegate)")
                .addComponent(autoScanCheckbox)
                .addSeparator()

                // Editor UI Section
                .addComponent(createSectionHeader("Editor UI"))
                .addComponent(showMinimapCheckbox)
                .addComponent(showPaletteCheckbox)
                .addComponent(showPropertiesCheckbox)
                .addLabeledComponent(new JBLabel("Theme:"), themeComboBox, 1, false)
                .addSeparator()

                // Grid Section
                .addComponent(createSectionHeader("Grid & Snapping"))
                .addComponent(gridSnappingCheckbox)
                .addLabeledComponent(new JBLabel("Grid size (pixels):"), gridSizeSpinner, 1, false)

                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        mainPanel.setBorder(JBUI.Borders.empty(10));

        return mainPanel;
    }

    private JComponent createSectionHeader(String title) {
        JBLabel label = new JBLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(JBUI.Borders.emptyTop(10));
        return label;
    }

    @Override
    public boolean isModified() {
        BpmnSettings settings = BpmnSettings.getInstance();
        return !scanPatternsField.getText().equals(settings.getScanPatternsAsString())
                || !annotationsField.getText().equals(settings.getDelegateAnnotationsAsString())
                || !interfaceField.getText().equals(settings.getDelegateInterface())
                || autoScanCheckbox.isSelected() != settings.isAutoScan()
                || showMinimapCheckbox.isSelected() != settings.isShowMinimap()
                || showPaletteCheckbox.isSelected() != settings.isShowPalette()
                || showPropertiesCheckbox.isSelected() != settings.isShowProperties()
                || gridSnappingCheckbox.isSelected() != settings.isGridSnapping()
                || ((Integer) gridSizeSpinner.getValue()) != settings.getGridSize()
                || !themeComboBox.getSelectedItem().equals(settings.getEditorTheme());
    }

    @Override
    public void apply() throws ConfigurationException {
        BpmnSettings settings = BpmnSettings.getInstance();
        settings.setScanPatternsFromString(scanPatternsField.getText());
        settings.setDelegateAnnotationsFromString(annotationsField.getText());
        settings.setDelegateInterface(interfaceField.getText());
        settings.setAutoScan(autoScanCheckbox.isSelected());
        settings.setShowMinimap(showMinimapCheckbox.isSelected());
        settings.setShowPalette(showPaletteCheckbox.isSelected());
        settings.setShowProperties(showPropertiesCheckbox.isSelected());
        settings.setGridSnapping(gridSnappingCheckbox.isSelected());
        settings.setGridSize((Integer) gridSizeSpinner.getValue());
        settings.setEditorTheme((String) themeComboBox.getSelectedItem());
    }

    @Override
    public void reset() {
        BpmnSettings settings = BpmnSettings.getInstance();
        scanPatternsField.setText(settings.getScanPatternsAsString());
        annotationsField.setText(settings.getDelegateAnnotationsAsString());
        interfaceField.setText(settings.getDelegateInterface());
        autoScanCheckbox.setSelected(settings.isAutoScan());
        showMinimapCheckbox.setSelected(settings.isShowMinimap());
        showPaletteCheckbox.setSelected(settings.isShowPalette());
        showPropertiesCheckbox.setSelected(settings.isShowProperties());
        gridSnappingCheckbox.setSelected(settings.isGridSnapping());
        gridSizeSpinner.setValue(settings.getGridSize());
        themeComboBox.setSelectedItem(settings.getEditorTheme());
    }
}

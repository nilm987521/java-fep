package com.fep.jmeter.gui.schema;

import com.fep.message.generic.schema.FieldSchema;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Custom cell renderer for schema table with hierarchical styling.
 * Provides visual differentiation for bitmap-controlled and composite child fields.
 */
public class SchemaTableCellRenderer extends DefaultTableCellRenderer {

    private static final Color BITMAP_CONTROLLED_BG = new Color(255, 250, 230); // Light yellow
    private static final Color COMPOSITE_CHILD_BG = new Color(240, 248, 255);   // Alice blue
    private static final Color BITMAP_FIELD_BG = new Color(230, 255, 230);      // Light green
    private static final Color COMPOSITE_FIELD_BG = new Color(230, 240, 255);   // Light blue
    private static final Color REQUIRED_FG = new Color(180, 0, 0);              // Dark red
    private static final Color SENSITIVE_FG = new Color(180, 100, 0);           // Dark orange

    private final SchemaTableModel tableModel;

    public SchemaTableCellRenderer(SchemaTableModel tableModel) {
        this.tableModel = tableModel;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {

        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (isSelected) {
            return c;
        }

        FieldRowData rowData = tableModel.getRowData(row);
        if (rowData == null) {
            return c;
        }

        // Set background color based on field type and hierarchy
        Color bgColor = getBackgroundColor(rowData);
        c.setBackground(bgColor);

        // Set foreground color for special columns
        c.setForeground(getForegroundColor(rowData, column));

        // Set font for hierarchical display
        if (column == 0 && rowData.getLevel() > 0) {
            c.setFont(c.getFont().deriveFont(Font.PLAIN));
        }

        // Add tooltip for the first column
        if (c instanceof JComponent jc) {
            setTooltip(jc, rowData, column);
        }

        return c;
    }

    private Color getBackgroundColor(FieldRowData rowData) {
        FieldSchema field = rowData.getField();

        // Special field types
        if (field.isBitmap()) {
            return BITMAP_FIELD_BG;
        }
        if (field.isComposite()) {
            return COMPOSITE_FIELD_BG;
        }

        // Hierarchical fields
        if (rowData.isControlledByBitmap()) {
            return BITMAP_CONTROLLED_BG;
        }
        if (rowData.isCompositeChild()) {
            return COMPOSITE_CHILD_BG;
        }

        return Color.WHITE;
    }

    private Color getForegroundColor(FieldRowData rowData, int column) {
        FieldSchema field = rowData.getField();

        // Required field indicator in name column
        if (column == 1 && field.isRequired()) {
            return REQUIRED_FG;
        }

        // Sensitive field indicator in name column
        if (column == 1 && field.isSensitive()) {
            return SENSITIVE_FG;
        }

        return Color.BLACK;
    }

    private void setTooltip(JComponent component, FieldRowData rowData, int column) {
        FieldSchema field = rowData.getField();
        String tooltip = null;

        if (column == 0) {
            // Field ID column - show description
            if (field.getDescription() != null && !field.getDescription().isEmpty()) {
                tooltip = field.getDescription();
            }
        } else if (column == 2) {
            // Type column - show additional info for special types
            if (field.isBitmap() && field.getControls() != null) {
                tooltip = "控制欄位: " + String.join(", ", field.getControls());
            }
        } else if (column == 9) {
            // Remarks column
            if (rowData.isControlledByBitmap()) {
                tooltip = "此欄位的存在由 bitmap 欄位決定";
            }
        }

        component.setToolTipText(tooltip);
    }
}

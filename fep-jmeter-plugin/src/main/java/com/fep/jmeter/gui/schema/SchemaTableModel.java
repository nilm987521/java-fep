package com.fep.jmeter.gui.schema;

import com.fep.message.generic.schema.FieldSchema;
import com.fep.message.generic.schema.MessageSchema;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Table model for displaying message schema fields.
 * Handles flattening of composite and bitmap-controlled fields.
 */
public class SchemaTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {
        "欄位ID", "名稱", "類型", "長度", "長度類型",
        "編碼", "必填", "敏感", "預設值", "備註"
    };

    private static final Class<?>[] COLUMN_CLASSES = {
        String.class, String.class, String.class, String.class, String.class,
        String.class, Boolean.class, Boolean.class, String.class, String.class
    };

    private final List<FieldRowData> rows = new ArrayList<>();

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex >= rows.size()) {
            return null;
        }

        FieldRowData row = rows.get(rowIndex);
        FieldSchema field = row.getField();

        return switch (columnIndex) {
            case 0 -> row.getDisplayId();
            case 1 -> field.getName();
            case 2 -> row.getTypeDescription();
            case 3 -> formatLength(field);
            case 4 -> field.getLengthType().name();
            case 5 -> field.getEncoding();
            case 6 -> field.isRequired();
            case 7 -> field.isSensitive();
            case 8 -> field.getDefaultValue() != null ? field.getDefaultValue() : "";
            case 9 -> row.getRemarks();
            default -> null;
        };
    }

    /**
     * Gets the FieldRowData at the specified row index.
     */
    public FieldRowData getRowData(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < rows.size()) {
            return rows.get(rowIndex);
        }
        return null;
    }

    /**
     * Loads schema and flattens it for table display.
     *
     * @param schema the message schema to load
     */
    public void loadSchema(MessageSchema schema) {
        rows.clear();

        if (schema == null || schema.getFields() == null) {
            fireTableDataChanged();
            return;
        }

        // First pass: collect all bitmap-controlled field IDs
        Set<String> bitmapControlledIds = collectBitmapControlledIds(schema.getFields());

        // Second pass: flatten fields with hierarchy
        for (FieldSchema field : schema.getFields()) {
            addFieldToRows(field, 0, null, bitmapControlledIds, false);
        }

        fireTableDataChanged();
    }

    /**
     * Collects all field IDs that are controlled by bitmap fields.
     */
    private Set<String> collectBitmapControlledIds(List<FieldSchema> fields) {
        Set<String> controlledIds = new HashSet<>();
        for (FieldSchema field : fields) {
            if (field.isBitmap() && field.getControls() != null) {
                controlledIds.addAll(field.getControls());
            }
        }
        return controlledIds;
    }

    /**
     * Recursively adds fields to the rows list with proper hierarchy.
     */
    private void addFieldToRows(FieldSchema field, int level, String parentId,
                                 Set<String> bitmapControlledIds, boolean isCompositeChild) {

        boolean controlledByBitmap = bitmapControlledIds.contains(field.getId());
        int effectiveLevel = level;

        // If controlled by bitmap (and not already nested), indent it
        if (controlledByBitmap && level == 0) {
            effectiveLevel = 1;
        }

        int childCount = 0;
        int controlledCount = 0;

        if (field.isComposite() && field.getFields() != null) {
            childCount = field.getFields().size();
        }
        if (field.isBitmap() && field.getControls() != null) {
            controlledCount = field.getControls().size();
        }

        FieldRowData rowData = FieldRowData.builder()
            .level(effectiveLevel)
            .field(field)
            .parentId(parentId)
            .controlledByBitmap(controlledByBitmap)
            .compositeChild(isCompositeChild)
            .childCount(childCount)
            .controlledCount(controlledCount)
            .build();

        rows.add(rowData);

        // Add composite children
        if (field.isComposite() && field.getFields() != null) {
            for (FieldSchema childField : field.getFields()) {
                addFieldToRows(childField, effectiveLevel + 1, field.getId(),
                              bitmapControlledIds, true);
            }
        }
    }

    /**
     * Formats the length field for display.
     */
    private String formatLength(FieldSchema field) {
        int length = field.getLength();
        if (field.isVariableLength()) {
            return "≤" + length;
        }
        return String.valueOf(length);
    }

    /**
     * Clears all data from the model.
     */
    public void clear() {
        rows.clear();
        fireTableDataChanged();
    }
}

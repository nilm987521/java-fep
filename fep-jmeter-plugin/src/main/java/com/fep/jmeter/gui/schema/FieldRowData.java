package com.fep.jmeter.gui.schema;

import com.fep.message.generic.schema.FieldSchema;
import lombok.Builder;
import lombok.Data;

/**
 * Flattened field data for table display.
 * Represents a single row in the schema table with hierarchical information.
 */
@Data
@Builder
public class FieldRowData {

    /**
     * Indentation level for display.
     * 0 = root level field
     * 1 = bitmap-controlled field or composite child
     * 2+ = nested composite children
     */
    private int level;

    /**
     * The original field schema definition.
     */
    private FieldSchema field;

    /**
     * Parent field ID (for bitmap-controlled or composite children).
     */
    private String parentId;

    /**
     * Whether this field is controlled by a bitmap field.
     */
    private boolean controlledByBitmap;

    /**
     * Whether this field is a child of a composite field.
     */
    private boolean compositeChild;

    /**
     * Number of child fields (for COMPOSITE type).
     */
    private int childCount;

    /**
     * Number of controlled fields (for BITMAP type).
     */
    private int controlledCount;

    /**
     * Gets the display ID with indentation prefix.
     */
    public String getDisplayId() {
        if (level == 0) {
            return field.getId();
        }
        String prefix = "  ".repeat(level - 1) + "└ ";
        return prefix + field.getId();
    }

    /**
     * Gets the type description with additional info.
     */
    public String getTypeDescription() {
        FieldSchema.FieldDataType type = field.getType();
        if (type == FieldSchema.FieldDataType.BITMAP && controlledCount > 0) {
            return "BITMAP (" + controlledCount + " fields)";
        }
        if (type == FieldSchema.FieldDataType.COMPOSITE && childCount > 0) {
            return "COMPOSITE (" + childCount + " fields)";
        }
        return type.name();
    }

    /**
     * Gets the remarks/notes for this field.
     */
    public String getRemarks() {
        if (controlledByBitmap) {
            return "由 bitmap 控制";
        }
        if (compositeChild) {
            return "子欄位";
        }
        return "";
    }
}

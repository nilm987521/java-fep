package com.fep.message.generic.message;

import com.fep.message.generic.schema.FieldSchema;
import com.fep.message.generic.schema.MessageSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic message container that supports any message schema.
 * Fields are accessed by string ID instead of numeric position.
 */
@Slf4j
public class GenericMessage {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    @Getter
    private final MessageSchema schema;

    private final Map<String, Object> fields;
    private final Map<String, Map<String, Object>> compositeFields;

    @Getter
    private byte[] rawData;

    public GenericMessage(MessageSchema schema) {
        this.schema = schema;
        this.fields = new LinkedHashMap<>();
        this.compositeFields = new LinkedHashMap<>();
    }

    /**
     * Populates fields with default values from the schema.
     * Only populates fields that are not already set.
     * This should be called before applyVariables() to allow variable substitution in default values.
     */
    public void populateDefaults() {
        if (schema.getFields() == null) {
            return;
        }
        for (FieldSchema fieldSchema : schema.getFields()) {
            String fieldId = fieldSchema.getId();
            // Only set if not already present
            if (!fields.containsKey(fieldId)) {
                String defaultValue = fieldSchema.getDefaultValue();
                if (defaultValue != null && !defaultValue.isEmpty()) {
                    fields.put(fieldId, defaultValue);
                }
            }
        }
    }

    /**
     * Sets a field value by ID.
     *
     * @param fieldId the field ID
     * @param value   the value
     */
    public void setField(String fieldId, Object value) {
        fields.put(fieldId, value);
    }

    /**
     * Gets a field value by ID.
     *
     * @param fieldId the field ID
     * @return the value, or null if not set
     */
    public Object getField(String fieldId) {
        return fields.get(fieldId);
    }

    /**
     * Gets a field value as String.
     *
     * @param fieldId the field ID
     * @return the string value, or null if not set
     */
    public String getFieldAsString(String fieldId) {
        Object value = fields.get(fieldId);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets a field value as byte array.
     *
     * @param fieldId the field ID
     * @return the byte array, or null if not set
     */
    public byte[] getFieldAsBytes(String fieldId) {
        Object value = fields.get(fieldId);
        if (value instanceof byte[] bytes) {
            return bytes;
        } else if (value != null) {
            return value.toString().getBytes();
        }
        return null;
    }

    /**
     * Checks if a field is set.
     *
     * @param fieldId the field ID
     * @return true if set
     */
    public boolean hasField(String fieldId) {
        return fields.containsKey(fieldId);
    }

    /**
     * Sets a nested field value within a composite field.
     *
     * @param parentId the parent field ID
     * @param childId  the child field ID
     * @param value    the value
     */
    public void setNestedField(String parentId, String childId, Object value) {
        compositeFields.computeIfAbsent(parentId, k -> new LinkedHashMap<>()).put(childId, value);
    }

    /**
     * Gets a nested field value from a composite field.
     *
     * @param parentId the parent field ID
     * @param childId  the child field ID
     * @return the value, or null if not set
     */
    public Object getNestedField(String parentId, String childId) {
        Map<String, Object> parent = compositeFields.get(parentId);
        return parent != null ? parent.get(childId) : null;
    }

    /**
     * Gets all nested fields for a composite field.
     *
     * @param parentId the parent field ID
     * @return map of child fields, or empty map if not set
     */
    public Map<String, Object> getNestedFields(String parentId) {
        return compositeFields.getOrDefault(parentId, Collections.emptyMap());
    }

    /**
     * Sets the raw message bytes.
     *
     * @param data the raw data
     */
    public void setRawData(byte[] data) {
        this.rawData = data;
    }

    /**
     * Gets all field IDs that have been set.
     *
     * @return set of field IDs
     */
    public Set<String> getSetFieldIds() {
        return new LinkedHashSet<>(fields.keySet());
    }

    /**
     * Gets all field values as a map (only explicitly set fields).
     *
     * @return map of field ID to value
     */
    public Map<String, Object> getAllFields() {
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Gets all field values including schema default values.
     * For fields not explicitly set, uses the defaultValue from schema if available.
     *
     * @return map of field ID to value (including defaults)
     */
    public Map<String, Object> getAllFieldsWithDefaults() {
        Map<String, Object> result = new LinkedHashMap<>();

        // First add all schema fields with their default values
        if (schema.getFields() != null) {
            for (FieldSchema fieldSchema : schema.getFields()) {
                String fieldId = fieldSchema.getId();
                if (fields.containsKey(fieldId)) {
                    // Use explicitly set value
                    result.put(fieldId, fields.get(fieldId));
                } else if (fieldSchema.getDefaultValue() != null && !fieldSchema.getDefaultValue().isEmpty()) {
                    // Use schema default value
                    result.put(fieldId, fieldSchema.getDefaultValue());
                }
            }
        }

        // Add any extra fields not in schema (preserve order)
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * Gets the effective value for a field, considering default values.
     *
     * @param fieldId the field ID
     * @return the value (explicit or default), or null if not set and no default
     */
    public Object getFieldWithDefault(String fieldId) {
        if (fields.containsKey(fieldId)) {
            return fields.get(fieldId);
        }
        // Check schema for default value
        return schema.getField(fieldId)
                .map(FieldSchema::getDefaultValue)
                .filter(d -> d != null && !d.isEmpty())
                .orElse(null);
    }

    /**
     * Gets the effective value for a field as String, considering default values.
     *
     * @param fieldId the field ID
     * @return the string value (explicit or default), or null if not set and no default
     */
    public String getFieldWithDefaultAsString(String fieldId) {
        Object value = getFieldWithDefault(fieldId);
        return value != null ? value.toString() : null;
    }

    /**
     * Applies variables to all field values.
     * Replaces ${varName} with the corresponding value from the variables map.
     *
     * @param variables map of variable name to value
     */
    public void applyVariables(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String strValue) {
                String substituted = substituteVariables(strValue, variables);
                if (!substituted.equals(strValue)) {
                    entry.setValue(substituted);
                }
            }
        }

        // Also apply to composite fields
        for (Map<String, Object> nested : compositeFields.values()) {
            for (Map.Entry<String, Object> entry : nested.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String strValue) {
                    String substituted = substituteVariables(strValue, variables);
                    if (!substituted.equals(strValue)) {
                        entry.setValue(substituted);
                    }
                }
            }
        }
    }

    private String substituteVariables(String template, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = variables.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Validates the message against the schema.
     *
     * @return validation result
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        // Check required fields
        for (FieldSchema fieldSchema : schema.getFields()) {
            if (fieldSchema.isRequired() && !hasField(fieldSchema.getId())) {
                errors.add("Required field missing: " + fieldSchema.getId());
            }
        }

        // Check field types and lengths
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String fieldId = entry.getKey();
            Object value = entry.getValue();

            Optional<FieldSchema> fieldSchemaOpt = schema.getField(fieldId);
            if (fieldSchemaOpt.isEmpty()) {
                // Unknown field - could be a warning
                log.debug("Unknown field in message: {}", fieldId);
                continue;
            }

            FieldSchema fieldSchema = fieldSchemaOpt.get();
            String strValue = value != null ? value.toString() : "";

            // Check length
            if (strValue.length() > fieldSchema.getLength()) {
                errors.add("Field '" + fieldId + "' exceeds max length " + fieldSchema.getLength() +
                        " (actual: " + strValue.length() + ")");
            }

            // Check numeric type
            if (fieldSchema.getType() == FieldSchema.FieldDataType.NUMERIC) {
                if (!strValue.matches("\\d*")) {
                    errors.add("Field '" + fieldId + "' must be numeric");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Creates a copy of this message.
     *
     * @return a new GenericMessage with copied fields
     */
    public GenericMessage copy() {
        GenericMessage copy = new GenericMessage(this.schema);
        copy.fields.putAll(this.fields);
        for (Map.Entry<String, Map<String, Object>> entry : this.compositeFields.entrySet()) {
            copy.compositeFields.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        if (this.rawData != null) {
            copy.rawData = Arrays.copyOf(this.rawData, this.rawData.length);
        }
        return copy;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /**
     * Returns a string representation of the message.
     *
     * @param includeDefaults if true, includes fields with default values from schema
     * @return string representation
     */
    public String toString(boolean includeDefaults) {
        StringBuilder sb = new StringBuilder();
        sb.append("GenericMessage[").append(schema.getName()).append("] {\n");

        Map<String, Object> fieldsToDisplay = includeDefaults ? getAllFieldsWithDefaults() : fields;

        // Track which fields have been displayed
        Set<String> displayedFields = new LinkedHashSet<>();

        for (Map.Entry<String, Object> entry : fieldsToDisplay.entrySet()) {
            String fieldId = entry.getKey();
            Object value = entry.getValue();
            displayedFields.add(fieldId);

            // Check if sensitive
            Optional<FieldSchema> fieldSchema = schema.getField(fieldId);
            boolean sensitive = fieldSchema.map(FieldSchema::isSensitive).orElse(false);
            boolean isDefault = includeDefaults && !fields.containsKey(fieldId);
            boolean isBitmap = fieldSchema.map(FieldSchema::isBitmap).orElse(false);

            sb.append("  ").append(fieldId).append("=");
            if (sensitive) {
                sb.append("****");
            } else if (isBitmap && value instanceof byte[] bitmapBytes) {
                // Display bitmap as list of present fields (from parsed response)
                sb.append(formatBitmapAsFieldList(fieldSchema.get(), bitmapBytes));
            } else if (value instanceof byte[] bytes) {
                sb.append("[").append(bytes.length).append(" bytes]");
            } else {
                sb.append(value);
            }
            if (isDefault) {
                sb.append(" (default)");
            }
            sb.append("\n");
        }

        // Display bitmap fields that weren't in fieldsToDisplay (for request messages)
        // These bitmaps are dynamically generated based on which controlled fields are set
        for (FieldSchema fieldSchema : schema.getFields()) {
            if (fieldSchema.isBitmap() && !displayedFields.contains(fieldSchema.getId())) {
                sb.append("  ").append(fieldSchema.getId()).append("=");
                sb.append(formatBitmapFromPresentFields(fieldSchema));
                sb.append(" (auto-generated)\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Formats a bitmap field value as a list of present fields.
     *
     * @param fieldSchema the bitmap field schema
     * @param bitmapBytes the bitmap byte array
     * @return formatted string showing which fields are present
     */
    private String formatBitmapAsFieldList(FieldSchema fieldSchema, byte[] bitmapBytes) {
        List<String> controls = fieldSchema.getControls();
        if (controls == null || controls.isEmpty()) {
            // No controls defined, show hex value
            return HexFormat.of().formatHex(bitmapBytes);
        }

        List<String> presentFields = new ArrayList<>();
        for (int i = 0; i < controls.size(); i++) {
            if (isBitSet(bitmapBytes, i)) {
                presentFields.add(controls.get(i));
            }
        }

        if (presentFields.isEmpty()) {
            return "(no fields present)";
        }

        return "[" + String.join(", ", presentFields) + "]";
    }

    /**
     * Formats a bitmap based on which controlled fields are currently set in the message.
     * Used for displaying request messages where bitmap is auto-generated.
     *
     * @param fieldSchema the bitmap field schema
     * @return formatted string showing which fields will be present in bitmap
     */
    private String formatBitmapFromPresentFields(FieldSchema fieldSchema) {
        List<String> controls = fieldSchema.getControls();
        if (controls == null || controls.isEmpty()) {
            return "(no controls defined)";
        }

        List<String> presentFields = new ArrayList<>();
        for (String controlledFieldId : controls) {
            if (fields.containsKey(controlledFieldId)) {
                presentFields.add(controlledFieldId);
            }
        }

        if (presentFields.isEmpty()) {
            return "(no fields present)";
        }

        return "[" + String.join(", ", presentFields) + "]";
    }

    /**
     * Checks if a specific bit is set in the bitmap.
     * Bit 0 is MSB of first byte, bit 7 is LSB of first byte, etc.
     *
     * @param bitmap   the bitmap byte array
     * @param bitIndex the bit index (0-based)
     * @return true if the bit is set
     */
    private boolean isBitSet(byte[] bitmap, int bitIndex) {
        int byteIndex = bitIndex / 8;
        int bitPosition = 7 - (bitIndex % 8);  // MSB first
        if (byteIndex >= bitmap.length) {
            return false;
        }
        return (bitmap[byteIndex] & (1 << bitPosition)) != 0;
    }

    /**
     * Validation result.
     */
    public record ValidationResult(boolean valid, List<String> errors) {
        public boolean isValid() {
            return valid;
        }
    }
}

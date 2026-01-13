package com.fep.message.generic.schema;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Schema definition for a message field.
 * Supports various field types including composite (nested) fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonClassDescription("Schema definition for a message field")
public class FieldSchema {

    /**
     * Unique identifier for the field (e.g., "terminalId", "track2").
     */
    @JsonProperty(required = true)
    @JsonPropertyDescription("Unique identifier for the field")
    private String id;

    /**
     * Human-readable field name for display.
     */
    @JsonPropertyDescription("name of the field")
    private String name;

    /**
     * Field description.
     */
    @JsonPropertyDescription("description of the field")
    private String description;

    /**
     * Data type of the field.
     */
    @Builder.Default
    @JsonPropertyDescription("data type of the field")
    private FieldDataType type = FieldDataType.ALPHANUMERIC;

    /**
     * Fixed length or maximum length for variable-length fields.
     */
    @JsonProperty(required = true)
    @JsonPropertyDescription("Fixed length or maximum length for variable-length fields")
    private int length;

    /**
     * Length type (FIXED, LLVAR, LLLVAR, LLLLVAR).
     */
    @JsonPropertyDescription("length type of the field")
    @Builder.Default
    private GenericLengthType lengthType = GenericLengthType.FIXED;

    /**
     * Data encoding (ASCII, BCD, EBCDIC, HEX, BINARY, PACKED_DECIMAL).
     */
    @Builder.Default
    @JsonPropertyDescription("encoding of the field")
    private String encoding = "ASCII";

    /**
     * Length prefix encoding for variable-length fields.
     */
    @JsonProperty("lengthEncoding")
    @Builder.Default
    @JsonPropertyDescription("length prefix encoding for variable-length fields")
    private String lengthEncoding = "BCD";

    /**
     * Padding configuration.
     */
    @JsonPropertyDescription("padding configuration")
    private PaddingConfig padding;

    /**
     * Whether this field contains sensitive data (masked in logs).
     */
    @Builder.Default
    @JsonPropertyDescription("whether this field contains sensitive data")
    private boolean sensitive = false;

    /**
     * Whether this field is required.
     */
    @Builder.Default
    @JsonPropertyDescription("whether this field is required")
    private boolean required = false;

    /**
     * Default value for the field.
     */
    @JsonPropertyDescription("default value for the field")
    @JsonProperty("defaultValue")
    private String defaultValue;

    /**
     * Child fields for composite type.
     */
    @JsonPropertyDescription("child fields for composite type")
    private List<FieldSchema> fields;

    /**
     * Field IDs controlled by this bitmap field.
     */
    @JsonPropertyDescription("field IDs controlled by this bitmap field")
    private List<String> controls;

    /**
     * Custom codec class name (optional).
     */
    @JsonPropertyDescription("custom codec class name (optional)")
    private String customCodec;

    /**
     * Checks if this is a fixed-length field.
     */
    public boolean isFixedLength() {
        return lengthType == GenericLengthType.FIXED;
    }

    /**
     * Checks if this is a variable-length field.
     */
    public boolean isVariableLength() {
        return lengthType != GenericLengthType.FIXED;
    }

    /**
     * Checks if this is a composite field with nested fields.
     */
    public boolean isComposite() {
        return type == FieldDataType.COMPOSITE && fields != null && !fields.isEmpty();
    }

    /**
     * Checks if this is a bitmap field.
     */
    public boolean isBitmap() {
        return type == FieldDataType.BITMAP;
    }

    /**
     * Gets the effective padding character.
     */
    public char getEffectivePaddingChar() {
        if (padding != null && padding.getCharacter() != null) {
            return padding.getCharacter().charAt(0);
        }
        return type == FieldDataType.NUMERIC ? '0' : ' ';
    }

    /**
     * Gets effective padding direction (left or right).
     */
    public boolean isLeftPadding() {
        if (padding != null && padding.getDirection() != null) {
            return padding.getDirection() == PaddingDirection.LEFT;
        }
        return type == FieldDataType.NUMERIC;
    }

    /**
     * Gets the number of digits in the length prefix.
     */
    public int getLengthPrefixDigits() {
        return switch (lengthType) {
            case FIXED -> 0;
            case LLVAR -> 2;
            case LLLVAR -> 3;
            case LLLLVAR -> 4;
        };
    }

    /**
     * Field data types.
     */
    public enum FieldDataType {
        NUMERIC,
        ALPHA,
        ALPHANUMERIC,
        BINARY,
        TRACK2,
        COMPOSITE,
        BITMAP
    }

    public enum FieldRequired {
        True,
        False,
        AutoGen
    }

    /**
     * Length types for variable-length fields.
     */
    public enum GenericLengthType {
        FIXED,
        LLVAR,
        LLLVAR,
        LLLLVAR
    }

    /**
     * Padding configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaddingConfig {
        @JsonProperty("char")
        private String character;
        private PaddingDirection direction;
    }

    /**
     * Padding direction enum.
     * Determines whether padding is applied to the left or right of the value.
     */
    public enum PaddingDirection {
        LEFT("left"),
        RIGHT("right");

        private final String value;

        PaddingDirection(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static PaddingDirection fromString(String value) {
            if (value == null) {
                return RIGHT;
            }
            String normalized = value.toLowerCase().trim();
            for (PaddingDirection direction : values()) {
                if (direction.value.equals(normalized) || direction.name().equalsIgnoreCase(normalized)) {
                    return direction;
                }
            }
            return RIGHT;
        }
    }
}

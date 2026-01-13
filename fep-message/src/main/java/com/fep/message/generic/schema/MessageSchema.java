package com.fep.message.generic.schema;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Schema definition for a complete message format.
 * Supports header, body fields, and trailer sections.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonClassDescription("Schema definition for a complete message format")
public class MessageSchema {

    /**
     * Schema format identifier.
     */
    @JsonProperty("$schema")
    @Builder.Default
    private String schema = "fep-message-schema-v1";

    /**
     * Protocol name (e.g., "NCR NDC Protocol").
     */
    @JsonProperty(required = true)
    @JsonPropertyDescription("Protocol name (e.g., NCR NDC Protocol)")
    @Schema(example = "FISC ATM Protocol")
    private String name;

    /**
     * Schema version.
     */
    @Schema(example = "1.0.0")
    private String version;

    /**
     * Vendor name (e.g., "NCR", "Diebold", "Wincor").
     */
    @Schema(example = "NCR")
    private String vendor;

    /**
     * Protocol description.
     */
    @Schema(example = "FISC interbank ATM transaction protocol")
    private String description;

    /**
     * Header section (length prefix, protocol ID, etc.).
     */
    private HeaderSection header;

    /**
     * Main body fields.
     */
    private List<FieldSchema> fields;

    /**
     * Trailer section (MAC, checksum, etc.).
     */
    private TrailerSection trailer;

    /**
     * Global encoding configuration.
     */
    private EncodingConfig encoding;

    /**
     * Validation rules.
     */
    private ValidationConfig validation;

    /**
     * Gets a field by its ID.
     */
    public Optional<FieldSchema> getField(String fieldId) {
        if (fields == null) {
            return Optional.empty();
        }
        return fields.stream()
                .filter(f -> fieldId.equals(f.getId()))
                .findFirst();
    }

    /**
     * Gets all fields as a map (id -> schema).
     */
    public Map<String, FieldSchema> getFieldMap() {
        Map<String, FieldSchema> map = new LinkedHashMap<>();
        if (fields != null) {
            for (FieldSchema field : fields) {
                map.put(field.getId(), field);
            }
        }
        return map;
    }

    /**
     * Gets the default character encoding.
     */
    public String getDefaultEncoding() {
        if (encoding != null && encoding.getDefaultCharset() != null) {
            return encoding.getDefaultCharset();
        }
        return "ASCII";
    }

    /**
     * Header section definition.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeaderSection {
        private List<FieldSchema> fields;

        /**
         * Whether to include message length in header.
         */
        @Builder.Default
        private boolean includeLength = true;

        /**
         * Length field encoding.
         */
        @Builder.Default
        private String lengthEncoding = "BCD";

        /**
         * Length field size in bytes.
         */
        @Builder.Default
        private int lengthBytes = 2;

        /**
         * Whether length includes header itself.
         */
        @Builder.Default
        private boolean lengthIncludesHeader = false;
    }

    /**
     * Trailer section definition.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrailerSection {
        private List<FieldSchema> fields;
    }

    /**
     * Global encoding configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EncodingConfig {
        /**
         * Default character encoding (ASCII, EBCDIC).
         */
        @Builder.Default
        private String defaultCharset = "ASCII";

        /**
         * BCD packing mode (LEFT_JUSTIFIED, RIGHT_JUSTIFIED).
         */
        @Builder.Default
        private BcdPacking bcdPacking = BcdPacking.RIGHT_JUSTIFIED;

        /**
         * Byte order (BIG_ENDIAN, LITTLE_ENDIAN).
         */
        @Builder.Default
        private Endianness endianness = Endianness.BIG_ENDIAN;
    }

    /**
     * BCD packing mode enum.
     * Determines how odd-length BCD values are padded.
     */
    public enum BcdPacking {
        LEFT_JUSTIFIED("LEFT_JUSTIFIED"),
        RIGHT_JUSTIFIED("RIGHT_JUSTIFIED");

        private final String value;

        BcdPacking(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static BcdPacking fromString(String value) {
            if (value == null) {
                return RIGHT_JUSTIFIED;
            }
            String normalized = value.toUpperCase().replace("-", "_");
            for (BcdPacking packing : values()) {
                if (packing.value.equals(normalized) || packing.name().equals(normalized)) {
                    return packing;
                }
            }
            return RIGHT_JUSTIFIED;
        }
    }

    /**
     * Byte order enum.
     * Determines how multi-byte values are serialized.
     */
    public enum Endianness {
        BIG_ENDIAN("BIG_ENDIAN"),
        LITTLE_ENDIAN("LITTLE_ENDIAN");

        private final String value;

        Endianness(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static Endianness fromString(String value) {
            if (value == null) {
                return BIG_ENDIAN;
            }
            String normalized = value.toUpperCase().replace("-", "_");
            for (Endianness endian : values()) {
                if (endian.value.equals(normalized) || endian.name().equals(normalized)) {
                    return endian;
                }
            }
            return BIG_ENDIAN;
        }
    }

    /**
     * Validation configuration.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationConfig {
        private List<ValidationRule> rules;
    }

    /**
     * Validation rule.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationRule {
        /**
         * Rule type (required, conditional, pattern, etc.).
         */
        private String type;

        /**
         * Fields affected by this rule.
         */
        private List<String> fields;

        /**
         * Condition for conditional rules.
         */
        @JsonProperty("if")
        private Condition condition;

        /**
         * Action when condition is met.
         */
        @JsonProperty("then")
        private Action action;
    }

    /**
     * Condition for conditional validation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Condition {
        private String field;
        private String equals;
        private String matches;
    }

    /**
     * Action for conditional validation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        private List<String> required;
    }
}

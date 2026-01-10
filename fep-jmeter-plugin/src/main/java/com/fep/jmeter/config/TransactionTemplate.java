package com.fep.jmeter.config;

import com.fep.message.iso8583.Iso8583Message;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Transaction Template for flexible ISO 8583 message configuration.
 *
 * <p>Allows defining custom transaction types with arbitrary field configurations.
 * Templates can be loaded from JSON files or configured programmatically.
 *
 * <p>Example JSON format:
 * <pre>
 * {
 *   "name": "Custom Withdrawal",
 *   "mti": "0200",
 *   "description": "ATM cash withdrawal transaction",
 *   "fields": {
 *     "3": "010000",
 *     "4": "${amount}",
 *     "11": "${stan}",
 *     "41": "${terminalId}",
 *     "43": "ATM Location"
 *   },
 *   "validationRules": "REQUIRED:2,3,4,11,41"
 * }
 * </pre>
 */
public class TransactionTemplate implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String name;
    private String mti;
    private String description;
    private Map<Integer, String> fields;
    private String validationRules;
    private boolean enabled;
    private int priority;

    public TransactionTemplate() {
        this.fields = new LinkedHashMap<>();
        this.enabled = true;
        this.priority = 0;
    }

    public TransactionTemplate(String name, String mti) {
        this();
        this.name = name;
        this.mti = mti;
    }

    /**
     * Creates an ISO 8583 message from this template.
     *
     * @param variables Variable substitution map for ${variable} placeholders
     * @return Configured ISO 8583 message
     */
    public Iso8583Message createMessage(Map<String, String> variables) {
        Iso8583Message message = new Iso8583Message(mti);

        for (Map.Entry<Integer, String> entry : fields.entrySet()) {
            String value = entry.getValue();

            // Substitute variables
            if (variables != null) {
                value = substituteVariables(value, variables);
            }

            message.setField(entry.getKey(), value);
        }

        return message;
    }

    /**
     * Creates an ISO 8583 message from this template without variable substitution.
     */
    public Iso8583Message createMessage() {
        return createMessage(null);
    }

    /**
     * Substitutes ${variable} placeholders in the value.
     */
    private String substituteVariables(String value, Map<String, String> variables) {
        if (value == null || !value.contains("${")) {
            return value;
        }

        StringBuilder result = new StringBuilder(value);
        int start;
        while ((start = result.indexOf("${")) >= 0) {
            int end = result.indexOf("}", start);
            if (end < 0) break;

            String varName = result.substring(start + 2, end);
            String varValue = variables.get(varName);

            if (varValue != null) {
                result.replace(start, end + 1, varValue);
            } else {
                // Skip this variable if not found
                break;
            }
        }

        return result.toString();
    }

    /**
     * Loads a transaction template from JSON string.
     */
    public static TransactionTemplate fromJson(String json) {
        return GSON.fromJson(json, TransactionTemplate.class);
    }

    /**
     * Loads multiple transaction templates from JSON array string.
     */
    public static List<TransactionTemplate> listFromJson(String json) {
        return GSON.fromJson(json, new TypeToken<List<TransactionTemplate>>(){}.getType());
    }

    /**
     * Loads a transaction template from a JSON file.
     */
    public static TransactionTemplate fromFile(Path path) throws IOException {
        String json = Files.readString(path);
        return fromJson(json);
    }

    /**
     * Loads multiple transaction templates from a JSON file.
     */
    public static List<TransactionTemplate> listFromFile(Path path) throws IOException {
        String json = Files.readString(path);
        return listFromJson(json);
    }

    /**
     * Converts this template to JSON string.
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Converts multiple templates to JSON array string.
     */
    public static String listToJson(List<TransactionTemplate> templates) {
        return GSON.toJson(templates);
    }

    /**
     * Saves this template to a JSON file.
     */
    public void saveToFile(Path path) throws IOException {
        Files.writeString(path, toJson());
    }

    /**
     * Saves multiple templates to a JSON file.
     */
    public static void saveListToFile(List<TransactionTemplate> templates, Path path) throws IOException {
        Files.writeString(path, listToJson(templates));
    }

    // Builder pattern for fluent configuration
    public TransactionTemplate withName(String name) {
        this.name = name;
        return this;
    }

    public TransactionTemplate withMti(String mti) {
        this.mti = mti;
        return this;
    }

    public TransactionTemplate withDescription(String description) {
        this.description = description;
        return this;
    }

    public TransactionTemplate withField(int fieldNumber, String value) {
        this.fields.put(fieldNumber, value);
        return this;
    }

    public TransactionTemplate withFields(Map<Integer, String> fields) {
        this.fields.putAll(fields);
        return this;
    }

    public TransactionTemplate withValidationRules(String rules) {
        this.validationRules = rules;
        return this;
    }

    public TransactionTemplate withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public TransactionTemplate withPriority(int priority) {
        this.priority = priority;
        return this;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMti() {
        return mti;
    }

    public void setMti(String mti) {
        this.mti = mti;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<Integer, String> getFields() {
        return fields;
    }

    public void setFields(Map<Integer, String> fields) {
        this.fields = fields != null ? fields : new LinkedHashMap<>();
    }

    public String getValidationRules() {
        return validationRules;
    }

    public void setValidationRules(String validationRules) {
        this.validationRules = validationRules;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return String.format("TransactionTemplate{name='%s', mti='%s', fields=%d}",
            name, mti, fields.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionTemplate that = (TransactionTemplate) o;
        return Objects.equals(name, that.name) && Objects.equals(mti, that.mti);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, mti);
    }

    // Predefined templates for common transaction types
    public static class CommonTemplates {

        public static TransactionTemplate withdrawal() {
            return new TransactionTemplate("Withdrawal", "0200")
                .withDescription("ATM Cash Withdrawal")
                .withField(3, "010000")
                .withField(4, "${amount}")
                .withField(11, "${stan}")
                .withField(12, "${time}")
                .withField(13, "${date}")
                .withField(22, "051")
                .withField(25, "00")
                .withField(32, "${bankCode}")
                .withField(41, "${terminalId}")
                .withValidationRules("REQUIRED:2,3,4,11,41");
        }

        public static TransactionTemplate balanceInquiry() {
            return new TransactionTemplate("Balance Inquiry", "0200")
                .withDescription("Account Balance Inquiry")
                .withField(3, "310000")
                .withField(11, "${stan}")
                .withField(12, "${time}")
                .withField(13, "${date}")
                .withField(22, "051")
                .withField(25, "00")
                .withField(32, "${bankCode}")
                .withField(41, "${terminalId}")
                .withValidationRules("REQUIRED:2,3,11,41");
        }

        public static TransactionTemplate transfer() {
            return new TransactionTemplate("Fund Transfer", "0200")
                .withDescription("Interbank Fund Transfer")
                .withField(3, "400000")
                .withField(4, "${amount}")
                .withField(11, "${stan}")
                .withField(12, "${time}")
                .withField(13, "${date}")
                .withField(22, "051")
                .withField(25, "00")
                .withField(32, "${bankCode}")
                .withField(41, "${terminalId}")
                .withField(102, "${sourceAccount}")
                .withField(103, "${destAccount}")
                .withValidationRules("REQUIRED:2,3,4,11,41,102,103");
        }

        public static TransactionTemplate billPayment() {
            return new TransactionTemplate("Bill Payment", "0200")
                .withDescription("Utility Bill Payment")
                .withField(3, "500000")
                .withField(4, "${amount}")
                .withField(11, "${stan}")
                .withField(12, "${time}")
                .withField(13, "${date}")
                .withField(32, "${bankCode}")
                .withField(41, "${terminalId}")
                .withField(48, "${billerCode}")
                .withField(62, "${billReference}")
                .withValidationRules("REQUIRED:3,4,11,41,48,62");
        }

        public static TransactionTemplate signOn() {
            return new TransactionTemplate("Sign On", "0800")
                .withDescription("Network Management - Sign On")
                .withField(11, "${stan}")
                .withField(12, "${time}")
                .withField(13, "${date}")
                .withField(70, "001")
                .withValidationRules("REQUIRED:11,70");
        }

        public static TransactionTemplate signOff() {
            return new TransactionTemplate("Sign Off", "0800")
                .withDescription("Network Management - Sign Off")
                .withField(11, "${stan}")
                .withField(12, "${time}")
                .withField(13, "${date}")
                .withField(70, "002")
                .withValidationRules("REQUIRED:11,70");
        }

        public static TransactionTemplate echoTest() {
            return new TransactionTemplate("Echo Test", "0800")
                .withDescription("Network Management - Echo Test")
                .withField(11, "${stan}")
                .withField(12, "${time}")
                .withField(13, "${date}")
                .withField(70, "301")
                .withValidationRules("REQUIRED:11,70");
        }

        public static TransactionTemplate reversal() {
            return new TransactionTemplate("Reversal", "0400")
                .withDescription("Transaction Reversal")
                .withField(3, "${originalProcessingCode}")
                .withField(4, "${amount}")
                .withField(11, "${stan}")
                .withField(12, "${time}")
                .withField(13, "${date}")
                .withField(32, "${bankCode}")
                .withField(37, "${originalRrn}")
                .withField(41, "${terminalId}")
                .withField(90, "${originalDataElements}")
                .withValidationRules("REQUIRED:3,4,11,37,41,90");
        }

        public static List<TransactionTemplate> all() {
            return Arrays.asList(
                withdrawal(),
                balanceInquiry(),
                transfer(),
                billPayment(),
                signOn(),
                signOff(),
                echoTest(),
                reversal()
            );
        }
    }
}

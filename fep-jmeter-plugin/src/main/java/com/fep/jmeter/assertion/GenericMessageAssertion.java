package com.fep.jmeter.assertion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fep.jmeter.gui.GenericMessageAssertionGui;
import com.fep.jmeter.sampler.SchemaSource;
import com.fep.message.generic.message.GenericMessage;
import com.fep.message.generic.parser.GenericMessageParser;
import com.fep.message.generic.schema.JsonSchemaLoader;
import com.fep.message.generic.schema.MessageSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.assertions.Assertion;
import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterVariables;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;

/**
 * JMeter Assertion for validating Generic Message responses.
 *
 * <p>This assertion allows users to:
 * <ul>
 *   <li>Select a schema to parse the response</li>
 *   <li>Define expected field values in JSON format</li>
 *   <li>Use various matching operators (eq, regex, contains, etc.)</li>
 * </ul>
 *
 * <p>Example expected values JSON:
 * <pre>
 * {
 *   "responseCode": "00",
 *   "mti": "0210",
 *   "amount": { "$regex": "^\\d{12}$" }
 * }
 * </pre>
 */
@Slf4j
public class GenericMessageAssertion extends AbstractTestElement
        implements Assertion, Serializable {

    private static final long serialVersionUID = 1L;

    // Property names
    public static final String SCHEMA_FILE = "GenericMessageAssertion.schemaFile";
    public static final String SELECTED_SCHEMA = "GenericMessageAssertion.selectedSchema";
    public static final String EXPECTED_VALUES = "GenericMessageAssertion.expectedValues";

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final GenericMessageParser parser = new GenericMessageParser();

    public GenericMessageAssertion() {
        super();
        setProperty(TestElement.GUI_CLASS, GenericMessageAssertionGui.class.getName());
    }

    @Override
    public AssertionResult getResult(SampleResult sampleResult) {
        AssertionResult result = new AssertionResult(getName());

        try {
            // Get raw response bytes from JMeter variable (set by AtmSimulatorSampler)
            JMeterVariables vars = JMeterContextService.getContext().getVariables();
            byte[] responseBytes = null;
            if (vars != null) {
                responseBytes = (byte[]) vars.getObject("RESPONSE_RAW_BYTES");
            }

            if (responseBytes == null || responseBytes.length == 0) {
                result.setFailure(true);
                result.setFailureMessage("No response data available (RESPONSE_RAW_BYTES not found). " +
                        "Ensure you are using ATM Simulator Sampler.");
                return result;
            }

            // Load schema
            MessageSchema schema = loadSchema();
            if (schema == null) {
                result.setFailure(true);
                result.setFailureMessage("Failed to load schema");
                return result;
            }

            // Parse response message
            // skipLengthField=true because length field is stripped by decoder
            GenericMessage response = parser.parse(responseBytes, schema, true);

            // Parse expected values
            String expectedJson = getExpectedValues();
            if (expectedJson == null || expectedJson.isBlank()) {
                // No expected values - assertion passes
                return result;
            }

            Map<String, Object> expectedValues = objectMapper.readValue(
                    expectedJson, new TypeReference<Map<String, Object>>() {});

            // Validate each expected field
            StringBuilder failures = new StringBuilder();
            int failureCount = 0;

            for (Map.Entry<String, Object> entry : expectedValues.entrySet()) {
                String fieldId = entry.getKey();
                Object expectedValue = entry.getValue();

                // Create matcher from expected value
                FieldMatcher matcher = FieldMatcher.fromValue(fieldId, expectedValue);

                // Get actual value from response
                String actualValue = response.getFieldAsString(fieldId);

                // Perform match
                FieldMatcher.MatchResult matchResult = matcher.match(actualValue);

                if (!matchResult.isSuccess()) {
                    if (failureCount > 0) {
                        failures.append("\n");
                    }
                    failures.append(matchResult.getFailureMessage());
                    failureCount++;
                }
            }

            if (failureCount > 0) {
                result.setFailure(true);
                result.setFailureMessage(failures.toString());
            }

        } catch (Exception e) {
            log.error("Assertion error", e);
            result.setError(true);
            result.setFailureMessage("Assertion error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Loads the message schema from the configured file.
     */
    private MessageSchema loadSchema() {
        String schemaFile = getSchemaFile();
        if (schemaFile == null || schemaFile.isBlank()) {
            schemaFile = SchemaSource.getDefaultSchemaPath();
        }

        String selectedSchema = getSelectedSchema();
        if (selectedSchema == null || selectedSchema.isBlank()) {
            selectedSchema = "FISC ATM Format";
        }

        try {
            return JsonSchemaLoader.fromCollectionFile(Path.of(schemaFile), selectedSchema);
        } catch (Exception e) {
            log.error("Failed to load schema: {} from {}", selectedSchema, schemaFile, e);
            return null;
        }
    }

    // Getters and Setters

    public String getSchemaFile() {
        return getPropertyAsString(SCHEMA_FILE, SchemaSource.getDefaultSchemaPath());
    }

    public void setSchemaFile(String schemaFile) {
        setProperty(SCHEMA_FILE, schemaFile);
    }

    public String getSelectedSchema() {
        return getPropertyAsString(SELECTED_SCHEMA, "FISC ATM Format");
    }

    public void setSelectedSchema(String selectedSchema) {
        setProperty(SELECTED_SCHEMA, selectedSchema);
    }

    public String getExpectedValues() {
        return getPropertyAsString(EXPECTED_VALUES, "");
    }

    public void setExpectedValues(String expectedValues) {
        setProperty(EXPECTED_VALUES, expectedValues);
    }
}

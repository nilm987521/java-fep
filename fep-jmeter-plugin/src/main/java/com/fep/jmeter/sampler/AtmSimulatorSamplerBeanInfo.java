package com.fep.jmeter.sampler;

import org.apache.jmeter.testbeans.BeanInfoSupport;
import org.apache.jmeter.testbeans.gui.TextAreaEditor;

import java.beans.PropertyDescriptor;

/**
 * BeanInfo for AtmSimulatorSampler.
 *
 * <p>Defines the GUI elements for configuring the ATM Simulator.
 * Uses Generic Schema for flexible message format definition.
 */
public class AtmSimulatorSamplerBeanInfo extends BeanInfoSupport {

    // Property groups
    private static final String CONNECTION_GROUP = "connection";
    private static final String GENERIC_SCHEMA_GROUP = "genericSchema";

    // Schema options
    private static final String[] SCHEMA_SOURCES = SchemaSource.names();
    private static final String[] PRESET_SCHEMAS = PresetSchema.names();

    public AtmSimulatorSamplerBeanInfo() {
        super(AtmSimulatorSampler.class);

        // Create property groups
        createPropertyGroup(CONNECTION_GROUP, new String[]{
            AtmSimulatorSampler.FEP_HOST,
            AtmSimulatorSampler.FEP_PORT,
            AtmSimulatorSampler.CONNECTION_TIMEOUT,
            AtmSimulatorSampler.READ_TIMEOUT,
            AtmSimulatorSampler.EXPECT_RESPONSE
        });

        createPropertyGroup(GENERIC_SCHEMA_GROUP, new String[]{
            AtmSimulatorSampler.SCHEMA_SOURCE,
            AtmSimulatorSampler.PRESET_SCHEMA,
            AtmSimulatorSampler.SCHEMA_FILE,
            AtmSimulatorSampler.SCHEMA_CONTENT,
            AtmSimulatorSampler.FIELD_VALUES
        });

        // ===== Connection properties =====
        PropertyDescriptor fepHostProp = property(AtmSimulatorSampler.FEP_HOST);
        fepHostProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        fepHostProp.setValue(DEFAULT, "localhost");
        fepHostProp.setDisplayName("FEP Host");
        fepHostProp.setShortDescription("Hostname or IP address of the FEP server.");

        PropertyDescriptor fepPortProp = property(AtmSimulatorSampler.FEP_PORT);
        fepPortProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        fepPortProp.setValue(DEFAULT, 8080);
        fepPortProp.setDisplayName("FEP Port");
        fepPortProp.setShortDescription("TCP port of the FEP server for ATM connections.");

        PropertyDescriptor connTimeoutProp = property(AtmSimulatorSampler.CONNECTION_TIMEOUT);
        connTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        connTimeoutProp.setValue(DEFAULT, 10000);
        connTimeoutProp.setDisplayName("Connection Timeout (ms)");
        connTimeoutProp.setShortDescription("Maximum time to wait for connection in milliseconds.");

        PropertyDescriptor readTimeoutProp = property(AtmSimulatorSampler.READ_TIMEOUT);
        readTimeoutProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        readTimeoutProp.setValue(DEFAULT, 30000);
        readTimeoutProp.setDisplayName("Read Timeout (ms)");
        readTimeoutProp.setShortDescription("Maximum time to wait for response in milliseconds.");

        PropertyDescriptor expectResponseProp = property(AtmSimulatorSampler.EXPECT_RESPONSE);
        expectResponseProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        expectResponseProp.setValue(DEFAULT, Boolean.TRUE);
        expectResponseProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        expectResponseProp.setValue(NOT_OTHER, Boolean.TRUE);
        expectResponseProp.setDisplayName("Expect Response");
        expectResponseProp.setShortDescription(
            "Wait for response after sending.\n" +
            "true - Wait for response (default)\n" +
            "false - Fire and forget (no response expected)"
        );

        // ===== Generic Schema properties =====
        PropertyDescriptor schemaSourceProp = property(AtmSimulatorSampler.SCHEMA_SOURCE);
        schemaSourceProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        schemaSourceProp.setValue(DEFAULT, SchemaSource.PRESET.name());
        schemaSourceProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        schemaSourceProp.setValue(NOT_OTHER, Boolean.TRUE);
        schemaSourceProp.setValue(TAGS, SCHEMA_SOURCES);
        schemaSourceProp.setDisplayName("Schema Source");
        schemaSourceProp.setShortDescription(
            "Source of message schema.\n" +
            "PRESET - Use built-in schema for common ATM protocols\n" +
            "FILE - Load schema from external JSON file\n" +
            "INLINE - Use inline JSON schema definition"
        );

        PropertyDescriptor presetSchemaProp = property(AtmSimulatorSampler.PRESET_SCHEMA);
        presetSchemaProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        presetSchemaProp.setValue(DEFAULT, PresetSchema.FISC_ATM.name());
        presetSchemaProp.setValue(NOT_EXPRESSION, Boolean.TRUE);
        presetSchemaProp.setValue(NOT_OTHER, Boolean.TRUE);
        presetSchemaProp.setValue(TAGS, PRESET_SCHEMAS);
        presetSchemaProp.setDisplayName("Preset Schema");
        presetSchemaProp.setShortDescription(
            "Built-in schema to use (only when Schema Source = PRESET).\n" +
            "FISC_ATM - Taiwan FISC ATM message format\n" +
            "NCR_NDC - NCR NDC ATM protocol\n" +
            "DIEBOLD_91X - Diebold 91x ATM protocol\n" +
            "WINCOR_DDC - Wincor Nixdorf DDC protocol\n" +
            "ISO8583_GENERIC - Generic ISO 8583 format via schema"
        );

        PropertyDescriptor schemaFileProp = property(AtmSimulatorSampler.SCHEMA_FILE);
        schemaFileProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        schemaFileProp.setValue(DEFAULT, "");
        schemaFileProp.setDisplayName("Schema File Path");
        schemaFileProp.setShortDescription(
            "Path to JSON schema file (only when Schema Source = FILE).\n" +
            "Absolute or relative path to the schema JSON file.\n" +
            "Example: /path/to/my-protocol-schema.json\n" +
            "Supports JMeter variables: ${schemaPath}"
        );

        PropertyDescriptor schemaContentProp = property(AtmSimulatorSampler.SCHEMA_CONTENT);
        schemaContentProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        schemaContentProp.setValue(DEFAULT, "");
        schemaContentProp.setPropertyEditorClass(TextAreaEditor.class);
        schemaContentProp.setDisplayName("Inline Schema (JSON)");
        schemaContentProp.setShortDescription(
            "Inline JSON schema definition (only when Schema Source = INLINE).\n\n" +
            "Example:\n" +
            "{\n" +
            "  \"name\": \"My Protocol\",\n" +
            "  \"fields\": [\n" +
            "    { \"id\": \"command\", \"length\": 2, \"encoding\": \"ASCII\" },\n" +
            "    { \"id\": \"terminalId\", \"length\": 8, \"encoding\": \"ASCII\" },\n" +
            "    { \"id\": \"amount\", \"length\": 12, \"encoding\": \"BCD\" }\n" +
            "  ]\n" +
            "}"
        );

        PropertyDescriptor fieldValuesProp = property(AtmSimulatorSampler.FIELD_VALUES);
        fieldValuesProp.setValue(NOT_UNDEFINED, Boolean.TRUE);
        fieldValuesProp.setValue(DEFAULT, "");
        fieldValuesProp.setPropertyEditorClass(TextAreaEditor.class);
        fieldValuesProp.setDisplayName("Field Values (JSON)");
        fieldValuesProp.setShortDescription(
            "JSON object with field values.\n" +
            "Keys are field IDs from the schema, values are the data to send.\n\n" +
            "Example:\n" +
            "{\n" +
            "  \"mti\": \"0200\",\n" +
            "  \"processingCode\": \"010000\",\n" +
            "  \"amount\": \"000000100000\",\n" +
            "  \"pan\": \"4111111111111111\",\n" +
            "  \"terminalId\": \"${atmId}\"\n" +
            "}\n\n" +
            "Auto-generated variables (if not provided):\n" +
            "  ${stan} - System Trace Audit Number\n" +
            "  ${time} - Local time (HHmmss)\n" +
            "  ${date} - Local date (MMdd)\n" +
            "  ${rrn} - Retrieval Reference Number\n" +
            "Supports JMeter variables for all values."
        );
    }
}
